#!/usr/bin/env sh
set -eu

if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
VERSION="8.10.2"
DIST_DIR="$ROOT_DIR/.gradle/local-gradle"
GRADLE_HOME="$DIST_DIR/gradle-$VERSION"

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$DIST_DIR"
  ZIP_FILE="$DIST_DIR/gradle-$VERSION-bin.zip"
  if [ ! -f "$ZIP_FILE" ]; then
    curl -L "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$ZIP_FILE"
  fi
  unzip -q "$ZIP_FILE" -d "$DIST_DIR"
fi

exec "$GRADLE_HOME/bin/gradle" "$@"

