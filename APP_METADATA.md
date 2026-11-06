# App Metadata

## Identity

- Project / repository: Android MCP Remote Control
- Repository: <https://github.com/pulipulichen/Android-MCP-Remote-Control>
- Display name: Android MCP Remote Control
- Application ID / namespace: `info.pulipuli.app.androidmcpremotecontrol`
- PoC version: `0.1.0-poc.1` (versionCode `1`)
- Use: personal sideload testing; no Google Play listing planned
- First device: Lenovo Y700 `TB320FC`, Android 15 / API 35
- Default language: Traditional Chinese (Taiwan)

## Build baseline

- minSdk 26, targetSdk 35, compileSdk 36
- Android arm64 (`arm64-v8a`) only
- JDK 17, Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.2.21
- Go 1.27.3, OpenAI Tunnel Client Go SDK `v0.0.19`, Model Context Protocol Go SDK `v1.7.0`
- Kotlin ADB library: `io.github.rhythmcache:adb-kt:1.0.0`

## Permissions and behavior

The manifest requests Internet, network state/change, notifications, and foreground-service permissions. The foreground service maintains the user-started Tunnel connection. The app does not use Accessibility or Shizuku.

Wireless ADB uses the Android Wireless debugging pairing flow and TLS. Its generated RSA key is kept in the app's private internal files. Pairing code is transient and not saved.

The Runtime API key is encrypted with AES-GCM using an Android Keystore key. It is not written to source, Actions logs, or app logs. Tunnel ID and non-secret ADB settings are stored in app-private preferences.

MCP tools are restricted to `device_info` and `tap_test_zone`. Remote tapping is off by default and can only tap the app's visible PoC test area. Arbitrary shell access is not implemented.

## Distribution and signing

The workflow produces a debug-signed APK for private sideload testing. GitHub Actions retains the test artifact for three days to support installation. No public GitHub Release, AAB, Play Console submission, or production signing key is configured.
