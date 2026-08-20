#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

extract_keys() {
  sed -n 's/.*<string name="\([^"]*\)".*/\1/p' "$1" | sort
}

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
extract_keys app/src/main/res/values/strings.xml > "$TMP_DIR/en.keys"
extract_keys app/src/main/res/values-vi/strings.xml > "$TMP_DIR/vi.keys"
if ! diff -u "$TMP_DIR/en.keys" "$TMP_DIR/vi.keys"; then
  echo "I18N_KEY_PARITY_FAIL" >&2
  exit 1
fi

forbidden='MediaPlayer|ExoPlayer|androidx\.media3|MediaSession|AudioTrack|previewUrl|MANAGE_EXTERNAL_STORAGE|fallbackToDestructiveMigration'
if grep -RIn --include='*.kt' --include='*.xml' -E "$forbidden" app/src/main; then
  echo "INVARIANT_FAIL" >&2
  exit 1
fi

echo "I18N_KEY_PARITY_PASS keys=$(wc -l < "$TMP_DIR/en.keys")"
echo "INVARIANT_PASS"
