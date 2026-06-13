# Roadmap

Living plan for **Sound Kit Community**. Nothing here is a commitment or timeline; it records direction so contributors and users know what might come next. All work stays **local-first** (phone BLE only) unless an explicit decision says otherwise.

*Last refreshed: June 2026 — Vehicle onboarding tiers, diagnostics support email, widget/shortcuts, settings backup, iOS scaffold, Wear tile.*

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
| **Favorites** | Up to 8 saved receivers, nicknames, default star, connect on launch; shared `RememberedDeviceConnector`. |
| **Smarter notifications** | `NotificationCopy` gating; nickname title; not-ready copy; valve actions disabled when unsafe. |
| **Quick Settings polish** | `QsTilePresenter`; not-ready inactive state; open app when disconnected. |
| **Rules engine (design)** | Domain models, `RuleEvaluator`, ADR/SPEC — no BLE execution or persistence yet. |
| **Rules execution (Beta)** | Persisted rules, `RuleExecutionEngine`, activity log, state-gated BLE writes. |
| **Time automation (Beta)** | Schedule triggers via WorkManager + evaluate on connect when ready. |
| **Geofencing (Beta)** | Up to 4 zones, opt-in location, enter/exit triggers via Geofencing API. |
| **Automation notifications (Beta)** | Pause/resume automation + last-run cause in foreground notification. |
| **Drive mode** | Preferred Open/Closed on connect; optional quiet-start window (hold closed N min); Settings + Home shortcut; replaces Beta rules/geofencing. |
| **Reconnect cap** | Auto-reconnect stops after 8 attempts (~2 min); honest “Couldn't reach receiver — tap to retry” UI. |
| **Quiet neighbours hours** | Editable start/end times, overnight windows (e.g. 22:00–06:00), 3 min default hold; manual override respected per connect session. |
| **Theme contrast** | Audi RS Dark and dark primary buttons readable on Home; low-contrast gradient guard on action buttons. |
| **More screen** | Settings and Appearance on More; Advanced hub for Diagnostics, Android Auto, Roadmap, and Developer. |
| **Vehicle onboarding** | Car picker with Supported (Tier 1) and Beta (Tier 2) labels; persisted for diagnostics. |
| **Diagnostics support** | support@appsforgood.net copy/email from Diagnostics; user-initiated export only. |
| **Home widget and shortcuts** | Widget and launcher shortcuts for open/close/connect. |
| **Settings backup** | Local JSON export/import via Files. |
| **Receiver not-ready checklist** | Step-by-step help for status `04`. |
| **RSSI hints** | Weak-signal copy on scan list. |
| **Drive mode profiles** | Everyday, Quiet street, Track quick presets. |
| **iOS scaffold** | SwiftUI + CoreBluetooth sources under `ios/SoundKitCommunity/`. |
| **Wear OS tile** | `:wear` module opens phone app for connect. |

## Up next

Maintenance driven by owner feedback; no fixed timeline.

| Area | Intent |
|------|--------|
| **iOS parity** | Ship TestFlight-quality connect/open/close matching Android safety rules. |
| **Compatibility matrix** | Promote Beta vehicles as Supported when field-validated. |

## Later (product)

| Area | Intent |
|------|--------|
| **Assistant intents** | Local voice shortcuts when connected and state-known. |
| **CarPlay** | Explore in-car surface separate from Android Auto IoT. |

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
