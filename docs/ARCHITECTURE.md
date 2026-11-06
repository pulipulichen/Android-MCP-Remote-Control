# Architecture

## Goal

建立一個供個人自用的 Android App，讓使用者設定 Tunnel ID 與 Panel API Key，並透過遠端 MCP 操作自己的 Android 裝置。預期工具包含裝置資訊、截圖、UI 操作與受控 shell 操作。

本文件是規劃，不代表這些功能已實作或 Tunnel 已連線。

## Proposed boundaries

- **Android app** — 顯示連線狀態、設定 Tunnel 資料，並呈現裝置控制能力與授權狀態。
- **Tunnel / MCP transport** — 需先確認 OpenAI MCP Tunnel 是否有受支援的 Android client/API、註冊方式及 health-check；不得臆測協定或複製 Linux 專用安裝流程。
- **Device control backend** — 需在 Y700 上驗證 App 能否使用 Wireless debugging / ADB；若平台限制 App 直接存取 adbd，再評估 Shizuku 或 Accessibility 等替代方式，並記錄能力差異。
- **Remote tools** — 只公開明確定義的操作。shell 能力、危險操作的確認方式及工具回傳資料範圍待設計。
- **Credential handling** — Panel API Key 僅由使用者設定，採 Android Keystore 保護的儲存方式待實作；不得寫入來源碼或一般日誌。

## Decisions still needed

1. 官方 Tunnel 的 Android 相容性、認證欄位與連線狀態驗證方式。
2. 裝置控制採用 ADB、Shizuku、Accessibility，或分層組合。
3. 是否需要常駐 foreground service，以及 Android 背景執行限制的處理方式。
4. MCP 工具清單、每個工具的輸入／輸出格式與 shell 操作限制。
5. 截圖及命令輸出經 Tunnel 傳送時的資料流、記錄與保留策略。
6. 固定簽章金鑰與 APK 發佈位置；目前 repo 為公開狀態。

## Validation order

1. 先以 GitHub Actions 建置最小 APK。
2. 在 Y700 確認安裝、啟動、基本連線與錯誤狀態。
3. 逐項驗證 Tunnel、ADB／裝置控制、截圖和 UI 操作。
4. 依實際功能與資料流更新權限清單、隱私說明與發佈文件。
