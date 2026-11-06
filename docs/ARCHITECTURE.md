# Architecture

## Goal

Test the two core assumptions on a Lenovo Y700 (`TB320FC`, Android 15): can an Android App run an OpenAI Tunnel Client inside its own process, and can the same App use Wireless ADB to control its own device?

This implementation is a sideload-only proof of concept. It does not claim Android support from the OpenAI client project.

## Runtime components

1. **Compose app** — collects ADB pairing/connection details, Tunnel ID and runtime API key; displays both connection states.
2. **Kotlin ADB client** — uses `adb-kt` SPAKE2 pairing and TLS connection to Y700's `adbd`. Its generated RSA identity stays in app-private storage.
3. **Local bridge** — loopback-only TCP socket bound to `127.0.0.1`, with a random per-process token. It passes a small allowlisted set of commands between the Go SDK and the Kotlin ADB client.
4. **Foreground service** — starts the Tunnel only after the user presses Start. The system notification shows the live state and provides a stop action.
5. **OpenAI Tunnel Go SDK** — built as an Android arm64 AAR with Go Mobile. The Go MCP server and Tunnel client communicate through the SDK's in-memory MCP transport.

## MCP tools

- `device_info`: read model and Android release using fixed `getprop` calls.
- `tap_test_zone`: issue `input tap` at the center of the app's test button. The app switch defaults off. No arbitrary coordinates or shell command are accepted from the MCP caller.

## Credential handling

- Tunnel API key is encrypted with AES-GCM using Android Keystore and is never logged.
- Tunnel ID and ADB connection address/port are stored in app-private preferences.
- Pairing code is used only for the pairing request and is not persisted.
- Local bridge accepts only loopback TCP and validates a process-random token.

## Validation still required

- Build the Go SDK AAR and Android APK in GitHub Actions.
- Confirm the app can pair to the same Y700 adbd using the app's own identity.
- Confirm `input tap` changes the app's visible test counter.
- With the user's Tunnel ID and runtime key, confirm SDK readiness. Then call `device_info` and, if enabled by the user, `tap_test_zone` through the configured MCP client.
- Confirm stop, reconnect, network changes, and Android background restrictions on this device before treating the runtime as reliable.
