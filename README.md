# Sound Kit Community

Open-source Android replacement scaffold for the abandoned Akrapovič Car SoundKit app (`si.sunesis.akrapovic.soundkit`).

This app is designed for local Bluetooth Low Energy control only. It has no internet permission, no account system, no cloud backend, and no telemetry.

## Why this project

The **official** Car SoundKit Android app for the Sound Kit receiver is **no longer a practical option on current Android**. The original package (`si.sunesis.akrapovic.soundkit`) is **abandoned / unmaintained**: it targets an older permission and execution model, so on **Android 12 and newer** you typically hit **Bluetooth scan/connect permission requirements**, **foreground execution expectations**, and **Play policy constraints** that the original app was never updated to satisfy. In practice, many owners cannot rely on the official app for day-to-day use on a modern phone.

**Sound Kit Community** is an **independent, open-source project**. It is **not** affiliated with, endorsed by, or supported by Akrapovič d.d. or the original publisher. It exists so enthusiasts can run a **maintained** BLE client with explicit safety guardrails and public documentation.

Visual and interaction polish is **ongoing**; see `ROADMAP.md` for planned UX work alongside product features.

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

## Documentation

- `README.md` — overview, build, safety
- `ROADMAP.md` — planned features (favorites, rules, automations, UI/UX)
- `SPEC.md` — technical behavior
- `DECISIONS.md` — architecture decisions
- `DOCS.md` — developer workflow
- `STYLE_GUIDE.md` — code and UI conventions
- `BLE_PROTOCOL.md` / `APK_ANALYSIS.md` — protocol and reverse engineering
- `TESTING.md` — test strategy and hardware checklist

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

Install Android Studio with Android SDK 35 and JDK 17, then from the repository root run:

```bash
./gradlew :app:assembleDebug
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
