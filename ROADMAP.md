# Roadmap

Living direction for **Sound Kit Community**. It distinguishes code present in this repository from releases and hardware/platform access that have actually been validated. Nothing here commits to a timeline. The product remains local-first: phone-to-receiver BLE, no accounts, cloud control, or telemetry.

*Last verified: 2026-07-11.*

## Reading this roadmap

- **Shipped in code** means the implementation and its automated coverage are in the repository. It does not imply store distribution, physical-device validation, or a third-party platform approval.
- **Validated release gates** records evidence needed before a feature can be represented as ready for its intended audience.
- **Manual/external gates** cannot be passed by CI and remain blocked until their listed evidence or approval exists.

## Principles

- **Safety first** — Every valve write is state-gated, serialized, and confirmed by a receiver status notification. Manual control wins for the current connection session.
- **Honest capability claims** — A scaffold, simulator build, or sideload route is not described as public platform availability.
- **Privacy** — No internet permission, accounts, telemetry, location automation, or background geofencing.
- **Accessible, low-distraction UI** — Preserve explicit state text, large targets, reduced-motion behavior, and phone-only setup for car surfaces.

## Shipped in code

| Area | Current implementation |
|---|---|
| **BLE safety and stability** | APK-verified toggle (`0x01`), advertising signature scan, pairing, notification-derived state, state-gated OPEN/CLOSE, five-second confirmation, status `04` connected-not-ready handling, and an eight-attempt reconnect cap. |
| **Central command authority** | `ValveCommandCoordinator` serializes phone, notification, Quick Settings, widget, drive-mode, Android Auto, and shortcut commands; disconnected, unknown, and not-ready requests fail closed. |
| **Core Android experience** | Unified Home, consumer-focused More/Settings/Diagnostics, onboarding/risk acknowledgement, recovery states, themes, vehicle tiers, RSSI hints, receiver-not-ready checklist, and local diagnostics export. |
| **Exhaust-tip visual and launcher mark** | Procedural carbon/titanium exhaust-tip `ValveVisual`, state gallery, visual regression coverage, Android adaptive icon assets, and matching iOS app icon asset. |
| **Saved receivers and connection policies** | Up to eight receivers, nickname/default/forget, local backup import/export, independent `connectOnLaunch` and `connectInCar`, head-unit priority, contention yield, and Take control. |
| **Drive mode** | Preferred Open/Closed on first connect-ready, quiet-neighbours window with editable/overnight times, profiles, notification pause/resume, and manual override per session. It replaced the former rules, schedules, and geofencing experiment. |
| **Android companion surfaces** | Notification and Quick Settings safety gating, widget/launcher shortcuts, Wear handoff tile, and an IoT Car App `GridTemplate` with separate Open/Close actions. |
| **Android shortcuts, not custom Assistant App Actions** | Internal Open, Close, and Status launcher shortcuts use a closed action enum, saved default receiver, eight-second reconnect bound, and `ValveCommandCoordinator`. No custom Google Assistant App Action is declared or claimed. |
| **iOS dev v1 parity** | Committed SwiftUI/CoreBluetooth project with GATT lifecycle, state-confirmed commands, onboarding, receiver/settings management, drive mode, diagnostics, backups, theme, status `04` help, and XCTest/CI coverage. |
| **iOS Siri** | Open, Close, and status App Intents route through `ValveControlCoordinator`; success is spoken only after notification confirmation. Siri does not depend on CarPlay approval. |
| **CarPlay scaffold** | A shallow, coordinator-routed `CPGridTemplate` and car-session hook exist behind `CARPLAY_ENABLED=NO`. Normal builds declare neither the scene nor driving-task entitlement. |

## Validated release gates

| Surface | Evidence | Current state |
|---|---|---|
| **Android automated build** | JVM tests, Paparazzi, debug phone/Wear builds, and iOS Simulator XCTest are recorded in `TESTING.md`. | Automated gates passed for the documented 2026-07-11 evidence. |
| **Android receiver behavior** | Parked real-car connection, pairing, and Open/Close acceptance have been recorded. | Validated for the reference hardware exercise; repeat the physical smoke checklist for every public release candidate. |
| **Android public release** | Automated matrix, instrumented smoke, parked receiver smoke, reviewed diagnostics, and release signing. | Release-candidate gate; do not infer it from a debug APK or CI artifact. |
| **iOS developer builds** | Xcode device install and Simulator XCTest path are documented. | Developer-only route; Simulator does not validate CoreBluetooth, Siri readiness, or background behavior. |

## Manual and external gates

| Area | Gate and truthful status |
|---|---|
| **iOS hardware smoke** | **Blocked pending hardware smoke.** Run the parked RS3/iPhone checklist in `TESTING.md`, including discovery, pairing, status `04`, commands, reconnect, and drive mode. |
| **TestFlight / App Store** | **Blocked pending iOS hardware smoke and Apple developer/distribution access.** No TestFlight or public iOS distribution is claimed. |
| **Siri release** | Can ship independently of CarPlay once its physical iPhone/locked-phone smoke is passed and the normal iOS distribution gate is available. |
| **CarPlay** | **Entitlement-gated.** Apple must approve `com.apple.developer.carplay-driving-task`, and a provisioning profile must carry it. Approval and hardware validation are required before enabling, marketing, or submitting the scene. |
| **DHU / real Android Auto** | **Manual gate.** The IoT template has automated presenter coverage, but DHU and a compatible real Automotive OS/projected sideload session must be exercised locally while parked. |
| **Compatibility matrix** | Promote Tier 2/Beta vehicles only after parked field validation of the matching receiver protocol. |

## Next work

Maintenance follows owner feedback and gate evidence rather than a fixed timeline:

1. Complete and record iOS physical receiver, Siri, DHU, and real-head-unit smoke results.
2. Obtain the appropriate Apple developer access before considering TestFlight; pursue CarPlay entitlement only if its review risk is acceptable.
3. Expand `COMPATIBILITY.md` only with field-validated receiver/vehicle evidence.

## Non-goals

- Cloud accounts, remote valve control, telemetry backends, or location/geofence automation.
- Returning to the retired rules, schedule, WorkManager, or geofencing automation system.
- Claiming custom Google Assistant App Actions before a supported, verified fulfillment contract exists.
- Claiming CarPlay availability before entitlement approval and provisioning.
- Google Play distribution for projected Android Auto. Sideload + developer mode and Automotive OS/DHU testing remain manual routes.
- Official Akrapovič integration, warranty support, ECU coding, or factory sound-mode wiring.

## Contributing

For any change affecting valve movement, persistence, background behavior, or a platform declaration, include the relevant safety boundary, automated test, and manual/external gate update in `DECISIONS.md`, `SPEC.md`, `TESTING.md`, and this roadmap.
