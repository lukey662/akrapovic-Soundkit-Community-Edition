# Style Guide

## Kotlin

- Prefer immutable data classes for UI and domain state.
- Use sealed interfaces for state machines and typed results.
- Keep Android framework calls inside platform-facing classes.
- Use coroutines and `StateFlow` for asynchronous state.
- Keep BLE operations serialized through a mutex or explicit operation queue.
- Model valve commands with `CommandResult` and `CommandPhase`; presentation surfaces call `ValveCommandCoordinator`, not BLE writes.
- Treat a matching known valve state as successful no-op. Disconnected, unknown, and receiver-not-ready requests fail closed before any BLE write.

## Compose

- Use Material 3 components.
- Keep touch targets large and labels explicit.
- Use semantic descriptions for critical controls.
- Meet WCAG 2.1 AA interaction expectations: 48dp Android targets (or platform-equivalent iOS targets), explicit selected/state semantics for tabs and switches, and polite live announcements for valve, connection, and recoverable-error changes. Decorative valve artwork is hidden from assistive technology when adjacent text provides the state.
- Represent loading, empty, error, and disabled states directly in UI.
- Haptics communicate a completed valve command or a command failure only; respect system reduced-motion settings and never make animation the only state indicator.
- Keep vehicle-use UI simple and low distraction.
- Car App screens use typed presenter models and low-distraction templates; setup, permissions, and receiver selection always redirect to the phone.
- Car templates show a loading state while connecting, a status message when controls are unavailable, and discrete Open/Close actions only when the receiver is ready. Never build an idle grid with no items. Make current-state and in-flight actions inert.

## BLE

- Log scan, connect, service discovery, notification, and write events at debug level.
- Never write unknown bytes.
- Treat the Sound Kit `01` payload as a toggle only. Do not expose it directly as separate open/close commands.
- Require a known notification-derived valve state before sending a toggle command.
- Update `BLE_PROTOCOL.md` and tests with every protocol change.
- Isolate deprecated Android BLE calls behind SDK checks with comments explaining why they are unavoidable.
- iOS voice and car surfaces must invoke `ValveControlCoordinator`, never `BLEManager` writes. A spoken or CarPlay success state requires receiver-notification confirmation, not a GATT write acknowledgement.
- Android shortcut or future Assistant fulfillment must accept a closed action enum only, resolve only the saved default receiver, and invoke `VoiceValveActionRouter` rather than a BLE service or address-bearing intent.
- Keep CarPlay templates shallow and event-driven; setup, diagnostics, permissions, and device selection belong on the phone.

## Security

- Do not add internet access unless a future ADR explains why.
- Diagnostics and crash exports must remain user-initiated through Android share/copy flows; do not auto-upload logs.
- Shareable files must use a non-exported `FileProvider` with narrow cache/files paths.
- Persist only minimal local settings.
- Prefer fail-closed behavior for protocol mismatches, missing permissions, unknown receiver services, unknown valve state, and receiver error status.
- Keep release credentials and keystores out of source control. Release signing
  reads only environment-provided secrets and must fail closed when incomplete.
- Production Android car surfaces use a project-owned signed-host allowlist;
  `ALLOW_ALL` is debug-only. Treat host certificate rotations as a security
  update.
- Do not declare unavailable platform entitlements or scenes in normal
  distribution builds. Privacy manifests must reflect actual collection and
  required-reason API use.

## Documentation

- Update `DECISIONS.md` for architecture or security decisions.
- Update `SPEC.md` when behavior changes.
- Update `ROADMAP.md` when planned features, UX direction, or non-goals change.
- Update `BLE_PROTOCOL.md` when protocol evidence changes.
- Update `README.md` for build, install, or user-facing workflow changes.

## Visual language (Car Sound Kit Control)

