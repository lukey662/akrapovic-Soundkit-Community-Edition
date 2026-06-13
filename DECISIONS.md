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

## 2026-05-16: Diagnostics Share Is File-Only To Avoid Email Auto-Population

### Context

The previous diagnostics share intent attached the report file *and* set `EXTRA_SUBJECT` and `EXTRA_TEXT` to a preview of the report. Gmail (and similar email handlers) treat any `ACTION_SEND` with subject/body text as a draft email and auto-populate the user's signed-in account in the From: line. Users reported this leaked their personal email into the share flow even when they only wanted to attach the file.

### Decision

Strip `EXTRA_SUBJECT` and `EXTRA_TEXT` from the share intent. The chooser now offers a **file attachment only** flow, with `EXTRA_STREAM` and `ClipData` carrying the report URI. Pair this with a new **Save to file** action that uses `ACTION_CREATE_DOCUMENT` (the Storage Access Framework) so users can save reports to Files / Drive / SD card without engaging any email handler at all.

### Consequences

- No more From: auto-fill of the user's email account.
- Users get an explicit "Save" option that never touches a mail client.
- Email clients can still receive the report when chosen explicitly; they simply start with a blank compose.
- The instrumented test `DiagnosticsShareTest` asserts that the share intent contains neither `EXTRA_SUBJECT` nor `EXTRA_TEXT` to prevent regressions.

## 2026-05-16: Blocking First-Run Risk Acknowledgement

### Context

The app drives a reverse-engineered protocol on a third-party exhaust accessory. Misuse can affect warranty, emissions compliance, and personal safety. Burying that disclosure inside Settings or the README does not give users a reasonable chance to opt out.

### Decision

Show a non-dismissible `AlertDialog` on first launch that summarizes: independent project, reverse-engineered protocol, may void warranty, may affect emissions / noise compliance, do not operate while driving, no warranty. The user must tap **I understand and accept** to continue, or **Exit app** to leave. Acceptance timestamp is persisted in DataStore so the dialog never returns once accepted.

### Consequences

- Users are explicitly informed before any feature (including scanning) is reachable.
- The same disclaimer is mirrored prominently in `README.md`.
- Clearing app data re-shows the dialog, which is the desired behavior.
- The dialog adds one tap to the first launch only; no other flow is affected.

## 2026-05-16: Projected Android Auto Is A Non-Goal

### Context

Several users asked why the app does not appear in their car's projected Android Auto. Google's Android Auto policy only allows projected apps in published categories (navigation, parking, charging, media, messaging, video, weather, POI). A valve-toggle controller fits none of them. The Car App Library `IOT` category we declare is intended for Android **Automotive OS** built-in head units and the Desktop Head Unit simulator.

### Decision

Document the limitation in `DOCS.md` § Android Auto Testing and list "Projected Android Auto distribution" as a Non-goal in `ROADMAP.md`. Continue to ship the IoT Car App surface so Automotive OS users and DHU testers can reach the same state-gated controls.

### Consequences

- Expectations are set up front, with steps to test via the Desktop Head Unit when contributors want to exercise the surface.
- No effort is sunk into trying to fit valve control into an unrelated Play category.
- If Google ever publishes a category that fits, this decision can be revisited.

### Update (June 2026)

Google documents the Car App Library **IoT** category for both **projected Android Auto** and **Automotive OS**. Sideloaded debug/release APKs still require AA **Developer mode**, **Unknown sources**, and often **Customize launcher** on the phone — installing via `adb` alone is not enough. In-app setup lives under **More → Android Auto**; diagnostics exports include **CAR APP READINESS**. Play Store projected listing remains a non-goal.

## 2026-05-16: Saved Receivers And Rules Engine (Design Spike)

### Context

Users need multiple remembered BLE receivers, connect-on-launch, and a path toward automation without risking surprise valve writes. Notification and Quick Settings surfaces must respect receiver-not-ready (status `0x04`) the same way as the main UI.

### Decision

- Persist up to **8** saved receivers as JSON in DataStore (`SavedReceiversCodec`), migrating legacy `remembered_device_*` keys on first read. Exactly one default is enforced in the repository.
- Share connect policy via **`RememberedDeviceConnector`** for phone launch, car bootstrap, and retry.
- **Connect on launch** is separate from **auto reconnect** (post-drop behavior in `BleRepository`).
- Extract pure **`NotificationCopy`** and **`QsTilePresenter`** mappers; gate valve actions when disconnected, valve unknown, or not-ready.
- **Rules engine (spike only):** domain models + `RuleEvaluator` pure logic + unit tests. **No** production persistence, WorkManager, geofence permissions, or BLE execution in this epic.

### Precedence (rules spike)

Manual override > manual pause (no automation) > enabled rules by priority. Writes remain state-gated (connected + known valve) when execution is added later.

### Storage evolution

Start with DataStore JSON for rules prototypes; move to **Room** if rule count, queries, or execution logs exceed comfortable JSON size.

### Consequences

- Settings and scan surfaces manage favorites; notifications show nickname when connected.
- Rules ADR/SPEC describe triggers and conflicts; implementation can land incrementally without blocking favorites ship.

## 2026-05-17: Beta Automation Execution

### Context

Roadmap items for rules execution, schedules, geofencing, and notification automation UX needed shipping behind a clear experimental surface without polluting the primary Home journey.

### Decision

