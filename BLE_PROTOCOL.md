# BLE Protocol

This file is the source of truth for the Akrapovic Sound Kit BLE protocol used by this replacement app.

The implementation must remain fail-closed until the values below are verified from the original `si.sunesis.akrapovic.soundkit` APK or from a physical BLE capture.

## Verification Status

| Field | Status |
| --- | --- |
| Original APK package verified | Pending |
| Original APK hash recorded | Pending |
| Service UUID verified | Pending |
| Write characteristic UUID verified | Pending |
| Open command bytes verified | Pending |
| Close command bytes verified | Pending |
| Write type verified | Pending |
| Notify/indicate behavior verified | Pending |
| Pairing/PIN behavior verified | Partially documented from public app notes and Sound Kit manual |

## Known Public Information

The original Google Play listing states that the app communicates with a receiver that drives actuators which open or close exhaust valves. Public notes also mention BLE pairing issues on Android 5.0 and Android 6.0, implying that bonding/PIN behavior is part of the original flow.

The Akrapovic Sound Kit instruction manual should be used to confirm end-user pairing behavior and PIN instructions. Protocol bytes still require APK analysis or BLE capture.

## APK Identity

| Property | Value |
| --- | --- |
| App | Akrapovic Car SoundKit |
| Package | `si.sunesis.akrapovic.soundkit` |
| Developer | Akrapovic Exhaust System Company, d.d. |
| Canonical listing | <https://play.google.com/store/apps/details?id=si.sunesis.akrapovic.soundkit> |
| Verified APK source | Pending |
| Version name | Pending |
| Version code | Pending |
| SHA-256 | Pending |
| Signing certificate SHA-256 | Pending |

## Advertising And Scanning

Pending APK/capture verification.

Expected evidence to collect:

- Advertising device name
- Complete or partial service UUIDs in advertising packets
- Manufacturer data, if present
- Whether the original app scans by service UUID, name prefix, or all BLE devices

Implementation rule until verified:

- The scanner may show devices whose advertised or connected name contains `Akrapovic`, `Akrapovič`, or `SoundKit`.
- The app must label protocol writes unavailable until the service and characteristic are verified.

## Pairing And Bonding

Pending APK/capture verification.

Expected evidence to collect:

- Whether the receiver requires bonding before service discovery or before writes
- PIN/passkey behavior
- Whether the original app intercepts `ACTION_PAIRING_REQUEST`
- Whether Android system pairing UI is required

Implementation rule until verified:

- The replacement app will not hard-code a PIN.
- If bonding is required, the app will surface Android's system pairing flow and log bond state changes.

## GATT Services

| Purpose | UUID | Evidence | Status |
| --- | --- | --- | --- |
| Primary Sound Kit service | Pending | Pending JADX/HCI evidence | Pending |
| Command/write characteristic | Pending | Pending JADX/HCI evidence | Pending |
| Notification characteristic | Pending | Pending JADX/HCI evidence | Pending |
| CCCD descriptor | `00002902-0000-1000-8000-00805f9b34fb` if notifications are used | Standard BLE descriptor; use only if characteristic confirms notify/indicate | Pending |

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

Use this app capture only to identify candidate UUIDs and properties. Command payloads and write type still require original APK analysis, nRF Connect observation, or Android HCI snoop evidence.

## Valve Commands

| Command | Payload | Write Type | Evidence | Status |
| --- | --- | --- | --- | --- |
| OPEN | Pending | Pending | Pending JADX/HCI evidence | Pending |
| CLOSE | Pending | Pending | Pending JADX/HCI evidence | Pending |

Implementation rule until verified:

- `openValve()` and `closeValve()` must return a protocol-not-verified error instead of writing unknown bytes.
- Once verified, update `SoundKitProtocol.kt`, this document, and protocol unit tests in the same change.

## Notify And Status Patterns

Pending APK/capture verification.

Expected evidence to collect:

- Whether the receiver sends command acknowledgements
- Whether valve state is reported as a notification
- Whether connection health requires keepalive writes
- Whether the app infers state from successful writes only

Implementation rule until verified:

- Treat valve state as `Unknown` after connection.
- Treat a successful verified command write as the last requested state unless notifications later prove actual state.

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

