#!/usr/bin/env bash
# Resolves tools/modlist.txt into the packwiz pack in pack/ (one .pw.toml per mod).
# Usage: tools/build-pack.sh [path/to/skycraft-core.jar]
# Requires packwiz on PATH and network access to Modrinth/CurseForge.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PACK="$ROOT/pack"
LIST="$ROOT/tools/modlist.txt"
REPORT="$ROOT/pack/RESOLVE_REPORT.md"
cd "$PACK"

echo "# Mod resolution report" > "$REPORT"
echo "" >> "$REPORT"
ok=0; failed=0
while IFS= read -r raw; do
  line="${raw%%#*}"
  line="$(echo "$line" | xargs)"
  [ -z "$line" ] && continue
  resolved=""
  IFS='|' read -ra options <<< "$line"
  # Already resolved earlier (committed .pw.toml)? Keep the pinned version.
  for opt in "${options[@]}"; do
    slug="$(echo "${opt#*:}" | xargs)"
    for dir in mods resourcepacks shaderpacks; do
      if [ -f "$PACK/$dir/$slug.pw.toml" ]; then resolved="$(echo "$opt" | xargs)"; fi
    done
  done
  if [ -n "$resolved" ]; then
    ok=$((ok+1)); echo "- [x] \`$resolved\` (pinned)" >> "$REPORT"; continue
  fi
  for opt in "${options[@]}"; do
    opt="$(echo "$opt" | xargs)"
    src="${opt%%:*}"; slug="${opt#*:}"
    case "$src" in
      mr) cmd=(packwiz modrinth add "$slug" -y) ;;
      cf) cmd=(packwiz curseforge add "$slug" -y) ;;
      *) echo "unknown source in: $opt"; continue ;;
    esac
    echo ">>> ${cmd[*]}"
    if out="$("${cmd[@]}" 2>&1)"; then
      if echo "$out" | grep -qiE "no (projects|files|valid versions)|not found|failed|error"; then
        echo "$out"
        continue
      fi
      echo "$out"
      resolved="$opt"
      break
    else
      echo "$out"
    fi
  done
  if [ -n "$resolved" ]; then
    ok=$((ok+1)); echo "- [x] \`$resolved\`" >> "$REPORT"
  else
    failed=$((failed+1)); echo "- [ ] **unresolved:** \`$line\`" >> "$REPORT"
  fi
done < "$LIST"

if [ $# -ge 1 ] && [ -f "$1" ]; then
  mkdir -p "$PACK/mods"
  cp "$1" "$PACK/mods/skycraft-core.jar"
  echo "- [x] Skycraft Core (local jar)" >> "$REPORT"
fi

packwiz refresh
echo "" >> "$REPORT"
echo "Resolved: $ok, unresolved: $failed" >> "$REPORT"
cat "$REPORT"
