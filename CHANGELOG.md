# Changelog

## Unreleased — stable signing

- Created a project-specific permanent PKCS12 RSA-3072 signing identity for future sideload upgrades.
- Added safe GitHub Secrets sync and signing verification commands, plus gitignore rules for private material.
- GitHub Actions requires the durable signing certificate, verifies the APK signature, and uses increasing build codes; unsigned or ephemeral-signed CI output is prohibited.
- Documented one-time uninstall required when migrating from previously installed disposable debug certificates.

## 0.1.0-poc.2

- Added opt-in MCP tools for package listing, inspection, launch, HTTPS APK installation, uninstall, Logcat and Android ADB shell.
- APK installation accepts an optional SHA-256 digest, preserves app data on signature-compatible upgrades, and reports package manager errors.
- Developer commands have time and output limits. The ADB shell returns separate stdout, stderr and exit status.

## 0.1.0-poc.1 — Phase 0 / Phase 2 proof of concept

- Added Android Compose app screens for Wireless ADB pairing, self-ADB tap verification, Tunnel settings, and connection status.
- Embedded OpenAI Tunnel Go SDK through an arm64 Go Mobile AAR with an in-memory MCP server.
- Added a foreground Tunnel service and a loopback-only authenticated bridge to the Kotlin ADB client.
- Added allowlisted `device_info` and opt-in `tap_test_zone` MCP tools. Arbitrary remote shell is excluded.
- Encrypted the Runtime API key with Android Keystore and added third-party notices.
- Added GitHub Actions build for a temporary, short-retention test APK.
- First CI run stopped while `setup-android` attempted license acceptance and the retired `tools` package; the workflow now uses the runner's Android SDK and accepts licenses before installing the required packages.
- Go Mobile's current binder requires `gobind` as a Go tool directive; the module now pins that tool alongside its runtime dependency.
- Resolve the module graph before binding and align the generated Java package and port type with the Kotlin caller.
- Build adb-kt and its pairing library from pinned source commits after Gradle could not resolve the advertised Maven coordinate.
- Recalculate test tap coordinates from the focused app window immediately before sending the ADB command.

- [Actions run #5](https://github.com/pulipulichen/Android-MCP-Remote-Control/actions/runs/42239322393) built the arm64 APK. The artifact and APK checksums matched before CT 219 installed version `0.1.0-poc.1` on the Android 15 Y700. ADB confirmed the app launched and reached the foreground.

In-app Wireless ADB pairing, self-control, and Tunnel readiness remain to be tested on the device.
