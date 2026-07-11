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

ios/SoundKitCommunity/
  BLE/          CoreBluetooth manager (scan, GATT, reconnect)
  Data/         SettingsStore, DiagnosticsStore
  Domain/       DriveModeEngine, QuietWindowEvaluator
  Views/        SwiftUI screens (Home, onboarding, drive mode, diagnostics)
```

## Local Development

1. Install Android Studio and JDK 17.
2. Install Android SDK 35.
3. Open the repository in Android Studio.
4. Build with `./gradlew :app:assembleDebug` (uses the committed Gradle wrapper).
5. Install on a physical Android device with BLE support.

### Android packages, build variants, and diagnostics

- The release application ID is `com.akrapovic.soundkit.community`; the debug build adds `.debug` and `-debug`. There are no product flavors.
- Before handing off an APK, run `./gradlew :app:testDebugUnitTest :app:assembleDebug`; run `:app:verifyPaparazziDebug` when the task is present and UI snapshots changed.
- The Android foreground service owns connection lifetime. Every command surface—including the Compose UI, notification, Quick Settings, widget, drive mode, and Car App—uses `ValveCommandCoordinator`; only `BleConnectionManager` performs GATT discovery, subscription, and transport.
- **More → Advanced → Diagnostics** records package/build identifiers, Car App readiness, local BLE events, and the GATT profile. Export remains user-initiated and file-only; do not add telemetry or upload paths.

## iOS development

**Owners:** iOS is not available for public install yet — see `INSTALL.md`.

**Developers:**

1. Install Xcode 15+ on macOS.
2. Open `ios/SoundKitCommunity.xcodeproj`.
3. Set your **Team** under Signing & Capabilities for device runs.
4. Build and run on a physical iPhone (BLE requires hardware).

Regenerate the project after editing `ios/project.yml`:

```bash
cd ios && xcodegen generate
```

Unit tests (Simulator, unsigned):

```bash
cd ios
xcodebuild -project SoundKitCommunity.xcodeproj -scheme SoundKitCommunity \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  CODE_SIGNING_ALLOWED=NO test
```

Physical receiver validation: `TESTING.md` § iOS device smoke. Full iOS layout and dev install: `ios/SoundKitCommunity/README.md`.

### CarPlay entitlement and Siri

CarPlay is **not available or marketed** until Apple approves the driving-task entitlement. Before requesting distribution, request `com.apple.developer.carplay-driving-task`; valve control has a high approval risk because it can affect noise, emissions, and safe driving. The committed entitlement file deliberately keeps that key commented under `CARPLAY_ENABLED`. After written approval, add the key and use a provisioning profile that contains it—never bypass entitlement signing.

If Apple rejects the request, ship Siri App Intents and the phone UI only. There is no entitlement workaround and the CarPlay scene must remain unavailable.

Once approved and correctly provisioned, the CarPlay scene is a shallow `CPGridTemplate`: Open, Close, and a non-interactive status. It shares the app's `ValveControlCoordinator`, updates only on BLE/coordinator events, and directs setup, permissions, diagnostics, and receiver selection to the phone.

Siri shortcuts are independent of CarPlay approval:

- “Open valves in Sound Kit Community”
- “Close valves in Sound Kit Community”
- “Get valve status in Sound Kit Community”

They run without opening the app where iOS permits it, but never start an unsafe blind command: a result is spoken as successful only after the notification-confirmed coordinator result. If Bluetooth state cannot restore or become ready in time, Siri tells the user to unlock the phone and open the app.

## README screenshots

Marketing screenshots for `docs/screenshots/` and the README are recorded with [Paparazzi](https://github.com/cashapp/paparazzi) — JVM tests, no emulator or phone:

```bash
./scripts/capture-docs-screenshots.sh
```

This runs `DocsScreenshotPaparazziTest`, writes goldens under `app/src/test/snapshots/images/`, and copies friendly filenames into `docs/screenshots/`. Uses full `DeviceConfig.PIXEL_6` pixel dimensions — do not shrink `screenWidth`/`screenHeight` or buttons and labels clip. CI can gate regressions with `./gradlew :app:verifyPaparazziDebug`.

**Note:** Compose UI tests on a physical device require Espresso; Android 16 (API 36) currently breaks Espresso's `InputManager` shim, so prefer Paparazzi for doc captures on bleeding-edge phones.

## First-run onboarding

Until `onboardingCompletedAt` is set in DataStore, the app shows a single scrollable setup screen (not the main tabs) with a breadcrumb progress strip and four inline sections:

1. **Risk** — short summary plus expandable full disclaimer; checkbox to accept.
2. **Bluetooth** — inline **Grant** when needed (API 31+ uses `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`; older APIs use location).
3. **Notifications** — inline grant on API 33+ when needed.
4. **Battery** — optional **Open battery settings** (same intent as Settings).

**Get started** (bottom bar) finishes setup when risk is accepted and required permissions are granted.

Clearing app data resets onboarding. Existing installs see the full screen again until they tap **Get started**.

## Saved receivers and automatic connection

- Up to **8** receivers stored as JSON in DataStore (`saved_receivers_json`), with legacy `remembered_device_*` migrated on first read.
- **Settings** lists saved receivers, default star, independent **Connect on launch** and **Connect in car** toggles, and per-receiver remove.
- **Home (scan)** shows saved chips and marks default devices; connecting saves/updates the receiver and sets default when appropriate.
- **`RememberedDeviceConnector`** (domain) keeps phone launch (`connectOnLaunch`) and car session entry (`connectInCar`) policies separate from post-drop **auto reconnect** in `BleRepository`.

## Drive mode

Entry: **Settings** (full controls) or **Home** shortcut → Drive mode screen.

- Set **preferred valves** (Open or Closed) applied when connected and ready.
- Optional **quiet neighbours** window (editable **Start** / **End** times in Settings; default 06:00–09:00): after each connect during the window, hold Closed for N minutes (default **3**), then apply preferred mode. Set e.g. **03:00–09:00** for early starts. If end is earlier than start (e.g. **22:00–06:00**), the window runs overnight into the next morning. Manual open/close from Home, notification, or Android Auto wins for that connect session.
- **Pause drive mode** from Settings or the foreground notification.
- Drive mode applies only when **connected**, valve state is **known**, and the receiver is **not** in status `0x04`.
- **Auto-reconnect** stops after 8 attempts (~90s backoff); honors the Settings toggle for initial failures too; use **Try again** on Home if unreachable.

## Notification and Quick Settings

- `BleConnectionService` builds notifications from connection, valve, `receiverStatusMessage`, and default receiver display name.
- Open/Close notification actions are omitted when disconnected, valve state is unknown, or the receiver reports not-ready (status `0x04`).
- Quick Settings tile uses `QsTilePresenter`; opens the app when disconnected; shows `not ready` when status blocks commands.

## UI System

The app uses a consumer-first companion UI built from reusable Compose components in `ui/components`. Screens should prefer calm hierarchy, rounded elevated panels, concise headers, large touch targets, and theme-driven gradients instead of square debug-style cards or protocol-heavy panels. Garage themes are persisted in DataStore and applied app-wide through `SoundKitTheme`; each brand-inspired family has explicit Light and Dark variants plus a local `brand_*.xml` mark that can be replaced with assets the user has rights to use.

**More tab:** **Settings** and **Appearance** are the primary destinations. **Advanced** opens a hub for Diagnostics, Android Auto setup, Roadmap, and Developer (detailed logs).

**First launch:** After the risk disclaimer, onboarding includes a **vehicle picker** (Supported vs Beta tiers). Selection is stored in DataStore and included in diagnostics exports.

**Support:** Diagnostics includes **Copy email** and **Email support** for **support@appsforgood.net** — user attaches exported `.txt` manually; no auto-upload.

### Valve hero rendering

`ValveVisual` (the Home hero) is a **minimal ring-and-disc** animation: carbon rim stroke, titanium lip, dark bore, flat disc at 80% fill when closed; disc clears and lip brightens when open. No glow or air effects. Prototype: `design/valve-simple-animations.html` (Option 5). Optional Blender experiments live under `design/blender/` but are not used in-app.

## Testing

Use `TESTING.md` as the source of truth for unit, regression, instrumented smoke, CI, and physical receiver validation.

Local `:app:assembleDebug` runs `:app:testDebugUnitTest` first (JVM tests + Paparazzi README screenshot verify). GitHub Actions runs the same path and publishes the debug APK artifact. Instrumented smoke tests (`connectedDebugAndroidTest`) are local-only — run on a phone or emulator when you change manifest, notifications, or Compose navigation.

## Diagnostics Export And Crash Logs

Diagnostics are local-only. **More → Advanced → Diagnostics** can copy a full report to the clipboard or share a generated `.txt` attachment through Android's share sheet. Reports include app version, device metadata, BLE diagnostics, and any pending crash log. The app does not request `INTERNET`, does not upload logs automatically, and uses a non-exported `FileProvider` to grant one-time access to files selected by the user.

If the app crashes, `CrashReporter` writes `files/crashes/last_crash.txt` synchronously before delegating to Android's crash handler. On next launch, Diagnostics shows a crash panel with copy/share/dismiss actions.

## Roadmap

Planned features, UX direction, and non-goals are tracked in `ROADMAP.md`.

## Protocol Enablement Workflow

1. Review `APK_ANALYSIS.md` and `BLE_PROTOCOL.md` for the verified original APK evidence.
2. Connect to the physical receiver with this app and complete Android system pairing with the receiver/manual PIN if prompted.
3. Copy the `GATT PROFILE START` / `GATT PROFILE END` diagnostics block from **More → Advanced → Diagnostics**.
4. Confirm the physical receiver exposes characteristic `0000fff4-0000-1000-8000-00805f9b34fb` and CCCD `00002902-0000-1000-8000-00805f9b34fb`.
5. Confirm notification status bytes match `BLE_PROTOCOL.md` before running command smoke tests.
6. Test commands while parked with diagnostics enabled.

## Android Auto Testing

The car surface uses the Android for Cars App Library **IoT** category (`androidx.car.app.category.IOT`). It is local-only and delegates all BLE work to the same repository path as the phone UI, including state-gated toggle safety.

When the car session opens, the app starts the BLE foreground service and auto-connects to the remembered receiver only when **Connect in car** is enabled. The `GridTemplate` provides separate **Open** and **Close** actions when the receiver is connected and status is known; the current-state action is inert and both are inert while a command runs. Unknown and not-ready receiver states hide actions. Onboarding, BLE permission, and receiver setup remain phone-only and show **Finish setup on phone** on the car display.

Diagnostics exports record Car App registration, package suffix, manifest Car API level, Bluetooth permission readiness, saved receiver, `connectInCar`, and current car-session status.

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

### Distribution and approval boundaries

- Android Auto's IoT surface supports DHU, Automotive OS, and sideloaded projected testing with developer settings. A projected Google Play listing is not planned; do not represent it as supported distribution.
- iOS Siri App Intents can ship independently after physical BLE validation. They fail safely if a locked/background phone cannot restore a ready BLE connection in time.
- CarPlay is entitlement-gated and not approved. Do not enable, advertise, or submit its scene until Apple approves the driving-task entitlement and the provisioning profile includes it. Approval still does not guarantee App Store acceptance for safety-, noise-, or emissions-sensitive valve control.

## Release Notes

The current build uses the verified APK toggle protocol and keeps commands state-gated. It is suitable for parked physical receiver smoke testing before public release.

