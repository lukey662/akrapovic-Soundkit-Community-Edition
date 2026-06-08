# Style Guide

## Kotlin

- Prefer immutable data classes for UI and domain state.
- Use sealed interfaces for state machines and typed results.
- Keep Android framework calls inside platform-facing classes.
- Use coroutines and `StateFlow` for asynchronous state.
- Keep BLE operations serialized through a mutex or explicit operation queue.

## Compose

- Use Material 3 components.
- Keep touch targets large and labels explicit.
- Use semantic descriptions for critical controls.
- Represent loading, empty, error, and disabled states directly in UI.
- Keep vehicle-use UI simple and low distraction.

## BLE

- Log scan, connect, service discovery, notification, and write events at debug level.
- Never write unknown bytes.
- Treat the Sound Kit `01` payload as a toggle only. Do not expose it directly as separate open/close commands.
- Require a known notification-derived valve state before sending a toggle command.
- Update `BLE_PROTOCOL.md` and tests with every protocol change.
- Isolate deprecated Android BLE calls behind SDK checks with comments explaining why they are unavoidable.

## Security

- Do not add internet access unless a future ADR explains why.
- Diagnostics and crash exports must remain user-initiated through Android share/copy flows; do not auto-upload logs.
- Shareable files must use a non-exported `FileProvider` with narrow cache/files paths.
- Persist only minimal local settings.
- Prefer fail-closed behavior for protocol mismatches, missing permissions, unknown receiver services, unknown valve state, and receiver error status.

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
- **`ValveVisual`** uses layered vector drawables (`valve_ring`, `valve_blade_*`, `valve_core_*`) shared with the launcher icon geometry. Spring motion for open/close; glow and flow lines when open; respect reduced motion.
- Launcher artwork should use the same dark carbon / titanium / amber vocabulary and remain vector-based.

