package com.smoothplayer.app.player

import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.DefaultAllocator
import com.smoothplayer.app.network.NetworkMonitor

/**
 * 智慧緩衝策略：
 * - 根據即時網速、影片位元率、已緩衝時間動態調整 min/max buffer。
 * - 網速高 → 允許較大緩衝以減少卡頓風險。
 * - 網速低或波動 → 縮小目標緩衝，優先保證連續播放而非高畫質。
 * - 避免單純把 buffer 設很大造成記憶體壓力。
 */
class SmartLoadControl(
    private val networkMonitor: NetworkMonitor
) : LoadControl by DefaultLoadControl.Builder()
    .setAllocator(DefaultAllocator(true, 16 * 1024))
    .setBufferDurationsMs(
        /* minBufferMs = */ 15_000,
        /* maxBufferMs = */ 50_000,
        /* bufferForPlaybackMs = */ 1_500,
        /* bufferForPlaybackAfterRebufferMs = */ 3_000
    )
    .setPrioritizeTimeOverSizeThresholds(true)
    .build() {

    private val baseMin = 10_000
    private val baseMax = 40_000
    private val highSpeedMin = 20_000
    private val highSpeedMax = 60_000
    private val lowSpeedMin = 5_000
    private val lowSpeedMax = 20_000

    fun createAdaptiveLoadControl(): LoadControl {
        val kbps = networkMonitor.state.value.estimatedBandwidthKbps
        val (minMs, maxMs) = when {
            kbps >= 5000 -> highSpeedMin to highSpeedMax
            kbps >= 1500 -> baseMin to baseMax
            else -> lowSpeedMin to lowSpeedMax
        }
        return DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, 16 * 1024))
            .setBufferDurationsMs(
                minMs,
                maxMs,
                1_200,
                2_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(5_000, true)
            .build()
    }
}
