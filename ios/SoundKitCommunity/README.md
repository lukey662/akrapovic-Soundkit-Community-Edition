# Sound Kit Community — iOS Companion

Minimal SwiftUI scaffold for the Akrapovič Car Sound Kit BLE receiver. Mirrors the Android app protocol constants and safety rules; BLE transport is stubbed for incremental implementation.

## Requirements

- Xcode 15+ (Swift 5.9+)
- iOS 17+ deployment target
- Physical iPhone with Bluetooth LE (CoreBluetooth)

## Setup

1. In Xcode: **File → New → Project → iOS → App**
   - Product name: `SoundKitCommunity`
   - Interface: SwiftUI
   - Language: Swift
   - Minimum deployment: iOS 17.0
2. Add the Swift sources from this folder to the app target (drag into the project navigator, or **File → Add Files to "SoundKitCommunity"…**).
3. Replace the template `App` and `ContentView` files if Xcode created duplicates — keep `SoundKitCommunityApp.swift` and `ContentView.swift` from this folder.
4. Enable the **Bluetooth** capability if you add background modes later; for foreground scan/connect, no special capability is required beyond usage strings.
5. Add to **Info.plist** (or target **Info** tab → Custom iOS Target Properties):

   | Key | Value |
   | --- | --- |
   | `NSBluetoothAlwaysUsageDescription` | Sound Kit Community scans for and connects to your Akrapovič Sound Kit receiver to open and close exhaust valves. |
   | `NSBluetoothPeripheralUsageDescription` | Same as above (legacy key; include for older SDK compatibility). |

6. Build and run on a device (BLE scanning is limited on the Simulator).

## BLE Protocol

All GATT UUIDs, toggle payload, status bytes, and state-gated OPEN/CLOSE rules are defined in `SoundKitProtocol.swift` and documented in the repository source of truth:

**[BLE_PROTOCOL.md](../../BLE_PROTOCOL.md)**

Do not change protocol constants without updating that file and the Android `SoundKitProtocol.kt`.

## Project Layout

| File | Purpose |
| --- | --- |
| `SoundKitCommunityApp.swift` | `@main` SwiftUI entry |
| `ContentView.swift` | Placeholder home: scan, connect, open, close |
| `SoundKitProtocol.swift` | Verified protocol constants (fff4, toggle `0x01`, status bytes, signature `103`) |
| `BLEManager.swift` | CoreBluetooth stub with state-gated toggle comments |
| `VehicleCompatibility.swift` | Vehicle catalog tiers (mirrors Android) |
| `DiagnosticsSupport.swift` | Support email constant |

## Next Steps

- Implement `CBCentralManager` scan filtering via `SoundKitProtocol.hasAdvertisingSignature`
- Discover services, locate characteristic `0000fff4-…`, enable notifications on CCCD `00002902-…`
- Gate valve writes on known `ValveState` per `SoundKitProtocol.commandPayload`
- Bond via system pairing when the receiver requires a PIN (see BLE_PROTOCOL.md)
