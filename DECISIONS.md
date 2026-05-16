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

## 2026-05-16: Verified Toggle Protocol With State-Gated Commands

### Context

Static analysis of the original `si.sunesis.akrapovic.soundkit` APK verified the command characteristic, notification behavior, pairing flow, and command payload. The original app sends a single toggle byte (`01`) rather than separate open and close commands.

### Decision

Expose OPEN and CLOSE as user-facing intents, but send the toggle payload only when notification-derived valve state proves the receiver is in the opposite state. Block writes while state is unknown, treat same-state requests as no-op success, and update actual state only from receiver status notifications.

### Consequences

- The app can support a simple open/close UX without pretending the protocol has distinct open and close payloads.
- A stale or unknown valve state fails closed instead of risking an unintended toggle.
- Android Auto, notification actions, quick settings, and phone UI all inherit the same state-gated command path.

## 2026-05-16: Consumer-First Primary UX

### Context

The app had accumulated developer-facing protocol language, dense diagnostic panels, and HUD-style chrome on primary screens.

### Decision

Make the primary journey read like a polished companion app: guided setup, current state, one safe next action, simple settings, and troubleshooting behind Diagnostics.

### Consequences

- Normal use no longer exposes hashes, UUIDs, or protocol status unless the user opens Diagnostics.
- The app remains testable and transparent, but developer detail moves out of the main control path.
- Compose smoke tests assert user-facing labels rather than reverse-engineering terminology.

## 2026-05-16: Theme Families With Local Brand Marks

### Context

The app needs more polished visual identity than a single dark preset, while preserving a local-first, self-contained build.

### Decision

Model garage appearance as brand-inspired families, each with explicit Light and Dark variants, theme-driven gradients, and a local drawable brand mark. Keep the saved setting as a single theme ID and flatten family variants into `GarageThemePresets` for existing lookup code.

### Consequences

- Users can switch between light and dark versions per car theme without adding a separate global display-mode setting.
- The Control screen and theme picker can show the selected family mark without changing BLE behavior.
- Brand marks stay local drawable resources and can be replaced by the user with assets they have rights to use.

## 2026-05-16: Local-Only Control

### Context

The user wants phone BLE control only, not internet-connected control.

### Decision

Do not request `INTERNET`; do not add analytics, accounts, remote APIs, or cloud services.

### Consequences

- The privacy and security surface stays small.
- Android Auto is a UI surface over the phone's BLE service, not a network transport.

## 2026-05-16: User-Initiated Diagnostics Sharing And Local Crash Capture

### Context

Hardware validation depends on long BLE diagnostics reports and crash details, but the app must remain local-only with no telemetry backend.

### Decision

Generate diagnostics and crash reports on-device, share them only through Android's user-initiated share sheet, and expose attachments through a non-exported `FileProvider`.

### Consequences

- The app still does not request `INTERNET`.
- Crash logs remain local until the user chooses to copy, share, or dismiss them.
- Email and messaging apps can receive full `.txt` attachments without relying on clipboard length.

## 2026-05-16: Fakeable BLE Boundaries For Tests

### Context

Production BLE classes depend on Android framework objects and physical radio state, which makes JVM tests brittle and hardware-dependent.

### Decision

Introduce narrow interfaces for scanning, connection management, and settings persistence while keeping the production implementations backed by Android BLE and DataStore.

### Consequences

- Repository and ViewModel tests can run in CI without a car or BLE receiver.
- Regression tests can prove fail-closed behavior before protocol values are known.
- Android-specific behavior remains covered by smaller instrumented smoke tests.

