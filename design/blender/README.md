# Valve Hero — Photoreal Sprite Pipeline

The Home screen valve hero plays a pre-rendered photoreal frame sequence
(closed → open) and overlays a live ember glow in-app. This folder produces
those frames.

## Why sprites?

Pure vector/Canvas drawing tops out at "stylised" — it fakes lighting with
gradients. A Blender render gives real PBR titanium/carbon, true reflections
from an environment map, and proper depth. We bake the rotation to frames and
index them by valve % at runtime, so it stays photoreal **and** still tracks the
real valve state with effectively zero runtime cost. See `DECISIONS.md` (ADR).

## Requirements

- [Blender](https://www.blender.org/) 3.6 LTS or 4.x
- Python + [Pillow](https://pillow.readthedocs.io/) for packing: `pip install pillow`
- (Optional, recommended) a studio HDRI, e.g. from [polyhaven.com](https://polyhaven.com/hdris)

## Workflow

### 1. Tweak the look (interactive)

Open Blender → Scripting workspace → open `valve_render.py` → **Run**. The scene
builds but does **not** render. Adjust materials/lighting/camera until you like
it. Point `CONFIG["hdri_path"]` at a studio HDRI for the best reflections.

### 2. Render the sequence (headless)

```bash
blender --background --python design/blender/valve_render.py
```

Writes `design/blender/out/frame_000.png … frame_047.png`.

**Blender 5.1 note:** `film_transparent=True` is broken in Cycles (alpha=0 everywhere). The script renders on a **pure black backdrop** instead; `pack_spritesheet.py` keys black pixels to transparent.

Tune `CONFIG` at the top of the script: `frame_count`, `res_x/res_y`, `samples`, `open_angle_deg`, `disc_fill` (0.80 = flat closed disc filling 80% of bore), etc.

### 3. Pack into a sprite sheet + manifest

On macOS use **`python3`** (there is no `python` / `pip` on PATH by default):

```bash
python3 -m pip install --user pillow   # once
python3 design/blender/pack_spritesheet.py \
    --frames design/blender/out \
    --out app/src/main/assets/valve
```

Produces:

- `app/src/main/assets/valve/valve_sheet.webp` — single packed sheet (lossless alpha after black key)
- `app/src/main/assets/valve/valve_manifest.json` — frame grid + bore alignment

If keying eats into the tip (too much transparency), raise the threshold:

```bash
python3 design/blender/pack_spritesheet.py --frames design/blender/out \
    --out app/src/main/assets/valve --key-threshold 32
```

If your rendered tip isn't perfectly centred, set the bore alignment so the
in-app glow lines up with the bore:

```bash
python design/blender/pack_spritesheet.py --frames design/blender/out \
    --out app/src/main/assets/valve \
    --bore-cx 0.5 --bore-cy 0.5 --bore-rx 0.34 --bore-ry 0.22
```

### 4. Run the app

`ValveVisual` auto-detects `assets/valve/valve_manifest.json`. When present it
plays the photoreal frames; when absent it falls back to the procedural
vector drawing (so the app always builds and renders, even before you render
frames).

## Manifest schema

| field | meaning |
|-------|---------|
| `frameCount` | number of frames (closed→open) |
| `cols`, `rows` | sprite-sheet grid |
| `frameWidth`, `frameHeight` | per-cell pixel size |
| `fps` | authoring fps (informational) |
| `boreCenterX/Y` | bore centre as a fraction of the frame (glow/ripple anchor) |
| `boreRadiusX/Y` | bore radii as a fraction of the frame |

Frame 0 = fully closed (disc flat, 80% fill). Frame `frameCount-1` = fully open.
