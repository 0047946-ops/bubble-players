package com.smoothplayer.app

import android.app.Application
import com.smoothplayer.app.network.NetworkMonitor
import com.smoothplayer.app.player.PlaybackPositionStore

class SmoothPlayerApp : Application() {
    lateinit var networkMonitor: NetworkMonitor
        private set
    lateinit var positionStore: PlaybackPositionStore
        private set

    override fun onCreate() {
        super.onCreate()
        networkMonitor = NetworkMonitor(this)
        positionStore = PlaybackPositionStore(this)
        networkMonitor.start()
    }

    override fun onTerminate() {
        networkMonitor.stop()
        super.onTerminate()
    }
}
