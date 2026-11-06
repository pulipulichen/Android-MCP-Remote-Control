# Test APK delivery

This is a personal sideload application (not Google Play). The `Android test APK` workflow builds an arm64 debug APK signed with a **permanent application-specific keystore**. It verifies the APK signature and uploads the APK together with `SHA256SUMS.txt` for three days. No Tunnel ID or Runtime API key is included.

The public GitHub repository contains only signing **scripts and documentation**. The keystore and its passwords must remain private. Set all four repository secrets (`SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_PASSWORD`, `SIGNING_KEY_ALIAS`) using `./signing/setup.sh --sync-github`. All four legacy `ANDROID_*` secret names remain supported. Missing credentials now **stop CI** rather than silently generating an incompatible debug certificate.

Every successful CI run derives its numeric `versionCode` from UTC epoch minutes. New APKs from this signing identity can be installed over older APKs signed with **the same identity**, provided the new versionCode is not lower.

**One-time migration:** Earlier APKs used temporary debug certificates. Android cannot replace an installed APK signed by a different certificate, even if its package name is unchanged. Back up any app data, uninstall the old debug APK once, and then install the first permanent-signed APK. Future APK updates using this key preserve app data.

See [signing/README.md](signing/README.md) for protected backup and GitHub Secret setup.
