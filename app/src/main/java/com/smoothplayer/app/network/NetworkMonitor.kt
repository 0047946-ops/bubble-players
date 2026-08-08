package com.smoothplayer.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * 即時網路監測：連線狀態、Wi-Fi／行動網路切換、斷線偵測、簡易速度估算。
 * 提供 StateFlow 供播放器與 UI 觀察。
 */
class NetworkMonitor(private val context: Context) {

    enum class NetworkType { NONE, WIFI, CELLULAR, OTHER }

    data class NetworkState(
        val isConnected: Boolean = false,
        val type: NetworkType = NetworkType.NONE,
        val estimatedBandwidthKbps: Long = 0L,
        val isMetered: Boolean = true
    )

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _state = MutableStateFlow(NetworkState())
    val state: StateFlow<NetworkState> = _state.asStateFlow()

    private val lastBytes = AtomicLong(0)
    private val lastTime = AtomicLong(0)
    private val handler = Handler(Looper.getMainLooper())
    private var speedRunnable: Runnable? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateState()
        }

        override fun onLost(network: Network) {
            updateState()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            updateState(capabilities)
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (_: Exception) {
            // 部分裝置可能限制
        }
        updateState()
        startSpeedEstimation()
    }

    fun stop() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
        speedRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun updateState(caps: NetworkCapabilities? = null) {
        val active = connectivityManager.activeNetwork
        val capabilities = caps ?: active?.let { connectivityManager.getNetworkCapabilities(it) }

        val connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        val type = when {
            capabilities == null -> NetworkType.NONE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            else -> NetworkType.OTHER
        }

        val metered = connectivityManager.isActiveNetworkMetered
        val bandwidth = capabilities?.linkDownstreamBandwidthKbps?.toLong() ?: 0L

        _state.value = NetworkState(
            isConnected = connected,
            type = type,
            estimatedBandwidthKbps = if (bandwidth > 0) bandwidth else _state.value.estimatedBandwidthKbps,
            isMetered = metered
        )
    }

    /**
     * 簡易即時速度估算（基於系統回報的 link bandwidth，並可後續結合實際下載量）。
     * 此處優先使用 NetworkCapabilities 提供的下游頻寬。
     */
    private fun startSpeedEstimation() {
        speedRunnable = object : Runnable {
            override fun run() {
                updateState()
                handler.postDelayed(this, 2000L)
            }
        }
        handler.post(speedRunnable!!)
    }

    fun isWifi(): Boolean = _state.value.type == NetworkType.WIFI
    fun isCellular(): Boolean = _state.value.type == NetworkType.CELLULAR
    fun isConnected(): Boolean = _state.value.isConnected
}
