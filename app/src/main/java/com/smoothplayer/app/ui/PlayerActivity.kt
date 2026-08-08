package com.smoothplayer.app.ui

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.smoothplayer.app.SmoothPlayerApp
import com.smoothplayer.app.databinding.ActivityPlayerBinding
import com.smoothplayer.app.player.SmoothPlayerManager
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 播放頁面：
 * - 自訂乾淨控制列（播放／暫停、±10 秒、進度、畫質、倍速、全螢幕、鎖定）
 * - 最小化緩衝指示（僅真正緩衝時顯示，避免「一直轉圈」）
 * - 網路狀態訊息提示
 * - 支援方向切換與沉浸式全螢幕
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
    }

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var playerManager: SmoothPlayerManager
    private val handler = Handler(Looper.getMainLooper())
    private var controlsVisible = true
    private var isLocked = false
    private var isFullscreen = false

    private val hideControlsRunnable = Runnable { hideControls() }

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val app = application as SmoothPlayerApp
        playerManager = SmoothPlayerManager(this, app.networkMonitor, app.positionStore)

        val player = playerManager.initialize()
        binding.playerView.player = player

        playerManager.listener = object : SmoothPlayerManager.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        // 僅在真正需要時短暫顯示，避免長時間轉圈
                        binding.bufferingIndicator.visibility = View.VISIBLE
                    }
                    Player.STATE_READY, Player.STATE_ENDED, Player.STATE_IDLE -> {
                        binding.bufferingIndicator.visibility = View.GONE
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayPauseIcon(isPlaying)
            }

            override fun onError(message: String, retry: Int) {
                showStatus(message)
            }

            override fun onNetworkMessage(message: String) {
                showStatus(message)
            }

            override fun onPositionUpdate(position: Long, duration: Long) {
                // 由 progress runnable 處理
            }
        }

        setupControls()
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (url.isNotBlank()) {
            playerManager.prepareAndPlay(url)
        } else {
            Toast.makeText(this, "無有效影片網址", Toast.LENGTH_SHORT).show()
            finish()
        }

        handler.post(updateProgressRunnable)
        scheduleHideControls()
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            if (!isLocked) {
                playerManager.togglePlayPause()
                scheduleHideControls()
            }
        }
        binding.btnRewind.setOnClickListener {
            if (!isLocked) {
                playerManager.seekBack()
                scheduleHideControls()
            }
        }
        binding.btnForward.setOnClickListener {
            if (!isLocked) {
                playerManager.seekForward()
                scheduleHideControls()
            }
        }
        binding.btnFullscreen.setOnClickListener {
            toggleFullscreen()
        }
        binding.btnLock.setOnClickListener {
            isLocked = !isLocked
            binding.btnLock.text = if (isLocked) "解鎖" else getString(com.smoothplayer.app.R.string.lock_controls)
            if (isLocked) hideControlsExceptLock() else showControls()
        }
        binding.btnSpeed.setOnClickListener {
            if (!isLocked) showSpeedDialog()
        }
        binding.btnQuality.setOnClickListener {
            if (!isLocked) {
                Toast.makeText(this, "畫質由智慧自適應控制，卡頓時自動降低", Toast.LENGTH_SHORT).show()
            }
        }

        binding.controlsContainer.setOnClickListener {
            if (isLocked) return@setOnClickListener
            if (controlsVisible) hideControls() else showControls()
        }

        binding.progressSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !isLocked) {
                val player = playerManager.getPlayer() ?: return@addOnChangeListener
                val duration = player.duration
                if (duration > 0) {
                    val pos = (value / 100f * duration).toLong()
                    player.seekTo(pos)
                }
            }
        }
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        binding.btnPlayPause.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    private fun updateProgress() {
        val player = playerManager.getPlayer() ?: return
        val pos = player.currentPosition
        val dur = player.duration.coerceAtLeast(0L)
        binding.positionText.text = formatTime(pos)
        binding.durationText.text = formatTime(dur)
        if (dur > 0 && !binding.progressSlider.isPressed) {
            binding.progressSlider.value = (pos.toFloat() / dur * 100f).coerceIn(0f, 100f)
        }

        // 更新網速顯示
        val app = application as SmoothPlayerApp
        val kbps = app.networkMonitor.state.value.estimatedBandwidthKbps
        binding.networkSpeedText.text = if (kbps > 0) "≈ ${kbps} kbps" else ""
    }

    private fun formatTime(ms: Long): String {
        val totalSec = TimeUnit.MILLISECONDS.toSeconds(ms)
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    private fun showControls() {
        controlsVisible = true
        binding.topBar.visibility = View.VISIBLE
        binding.centerControls.visibility = View.VISIBLE
        binding.bottomBar.visibility = View.VISIBLE
        scheduleHideControls()
    }

    private fun hideControls() {
        if (isLocked) return
        controlsVisible = false
        binding.topBar.visibility = View.GONE
        binding.centerControls.visibility = View.GONE
        binding.bottomBar.visibility = View.GONE
    }

    private fun hideControlsExceptLock() {
        binding.centerControls.visibility = View.GONE
        binding.bottomBar.visibility = View.GONE
        binding.topBar.visibility = View.VISIBLE
    }

    private fun scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 3500L)
    }

    private fun showStatus(msg: String) {
        binding.statusMessage.text = msg
        binding.statusMessage.visibility = View.VISIBLE
        handler.postDelayed({
            binding.statusMessage.visibility = View.GONE
        }, 3000L)
    }

    private fun showSpeedDialog() {
        val speeds = arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x")
        val values = floatArrayOf(0.5f, 0.75f, 1.0f, 1.5f, 1.5f, 2.0f)
        AlertDialog.Builder(this)
            .setTitle("播放倍速")
            .setItems(speeds) { _, which ->
                playerManager.setPlaybackSpeed(values[which])
                binding.btnSpeed.text = speeds[which]
            }
            .show()
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            WindowInsetsControllerCompat(window, binding.root).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowInsetsControllerCompat(window, binding.root)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onStop() {
        super.onStop()
        playerManager.saveCurrentPosition()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        playerManager.release()
        super.onDestroy()
    }
}
