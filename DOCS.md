# Developer Docs

## Project Layout

```text
app/src/main/java/com/akrapovic/soundkit/community/
  ble/          BLE scanning, permissions, GATT, protocol guardrails
  car/          Android Auto Car App Library surface
  data/         repositories, DataStore settings, diagnostics
  diagnostics/  diagnostics report generation, local crash capture, share intents
  domain/       app state and result models
  service/      foreground service, notification, Quick Settings tile
  ui/           Compose screens, theme, ViewModel
```

## Local Development

1. Install Android Studio and JDK 17.
2. Install Android SDK 35.
3. Open the repository in Android Studio.
4. Build the debug variant.
5. Install on a physical Android device with BLE support.

## UI System

The app uses a consumer-first companion UI built from reusable Compose components in `ui/components`. Screens should prefer calm hierarchy, rounded elevated panels, concise headers, large touch targets, and theme-driven gradients instead of square debug-style cards or protocol-heavy panels. Garage themes are persisted in DataStore and applied app-wide through `SoundKitTheme`; each brand-inspired family has explicit Light and Dark variants plus a local `brand_*.xml` mark that can be replaced with assets the user has rights to use.

## Testing

Use `TESTING.md` as the source of truth for unit, regression, instrumented smoke, CI, and physical receiver validation.

Local `:app:assembleDebug` runs `:app:testDebugUnitTest` first. GitHub Actions runs unit tests, Android 35 emulator smoke tests, then publishes the debug APK only after both test layers pass.

## Diagnostics Export And Crash Logs

Diagnostics are local-only. `More -> Diagnostics` can copy a full report to the clipboard or share a generated `.txt` attachment through Android's share sheet. Reports include app version, device metadata, BLE diagnostics, and any pending crash log. The app does not request `INTERNET`, does not upload logs automatically, and uses a non-exported `FileProvider` to grant one-time access to files selected by the user.

If the app crashes, `CrashReporter` writes `files/crashes/last_crash.txt` synchronously before delegating to Android's crash handler. On next launch, Diagnostics shows a crash panel with copy/share/dismiss actions.

## Roadmap

Planned features, UX direction, and non-goals are tracked in `ROADMAP.md`.

## Protocol Enablement Workflow

1. Review `APK_ANALYSIS.md` and `BLE_PROTOCOL.md` for the verified original APK evidence.
2. Connect to the physical receiver with this app and complete Android system pairing with the receiver/manual PIN if prompted.
3. Copy the `GATT PROFILE START` / `GATT PROFILE END` diagnostics block from `More -> Diagnostics`.
4. Confirm the physical receiver exposes characteristic `0000fff4-0000-1000-8000-00805f9b34fb` and CCCD `00002902-0000-1000-8000-00805f9b34fb`.
5. Confirm notification status bytes match `BLE_PROTOCOL.md` before running command smoke tests.
6. Test commands while parked with diagnostics enabled.

## Android Auto Testing

The Android Auto surface uses the Android for Cars App Library IoT category. It is local-only and delegates all BLE work to the same repository path as the phone UI, including state-gated toggle safety.

Use the Desktop Head Unit or a developer-enabled Android Auto environment for testing. Public distribution may require additional policy review.

## Release Notes

The current build uses the verified APK toggle protocol and keeps commands state-gated. It is suitable for parked physical receiver smoke testing before public release.

