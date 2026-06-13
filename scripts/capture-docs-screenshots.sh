#!/usr/bin/env bash
# Record README screenshots via Paparazzi (JVM — no device or emulator required).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

LOCAL_DIR="$ROOT/docs/screenshots"
SNAPSHOT_DIR="$ROOT/app/src/test/snapshots/images"
TEST_CLASS="com.akrapovic.soundkit.community.ui.DocsScreenshotPaparazziTest"

echo "Recording Paparazzi screenshots…"
./gradlew :app:recordPaparazziDebug --tests "$TEST_CLASS" --quiet

mkdir -p "$LOCAL_DIR"
shopt -s nullglob
for src in "$SNAPSHOT_DIR"/*DocsScreenshotPaparazziTest*.png; do
  dest="$(basename "$src")"
  dest="${dest##*_}"
  cp "$src" "$LOCAL_DIR/$dest"
done
shopt -u nullglob

count="$(find "$LOCAL_DIR" -maxdepth 1 -name '*.png' 2>/dev/null | wc -l | tr -d ' ')"
echo "Done — $count screenshot(s) in docs/screenshots/"
