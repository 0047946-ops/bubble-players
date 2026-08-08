package com.smoothplayer.app.player

import android.content.Context
import android.content.SharedPreferences

/**
 * 播放位置持久化。使用 SharedPreferences 以降低記憶體佔用，適合低記憶體裝置。
 */
class PlaybackPositionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("smooth_player_positions", Context.MODE_PRIVATE)

    fun savePosition(mediaId: String, positionMs: Long, durationMs: Long) {
        if (mediaId.isBlank() || positionMs < 0) return
        prefs.edit()
            .putLong("pos_$mediaId", positionMs)
            .putLong("dur_$mediaId", durationMs)
            .apply()
    }

    fun getPosition(mediaId: String): Long {
        return prefs.getLong("pos_$mediaId", 0L)
    }

    fun clearPosition(mediaId: String) {
        prefs.edit()
            .remove("pos_$mediaId")
            .remove("dur_$mediaId")
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
