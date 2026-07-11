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
- Car templates show only status and discrete Open/Close actions. Hide controls for unknown or not-ready state; make current-state and in-flight actions inert.

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

## Visual language (Sound Kit Community)

- **Calm shell, loud moment**: flat backgrounds and list rows by default; reserve emphasis for Home valve state and primary CTAs.
- Use **`AkraSurface`** for grouped content (16dp radius, no shadow, optional hairline). Use **`AkraElevated`** only for modals, empty states, and blocking panels.
- Prefer **`AkraListGroup` / `AkraListRow`** for Settings, More, and secondary actions — not a full card per row.
- **`AkraHeroHeader`**: compact title + one subtitle on secondary screens; Home uses inline status + valve hero instead of a page hero.
- **Status**: inline dot + text (`AkraInlineStatus`) or slim **`AkraBanner`** for errors/reconnect — not stacked cards with pills.
- **`AkraStatusPill`**: warnings and onboarding badges only, not connection/valve state.
- Primary screens use plain customer language: scan nearby, connect, open valves, close valves, drive mode, settings, diagnostics.
- Keep UUIDs, hashes, GATT details, raw logs, and reverse-engineering language inside Diagnostics or documentation.
- Garage themes are brand-inspired families with Light/Dark variants. Themes change accent, background, and preview strip colors — layout stays the same.
- Brand marks load from local `brand_*.xml` drawables. Only ship marks you have rights to use.
- Prefer theme color roles (`MaterialTheme.colorScheme.onSurface`, `onSurfaceVariant`, etc.) over fixed white/gray text.
- **Avoid**: screen + card double gradients, 10dp shadows on every block, uppercase eyebrow labels on every screen, decorative pill bottom nav.
- Optional accent gradients belong on Home hero glow or theme preview strips — not default card treatment.
- **`ValveVisual`** is the Home exhaust-tip hero: carbon sleeve, titanium lip, dark bore, hinged disc on a horizontal axis (face-on when closed, edge-on when open), amber heat glow when open. It may animate only for unknown/busy state and is static when reduced motion is enabled. Uses [`ExhaustTipPalette`](app/src/main/java/com/akrapovic/soundkit/community/ui/components/ExhaustTipPalette.kt). Layout: `fillMaxWidth()` × ~168dp on Home. Preview all states via **More → Advanced → Developer → Valve visual states**.
- Launcher artwork uses the photoreal exhaust-tip mark (`mipmap-*/ic_launcher_foreground.png` + dark adaptive background). Monochrome remains a vector silhouette. iOS ships the matching 1024px `AppIcon`.

