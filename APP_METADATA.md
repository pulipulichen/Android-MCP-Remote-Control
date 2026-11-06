# App Metadata

本文件記錄自用 Android App 的初始識別資料。尚未建立 Gradle 專案；標記「規劃」的值須在建立 app module 時寫入並核對。

## Identity

- Project / repository: Android MCP Remote Control
- Repository: https://github.com/pulipulichen/Android-MCP-Remote-Control
- App display name: Android MCP Remote Control
- Application ID（規劃）: `info.pulipuli.app.androidmcpremotecontrol`
- Gradle namespace（規劃）: `info.pulipuli.app.androidmcpremotecontrol`
- Initial version（規劃）: versionName `0.1.0`, versionCode `1`
- Intended use: 布丁個人自用、APK 側載
- Google Play: 不上架；不建立 Play 商店素材或 Play Console 設定

## Android baseline

- 首要測試裝置: Lenovo Y700, model `TB320FC`
- 裝置 Android: 15 / API 35
- minSdk: 規劃採 Android skill 預設 API 26；須依最終 ADB／裝置控制方案確認
- compileSdk / targetSdk: 待選定相容的 Android Gradle Plugin 與控制方案後設定
- 預設介面語言: 繁體中文（台灣）
- 其他語系: 待確認

## App behavior and permissions

預期功能為設定 Tunnel ID 與 Panel API Key，建立遠端 MCP 連線，並依授權執行裝置資訊、截圖、UI 操作及 shell 操作。

- Manifest permissions: 待控制方案確定後逐項核定；不預先加入未使用的權限。
- 網路存取: 預期需要；實際 Tunnel 傳輸方式與資料路徑待驗證。
- Accessibility、Wireless debugging、Shizuku 或其他系統整合: 尚未選定。
- API Key 儲存與保護方式: 待實作；不得寫入 Git、一般日誌或明文診斷輸出。
- 資料處理與保留: 待依實際程式碼及 Tunnel 路徑完成資料流盤點。
- 隱私政策: 目前為個人自用且尚無實作；完成資料流盤點前不宣稱已完成政策或資料安全稽核。

## Developer and support

- Developer: 布丁布丁吃布丁（pulipulichen）
- Website: https://blog.pulipuli.info/
- Source and issue tracking: https://github.com/pulipulichen/Android-MCP-Remote-Control
- Support email: 未設定

## Distribution and signing

- Distribution: 個人使用 APK 側載，不發佈 AAB。
- Repository visibility: 目前為公開 repo。GitHub Release 的 APK 會公開供下載；是否使用公開 Release 尚待決定。
- Signing: 尚未建立正式簽章身分。若需要跨版本直接更新，須先建立並安全備份固定 keystore；任何金鑰與密碼都不得提交至 repo。
- Store category / store listing / Play Console choices: 不適用。
