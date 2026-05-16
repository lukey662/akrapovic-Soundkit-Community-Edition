# Sound Kit Community

Open-source Android replacement scaffold for the abandoned Akrapovic Car SoundKit app (`si.sunesis.akrapovic.soundkit`).

This app is designed for local Bluetooth Low Energy control only. It has no internet permission, no account system, no cloud backend, and no telemetry.

## Status

The Android app builds around a fail-closed BLE protocol layer. OPEN and CLOSE writes are intentionally disabled until the original APK or a physical HCI capture verifies:

- Sound Kit service UUID
- Write characteristic UUID
- Open command bytes
- Close command bytes
- Write type
- Pairing/bonding behavior
- Notify/indicate behavior

See `APK_ANALYSIS.md` and `BLE_PROTOCOL.md`.

Testing strategy and the physical receiver smoke checklist are documented in `TESTING.md`.

## Features

- Kotlin, Jetpack Compose, and Material 3
- MVVM + repository architecture
- Standard Android BLE GATT APIs
- Android 12+ Bluetooth permission handling
- Legacy Android 8-11 location permission handling for BLE scanning
- Foreground `connectedDevice` service
- Persistent notification actions
- Quick Settings tile
- Local diagnostics log and export
- Local-only Android Auto IoT template surface
- Hilt dependency injection
- DataStore settings
- Debug Timber logging

## Build

Install Android Studio with Android SDK 35 and JDK 17, then run:

```bash
gradle :app:assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Sideload

Enable USB debugging on the test phone, connect it, then run:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Grant the requested Bluetooth permissions when prompted.

## Safety

Test while parked. Do not send unknown BLE payloads to the receiver. This repository refuses valve writes until the protocol is verified and documented.

## Android Auto

Android Auto support is implemented as a local-only IoT template surface:

```text
Android Auto screen -> phone CarAppService -> phone BLE service -> Sound Kit receiver
```

This does not use the internet. Public Google Play approval for Android Auto may be policy-sensitive because the app controls a vehicle-adjacent device.

## Reverse Engineering

Start with `APK_ANALYSIS.md`. The best APK source must be the original car package:

```text
si.sunesis.akrapovic.soundkit
```

Do not use `si.sunesis.akrapovic.soundkitcustom` as the source of truth for the car receiver protocol.

