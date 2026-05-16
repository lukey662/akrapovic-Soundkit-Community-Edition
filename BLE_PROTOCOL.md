# BLE Protocol

This file is the source of truth for the Akrapovic Sound Kit BLE protocol used by this replacement app.

The implementation must remain fail-closed until the values below are verified from the original `si.sunesis.akrapovic.soundkit` APK or from a physical BLE capture.

## Verification Status

| Field | Status |
| --- | --- |
| Original APK package verified | Verified from APKPure APK static analysis |
| Original APK hash recorded | Verified |
| Service UUID verified | Not present in APK; original app searches all discovered services for the command characteristic |
| Write characteristic UUID verified | Verified from JADX static analysis |
| Open command bytes verified | Not separate; original app sends a single toggle command |
| Close command bytes verified | Not separate; original app sends a single toggle command |
| Write type verified | Verified from JADX static analysis; original app uses Android default characteristic write type |
| Notify/indicate behavior verified | Verified from JADX static analysis; notifications are enabled on the command characteristic |
| Pairing/PIN behavior verified | Verified from JADX static analysis and bundled help/legal text |

## Known Public Information

The original Google Play listing states that the app communicates with a receiver that drives actuators which open or close exhaust valves. Public notes also mention BLE pairing issues on Android 5.0 and Android 6.0, implying that bonding/PIN behavior is part of the original flow.

The Akrapovic Sound Kit instruction manual should still be used to confirm end-user pairing behavior and PIN instructions for each physical receiver. Protocol bytes below are verified from the original Android APK static analysis; a physical GATT capture is still useful to confirm the receiver's service UUID and characteristic properties.

## APK Identity

| Property | Value |
| --- | --- |
| App | Akrapovic Car SoundKit |
| Package | `si.sunesis.akrapovic.soundkit` |
| Developer | Akrapovic Exhaust System Company, d.d. |
| Canonical listing | <https://play.google.com/store/apps/details?id=si.sunesis.akrapovic.soundkit> |
| Verified APK source | User-provided APKPure APK, statically analyzed locally on 2026-05-16 |
| Version name | `1.1.2` |
| Version code | `20` |
| SHA-256 | `dd72807560ead0cc41d0062fe4f4db1d2e917ce296e0c12d1c60fdf6e9b3529e` |
| Signing certificate SHA-256 | `0a0d55fe747cc2c8481a2d30fa6c19df3253986f257513fbb58978ed6994f04a` |

## Advertising And Scanning

The original APK starts an unfiltered legacy LE scan with `BluetoothAdapter.startLeScan(...)`. It does not use a service UUID scan filter.

The scan callback converts the raw advertising bytes to hex, finds the last `FFFFFF` marker, decodes the remaining non-zero bytes as ASCII, and accepts the device only when the decoded value is exactly `103`.

Known receiver evidence:

- Physical receiver advertised as `SoundKit`.
- The bundled help text says the discovery name is `SoundKit`.
- APK scan evidence uses the `FFFFFF` plus ASCII `103` advertising signature, not the display name.

Implementation rule:

- Prefer detecting the `FFFFFF` plus ASCII `103` advertising signature.
- Keep name hints (`Akrapovic`, `Akrapovič`, `SoundKit`, `Sound Kit`) as a compatibility fallback for Android scan records that do not expose the raw signature consistently.
- Do not require a service UUID scan filter because the original APK does not know or use one.

## Pairing And Bonding

The original APK checks the receiver bond state after connection:

- If the device is bonded (`BOND_BONDED` / `12`), it discovers services.
- If the device is not bonded, it calls `BluetoothDevice.createBond()`.
- It listens for `android.bluetooth.device.action.BOND_STATE_CHANGED` and discovers services after the bonded state is reached.
- It does not register an `ACTION_PAIRING_REQUEST` receiver and does not hard-code a PIN.

Bundled help/legal text says pairing requires the six-digit PIN printed on the Sound Kit receiver/manual. Android's system pairing UI is expected to collect that PIN.

## GATT Services

| Purpose | UUID | Evidence | Status |
| --- | --- | --- | --- |
| Primary Sound Kit service | Unknown | Original APK does not reference a service UUID; it searches all discovered services for the characteristic | Not applicable from APK |
| Command/write characteristic | `0000fff4-0000-1000-8000-00805f9b34fb` | `BluetoothLeService.f1405b` in JADX output | Verified |
| Notification characteristic | `0000fff4-0000-1000-8000-00805f9b34fb` | Original APK enables notifications on the same characteristic before opening the control screen | Verified |
| CCCD descriptor | `00002902-0000-1000-8000-00805f9b34fb` | `BluetoothLeService.f1406c` in JADX output | Verified |

### App GATT Profile Capture

After connecting to the receiver, the app emits a copy-ready diagnostics block:

```text
GATT PROFILE START
services=<count>
service[0]=<uuid>
  type=PRIMARY|SECONDARY
  characteristic[0]=<uuid>
    properties=READ|WRITE|WRITE_NO_RESPONSE|NOTIFY|INDICATE (<raw int>)
    permissions=READ|WRITE|... (<raw int>)
    descriptor[0]=<uuid>
      permissions=<flags>
      known=CCCD
GATT PROFILE END
```

Capture steps:

