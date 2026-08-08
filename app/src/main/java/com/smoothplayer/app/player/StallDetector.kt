package com.smoothplayer.app.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * 卡頓趨勢偵測：
 * - 監控 isPlaying 與 bufferedPosition 差異。
 * - 當緩衝剩餘時間低於閾值且正在播放時，視為卡頓風險升高。
 * - 提供回調讓外部限制最高位元率或提前降畫質。
 */
class StallDetector(
    private val player: ExoPlayer,
    private val onStallRisk: (riskLevel: Int) -> Unit,
    private val onRecovered: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var lastBuffered = 0L
    private var stallCount = 0
    private var isMonitoring = false

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (!isMonitoring) return
            val current = player.currentPosition
            val buffered = player.bufferedPosition
            val remaining = buffered - current

            when {
                player.playbackState == Player.STATE_BUFFERING && player.playWhenReady -> {
                    stallCount++
                    if (stallCount >= 2) {
                        onStallRisk(minOf(stallCount, 3))
                    }
                }
                remaining < 3_000 && player.isPlaying -> {
                    stallCount++
                    onStallRisk(1)
                }
                remaining > 8_000 && stallCount > 0 -> {
                    stallCount = 0
                    onRecovered()
                }
                else -> {
                    if (stallCount > 0) stallCount = maxOf(0, stallCount - 1)
                }
            }
            lastBuffered = buffered
            handler.postDelayed(this, 800L)
        }
    }

    fun start() {
        if (isMonitoring) return
        isMonitoring = true
        stallCount = 0
        handler.post(checkRunnable)
    }

    fun stop() {
        isMonitoring = false
        handler.removeCallbacks(checkRunnable)
    }

    fun reset() {
        stallCount = 0
    }
}
