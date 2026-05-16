# APK Analysis

This document captures the repeatable process for finding, verifying, and analyzing the original Akrapovic Car SoundKit APK (`si.sunesis.akrapovic.soundkit`) before enabling valve writes in this replacement app.

## Current APK Discovery Status

The canonical package identity is:

- App name: Akrapovic Car SoundKit
- Package name: `si.sunesis.akrapovic.soundkit`
- Developer: Akrapovic Exhaust System Company, d.d.
- Google Play listing: <https://play.google.com/store/apps/details?id=si.sunesis.akrapovic.soundkit>
- Public mirror candidates found during initial research:
  - Softonic: `Akrapovic Car SoundKit`, reported version `1.1.2`, reported size `4.11 MB`, reported APK filename `si-sunesis-akrapovic-soundkit-20-52309198-bc720553a93abc4732a56f5fab5c32b0.apk`
  - FMSAT: `Akrapovic Car SoundKit`, reported package `si.sunesis.akrapovic.soundkit`

Do not use APKs whose package name differs from `si.sunesis.akrapovic.soundkit` for car protocol extraction. In particular, `si.sunesis.akrapovic.soundkitcustom` appears to be a different motorcycle/custom Sound Kit app and must not be treated as authoritative for the 2026 Audi RS3 car receiver.

## Verification Checklist

Before decompiling, verify the APK identity:

```bash
apksigner verify --print-certs original-akrapovic-soundkit.apk
aapt dump badging original-akrapovic-soundkit.apk
sha256sum original-akrapovic-soundkit.apk
```

Record:

- Package name
- Version name and version code
- SHA-256
- Signing certificate SHA-256
- Source URL and retrieval date
- APK size in bytes

Reject the APK if:

- The package name is not exactly `si.sunesis.akrapovic.soundkit`.
- The signing certificate or app metadata does not match a credible Akrapovic release.
- The APK is repackaged, modified, or bundled with an unrelated downloader.

## Tooling

Install the analysis tools:

```bash
brew install jadx apktool android-commandlinetools
```

If `aapt` or `apksigner` is not on `PATH`, use the Android SDK build-tools copy:

```bash
$ANDROID_HOME/build-tools/<version>/aapt dump badging original-akrapovic-soundkit.apk
$ANDROID_HOME/build-tools/<version>/apksigner verify --print-certs original-akrapovic-soundkit.apk
```

## JADX Static Analysis

Create a decompiled Java/Kotlin-like source tree:

```bash
jadx --deobf --show-bad-code --output-dir reverse/jadx original-akrapovic-soundkit.apk
```

Search the JADX output for BLE symbols:

```bash
rg "BluetoothGatt|BluetoothGattCallback|BluetoothDevice|BluetoothLeScanner|startScan|connectGatt|writeCharacteristic|setCharacteristicNotification|UUID|0000" reverse/jadx
```

Important files/classes to inspect:

- BLE scan callback classes
- GATT callback classes
- Any class that constructs `UUID.fromString(...)`
- Any class that writes `byte[]` values to a characteristic
- Any class that handles `ACTION_PAIRING_REQUEST`, bond state changes, or PIN entry
- Any resource strings containing device names, service UUIDs, PINs, or receiver labels

## APKTool Resource And Manifest Analysis

Decode resources and manifest:

```bash
apktool d --force --output reverse/apktool original-akrapovic-soundkit.apk
```

Inspect:

```bash
rg "BLUETOOTH|ACCESS_FINE_LOCATION|PAIR|BOND|permission|service|receiver|uses-permission" reverse/apktool/AndroidManifest.xml reverse/apktool/res
rg "Akrapovic|SoundKit|PIN|Bluetooth|BLE|valve|open|close" reverse/apktool/res
```

Record the original permission model:

- Bluetooth permissions
- Location permissions
- Background service declarations
- Broadcast receivers for pairing or bond state
- Minimum and target SDK

## BLE Protocol Extraction

From static analysis, capture:

- Scan filters: service UUIDs, manufacturer data, or device name patterns
- Primary GATT service UUID
- Writable characteristic UUID
- Notify/indicate characteristic UUIDs
- Client Characteristic Configuration Descriptor usage (`00002902-0000-1000-8000-00805f9b34fb`)
- Open valve command bytes
- Close valve command bytes
- Write type: with response or without response
- Any unlock, authentication, checksum, sequence, or keepalive bytes

If the command is constructed dynamically, trace the call chain from the UI button handler to the final `BluetoothGatt.writeCharacteristic(...)` invocation.

## Runtime Capture Workflow

If static analysis is insufficient, capture traffic from the original app:

1. Enable Developer Options on a test Android phone.
2. Enable Bluetooth HCI snoop log.
3. Pair/connect the original app to the Sound Kit receiver.
4. While parked and safe, send CLOSED then OPEN commands multiple times.
5. Pull the HCI log:

```bash
adb bugreport bugreport.zip
```

6. Open the extracted Bluetooth snoop log in Wireshark.
7. Filter for ATT writes:

```text
btatt.opcode == 0x12 || btatt.opcode == 0x52
```

8. Record the handle, UUID, payload bytes, response behavior, and timing.

Use nRF Connect as a secondary validation tool after UUIDs and characteristics are known. Do not blindly write unknown bytes to the receiver.

## Findings Template

Copy findings into `BLE_PROTOCOL.md` using this structure:

- APK identity and hash
- Evidence source: JADX, APKTool, HCI log, nRF Connect, or physical receiver test
- Device advertising name and scan filters
- Pairing/bonding behavior and PIN flow
- GATT service table
- Command characteristic
- Notify/indicate characteristic
- Open command payload
- Close command payload
- Write type
- Retry and reconnect behavior
- Safety notes and unresolved questions

