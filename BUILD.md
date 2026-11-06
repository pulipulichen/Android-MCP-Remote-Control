# Build and device validation

## CI build

GitHub Actions workflow: `.github/workflows/android-build.yml`.

The workflow uses Go 1.27.3 and Android NDK to build the OpenAI Tunnel Go SDK as an Android arm64 AAR, then builds `:app:assembleDebug` with Gradle 8.13 and JDK 17, using one permanent PKCS12 signing key restored from GitHub Actions secrets. The workflow fails if signing secrets are incomplete and verifies the resulting APK signature. It checks out pinned revisions of adb-kt and spake2-kt under `.deps/` because the adb-kt coordinate listed in the upstream README could not be resolved. The stable-signed APK and SHA-256 file are kept as a short-lived workflow artifact for direct Y700 installation. APK builds do not run in CT 219.

## Permanent signing setup

The signing key is **not** committed to this repository. Restore the private signing backup into `signing/release.keystore` and `signing/signing.properties` on a trusted computer. With `gh` authenticated, run `./signing/setup.sh --sync-github` to upload the four `SIGNING_*` secrets without printing the credentials. Do this once per signing identity and keep offline backups. See [signing/README.md](signing/README.md).

CI chooses a monotonically increasing UTC epoch-minute `versionCode`, while local Gradle still defaults to versionCode 2 unless overridden. Use `-PappVersionCode=NNN` for an upgrade-compatible local build. All workflows must use the same signing key and Android application ID. An APK already installed using the old disposable Android debug certificate **cannot be updated in place** with the new permanent certificate; back up its data and uninstall it once before installing the new key's first build.

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
