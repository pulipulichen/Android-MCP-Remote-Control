# Release and Sideloading

## Distribution

本 App 僅供個人自用，採 APK 側載，不上架 Google Play，也不產生 AAB。

repo 目前是公開的；放在 GitHub Release 的 APK 會公開供任何人下載。發佈 APK 前，需先決定是否接受公開下載，或改用其他交付方式。

## Signing

- 正式提供可更新的 APK 前，建立一組固定的簽章身分並安全離線備份 keystore。
- keystore、密碼及 Base64 匯出值不得提交至 Git 或輸出到 Actions log。
- 每次更新 APK 必須沿用相同簽章；換簽章通常需要先移除舊版 App，可能清除其本機資料。
- 目前尚無 keystore、release APK 或已發布版本。

## Release checklist

發佈前確認：

- APK 的 Application ID、versionName、versionCode 正確。
- APK 使用預期簽章，且可作為已安裝版本的更新。
- 在 Lenovo Y700 / Android 15 完成安裝、啟動與核心功能驗證。
- Tunnel ID 與 Panel API Key 未進入 APK、Git、公開錯誤訊息或 CI log。
- 公開 Release 的下載可見性符合個人使用需求。

目前沒有可下載版本。
