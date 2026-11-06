# Test APK delivery

This repository is for personal sideload use and does not currently publish a public GitHub Release.

The `Android test APK` Actions workflow creates a debug-signed arm64 APK and a SHA-256 checksum. The workflow artifact is retained for three days so it can be transferred to CT 219 and installed on the Y700. It contains no Tunnel ID or API key.

The debug signing identity is not configured as a durable release key. For later test builds, replacing the installed app may require an uninstall first; uninstalling clears its ADB identity and saved app settings. Do not use this APK as a production distribution build.