- **Obsidian system**: true black (dark) or cool white (light), flat surfaces, white or near-black type, one brand accent. The exhaust-tip valve is the only rich, textured element.
- **No gradients or shadows on chrome.** Screens use solid `colorScheme.background`; `AkraSurface` / `AkraListGroup` use solid `surface` plus a 1dp `outlineVariant` hairline (white 7% on dark, black 8% on light). `AkraElevated` is one tonal step (`surfaceVariant`), never a drop shadow.
- **Shapes**: cards and groups 16dp, buttons 13dp (46dp minimum height), segments 10dp, status pills 6dp.
- **Color roles**: `primary` = theme `button` (filled actions), `onPrimary` = `onButton`, `secondary` = theme `accent` (text, icons, links, toggles), `tertiary` = `glow` (valve heat). Never use `primary` for text.
- **Type scale** (`ui/theme/Type.kt`): displaySmall 24sp for the valve state, titleLarge 17, titleMedium 15, bodyLarge 15, bodyMedium 13, bodySmall 12, labelLarge 13, labelSmall 11. Nothing smaller than 11sp.
- **Brand palettes** (`ui/theme/GarageThemes.kt`), dark accent / glow / button:
  - Studio: `#D6BD73` / `#D6BD73` / `#D6BD73` (black label)
  - Akrapovič-inspired: `#E1173D` / `#E1173D` / `#E1173D`, carbon base `#0B0B0C`, heat-tinted titanium lip
  - Audi RS: progressive red `#F50537` / `#F50537` / `#E0042F` (one step deeper so white labels reach 4.5:1)
  - BMW M: `#00AEEF` / `#00AEEF` / `#0033A0`, M stripes `#00AEEF #0033A0 #E4002B`
  - Mercedes-AMG: silver `#C8CCCE` / Petronas teal `#00A19B` / `#C8CCCE` (black label)
  - Porsche: crest gold `#C69632` / Porsche red `#B12B28` / `#B12B28`
  - Volkswagen R: `#4C86E8` / `#4C86E8` / Lapiz `#2A5DB8`
  - Lamborghini: gold `#FFC000` / `#FFC000` / `#FFC000` (black label)
  - Light variants use a darker accent that reaches 4.5:1 on white. `GarageThemeFamiliesTest` enforces contrast for every preset.
- **Contrast rules**: text and muted text 4.5:1 on their surface, button labels 4.5:1 on the button, accents 3:1 on dark backgrounds and 4.5:1 on light.
- Prefer **`AkraListGroup` / `AkraListRow`** (with `leadingIcon` where useful) for Settings, More, and Home rows — not a full card per row.
- **`AkraHeroHeader`**: compact title + one subtitle on secondary screens. Home uses an app-title row, brand stripes, and a receiver chip instead of a page hero.
- **Status**: receiver chip or slim **`AkraBanner`** for errors and reconnects. Command failures use `CommandFailureCopy` titles; an unconfirmed toggle reads as "checking", never as an error.
- Primary screens use plain customer language: connect, open valves, close valves, drive mode, quiet start, activity.
- Keep UUIDs, hashes, GATT details, raw logs, and reverse-engineering language inside Diagnostics or documentation.
- Garage themes are brand-inspired families with light and dark variants. Themes change accent, glow, and button colors — layout stays the same. Brand marks are local `brand_*.xml` drawables; never ship a manufacturer or Akrapovič logo.
- **Avoid**: gradients on screens or cards, drop shadows, off-white or tinted text, hard-square CTAs, full-pill primary buttons, neon dashboard chrome, large display type for anything but the valve state.
- **Valve art** (`ui/components/ValveArt.kt`) is shared by Home (`ValveVisual`, 200dp), Android Auto tiles and the widget (`ValveBitmaps`). Layers: carbon twill sleeve, bevel, brushed titanium lip (heat-tinted for the Akrapovič-inspired theme), deep bore, butterfly disc on a steel shaft with boss and end bolts, and, when open, the perforated baffle and an ember glow in the theme's `glow`. It animates only for unknown or busy states and is static with reduced motion. Preview all states via **More → Advanced → Developer → Valve visual states**.
- Launcher artwork uses the photoreal exhaust-tip mark (`mipmap-*/ic_launcher_foreground.png` + dark adaptive background). Monochrome remains a vector silhouette. iOS ships the matching 1024px `AppIcon`.

