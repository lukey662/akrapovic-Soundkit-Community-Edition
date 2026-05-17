# Roadmap

Living plan for **Sound Kit Community**. Nothing here is a commitment or timeline; it records direction so contributors and users know what might come next. All work stays **local-first** (phone BLE only) unless an explicit decision says otherwise.

*Last refreshed: May 2026 — unified onboarding, empty/error states, CI wrapper, theme polish, and accessibility pass shipped.*

## Principles

- **Safety first** — No silent automation that could surprise a driver. Clear manual override and visible “why did the valve change?” affordances where automation exists.
- **State-gated writes** — Manual OPEN/CLOSE only sends the verified toggle when receiver state is known and differs from the target. Future automation must follow the same rule.
- **Privacy** — Geofencing and location-backed rules need **opt-in**, plain-language disclosure, and minimal retention. No selling or sharing location data (there is no backend today).
- **Accessibility** — Target **WCAG 2.1 AA** for contrast, semantics, touch targets (≥48dp), and screen reader labels as screens are touched.

## Done

| Area | What shipped |
|------|----------------|
| **BLE protocol** | APK-verified toggle (`0x01`), advertising signature scan, notification-driven valve state, state-gated OPEN/CLOSE, pairing flow. |
| **BLE stability (status `04`)** | Idle `0x04` stays connected with “receiver not ready” copy; no auto-reconnect storm; honest reconnect attempt counter. |
| **Physical receiver** | Real-car validation: connect, pair, open/close accepted by receiver. |
| **Unified Home** | Single primary tab: scan when disconnected, valve control when connected (no separate Find / Control tabs). |
| **Valve control card** | Combined valve visual + one Open/Close action button on the control surface. |
| **Consumer UI** | Calm Home / More / Settings / Diagnostics; protocol hidden from primary journey. |
| **Themes** | Brand-inspired families with Light/Dark variants, gradients, local brand marks; default **Studio Blue** (navy + electric blue). |
| **Onboarding & legal** | Unified first-run flow: risk, Bluetooth, notifications, battery (skippable); `onboardingCompletedAt` in DataStore. |
| **Empty / error states** | Shared `AkraStatePanel` on scan, control reconnect, diagnostics; retry connection from Home. |
| **Theme polish** | Stronger Studio/Audi light contrast; larger labeled color swatches on Appearance. |
| **CI reliability** | Committed `gradle-wrapper.jar`; GitHub Actions runs `./gradlew`. |
| **Accessibility pass** | Headings, content descriptions on onboarding and primary actions; smoke/instrumented coverage. |
| **Diagnostics export** | Copy, Save-to-file (SAF), file-only Share (no email subject/body autofill). |
| **Launcher** | Adaptive valve-glyph icon (foreground, background, monochrome). |
| **Android Auto / Automotive** | IoT Car App service, manifest descriptor, DHU testing. |
| **Projected AA (sideload)** | Car auto-reconnect, toggle + status-04 on template, Open on phone, notification/QS fallback docs. |
| **Confirmations** | Disconnect and Forget receiver confirmation dialogs. |

## Near term

| Area | Intent |
|------|--------|
| **Maintenance** | Device feedback on themes, BLE edge cases, projected AA validation on more head units, Play policy as Android versions shift. |

## Later (product)

### Favorites and saved devices

- Pin or star receivers, **nicknames**, sort order.
- Optional **default device** and “connect on launch” (with clear disconnect and permission boundaries).

### Rules engine

- Persistent **rules**: when [triggers] then [actions] (e.g. open / close valve), with **precedence** and conflict resolution.
- Storage may evolve from DataStore to **Room** if rule complexity grows.

### Time-based automation

- **Schedules** — time windows, quiet hours vs “sport” hours, timezone-safe recurrence.
- **WorkManager** or **AlarmManager** with Doze / exact-alarm policy; execution tied to **connected BLE** and a user-visible last-run log.

### Geofencing automation

- **Geofence enter/exit** triggers (Android Geofencing API).
- Opt-in location (and possibly background location — high Play scrutiny); battery impact called out in UI.

### Notifications and Quick Settings

- Deep links to **pause all rules**, **last automation cause**, quick manual OPEN/CLOSE when connected.

## Non-goals (for now)

- **Cloud** accounts, remote control over the internet, or telemetry backends.
- **Official Akrapovič integration** or warranty support.
- **Play Store listing in projected Android Auto.** Policy categories (navigation, media, messaging, etc.) do not fit a valve controller. **Sideload + developer mode** for projected AA and **IoT** for Automotive OS / DHU remain in scope — see `DOCS.md` § Android Auto Testing.

## Dependency sketch

```mermaid
flowchart LR
  subgraph done [Shipped]
    Proto[Verified_BLE]
    Status04[Status_04_handling]
    Home[Unified_Home]
    UX[Consumer_UI_and_themes]
    Onboard[Unified_onboarding]
    Car[Automotive_IoT_surface]
  end
  subgraph later [Product]
    Fav[Favorites]
    Rules[Rules_engine]
    Time[Time_triggers]
    Geo[Geofence_triggers]
  end
  Proto --> Fav
  Fav --> Rules
  Rules --> Time
  Rules --> Geo
  UX --> Onboard
```

## Contributing

Open issues or PRs with a short **safety/privacy** note for any feature that moves metal (valve) or uses location in the background.
