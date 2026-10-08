#!/usr/bin/env bash
# CI smoke test: boots the packaged Skycraft server, lets it generate the spawn area, runs a few commands,
# stops it, and fails if it crashed or never finished starting. Logs are copied to dist/server-test/.
# NOTE: this sets eula=true inside the throwaway CI directory only, to run the automated test.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/dist/server-test"
WORK="$(mktemp -d)"
mkdir -p "$OUT"
unzip -q "$ROOT/dist/Skycraft-server.zip" -d "$WORK"
SRV="$WORK/server"
cd "$SRV"
echo "eula=true" > eula.txt
cat > server.properties <<PROPS
online-mode=false
level-seed=skycraft
view-distance=6
simulation-distance=6
spawn-protection=0
max-tick-time=-1
PROPS
cat > user_jvm_args.txt <<ARGS
-Xms2G
-Xmx5G
ARGS
mkfifo console
( ./run.sh nogui < console > console.log 2>&1; echo "EXIT $?" >> console.log ) &
exec 3> console
started=0
for i in $(seq 1 180); do
  sleep 5
  if grep -q "Done (" console.log; then started=1; break; fi
  if grep -q "^EXIT" console.log; then break; fi
done
if [ "$started" = 1 ]; then
  echo "Server started after ~$((i*5))s"
  for cmd in "forceload add -64 -64 64 64" "locate structure #minecraft:village" "locate structure #skycraft:dungeons" \
             "skycraft-roads info" "list" "save-all"; do
    echo "$cmd" >&3; sleep 8
  done
  sleep 30
  echo "stop" >&3
  for i in $(seq 1 60); do sleep 3; grep -q "^EXIT" console.log && break; done
fi
exec 3>&-
cp console.log "$OUT/console.log"
cp logs/latest.log "$OUT/latest.log" 2>/dev/null || true
cp logs/debug.log "$OUT/debug.log" 2>/dev/null || true
cp -r crash-reports "$OUT/" 2>/dev/null || true

echo "=== Skycraft errors/warnings ==="
grep -iE "skycraft" "$OUT/latest.log" 2>/dev/null | grep -iE "error|exception|warn|failed" | head -100
echo "=== All ERROR lines (first 150) ==="
grep -E "/ERROR\]|\[ERROR\]" "$OUT/latest.log" 2>/dev/null | head -150
if [ "$started" != 1 ]; then echo "Server did not finish starting"; tail -150 console.log; exit 1; fi
if ls "$OUT/crash-reports"/*.txt >/dev/null 2>&1; then echo "Crash report produced:"; head -80 "$OUT"/crash-reports/*.txt; exit 1; fi
echo "Server smoke test passed"
