#!/usr/bin/env bash
# Builds ready-to-use downloads from the packwiz pack:
#   dist/Skycraft-client-instance.zip  Prism Launcher / MultiMC instance (import the zip)
#   dist/Skycraft-server.zip           Forge server with all server-side mods
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$ROOT/dist"
WORK="$(mktemp -d)"
MC=1.20.1
FORGE=47.4.10
mkdir -p "$DIST"

# Serve the pack over HTTP for packwiz-installer.
(cd "$ROOT/pack" && python3 -m http.server 8765 >/dev/null 2>&1) &
SERVER_PID=$!
trap 'kill $SERVER_PID 2>/dev/null || true' EXIT
sleep 2

BOOT="$WORK/packwiz-installer-bootstrap.jar"
curl -fsSL -o "$BOOT" https://github.com/packwiz/packwiz-installer-bootstrap/releases/download/v0.0.3/packwiz-installer-bootstrap.jar

# ---------------------------------------------------------------- client instance (Prism/MultiMC format)
INST="$WORK/Skycraft"
mkdir -p "$INST/.minecraft"
(cd "$INST/.minecraft" && java -jar "$BOOT" -g -s client http://localhost:8765/pack.toml)
cat > "$INST/instance.cfg" <<CFG
InstanceType=OneSix
name=Skycraft
iconKey=default
JvmArgs=-Xms4G -Xmx8G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M
OverrideMemory=true
MinMemAlloc=4096
MaxMemAlloc=8192
CFG
cat > "$INST/mmc-pack.json" <<JSON
{
  "components": [
    {"uid": "net.minecraft", "version": "$MC", "important": true},
    {"uid": "net.minecraftforge", "version": "$FORGE"}
  ],
  "formatVersion": 1
}
JSON
(cd "$WORK" && zip -qr "$DIST/Skycraft-client-instance.zip" Skycraft)

# ---------------------------------------------------------------- dedicated server
SRV="$WORK/server"
mkdir -p "$SRV"
curl -fsSL -o "$SRV/forge-installer.jar" "https://maven.minecraftforge.net/net/minecraftforge/forge/$MC-$FORGE/forge-$MC-$FORGE-installer.jar"
(cd "$SRV" && java -jar forge-installer.jar --installServer >/dev/null && rm -f forge-installer.jar forge-installer.jar.log)
(cd "$SRV" && java -jar "$BOOT" -g -s server http://localhost:8765/pack.toml)
cat > "$SRV/server.properties" <<PROPS
# Skycraft defaults
motd=Skycraft - The Elder Scrolls in Minecraft
difficulty=normal
# giants launch players and shouts fling them: don't kick for "flying"
allow-flight=true
# heavy modded worldgen can stall a tick; don't let the watchdog kill the server
max-tick-time=-1
view-distance=10
simulation-distance=8
spawn-protection=0
enable-command-block=false
sync-chunk-writes=false
online-mode=true
pvp=true
PROPS
cat > "$SRV/user_jvm_args.txt" <<ARGS
-Xms4G
-Xmx8G
-XX:+UseG1GC
-XX:+ParallelRefProcEnabled
-XX:MaxGCPauseMillis=200
-XX:+UnlockExperimentalVMOptions
-XX:+DisableExplicitGC
-XX:G1NewSizePercent=30
-XX:G1MaxNewSizePercent=40
-XX:G1HeapRegionSize=8M
-XX:G1ReservePercent=20
ARGS
cat > "$SRV/README.txt" <<TXT
Skycraft dedicated server (Minecraft $MC, Forge $FORGE)

1. Read and accept the Minecraft EULA (https://aka.ms/MinecraftEULA) by setting eula=true in eula.txt.
2. Start with ./run.sh (Linux/macOS) or run.bat (Windows).
3. Give the server 6-10 GB of RAM in user_jvm_args.txt. Distant Horizons builds distant terrain in the background.
TXT
(cd "$WORK" && zip -qr "$DIST/Skycraft-server.zip" server)
ls -la "$DIST"