1. Install the debug APK.
2. Open the app and connect to the receiver while parked.
3. Go to `More -> Diagnostics`.
4. Tap `Copy diagnostics report`.
5. Paste the `GATT PROFILE START` / `GATT PROFILE END` block into this file before changing any protocol constants.

Use this app capture to confirm the unknown service UUID and the characteristic properties reported by the physical receiver. Command payloads and write type are already identified from APK static analysis.

## Valve Commands

| Command | Payload | Write Type | Evidence | Status |
| --- | --- | --- | --- | --- |
| TOGGLE | `01` | Android default `BluetoothGattCharacteristic` write type, effectively write request / `WRITE_TYPE_DEFAULT` | `DeviceActivity.onClick()` calls `BluetoothLeService.m1305a(BluetoothLeService.f1405b, new byte[]{1})`; the service calls `setValue(...)` then `writeCharacteristic(...)` without `setWriteType(...)` | Verified |
| OPEN | No distinct payload in original APK | N/A | Original app has one valve image button and sends the toggle payload | Not a distinct APK command |
| CLOSE | No distinct payload in original APK | N/A | Original app has one valve image button and sends the toggle payload | Not a distinct APK command |

Implementation rule:

- The replacement app must not blindly map both OPEN and CLOSE to `01` unless the current receiver state is known.
- If the current state is unknown, OPEN/CLOSE must fail closed and ask the user to wait for receiver status.
- If the current state already matches the requested state, the operation can be treated as a no-op success.
- If the current state is the opposite of the requested state, send the verified toggle payload `01`.

## Notify And Status Patterns

The original APK enables notifications on characteristic `0000fff4-0000-1000-8000-00805f9b34fb` by writing `ENABLE_NOTIFICATION_VALUE` to CCCD `00002902-0000-1000-8000-00805f9b34fb`.

Observed APK status handling:

| Status byte | Original UI action | Interpretation |
| --- | --- | --- |
| `02` | Show closed-valve image | Closed |
| `03` | Show open-valve image | Open |
| `04` | Show error dialog and disconnect | Receiver/error condition |
| `06` | Show open-valve image | Open |
| `07` | Show closed-valve image | Closed |

The APK waits for notification data after service discovery before opening the saved-device control screen, which implies status notifications are the source of truth for the current valve state.

Implementation rule:

- Treat valve state as `Unknown` after connection until a status notification is received.
- Update valve state from status bytes `02`, `03`, `06`, and `07`.
- Treat status byte `04` as a recoverable receiver error and disconnect or block valve commands.
- Do not infer actual valve state from a successful write alone.

## Retry And Timing

Pending APK/capture verification.

Default replacement behavior:

- Retry failed connection attempts with exponential backoff capped at 30 seconds.
- Do not retry valve commands blindly if the write characteristic is missing or protocol values are unverified.
- Allow one explicit user retry after a failed command.

## Evidence Log

Add entries here as protocol facts are discovered.

### YYYY-MM-DD - Initial Repository Scaffold

- No verified APK or capture is present in the repository.
- Public app metadata was found for `si.sunesis.akrapovic.soundkit`.
- Protocol constants remain intentionally unresolved and command writes are disabled.

### 2026-05-16 - Real Receiver Scan And Connection

- Physical receiver advertised as `SoundKit`.
- Receiver address observed as `DC:F3:1C:16:EE:DA`.
- RSSI observed around `-51` to `-59` near the vehicle.
- Android link-layer connection succeeded with `status=0 newState=2`.
- GATT service discovery completed for `SoundKit`.
- Service UUIDs, characteristic UUIDs, descriptors, command bytes, and write type remain pending until a full `GATT PROFILE` diagnostics block and command evidence are captured.

### 2026-05-16 - Original APK Static Analysis

- Source file: `/Users/lukeyates/Downloads/Akrapovič Car SoundKit_1.1.2_APKPure.apk`.
- Static-only handling: the APK was not installed or executed.
- Package verified as `si.sunesis.akrapovic.soundkit`, version `1.1.2`, version code `20`.
- APK SHA-256: `dd72807560ead0cc41d0062fe4f4db1d2e917ce296e0c12d1c60fdf6e9b3529e`.
- Signer DN: `CN=Sunesis, OU=IT, O=Akrapovic Manufacturing, L=Ivancna Gorica, ST=Ivancna Gorica, C=SI`.
- Signer certificate SHA-256: `0a0d55fe747cc2c8481a2d30fa6c19df3253986f257513fbb58978ed6994f04a`.
- Manifest permissions are limited to Bluetooth, vibration, and location for BLE scanning; no `INTERNET` permission is declared.
- No native `.so` libraries were present in the decoded APK.
- The original app scans without a service UUID filter and accepts advertisements whose raw bytes decode to signature `103` after the last `FFFFFF` marker.
- The original app bonds through Android system pairing (`createBond`) and does not hard-code a PIN.
- The write/notify characteristic is `0000fff4-0000-1000-8000-00805f9b34fb`.
- The original app writes only one command payload, `01`, which is a toggle rather than distinct OPEN/CLOSE commands.
- The original app uses the Android default characteristic write type by not calling `setWriteType`.
- The original app enables notifications on the same characteristic with CCCD `00002902-0000-1000-8000-00805f9b34fb` and maps status bytes `02`/`07` to closed and `03`/`06` to open.

