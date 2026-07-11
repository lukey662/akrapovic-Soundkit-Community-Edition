# Vehicle compatibility

Sound Kit Community is **receiver-centric**: it talks to the Akrapovič **Car** Sound Kit BLE receiver, not to a specific car model. The app uses the protocol verified from `si.sunesis.akrapovic.soundkit`.

## Tiers

| Tier | In-app label | Meaning |
|------|--------------|---------|
| **Tier 1** | Supported | Validated or high-confidence on the same Car SoundKit BLE stack (reference: Audi RS3). |
| **Tier 2** | Beta | Expected to work on the same protocol; not yet field-validated in this project. |
| **Tier 3** | Not compatible | No Car Sound Kit BLE receiver (e.g. remote-only kit, motorcycle Sound Kit Custom app). |

## Tier 1 (Supported)

| Make | Model / platform | Notes |
|------|------------------|-------|
| Audi | RS3 | Primary physical test vehicle for this project. |

## Tier 2 (Beta)

Same BLE discovery (`SoundKit` / advertising signature `103`) and GATT (`0000fff4…`, toggle `01`) assumed until proven otherwise:

| Make | Model / platform |
|------|------------------|
| Audi | RS (other) |
| BMW | M3 / M4 (F80/F82/F83) |
| BMW | X3 M / X4 M (F97/F98) |
| Porsche | Akrapovič Sound Kit |
| Mercedes-AMG | Akrapovič Sound Kit |
| Other | Car with Sound Kit |

## Out of scope

- **Motorcycle** Sound Kit (`si.sunesis.akrapovic.soundkitcustom`) — different app/protocol.
- **Remote-only** kits without BLE receiver.
- **Non-Akrapovič** exhaust valve systems.

## Submitting diagnostics

1. Connect while parked and reproduce the issue.
2. **More → Advanced → Diagnostics** → Copy, Save, or Share report.
3. Email **support@appsforgood.net** with the `.txt` attachment.

Include your vehicle tier from onboarding if possible. Reports may contain BLE MAC addresses — review before sending.

## Promoting Beta → Supported

Tier 2 entries remain Beta until field evidence is reviewed; shared names,
advertising hints, or an assumed protocol are not enough to promote them.

For a proposed promotion, capture a parked physical-receiver test on the
specific make/model/platform and include:

1. Device and app build details, receiver identity (redact the MAC address
   before publication), and the vehicle/platform.
2. Successful discovery, pairing where applicable, connection, and GATT
   profile evidence matching `BLE_PROTOCOL.md`.
3. Notification evidence for the expected valve states and status `04`
   readiness behavior.
4. One state-gated Open and Close physical smoke result, plus the relevant
   safety checks from `TESTING.md`; stop and report any unexpected movement.
5. A reviewed, user-approved diagnostics export or equivalent reproducible
   field record.

Then open a PR that links or attaches the redacted evidence, updates this file
and the in-app catalog in `VehicleCompatibility.kt`, and states the evidence
scope and remaining limitations. Do not promote a Tier 2 vehicle based on a
report that lacks this field evidence.
