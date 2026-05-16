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

- Prefer a premium companion-app style in `ui/components`: calm hierarchy, rounded elevated panels, restrained accent color, concise copy, and large touch targets.
- Primary screens should use plain customer language: find receiver, connect, open valves, close valves, settings, diagnostics.
- Keep UUIDs, hashes, GATT details, raw logs, and reverse-engineering language inside Diagnostics or documentation.
- Garage themes are organized as brand-inspired families with explicit Light and Dark variants. They may change gradients, accent, brand mark, and background color, but screens should keep the same layout, hierarchy, and local-only privacy language.
- Brand marks are loaded from local `brand_*.xml` drawables. Replace them with your own SVGs locally; only ship marks you have rights to use.
- Prefer theme color roles (`MaterialTheme.colorScheme.onSurface`, `onSurfaceVariant`, etc.) over fixed white/gray text so Studio Light remains readable.
- Use gradients for depth on screen backgrounds, cards, and primary actions. Keep them subtle and functional, not decorative noise.
- Avoid square debug-style cards, dense uppercase copy, decorative borders, and telemetry panels that do not help the user complete the current task.
- Launcher artwork should use the same dark carbon / titanium / amber vocabulary and remain vector-based.

