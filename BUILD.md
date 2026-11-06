# Build and device validation

## CI build

GitHub Actions workflow: `.github/workflows/android-build.yml`.

The workflow uses Go 1.27.3 and Android NDK to build the OpenAI Tunnel Go SDK as an Android arm64 AAR, then builds `:app:assembleDebug` with Gradle 8.13 and JDK 17. The resulting APK and SHA-256 file are kept as a short-lived workflow artifact for direct Y700 installation. APK builds do not run in CT 219.

## Device procedure

1. Enable **Developer options → Wireless debugging** on the Y700.
2. In the app, enter the Y700 IP, the temporary pairing port and 6-digit code from **Pair device with pairing code**. Pairing and TLS ADB connection use the app's own RSA identity.
3. Enter the connection port shown on the main Wireless debugging screen. The app runs `getprop` to verify its ADB connection.
4. Tap **ADB 點擊測試區**. A successful test increments the visible counter, proving that the app sent an `input tap` command to the same device.
5. Enter a Tunnel ID and Runtime API key, then start the foreground Tunnel service. Wait for `ready`.
6. Remote `device_info` reads device properties. Remote `tap_test_zone` remains unavailable until the user enables the matching switch in the app.

## Validation boundaries

- Device install, app launch, ADB pairing, ADB shell, touch injection and Tunnel readiness are checked on the real Y700.
- A Tunnel ID and runtime key are required to verify Phase 2 against the control plane. Do not put either in GitHub Actions inputs, source code, or logs.
- `ready` means the embedded client passed its SDK readiness check. A remote `tap_test_zone` call is the separate end-to-end MCP operation check.
- This PoC tests the upstream Go SDK through Go Mobile; it does not claim that OpenAI publishes or supports a prebuilt Android client.
- No emulator installation, end-to-end app installation in GitHub Actions, Play submission, or Play signing is part of this workflow.
