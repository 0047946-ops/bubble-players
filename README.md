# SmoothPlayer — 比 Bubble Player 更順暢的 Android 原生影片播放器

## 設計目標

- **無廣告**：App 內不插入任何廣告。
- **一開啟即連 YouTube**：主畫面提供一鍵開啟 YouTube 官方 App／網頁。
- **極低卡頓與轉圈**：智慧緩衝 + 卡頓趨勢偵測 + 動態位元率限制。
- **網路韌性**：即時監測、斷線保留狀態、恢復後自動繼續。
- **智慧畫質**：卡頓風險升高時主動限制最高位元率。
- **播放錯誤恢復**：最多 4 次遞進式恢復。
- **格式支援**：MP4、HLS、DASH。
- **播放功能**：倍速 0.5×～2×、±10 秒、全螢幕、硬體解碼優先。
- **目標**：Android 11（API 30）以上，Media3 / ExoPlayer 1.10.1。

## 技術規格

| 項目 | 版本 |
|------|------|
| Media3 / ExoPlayer | 1.10.1 |
| Android Gradle Plugin | 9.3.0 |
| Gradle | 9.5 |
| JDK | 17 |
| minSdk | 30 |
| targetSdk / compileSdk | 36 |

## 建置方式

使用 GitHub Actions 自動建置，或在 Android Studio 開啟後執行。
