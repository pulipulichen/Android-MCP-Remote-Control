# Android MCP Remote Control

A personal-use Android app project for controlling an Android device through an OpenAI MCP Tunnel. Planned capabilities include configuring a Tunnel ID and Panel API Key, retrieving screenshots, running approved shell operations, and automating the device UI.

本專案供個人自用與側載，不規劃上架 Google Play。

## Project status

This repository is at the project-baseline stage. It does not yet contain an Android Gradle project, an APK build workflow, or an installable APK. Tunnel compatibility and the Android-side command mechanism still need a device proof of concept.

## First device target

- Lenovo Y700, model TB320FC
- Android 15, API 35
- The Wi-Fi ADB connection from the `android-dev` CT has been verified. This confirms the test-device link; it does not yet verify this app.

## Build and device testing

- APK builds will run in GitHub Actions.
- The `android-dev` CT is for ADB installation and device verification; Android builds will not run there.
- Functional validation will use the Y700 after an installable build exists.
- No Google Play listing or AAB is planned.

## Project documents

- [APP_METADATA.md](APP_METADATA.md) — app identity and current decisions
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — proposed components and open technical questions
- [BUILD.md](BUILD.md) — build and validation policy
- [RELEASE.md](RELEASE.md) — personal sideloading and release notes
- [CHANGELOG.md](CHANGELOG.md) — project history
