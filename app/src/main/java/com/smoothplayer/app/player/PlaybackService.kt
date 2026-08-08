package com.smoothplayer.app.player

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * 預留背景播放／MediaSession 服務架構。
 * 目前為佔位，後續可擴充為完整 MediaSessionService 以支援通知列控制與背景播放。
 */
class PlaybackService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}
