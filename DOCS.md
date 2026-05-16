# Developer Docs

## Project Layout

```text
app/src/main/java/com/akrapovic/soundkit/community/
  ble/          BLE scanning, permissions, GATT, protocol guardrails
  car/          Android Auto Car App Library surface
  data/         repositories, DataStore settings, diagnostics
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

## Testing

Use `TESTING.md` as the source of truth for unit, regression, instrumented smoke, CI, and physical receiver validation.

## Roadmap

Planned features, UX direction, and non-goals are tracked in `ROADMAP.md`.

## Protocol Enablement Workflow

1. Obtain and verify the original `si.sunesis.akrapovic.soundkit` APK.
2. Follow `APK_ANALYSIS.md`.
3. Populate `BLE_PROTOCOL.md` with evidence.
4. Update `SoundKitProtocol.kt`.
5. Add protocol unit tests for UUIDs, write type, and payload bytes.
6. Test while parked with diagnostics enabled.

## Android Auto Testing

The Android Auto surface uses the Android for Cars App Library IoT category. It is local-only and delegates all BLE work to the phone service.

Use the Desktop Head Unit or a developer-enabled Android Auto environment for testing. Public distribution may require additional policy review.

## Release Notes

The current build is a protocol-safe scaffold. It is suitable for APK analysis, scan/connect diagnostics, and UI/service validation, but it will not send valve commands until protocol verification is complete.

