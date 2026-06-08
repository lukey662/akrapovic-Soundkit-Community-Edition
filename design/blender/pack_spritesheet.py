#!/usr/bin/env python3
"""
Pack the rendered valve frames into a single sprite sheet + JSON manifest that
the Compose `ValveVisual` loads from the app's assets.

Requires Pillow:  pip install pillow

USAGE:
    python design/blender/pack_spritesheet.py \
        --frames design/blender/out \
        --out app/src/main/assets/valve

Outputs:
    <out>/valve_sheet.webp     single packed sprite sheet (alpha, lossless)
    <out>/valve_manifest.json  geometry the in-app player needs

The frames must be named frame_000.png, frame_001.png, ... (0-based), which is
exactly what valve_render.py produces.
"""

import argparse
import json
import math
import os
import re
import sys

try:
    from PIL import Image
except ImportError:
    sys.exit("Pillow is required:  pip install pillow")

FRAME_RE = re.compile(r"frame_(\d+)\.png$", re.IGNORECASE)


def key_black_background(image, threshold=42):
    """Convert a black-backdrop render to straight RGBA (Blender 5.1 film_transparent workaround)."""
    keyed = image.convert("RGBA")
    px = keyed.load()
    w, h = keyed.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if r <= threshold and g <= threshold and b <= threshold:
                px[x, y] = (0, 0, 0, 0)
            else:
                px[x, y] = (r, g, b, 255)
    return keyed


def max_channel(image):
    px = image.getdata()
    return max(max(p[0], p[1], p[2]) for p in px)


def collect_frames(frames_dir):
    found = []
    for name in os.listdir(frames_dir):
        m = FRAME_RE.search(name)
        if m:
            found.append((int(m.group(1)), os.path.join(frames_dir, name)))
    found.sort(key=lambda t: t[0])
    if not found:
        sys.exit(f"No frame_###.png files found in {frames_dir}")
    return [path for _, path in found]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--frames", required=True, help="dir with frame_###.png")
    ap.add_argument("--out", required=True, help="output dir (app assets/valve)")
    ap.add_argument("--fps", type=int, default=60)
    ap.add_argument("--max-cols", type=int, default=8, help="sprite-sheet column cap")
    # Bore alignment as fractions of a frame (used to place the live glow/ripple).
    ap.add_argument("--bore-cx", type=float, default=0.5)
    ap.add_argument("--bore-cy", type=float, default=0.5)
    ap.add_argument("--bore-rx", type=float, default=0.34)
    ap.add_argument("--bore-ry", type=float, default=0.22)
    ap.add_argument(
        "--key-threshold",
        type=int,
        default=42,
        help="Pixels with R,G,B all <= this value become transparent (black backdrop key)",
    )
    args = ap.parse_args()

    paths = collect_frames(args.frames)
    frames = [key_black_background(Image.open(p).convert("RGBA"), args.key_threshold) for p in paths]
    fw, fh = frames[0].size
    for i, im in enumerate(frames):
        if im.size != (fw, fh):
            sys.exit(f"Frame {i} size {im.size} != {fw}x{fh}; all frames must match.")

    count = len(frames)
    cols = min(args.max_cols, count)
    rows = math.ceil(count / cols)

    # Sanity-check: reject empty renders (checks keyed alpha + source luminance).
    sample = frames[0]
    max_alpha = max(px[3] for px in sample.getdata())
    if max_alpha < 8:
        raw = Image.open(paths[0]).convert("RGBA")
        peak = max_channel(raw)
        sys.exit(
            "ERROR: frame_000.png has no visible content after black-backdrop keying.\n"
            f"  Raw frame peak RGB: {peak} (need > {args.key_threshold}).\n"
            "Re-render with the latest design/blender/valve_render.py (brighter lights, opaque black backdrop):\n"
            "  blender --background --python design/blender/valve_render.py"
        )

    sheet = Image.new("RGBA", (cols * fw, rows * fh), (0, 0, 0, 0))
    for i, im in enumerate(frames):
        x = (i % cols) * fw
        y = (i // cols) * fh
        sheet.paste(im, (x, y))

    os.makedirs(args.out, exist_ok=True)
    sheet_path = os.path.join(args.out, "valve_sheet.webp")
    sheet.save(sheet_path, "WEBP", lossless=True, quality=100, method=6)

    manifest = {
        "frameCount": count,
        "cols": cols,
        "rows": rows,
        "frameWidth": fw,
        "frameHeight": fh,
        "fps": args.fps,
        "boreCenterX": args.bore_cx,
        "boreCenterY": args.bore_cy,
        "boreRadiusX": args.bore_rx,
        "boreRadiusY": args.bore_ry,
    }
    manifest_path = os.path.join(args.out, "valve_manifest.json")
    with open(manifest_path, "w") as f:
        json.dump(manifest, f, indent=2)

    kb = os.path.getsize(sheet_path) / 1024.0
    print(f"Packed {count} frames -> {sheet_path} ({kb:.0f} KB, {cols}x{rows} grid)")
    print(f"Manifest -> {manifest_path}")


if __name__ == "__main__":
    main()
