# Sound Kit Community — iOS

SwiftUI companion for the Akrapovič Car Sound Kit BLE receiver. Mirrors Android safety rules and core features; **developer install only** until hardware validation and public distribution are complete.

## Requirements

- macOS with **Xcode 15+** (Swift 5.9+)
- **iOS 17.0+** deployment target
- Physical iPhone with Bluetooth LE (CoreBluetooth)
- Apple Developer account (free or paid) to sign and run on device

## Quick start (developers)

1. Open **`ios/SoundKitCommunity.xcodeproj`** in Xcode.
2. Select the **SoundKitCommunity** scheme and your iPhone as the run destination.
3. In the app target **Signing & Capabilities**, choose your **Team** (the project ships with an empty team ID for CI).
4. Build and run (**⌘R**). Grant Bluetooth when prompted during onboarding.

BLE scanning and valve control require a **physical device** — the Simulator cannot exercise CoreBluetooth.

### Regenerating the Xcode project

The project is generated from [`project.yml`](../project.yml) via [XcodeGen](https://github.com/yonaskolb/XcodeGen):

```bash
cd ios && xcodegen generate
```

Commit both `project.yml` and `SoundKitCommunity.xcodeproj` after structural changes.

## Distribution (dev v1)

| Method | Notes |
|--------|--------|
| **Xcode Run** | Fastest path for daily development on a registered device. |
| **Ad Hoc archive** | Archive → Distribute → Ad Hoc for up to 100 registered UDIDs (no public link). |

[INSTALL_IOS.md](../../INSTALL_IOS.md) documents Ad Hoc and TestFlight
preparation only; it does not claim a TestFlight or App Store release. Both
require Apple Developer access and physical iPhone/receiver smoke evidence.

## BLE protocol

GATT UUIDs, toggle payload, status bytes, and state-gated OPEN/CLOSE rules live in `SoundKitProtocol.swift`. Source of truth for both platforms:

**[BLE_PROTOCOL.md](../../BLE_PROTOCOL.md)**

Do not change protocol constants without updating that file and Android `SoundKitProtocol.kt`.

## Project layout

```text
ios/SoundKitCommunity/
  BLE/              CoreBluetooth scan, connect, GATT, reconnect cap
  Data/             SettingsStore (UserDefaults), DiagnosticsStore
  Domain/           DriveModeEngine, QuietWindowEvaluator, ConnectReadyObserver
  Models/           SoundKitSettings, SavedReceiver, QuietStartSettings
  Theme/            GarageThemes (Studio + Audi RS Dark)
  ViewModel/        SoundKitViewModel
  Views/            RootView, onboarding, Home, Drive mode, Diagnostics
  SoundKitProtocol.swift
  VehicleCompatibility.swift
  DiagnosticsSupport.swift
ios/SoundKitCommunityTests/   XCTest unit tests
```

## Features (dev v1)

- Scan (Sound Kit name hints + advertising signature `103`)
- Connect, system PIN pairing when required, notifications on `fff4`
- State-gated valve toggle (`0x01` only when state known and differs)
- Status `0x04` — stay connected, disable valve buttons
- Onboarding with risk disclaimer and vehicle picker
- Saved receivers, connect-on-launch, auto-reconnect (max 8 attempts)
- Drive mode: preferred Open/Closed on connect, quiet neighbours window
- Audi RS Dark theme (default when Audi RS3 selected)
- Diagnostics log, share export, mailto **support@appsforgood.net**
- Siri App Intents for Open, Close, and status; command success is spoken only after the receiver notification confirms it
- CarPlay `CPGridTemplate` scaffold, gated on Apple approval of `com.apple.developer.carplay-driving-task` (not currently available)

## Testing

```bash
cd ios
xcodebuild -project SoundKitCommunity.xcodeproj -scheme SoundKitCommunity \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  CODE_SIGNING_ALLOWED=NO test
```

Physical receiver smoke checklist: **[TESTING.md](../../TESTING.md)** § iOS device smoke.

## Info.plist keys

| Key | Purpose |
|-----|---------|
| `NSBluetoothAlwaysUsageDescription` | BLE scan and connect |
| `NSBluetoothPeripheralUsageDescription` | Legacy compatibility |
| `UIBackgroundModes` → `bluetooth-central` | CoreBluetooth recovery and state restoration |

No internet usage. Background restoration never authorizes a blind valve write; Siri and CarPlay commands still require a ready receiver and notification confirmation.

## CarPlay entitlement

CarPlay is disabled: normal builds set `CARPLAY_ENABLED=NO` and contain neither
the CarPlay scene declaration nor
`com.apple.developer.carplay-driving-task`. Do not enable, advertise, or
submit the CarPlay scene without Apple approval and a provisioning profile
containing that entitlement.

An Apple Developer Account holder must submit an entitlement-request package
with the driving-task use case, safety controls (parked-only, state-gated,
notification-confirmed commands), phone-only setup/diagnostics design,
supported vehicles and regions, physical hardware evidence, privacy
disclosures, and App Review contact/demo instructions. Approval is high-risk
for valve control because it can affect noise, emissions, and driving safety;
approval also does not guarantee App Store acceptance.

If approval is absent or rejected, distribute the phone UI and Siri App
Intents only. Siri has no entitlement workaround either: it must fail safely
when a ready receiver cannot be restored and tell the user to unlock and open
the app. See [DOCS.md](../../DOCS.md) and
[INSTALL_IOS.md](../../INSTALL_IOS.md) for the complete boundaries and
distribution preparation.
