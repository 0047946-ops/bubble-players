package com.smoothplayer.app.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.smoothplayer.app.network.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 核心播放管理器：
 * - Media3 / ExoPlayer 1.10.1
 * - 硬體解碼優先
 * - 智慧緩衝
 * - 卡頓偵測與動態畫質
 * - 錯誤恢復（最多 4 次）
 * - 網路恢復自動繼續
 * - 位置保存
 * - 支援 MP4 / HLS / DASH
 */
class SmoothPlayerManager(
    private val context: Context,
    private val networkMonitor: NetworkMonitor,
    private val positionStore: PlaybackPositionStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var stallDetector: StallDetector? = null
    private val errorHandler = ErrorRecoveryHandler(scope)

    private var currentUrl: String = ""
    private var currentMediaId: String = ""
    private var maxBitrateLimit: Int = Int.MAX_VALUE
    private var isRecovering = false

    var listener: Listener? = null

    interface Listener {
        fun onPlaybackStateChanged(state: Int)
        fun onIsPlayingChanged(isPlaying: Boolean)
        fun onError(message: String, retry: Int)
        fun onNetworkMessage(message: String)
        fun onPositionUpdate(position: Long, duration: Long)
    }

    fun initialize(): ExoPlayer {
        release()

        val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()

        trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setMaxVideoBitrate(maxBitrateLimit)
                .setForceHighestSupportedBitrate(false)
                .build()
        }

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(12_000)
            .setUserAgent("SmoothPlayer/1.0 (Android; Media3/1.10.1)")

        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

        val loadControl = SmartLoadControl(networkMonitor).createAdaptiveLoadControl()

        player = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector!!)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .also { exo ->
                exo.addListener(playerListener)
                exo.playWhenReady = true
                exo.setHandleAudioBecomingNoisy(true)
            }

        stallDetector = StallDetector(
            player = player!!,
            onStallRisk = { level -> handleStallRisk(level) },
            onRecovered = { handleStallRecovered() }
        )

        observeNetwork()
        return player!!
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            listener?.onPlaybackStateChanged(playbackState)
            if (playbackState == Player.STATE_READY) {
                errorHandler.reset()
                isRecovering = false
                stallDetector?.start()
            }
            if (playbackState == Player.STATE_ENDED) {
                positionStore.clearPosition(currentMediaId)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            listener?.onIsPlayingChanged(isPlaying)
            if (isPlaying) {
                saveCurrentPosition()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val pos = player?.currentPosition ?: 0L
            errorHandler.onError(
                player = player!!,
                error = error,
                mediaUrl = currentUrl,
                currentPosition = pos,
                onRetry = { retry, position ->
                    isRecovering = true
                    listener?.onError("播放錯誤，正在嘗試恢復 ($retry/4)", retry)
                    retryPlayback(position, retry)
                },
                onGiveUp = {
                    listener?.onError("無法恢復播放：${error.message}", 4)
                }
            )
        }
    }

    fun prepareAndPlay(url: String, mediaId: String = url) {
        currentUrl = url
        currentMediaId = mediaId
        val exo = player ?: initialize()

        val savedPos = positionStore.getPosition(mediaId)
        val mediaItem = MediaItem.fromUri(Uri.parse(url))

        exo.setMediaItem(mediaItem, savedPos)
        exo.prepare()
        exo.playWhenReady = true
        stallDetector?.start()
    }

    private fun retryPlayback(position: Long, retryIndex: Int) {
        val exo = player ?: return
        when (retryIndex) {
            1 -> {
                exo.seekTo(position)
                exo.prepare()
                exo.play()
            }
            2 -> {
                exo.stop()
                exo.setMediaItem(MediaItem.fromUri(Uri.parse(currentUrl)), position)
                exo.prepare()
                exo.play()
            }
            3 -> {
                // 強制降低最高位元率
                maxBitrateLimit = 1_500_000
                applyMaxBitrate(maxBitrateLimit)
                exo.stop()
                exo.setMediaItem(MediaItem.fromUri(Uri.parse(currentUrl)), position)
                exo.prepare()
                exo.play()
            }
            4 -> {
                maxBitrateLimit = 800_000
                applyMaxBitrate(maxBitrateLimit)
                exo.stop()
                exo.setMediaItem(MediaItem.fromUri(Uri.parse(currentUrl)), position)
                exo.prepare()
                exo.play()
            }
        }
    }

    private fun handleStallRisk(level: Int) {
        when (level) {
            1 -> applyMaxBitrate(2_500_000)
            2 -> applyMaxBitrate(1_200_000)
            3 -> applyMaxBitrate(600_000)
        }
        listener?.onNetworkMessage("偵測到卡頓風險，已降低畫質")
    }

    private fun handleStallRecovered() {
        // 網路恢復後逐步交回自適應
        maxBitrateLimit = Int.MAX_VALUE
        applyMaxBitrate(maxBitrateLimit)
        listener?.onNetworkMessage("網路穩定，畫質已恢復自適應")
    }

    private fun applyMaxBitrate(bitrate: Int) {
        trackSelector?.let { selector ->
            val params = selector.parameters.buildUpon()
                .setMaxVideoBitrate(bitrate)
                .build()
            selector.parameters = params
        }
    }

    private fun observeNetwork() {
        scope.launch {
            var previousConnected = networkMonitor.isConnected()
            networkMonitor.state.collectLatest { state ->
                if (!state.isConnected && previousConnected) {
                    // 斷線：保留狀態
                    saveCurrentPosition()
                    player?.playWhenReady = false
                    listener?.onNetworkMessage("網路已斷線，保留播放狀態中…")
                } else if (state.isConnected && !previousConnected) {
                    // 恢復
                    listener?.onNetworkMessage("網路已恢復，正在恢復播放…")
                    val pos = positionStore.getPosition(currentMediaId)
                    if (currentUrl.isNotBlank()) {
                        player?.seekTo(pos)
                        player?.prepare()
                        player?.playWhenReady = true
                    }
                    // 恢復後交回自適應
                    maxBitrateLimit = Int.MAX_VALUE
                    applyMaxBitrate(maxBitrateLimit)
                }
                previousConnected = state.isConnected
            }
        }
    }

    fun saveCurrentPosition() {
        val exo = player ?: return
        if (currentMediaId.isNotBlank() && exo.duration > 0) {
            positionStore.savePosition(currentMediaId, exo.currentPosition, exo.duration)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed.coerceIn(0.5f, 2.0f))
    }

    fun seekForward() {
        player?.seekForward()
    }

    fun seekBack() {
        player?.seekBack()
    }

    fun togglePlayPause() {
        player?.let {
            it.playWhenReady = !it.playWhenReady
        }
    }

    fun getPlayer(): ExoPlayer? = player

    fun release() {
        saveCurrentPosition()
        stallDetector?.stop()
        player?.removeListener(playerListener)
        player?.release()
        player = null
        trackSelector = null
        stallDetector = null
    }
}
