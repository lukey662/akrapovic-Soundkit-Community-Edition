# Store screenshots

- `phone/` — 1080×1920 (9:16) versions of `docs/screenshots/`, padded on black for the Play phone listing. Regenerate after `scripts/capture-docs-screenshots.sh`:

  ```bash
  for f in docs/screenshots/*.png; do b=$(basename "$f"); sips -s format png -Z 1920 "$f" --out "play/screenshots/phone/$b" >/dev/null && sips --padToHeightWidth 1920 1080 --padColor 000000 "play/screenshots/phone/$b" >/dev/null; done
  ```

- `android-auto/` — captured from the Desktop Head Unit (800×480) on the 0.3.7 debug build. Not uploaded to Play (no Android Auto distribution); kept for the README and for checking the head-unit layout.
