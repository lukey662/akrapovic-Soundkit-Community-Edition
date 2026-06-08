# Developer Docs

## Project Layout

```text
app/src/main/java/com/akrapovic/soundkit/community/
  ble/          BLE scanning, permissions, GATT, protocol guardrails
  car/          Android Auto Car App Library surface
  data/         repositories, DataStore settings, diagnostics
  diagnostics/  diagnostics report generation, local crash capture, share intents
  domain/       app state and result models
  service/      foreground service, notification, Quick Settings tile
  ui/           Compose screens, theme, ViewModel
```

## Local Development

1. Install Android Studio and JDK 17.
2. Install Android SDK 35.
3. Open the repository in Android Studio.
4. Build with `./gradlew :app:assembleDebug` (uses the committed Gradle wrapper).
5. Install on a physical Android device with BLE support.

## First-run onboarding

Until `onboardingCompletedAt` is set in DataStore, the app shows a single scrollable setup screen (not the main tabs) with a breadcrumb progress strip and four inline sections:

1. **Risk** — short summary plus expandable full disclaimer; checkbox to accept.
2. **Bluetooth** — inline **Grant** when needed (API 31+ uses `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`; older APIs use location).
3. **Notifications** — inline grant on API 33+ when needed.
4. **Battery** — optional **Open battery settings** (same intent as Settings).

**Get started** (bottom bar) finishes setup when risk is accepted and required permissions are granted.

Clearing app data resets onboarding. Existing installs see the full screen again until they tap **Get started**.

## Saved receivers and connect on launch

- Up to **8** receivers stored as JSON in DataStore (`saved_receivers_json`), with legacy `remembered_device_*` migrated on first read.
- **Settings** lists saved receivers, default star, connect-on-launch toggle, and per-receiver remove.
- **Home (scan)** shows saved chips and marks default devices; connecting saves/updates the receiver and sets default when appropriate.
- **`RememberedDeviceConnector`** (domain) decides whether to auto-connect on app launch (phone) or car session entry; separate from post-drop **auto reconnect** in `BleRepository`.

## Drive mode

Entry: **Settings** (full controls) or **Home** shortcut → Drive mode screen.

- Set **preferred valves** (Open or Closed) applied when connected and ready.
- Optional **quiet start** window: after each connect, hold Closed for N minutes, then apply preferred mode.
- **Pause drive mode** from Settings or the foreground notification.
- Drive mode applies only when **connected**, valve state is **known**, and the receiver is **not** in status `0x04`.
- **Auto-reconnect** stops after 8 attempts; use **Try again** on Home if unreachable.

## Notification and Quick Settings

- `BleConnectionService` builds notifications from connection, valve, `receiverStatusMessage`, and default receiver display name.
- Open/Close notification actions are omitted when disconnected, valve state is unknown, or the receiver reports not-ready (status `0x04`).
- Quick Settings tile uses `QsTilePresenter`; opens the app when disconnected; shows `not ready` when status blocks commands.

## UI System

The app uses a consumer-first companion UI built from reusable Compose components in `ui/components`. Screens should prefer calm hierarchy, rounded elevated panels, concise headers, large touch targets, and theme-driven gradients instead of square debug-style cards or protocol-heavy panels. Garage themes are persisted in DataStore and applied app-wide through `SoundKitTheme`; each brand-inspired family has explicit Light and Dark variants plus a local `brand_*.xml` mark that can be replaced with assets the user has rights to use.

## Testing

Use `TESTING.md` as the source of truth for unit, regression, instrumented smoke, CI, and physical receiver validation.

Local `:app:assembleDebug` runs `:app:testDebugUnitTest` first. GitHub Actions verifies `./gradlew --version`, runs unit tests, Android 35 emulator smoke tests (`OnboardingFlowTest`, `AkraStatePanelTest`, `ComposeSmokeTest`), then publishes the debug APK only after both test layers pass.

## Diagnostics Export And Crash Logs

Diagnostics are local-only. `More -> Diagnostics` can copy a full report to the clipboard or share a generated `.txt` attachment through Android's share sheet. Reports include app version, device metadata, BLE diagnostics, and any pending crash log. The app does not request `INTERNET`, does not upload logs automatically, and uses a non-exported `FileProvider` to grant one-time access to files selected by the user.

If the app crashes, `CrashReporter` writes `files/crashes/last_crash.txt` synchronously before delegating to Android's crash handler. On next launch, Diagnostics shows a crash panel with copy/share/dismiss actions.

## Roadmap

Planned features, UX direction, and non-goals are tracked in `ROADMAP.md`.

## Protocol Enablement Workflow

1. Review `APK_ANALYSIS.md` and `BLE_PROTOCOL.md` for the verified original APK evidence.
2. Connect to the physical receiver with this app and complete Android system pairing with the receiver/manual PIN if prompted.
3. Copy the `GATT PROFILE START` / `GATT PROFILE END` diagnostics block from `More -> Diagnostics`.
4. Confirm the physical receiver exposes characteristic `0000fff4-0000-1000-8000-00805f9b34fb` and CCCD `00002902-0000-1000-8000-00805f9b34fb`.
5. Confirm notification status bytes match `BLE_PROTOCOL.md` before running command smoke tests.
6. Test commands while parked with diagnostics enabled.

## Android Auto Testing

The car surface uses the Android for Cars App Library **IoT** category (`androidx.car.app.category.IOT`). It is local-only and delegates all BLE work to the same repository path as the phone UI, including state-gated toggle safety.

When the car session opens, the app starts the BLE foreground service and **auto-connects to the remembered receiver** if disconnected. The car template shows receiver/valve status, a single **Open valves** / **Close valves** toggle when safe, receiver-not-ready (`04`) messaging, and **Open on phone** when no receiver is saved.

The manifest must declare `com.google.android.gms.car.application` pointing at `res/xml/automotive_app_desc.xml` (with `<uses name="template" />`).

### Projected Android Auto (phone → car, sideload)

For **personal / debug** use on a standard head unit (USB or wireless Android Auto):

1. Connect to your receiver once on the phone so it is remembered.
2. **Pixel / modern Android:** Settings → Connected devices → Connection preferences → **Android Auto** (or search Settings). Tap **Version** 10× → **Developer mode**.
3. Enable **Unknown sources** (required for sideloaded Car apps).
4. Plug into the car or use wireless AA; open the AA launcher and launch **Sound Kit**.

Use the full checklist in `TESTING.md` § Projected Android Auto Validation.

**Play Store listing** for projected Android Auto is still a **non-goal** (valve control does not fit Google's published categories). Sideload + developer mode is the supported path for projected testing.

### Fallback without the AA launcher

If Sound Kit never appears in the car launcher, control valves while parked via:

- The **foreground notification** (Open / Close / Disconnect), and
- The **Quick Settings** tile (add from tile editor).

### Desktop Head Unit (DHU)

1. Enable developer mode and **Unknown sources** on the phone (same as above).
2. Enable **Start head unit server** in the Android Auto developer menu.
3. Run the SDK DHU binary, e.g. `~/Library/Android/sdk/extras/google/auto/desktop-head-unit`.
4. Launch **Sound Kit** from the DHU launcher; verify toggle and status match the phone.

### Android Automotive OS

Built-in head units (Polestar, Volvo, etc.) can host IoT Car apps without phone projection. The same `SoundKitCarScreen` template applies.

## Release Notes

The current build uses the verified APK toggle protocol and keeps commands state-gated. It is suitable for parked physical receiver smoke testing before public release.

