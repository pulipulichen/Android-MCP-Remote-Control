# Android MCP Remote Control

個人自用的 Android 側載 App。這個 PoC 用來驗證 Y700 能否在 App 內完成 Wireless ADB 配對、自我操作，並在同一個 Android 程序中啟動 OpenAI Tunnel Client。

目前尚未上架 Google Play，也不是正式發佈版。

## PoC 狀態

- **Phase 0：** Android App 內以 Kotlin ADB client 配對和連線到本機 adbd；提供裝置資訊及點擊測試區驗證。
- **Phase 2：** 以 Go Mobile 將 OpenAI Tunnel Go SDK 編成 arm64 AAR，App 啟動前景服務並連到 Tunnel。
- **MCP 工具：** `device_info`；`tap_test_zone`（預設關閉，需使用者在 App 內啟用）。沒有開放任意遠端 shell。
- **API key：** 使用 Runtime API key，經 Android Keystore 保護後儲存在 App 私有空間。
- **建置：** GitHub Actions 編譯 APK；CT 219 只負責 ADB 安裝與 Y700 驗證。

## Y700 測試

1. 在 GitHub Actions 的 `Android test APK` workflow 下載短期測試 APK。
2. 安裝後開啟 App。於 Y700 的「開發者選項 → 無線偵錯」取得配對埠與 6 位碼，在 Phase 0 輸入；再填入無線偵錯主畫面的連線埠。
3. 按「配對這台平板」，再按「連線並驗證」。使用「透過 ADB 點擊測試區」確認 App 內 ADB 真的能控制這台 Y700。
4. 輸入 Tunnel ID 和 Runtime API key，啟動 Tunnel。狀態顯示 `ready` 才代表 SDK 已建立控制平面連線。
5. 如需驗證遠端操作，再開啟「允許遠端點擊測試區」，由已設定此 Tunnel 的 MCP 用戶端呼叫 `tap_test_zone`。

ADB 配對碼只用於配對，不會儲存。Tunnel API key 不會寫入 log。測試 APK 用於個人側載，不提供 Google Play 簽章或商店發佈素材。

## 文件

- [APP_METADATA.md](APP_METADATA.md) — App 識別、權限與資料處理
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — Phase 0 / Phase 2 架構與已知限制
- [BUILD.md](BUILD.md) — GitHub Actions 建置及裝置驗證流程
- [RELEASE.md](RELEASE.md) — 測試 APK 交付方式
- [CHANGELOG.md](CHANGELOG.md) — 變更紀錄
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) — 第三方元件
