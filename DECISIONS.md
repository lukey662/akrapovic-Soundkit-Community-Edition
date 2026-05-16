# Decisions

## 2026-05-16: Use Standard Android GATT APIs

### Context

The project needs reliable Android 12+ BLE support. `androidx.bluetooth` remains alpha-only in public AndroidX release information.

### Decision

Use platform `android.bluetooth` GATT APIs with compatibility wrappers for API differences.

### Consequences

- The app avoids an unstable Bluetooth abstraction.
- Deprecated pre-API 33 write calls are isolated and documented because Android does not provide the newer overload on older devices.
- BLE behavior remains explicit and easier to compare against HCI captures.

## 2026-05-16: Fail Closed Until Protocol Verification

### Context

The original APK was not present in the repository, and public mirrors do not provide enough evidence to trust service UUIDs or command bytes.

### Decision

Implement the app and BLE lifecycle, but keep valve writes disabled until `BLE_PROTOCOL.md` is populated from verified APK analysis or physical BLE capture.

### Consequences

- The app cannot accidentally send unknown bytes to the receiver.
- UI, services, Android Auto, and notification actions all share the same guarded repository path.
- Enabling valve writes requires a small, auditable protocol update plus unit tests.

## 2026-05-16: Local-Only Control

### Context

The user wants phone BLE control only, not internet-connected control.

### Decision

Do not request `INTERNET`; do not add analytics, accounts, remote APIs, or cloud services.

### Consequences

- The privacy and security surface stays small.
- Android Auto is a UI surface over the phone's BLE service, not a network transport.

## 2026-05-16: Fakeable BLE Boundaries For Tests

### Context

Production BLE classes depend on Android framework objects and physical radio state, which makes JVM tests brittle and hardware-dependent.

### Decision

Introduce narrow interfaces for scanning, connection management, and settings persistence while keeping the production implementations backed by Android BLE and DataStore.

### Consequences

- Repository and ViewModel tests can run in CI without a car or BLE receiver.
- Regression tests can prove fail-closed behavior before protocol values are known.
- Android-specific behavior remains covered by smaller instrumented smoke tests.