- **Settings → Automation (Beta)** hub with disclaimer gate, master pause, rules CRUD, geofence zones (max 4), and execution log.
- **`RuleExecutionEngine`** evaluates persisted rules when connected + valve known + not-ready clear; debounce 60s per rule/action; uses existing `BleRepository` open/close paths.
- **WorkManager** periodic evaluation (15 min minimum) plus evaluate on connection-ready.
- **Geofencing** via Play Services `GeofencingClient`; `ACCESS_FINE_LOCATION` and optional background location only for Beta geofence setup — not required for core BLE scan (API 31+ uses `neverForLocation` scan flag).
- **Notification** adds Pause/Resume automation actions and last-run summary line when rules exist.

### Consequences

- Automation is experimental; users must accept Beta disclaimer once.
- Play policy for background location remains user responsibility when enabling geofences.
- Room migration remains optional until rule/log volume warrants it.

## 2026-06-08: Drive Mode Replaces Beta Automation

### Context

Beta rules, geofencing, and WorkManager polling added complexity and background churn. Users wanted a simple “favorite valve on connect” plus optional quiet morning start, and auto-reconnect could loop indefinitely when the receiver was away.

### Decision

- Replace **`RuleExecutionEngine`** with **`DriveModeEngine`**: preferred Open/Closed on connect; optional quiet-start window (hold closed N minutes after each connect).
- **Settings** hosts full drive mode controls; **Home** shows a shortcut card.
- Remove Beta hub, geofence UI, rules persistence, WorkManager worker, and `play-services-location`.
- Cap **auto-reconnect at 8 attempts** (~2 min); surface recoverable “Couldn't reach receiver — tap to retry”.
- Reuse **`RuleExecutionLog`** for last drive mode apply in notification copy.

### Consequences

- Manual valve toggle on Home wins for the current connect session.
- Geofence/schedule experiments archived; `RuleEvaluator` domain code may remain for unit tests only.
- Version **0.3.0**.

## 2026-06-08: Minimal Ring-and-Disc Valve Hero

### Context

Several iterations tried to reach a premium exhaust-tip look: procedural Canvas layers, Blender sprite sequences, and HTML prototypes. Photoreal and complex vector approaches read as over-engineered or failed to meet the bar; the team preferred a **very simple** Home hero that reads instantly at ~168dp.

### Decision

Ship a minimal procedural **`ValveVisual`**: carbon outer rim stroke, titanium lip stroke, dark bore, and a **flat disc at 80% fill** when closed. When open, the disc animates away (scale to zero) and the lip brightens. No heat glow, air effects, butterfly plates, or sprite assets in the app.

- Design prototypes live in `design/valve-simple-animations.html` (Option 5: Ring + Disc).
- Optional Blender pipeline remains in `design/blender/` for future experiments but is **not** wired into the app.

### Consequences

- Fast, crisp, and maintainable in Compose; works on all devices without large assets.
- Does not attempt photoreal titanium/carbon; adjacent status text carries precision for a11y.
- Public `ValveVisual` API unchanged; command-in-flight ring and success ripple preserved.

## 2026-06-13: Connect-Ready Edge Trigger, Quiet Hours UI, Dark Primary Contrast

### Context

Quiet neighbours appeared to hold valves closed for the entire morning window because `BleConnectionService` re-ran drive mode on every valve state change, clearing manual override. Quiet window start/end times were not editable in Settings. Audi RS Dark primary buttons faded into the matte black background due to near-black gradient partners and dark `onPrimary` text.

### Decision

- Introduce **`ConnectReadyObserver`**: drive mode runs only on the first connect-ready transition per BLE session (connected + valve known + not status `0x04`), not when valve state updates mid-session.
- Register **`onUserValveAdjustment`** from notification Open/Close actions and Android Auto toggle, matching Home behavior.
- Add **start/end TimePicker** controls for quiet neighbours; default hold **3 minutes**.
- Fix dark-theme **primary button contrast**: Audi RS Dark darker-red gradient partner, dark `onPrimary` → Pearl, low-contrast gradient guard in `AkraActionButton`.
- **Auto-reconnect**: honor Settings toggle on initial connect failure; skip duplicate reconnect scheduling when a job is already active.

### Consequences

- Manual valve control during quiet hold persists until disconnect or drive-mode resume.
- Reconnect spam remains bounded (8 attempts); duplicate error states no longer restart the backoff counter mid-flight.
- Theme preview and Home CTA remain readable on near-black garage themes.

## 2026-06-13: Vehicle Tiers, Support Email, And iOS Companion

### Context

Owners asked which cars are supported beyond the RS3 reference install, how to get help with diagnostics, and whether an iOS app is feasible.

### Decision

- Model support by **receiver protocol**, not VIN: **Supported** (Tier 1, RS3 reference) vs **Beta** (Tier 2, same Car SoundKit BLE stack, unvalidated here).
- Persist `selectedVehicleId` from onboarding; include vehicle tier in diagnostics headers.
- Route support to **support@appsforgood.net** (Apps for Good Product Studio) via user-initiated email with exported `.txt` — **no auto-upload**, no new `INTERNET` permission.
- Add **iOS scaffold** (`ios/SoundKitCommunity/`) sharing `BLE_PROTOCOL.md` as source of truth; Android remains primary until iOS parity ships.
- Exclude **ECU coding / exhaust sound-mode wiring** from product scope.

### Consequences

- Motorcycle Sound Kit Custom and remote-only kits stay out of the wizard catalog.
- Community can promote Beta → Supported via `COMPATIBILITY.md` and catalog updates after field validation.
- Wear OS tile (`:wear`) opens the phone app rather than duplicating BLE on the watch.
