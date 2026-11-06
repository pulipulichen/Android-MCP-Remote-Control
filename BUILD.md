# Build and Validation

## Current state

目前沒有 Android Gradle 專案或 APK build workflow，因此尚無可安裝版本。正式加入程式碼時，請先核對 Android Gradle Plugin、Gradle、JDK、Kotlin 與 compileSdk 的相容組合。

## Build policy

- Android APK 由 GitHub Actions 建置。
- `android-dev` CT 僅供 Wi-Fi ADB 安裝及 Y700 真機驗證，不在 CT 編譯 APK。
- 不以 emulator 作為預設驗證方式；本專案的功能驗證目標是 Y700。
- 供安裝的 APK 以 GitHub Release 資產交付，不使用 Actions artifact 作為長期下載位置。
- 不建立 Google Play AAB。

## Planned checks

- Gradle assemble 與資源／Manifest 編譯。
- APK package name、version、簽章與檔案完整性。
- 在 Y700 安裝並啟動 App。
- Tunnel 連線狀態、憑證錯誤與重新啟動後狀態。
- 每個裝置控制工具的成功、拒絕及錯誤回報。

實際 workflow 與指令會在 Android 專案骨架建立後補齊；目前沒有成功建置的 APK 可供驗證。
