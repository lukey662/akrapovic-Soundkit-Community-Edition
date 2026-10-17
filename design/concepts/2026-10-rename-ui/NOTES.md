# Workshop instrument concepts (2026-10)

Approval gate before any Compose redesign. Compare PNGs show current captures vs a static mock of the proposed direction.

## Files

| Pair | Before | After | Side by side |
|------|--------|-------|--------------|
| Home connected | `before-home.png` | `after-home.png` | `compare-home.png` |
| More / settings list | `before-settings.png` (drive-mode list capture) | `after-settings.png` | `compare-settings.png` |
| Onboarding / risk | `before-onboarding.png` | `after-onboarding.png` | `compare-onboarding.png` |

## Direction (approved + refined)

Keep the photoreal exhaust tip as the only loud moment. Warm charcoal workshop shell with soft titanium lift and amber accent.

Feedback after concept review: the first afters felt a bit **square** and **flat**. Ship with:

- Soft radii: surfaces/groups ~20dp, primary CTAs ~14dp (not hard rectangles, not full pills)
- Richer color: subtle shell + card vertical gradients; warm muted text; amber action gradients

## Applied in Compose

- [`Theme.kt`](../../../app/src/main/java/com/akrapovic/soundkit/community/ui/theme/Theme.kt) / [`GarageThemes.kt`](../../../app/src/main/java/com/akrapovic/soundkit/community/ui/theme/GarageThemes.kt): Studio Workshop default palette and warmer outlines
- [`AkraUi.kt`](../../../app/src/main/java/com/akrapovic/soundkit/community/ui/components/AkraUi.kt): soft shapes + gradient shell/surfaces
- [`STYLE_GUIDE.md`](../../../STYLE_GUIDE.md): visual language updated to match
