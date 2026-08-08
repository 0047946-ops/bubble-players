package com.smoothplayer.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smoothplayer.app.SmoothPlayerApp
import com.smoothplayer.app.databinding.ActivityMainBinding
import com.smoothplayer.app.network.NetworkMonitor
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 主入口：一開啟即提供 YouTube 入口，並可直接輸入網址播放（MP4/HLS/DASH）。
 * 無廣告、乾淨介面。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var networkMonitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as SmoothPlayerApp
        networkMonitor = app.networkMonitor

        setupUi()
        observeNetwork()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun setupUi() {
        binding.btnYouTube.setOnClickListener {
            // 開啟 YouTube 官方 App 或瀏覽器，無內嵌廣告干擾
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
                intent.setPackage("com.google.android.youtube")
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")))
            }
        }

        binding.btnPlayUrl.setOnClickListener {
            val url = binding.urlEditText.text?.toString()?.trim().orEmpty()
            if (url.isBlank()) {
                Toast.makeText(this, "請輸入有效的影片網址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                Toast.makeText(this, "網址需以 http 或 https 開頭", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            openPlayer(url)
        }
    }

    private fun openPlayer(url: String) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URL, url)
        }
        startActivity(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val data = intent?.data
        if (data != null && (data.scheme == "http" || data.scheme == "https")) {
            openPlayer(data.toString())
        }
    }

    private fun observeNetwork() {
        lifecycleScope.launch {
            networkMonitor.state.collectLatest { state ->
                val typeStr = when (state.type) {
                    NetworkMonitor.NetworkType.WIFI -> "Wi-Fi"
                    NetworkMonitor.NetworkType.CELLULAR -> "行動網路"
                    NetworkMonitor.NetworkType.OTHER -> "其他"
                    else -> "無連線"
                }
                val speed = if (state.estimatedBandwidthKbps > 0) {
                    "${state.estimatedBandwidthKbps} kbps"
                } else {
                    "估算中"
                }
                binding.networkInfoText.text =
                    "網路：$typeStr  |  估計頻寬：$speed  |  ${if (state.isConnected) "已連線" else "斷線"}"
            }
        }
    }
}
