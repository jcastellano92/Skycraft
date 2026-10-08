#!/usr/bin/env python3
"""
Build a personal resource pack that plays YOUR OWN copy of the Skyrim soundtrack in Skycraft.

Skycraft ships its own original score. If you own The Elder Scrolls V: Skyrim you may prefer the real thing: export
the music files from your own installation (see docs/MUSIC.md), point this script at that folder, and it converts
them to Ogg Vorbis with ffmpeg and writes a resource pack ("Skycraft - Personal Skyrim OST") whose
assets/skycraft/sounds.json maps them onto Skycraft's situational music events with "replace": true.

  * It never downloads anything and never touches files outside the input folder and the output zip.
  * The resulting pack contains copyrighted music: it is for your personal use only. Do not share or upload it.

Skyrim names its music like mus_explore_day_01, mus_explore_night_02, mus_town_day_01, mus_dungeon_01, mus_combat_03,
mus_combat_boss, mus_discover_dungeon_01, mus_levelup_01, mus_sovngarde_01 ... (.xwm, or .wav/.mp3/.flac/.ogg after
conversion). Files are matched by name (first matching rule wins); use --map 'regex=music.event' to add your own rules
and --dry-run to see the mapping without converting anything.

Usage:
    python3 tools/music/import_skyrim_ost.py ~/SkyrimMusic
    python3 tools/music/import_skyrim_ost.py ~/SkyrimMusic -o ~/.minecraft/resourcepacks/skycraft_personal_ost.zip
    python3 tools/music/import_skyrim_ost.py ~/SkyrimMusic --dry-run --map 'mus_special_.*=music.sovngarde'
"""
from __future__ import annotations

import argparse
import json
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

PACK_NAME = "Skycraft – Personal Skyrim OST"
PACK_FORMAT = 15  # Minecraft 1.20.1
EXTENSIONS = {".xwm", ".wav", ".mp3", ".flac", ".ogg", ".wma", ".m4a", ".opus"}

# Skycraft music events (registered by the world and atmosphere modules).
EVENTS = ["music.explore", "music.explore_night", "music.town", "music.dungeon", "music.combat", "music.boss",
          "music.oblivion", "music.sovngarde", "music.discovery", "music.level_up"]

# (regex on the lower-case file stem, event); the first match wins.
RULES = [
    (r"level_?up", "music.level_up"),
    (r"discover", "music.discovery"),
    (r"combat.*boss|boss|dragon|alduin", "music.boss"),
    (r"combat", "music.combat"),
    (r"sovngarde", "music.sovngarde"),
    (r"oblivion|apocrypha|soul_?cairn|daedric", "music.oblivion"),
    (r"town|tavern|\binn\b|city", "music.town"),
    (r"dungeon|dread|cave|ruin|dwemer|dwarven|crypt|barrow", "music.dungeon"),
    (r"explore.*night|night", "music.explore_night"),
    (r"explore|dawn|dusk|day|wilderness|travel", "music.explore"),
]

def classify(stem: str, rules) -> str | None:
    s = stem.lower()
    for pattern, event in rules:
        if re.search(pattern, s):
            return event
    return None


def safe_name(stem: str, used: set) -> str:
    base = re.sub(r"[^a-z0-9_]+", "_", stem.lower()).strip("_") or "track"
    name, i = base, 2
    while name in used:
        name = f"{base}_{i}"
        i += 1
    used.add(name)
    return name


