# Android Auto surface mocks (2026-10)

These are **template-accurate mocks** of the Car App Library UI from [`SoundKitCarScreen`](../../../app/src/main/java/com/akrapovic/soundkit/community/car/SoundKitCarScreen.kt), not DHU captures. No phone/DHU was connected when they were made.

| File | Real template | Title / copy |
|------|---------------|--------------|
| `aa-01-closed.jpg` | `GridTemplate` with Open + Close | Title **Closed**; Open enabled, Close inert |
| `aa-02-open.jpg` | `GridTemplate` with Open + Close | Title **Open**; Close enabled, Open inert |
| `aa-03-connecting.jpg` | `GridTemplate` loading | Title **Sound Kit**, spinner only |
| `aa-04-setup-phone.jpg` | `MessageTemplate` | Title **Sound Kit**, body **Finish setup on phone** |

In the car the host paints the chrome; the app only supplies title, loading, and the two grid actions (icons from `ic_valve_tile`).
