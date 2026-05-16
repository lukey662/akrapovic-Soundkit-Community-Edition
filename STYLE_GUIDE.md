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
- Update `BLE_PROTOCOL.md` and tests with every protocol change.
- Isolate deprecated Android BLE calls behind SDK checks with comments explaining why they are unavoidable.

## Security

- Do not add internet access unless a future ADR explains why.
- Persist only minimal local settings.
- Prefer fail-closed behavior for protocol mismatches, missing permissions, and unknown receiver services.

## Documentation

- Update `DECISIONS.md` for architecture or security decisions.
- Update `SPEC.md` when behavior changes.
- Update `BLE_PROTOCOL.md` when protocol evidence changes.
- Update `README.md` for build, install, or user-facing workflow changes.

