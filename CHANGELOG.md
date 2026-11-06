# Changelog

## Unreleased — Phase 0 / Phase 2 proof of concept

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

Device compatibility and Tunnel readiness are pending the first Actions build and Y700 test.
