#!/usr/bin/env python3
"""Fail if a skycraft item model is missing or references a missing texture.

Items are discovered from assets/skycraft/lang/en_us.json ("item.skycraft.<id>").
Run from the repo root: python tools/check_assets.py
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "mod" / "src" / "main"
ASSETS = ROOT / "resources" / "assets" / "skycraft"
LANG = [ROOT / "lang" / "skycraft"]
NS = "skycraft"


def item_ids():
    ids = set()
    for d in LANG:
        if not d.exists():
            continue
        for f in d.glob("*.json"):
            for k in json.loads(f.read_text(encoding="utf-8")):
                if k.startswith("item.skycraft."):
                    ids.add(k[len("item.skycraft."):])
    return ids


def tex_exists(ref):
    ns, _, path = ref.partition(":") if ":" in ref else (NS, "", ref)
    if ns != NS:
        return True  # vanilla / other mods
    return (ASSETS / "textures" / (path + ".png")).exists()


def model_exists(ref):
    ns, _, path = ref.partition(":") if ":" in ref else (NS, "", ref)
    if ns != NS:
        return True
    return (ASSETS / "models" / (path + ".json")).exists()


def check_model(path, seen, errors, owner):
    if not path.exists():
        errors.append(f"{owner}: missing model {path.name}")
        return
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception as e:  # noqa: BLE001
        errors.append(f"{owner}: bad json {path.name}: {e}")
        return
    parent = data.get("parent")
    if parent and ":" in parent and not parent.startswith("minecraft:") and parent not in seen:
        seen.add(parent)
        if not model_exists(parent):
            errors.append(f"{owner}: missing parent model {parent}")
    elif parent and ":" not in parent and not parent.startswith(("item/", "block/", "builtin/")):
        if not model_exists(parent):
            errors.append(f"{owner}: missing parent model {parent}")
    for name, ref in data.get("textures", {}).items():
        if isinstance(ref, str) and not ref.startswith("#") and not tex_exists(ref):
            errors.append(f"{owner}: missing texture {ref} ({name})")


def main():
    errors = []
    ids = sorted(item_ids())
    for i in ids:
        model = ASSETS / "models" / "item" / f"{i}.json"
        check_model(model, set(), errors, f"item {i}")
    for e in errors:
        print("ERROR", e)
    print(f"checked {len(ids)} items, {len(errors)} problems")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
