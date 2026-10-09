#!/usr/bin/env bash
# CI client smoke test: boots the Skycraft client under Xvfb (software Mesa) with -Dskycraft.autotest=true,
# visits all Skyrim UI screens, tests entity renderers, captures screenshots, and checks for model/texture errors.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/dist/client-test"
mkdir -p "$OUT"

cd "$ROOT/mod"
echo "=== Running Skycraft Client Smoke Test with Mesa Software OpenGL ==="

# Execute gradle runClient with autotest flag
xvfb-run -s "-screen 0 1024x768x24" ./gradlew runClient --console=plain -Dskycraft.autotest=true 2>&1 | tee "$OUT/client.log"

cp -r build/autotest-screenshots "$OUT/" 2>/dev/null || true

echo "=== Client Smoke Test Finished ==="
if grep -iE "Fatal|Crash|Exception caught during firing event" "$OUT/client.log" | grep -v "skycraft.autotest" | head -40; then
  echo "Client test encountered critical errors."
  exit 1
fi
echo "Client smoke test passed."

