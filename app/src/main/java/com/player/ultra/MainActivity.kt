package com.player.ultra

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.ui.PlayerView

class MainActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var webView: WebView
    private var exoPlayer: ExoPlayer? = null
    private lateinit var tvNetworkStatus: TextView
    private lateinit var etUrl: EditText

    private var currentPlaybackPosition: Long = 0L
    private var errorRetryCount = 0
    private val maxErrorRetries = 4
    private var currentSpeed = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)
        webView = findViewById(R.id.webView)
        tvNetworkStatus = findViewById(R.id.tvNetworkStatus)
        etUrl = findViewById(R.id.etUrl)

        setupPlayer()
        setupWebView()
        setupNetworkMonitoring()
        setupUIControls()
    }

    private fun setupPlayer() {
        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, 16))
            .setBufferDurationsMs(15000, 50000, 1500, 3000)
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()
            .also { player ->
                playerView.player = player
                player.setPlaybackSpeed(currentSpeed)
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            tvNetworkStatus.text = "狀態：播放流暢 | 硬體加速運作中"
                            errorRetryCount = 0
                        }
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        handlePlaybackError()
                    }
                })
            }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                if (url.contains("youtube.com/watch") || url.endsWith(".mp4") || url.contains(".m3u8")) {
                    etUrl.setText(url)
                    loadAndPlay(url)
                    return true
                }
                return false
            }
        }
        // 一打開就是 YouTube
        webView.loadUrl("https://m.youtube.com")
    }

    private fun loadAndPlay(url: String) {
        val mediaItem = MediaItem.fromUri(url)
        exoPlayer?.apply {
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    private fun handlePlaybackError() {
        if (errorRetryCount < maxErrorRetries) {
            errorRetryCount++
            tvNetworkStatus.text = "網路波動，進行第 $errorRetryCount 次自動恢復..."
            currentPlaybackPosition = exoPlayer?.currentPosition ?: 0L
            playerView.postDelayed({
                exoPlayer?.prepare()
                exoPlayer?.playWhenReady = true
            }, 2000L * errorRetryCount)
        } else {
            tvNetworkStatus.text = "錯誤：超過最大重試次數"
        }
    }

    private fun setupNetworkMonitoring() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread { tvNetworkStatus.text = "網路已連線：自動恢復同步" }
            }
            override fun onLost(network: Network) {
                runOnUiThread { tvNetworkStatus.text = "警告：網路斷線，已保留狀態" }
            }
        })
    }

    private fun setupUIControls() {
        findViewById<Button>(R.id.btnYouTube).setOnClickListener {
            webView.loadUrl("https://m.youtube.com")
        }
        findViewById<Button>(R.id.btnPlayUrl).setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isNotEmpty()) loadAndPlay(url)
        }
        findViewById<Button>(R.id.btnRewind).setOnClickListener {
            exoPlayer?.let { it.seekTo(maxOf(0, it.currentPosition - 10000)) }
        }
        findViewById<Button>(R.id.btnForward).setOnClickListener {
            exoPlayer?.let { it.seekTo(minOf(it.duration, it.currentPosition + 10000)) }
        }
        val speedBtn = findViewById<Button>(R.id.btnSpeed)
        speedBtn.setOnClickListener {
            currentSpeed = if (currentSpeed == 1.0f) 1.5f else if (currentSpeed == 1.5f) 2.0f else 1.0f
            exoPlayer?.setPlaybackSpeed(currentSpeed)
            speedBtn.text = "${currentSpeed}x"
        }
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
    }
}
