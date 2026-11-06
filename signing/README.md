# Permanent Android APK signing

This sideload-only application uses one unique permanent PKCS12 RSA-3072 key (alias `android-mcp-remote-control`, valid for 10,000 days). Both debug and release variants use that identity when configured, and CI **requires** it. Never share an unrelated application's signing key.

## Restore the original backup

The permanent key was generated separately and intentionally NOT committed to Git. Obtain the private backup, then extract its `signing/` directory into this repository, so these local files exist:

- `signing/release.keystore`: the private PKCS12 signing key.
- `signing/signing.properties`: store password, key password, and alias.

Verify the restored key:

```bash
./signing/setup.sh --verify
```

Do NOT run `--generate` if a previous signing identity exists: it generates a new unrelated key and breaks upgrades. Only use that option for an entirely fresh application identity when no key has ever been used.

## Configure GitHub Actions

On a trusted machine with JDK, `base64`, and GitHub CLI (`gh`) installed and authenticated:

```bash
gh auth login
./signing/setup.sh --sync-github
```

This streams the private key directly to GitHub Actions Secrets; it does not print or commit its Base64. It sets four repository secrets:

- `SIGNING_KEYSTORE_BASE64`
- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_PASSWORD`
- `SIGNING_KEY_ALIAS`

Secret settings: https://github.com/pulipulichen/Android-MCP-Remote-Control/settings/secrets/actions

For compatibility, the workflow also accepts the complete legacy set `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_PASSWORD`, and `ANDROID_KEY_ALIAS`. The `SIGNING_*` set takes precedence.

If the keys are absent or invalid, CI fails **before building the APK** instead of releasing an incompatible temporary debug certificate.

## Upgrade compatibility

Android checks package ID, signing identity, and versionCode. Existing APKs signed using the old disposable debug certificate cannot be overwritten by the new key. Before the **one-time migration** back up stored Tunnel settings and ADB identity if needed; then uninstall the old debug APK and install the first permanent-signed APK. Subsequent builds signed with this same keystore can update without deleting data.

The CI workflow sets versionCode from UTC epoch minutes (so it increases over time). For local builds pass `-PappVersionCode=NNN` with a greater code than already installed.

The ZIP backup contains secrets in plaintext within the archive. Keep a protected offline copy, restrict its access, and never add it, the extracted files, or their Base64 to Git or issue comments. Losing the signing key prevents signature-compatible updates.