def convert(ffmpeg: str, src: pathlib.Path, dst: pathlib.Path, quality: float) -> None:
    cmd = [ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-i", str(src), "-vn", "-map_metadata", "-1",
           "-ac", "2", "-ar", "44100", "-c:a", "libvorbis", "-q:a", str(quality), str(dst)]
    subprocess.run(cmd, check=True)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("folder", type=pathlib.Path, help="folder with music files exported from YOUR copy of Skyrim")
    ap.add_argument("-o", "--out", type=pathlib.Path, default=pathlib.Path("skycraft_personal_skyrim_ost.zip"),
                    help="output resource pack zip (default: ./skycraft_personal_skyrim_ost.zip)")
    ap.add_argument("--map", action="append", default=[], metavar="REGEX=EVENT",
                    help="extra rule tried before the built-in ones, e.g. 'mus_special_.*=music.sovngarde'")
    ap.add_argument("--quality", type=float, default=5.0, help="Vorbis quality for conversion (default 5)")
    ap.add_argument("--dry-run", action="store_true", help="only print which file goes to which event")
    args = ap.parse_args()

    if not args.folder.is_dir():
        print(f"error: {args.folder} is not a folder", file=sys.stderr)
        return 2
    rules = []
    for m in args.map:
        if "=" not in m:
            print(f"error: --map needs REGEX=EVENT, got {m!r}", file=sys.stderr)
            return 2
        pattern, event = m.rsplit("=", 1)
        if event not in EVENTS:
            print(f"error: unknown event {event!r}; choose from {', '.join(EVENTS)}", file=sys.stderr)
            return 2
        rules.append((pattern.lower(), event))
    rules += RULES

    files = sorted(p for p in args.folder.rglob("*") if p.is_file() and p.suffix.lower() in EXTENSIONS)
    if not files:
        print(f"error: no music files ({', '.join(sorted(EXTENSIONS))}) found under {args.folder}", file=sys.stderr)
        return 1

    plan: dict[str, list[pathlib.Path]] = {e: [] for e in EVENTS}
    skipped = []
    for f in files:
        event = classify(f.stem, rules)
        if event:
            plan[event].append(f)
        else:
            skipped.append(f)

    for event in EVENTS:
        if plan[event]:
            print(f"{event}:")
            for f in plan[event]:
                print(f"    {f.relative_to(args.folder)}")
    for event in EVENTS:
        if not plan[event]:
            print(f"{event}: (none found - Skycraft's original track stays)")
    if skipped:
        print("not mapped (use --map to assign):")
        for f in skipped:
            print(f"    {f.relative_to(args.folder)}")
    if args.dry_run:
        return 0
    if not any(plan.values()):
        print("error: nothing to put in the pack", file=sys.stderr)
        return 1

    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        print("error: ffmpeg was not found on PATH. Install it (https://ffmpeg.org, or your package manager) - it is\n"
              "needed to decode .xwm and to encode Ogg Vorbis, the only format Minecraft plays.", file=sys.stderr)
        return 1

    sounds = {}
    used: set = set()
    with tempfile.TemporaryDirectory() as tmp:
        tmpdir = pathlib.Path(tmp)
        converted: list[tuple[str, pathlib.Path]] = []
        for event in EVENTS:
            entries = []
            for f in plan[event]:
                name = safe_name(f.stem, used)
                dst = tmpdir / f"{name}.ogg"
                print(f"converting {f.name} -> personal/{name}.ogg")
                try:
                    convert(ffmpeg, f, dst, args.quality)
                except subprocess.CalledProcessError:
                    print(f"  warning: ffmpeg could not convert {f}; skipped", file=sys.stderr)
                    continue
                converted.append((name, dst))
                entries.append({"name": f"skycraft:music/personal/{name}", "stream": True})
            if entries:
                sounds[event] = {"replace": True, "sounds": entries}

        if not sounds:
            print("error: no file could be converted", file=sys.stderr)
            return 1
        if "music.combat" in sounds and "music.boss" in sounds:
            # Skycraft's situational player only asks for music.combat: keep boss pieces in that rotation
            sounds["music.combat"]["sounds"].append({"name": "skycraft:music.boss", "type": "event"})
        mcmeta = {"pack": {"pack_format": PACK_FORMAT,
                           "description": f"{PACK_NAME}\n§7Personal use only - do not redistribute"}}
        args.out.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(args.out, "w", compression=zipfile.ZIP_DEFLATED) as z:
            z.writestr("pack.mcmeta", json.dumps(mcmeta, indent=2, ensure_ascii=False))
            z.writestr("assets/skycraft/sounds.json", json.dumps(sounds, indent=2))
            z.writestr("README.txt",
                       f"{PACK_NAME}\n\nBuilt by Skycraft's tools/music/import_skyrim_ost.py from music files the pack's owner\n"
                       "exported from their own copy of The Elder Scrolls V: Skyrim. The music is (c) Bethesda Softworks /\n"
                       "its composer. This pack is for the owner's personal use only. Do not share or upload it.\n")
            for name, path in converted:
                z.write(path, f"assets/skycraft/sounds/music/personal/{name}.ogg", compress_type=zipfile.ZIP_STORED)

    print(f"\nwrote {args.out}  ({args.out.stat().st_size / 1e6:.1f} MB)")
    print("Copy it into .minecraft/resourcepacks/ and enable it above the Skycraft resources in Options > Resource Packs.")
    print("Reminder: this pack contains your personal copy of copyrighted music - keep it to yourself.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
