package com.smoothplayer.app.player

import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 播放錯誤分級恢復：
 * - 最多 4 次遞進式恢復，避免無限 prepare → play 死循環。
 * - 第 1 次：簡單重新 prepare
 * - 第 2 次：重新建立 media source
 * - 第 3 次：降低畫質後重試
 * - 第 4 次：最後嘗試，之後放棄並回報失敗
 * - 保留播放位置
 */
class ErrorRecoveryHandler(
    private val scope: CoroutineScope,
    private val maxRetries: Int = 4
) {
    private var retryCount = 0
    private var lastErrorTime = 0L

    fun onError(
        player: ExoPlayer,
        error: PlaybackException,
        mediaUrl: String,
        currentPosition: Long,
        onRetry: (retryIndex: Int, position: Long) -> Unit,
        onGiveUp: (error: PlaybackException) -> Unit
    ) {
        val now = System.currentTimeMillis()
        // 若短時間內連續錯誤，視為同一波，避免快速累加
        if (now - lastErrorTime < 1500) {
            // 仍計入但延遲較長
        }
        lastErrorTime = now

        if (retryCount >= maxRetries) {
            onGiveUp(error)
            return
        }

        retryCount++
        val delayMs = when (retryCount) {
            1 -> 500L
            2 -> 1200L
            3 -> 2500L
            else -> 4000L
        }

        scope.launch(Dispatchers.Main) {
            delay(delayMs)
            // 確保位置保留
            onRetry(retryCount, currentPosition)
        }
    }

    fun reset() {
        retryCount = 0
    }

    fun currentRetry(): Int = retryCount
}
