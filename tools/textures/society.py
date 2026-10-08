"""Generates the society module's textures (PIL).

Writes PNGs into mod/src/main/resources/assets/skycraft/textures/entity/society/:
  <role>_0.png, <role>_1.png         64x64 player-layout skins for every NPC role (0 = male, 1 = female)
  cosmetic/<id>.png                  64x64 faction regalia (CosmeticLayer UVs: hood, torso, tabard panels, cloak, pelts)

Every model box is painted face by face from the same UV layout as the Java models. Run:
  python3 tools/textures/society.py
"""
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "skycraft", "textures", "entity", "society")


# ---------------------------------------------------------------------------------------------------- color helpers

def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(c, f):
    return tuple(clamp(x * f) for x in c[:3])


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def jitter(c, rnd, amt):
    d = rnd.uniform(-amt, amt)
    return tuple(clamp(x + d + rnd.uniform(-amt * 0.3, amt * 0.3)) for x in c[:3])


def box_faces(u, v, w, h, d):
    """UV rectangles of a Minecraft model box (texOffs u,v; size w,h,d)."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


FACE_LIGHT = {"top": 1.06, "front": 1.0, "right": 0.93, "left": 0.93, "back": 0.9, "bottom": 0.8}


def paint_box(img, u, v, w, h, d, fn, rnd, noise=6, edges=True):
    """fn(face, x, y, fw, fh) -> rgb / rgba tuple, or None for transparent."""
    W, H = img.size
    for face, (x0, y0, fw, fh) in box_faces(u, v, w, h, d).items():
        for y in range(fh):
            for x in range(fw):
                px, py = x0 + x, y0 + y
                if px >= W or py >= H:
                    continue
                c = fn(face, x, y, fw, fh)
                if c is None:
                    continue
                alpha = c[3] if len(c) == 4 else 255
                c = shade(c[:3], FACE_LIGHT[face])
                if edges and (x == 0 or x == fw - 1 or y == fh - 1) and face != "top":
                    c = shade(c, 0.9)
                if noise:
                    c = jitter(c, rnd, noise)
                img.putpixel((px, py), (c[0], c[1], c[2], alpha))


def save(img, *path):
    p = os.path.join(OUT, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)
    print("wrote", os.path.relpath(p, ROOT))


# ---------------------------------------------------------------------------------------------------- humanoid layout

# name: (u, v, w, h, d, height of the part's top above the feet in pixels, kind)
BASE_PARTS = {
    "head": (0, 0, 8, 8, 8, 32, "head"),
    "body": (16, 16, 8, 12, 4, 24, "body"),
    "rarm": (40, 16, 4, 12, 4, 24, "arm"),
    "larm": (32, 48, 4, 12, 4, 24, "arm"),
    "rleg": (0, 16, 4, 12, 4, 12, "leg"),
    "lleg": (16, 48, 4, 12, 4, 12, "leg"),
}
OVER_PARTS = {
    "head": (32, 0, 8, 8, 8, 32, "head"),
    "body": (16, 32, 8, 12, 4, 24, "body"),
    "rarm": (40, 32, 4, 12, 4, 24, "arm"),
    "larm": (48, 48, 4, 12, 4, 24, "arm"),
    "rleg": (0, 32, 4, 12, 4, 12, "leg"),
    "lleg": (0, 48, 4, 12, 4, 12, "leg"),
}


class P:
    __slots__ = ("part", "kind", "face", "x", "y", "fw", "fh", "h", "side")

    def __init__(self, part, kind, face, x, y, fw, fh, h):
        self.part, self.kind, self.face, self.x, self.y, self.fw, self.fh, self.h = part, kind, face, x, y, fw, fh, h
        self.side = face in ("left", "right")


def paint_humanoid(img, base_fn, over_fn, rnd, noise=7):
    for parts, fn, is_over in ((BASE_PARTS, base_fn, False), (OVER_PARTS, over_fn, True)):
        for name, (u, v, w, h, d, top, kind) in parts.items():
            def f(face, x, y, fw, fh, name=name, kind=kind, top=top, h=h):
                if face == "top":
                    hh = top
                elif face == "bottom":
                    hh = top - h
                else:
                    hh = top - y - 0.5
                return fn(P(name, kind, face, x, y, fw, fh, hh))
            paint_box(img, u, v, w, h, d, f, rnd, noise=noise, edges=not is_over)


SKIN = {"nord": hexc("e0b79a"), "pale": hexc("e8cbb6"), "tan": hexc("c69072"), "dark": hexc("8a5a3e"),
        "altmer": hexc("e2c27a"), "dunmer": hexc("7c8a92"), "vampire": hexc("d8d2cc"), "corpse": hexc("b8b0a8")}
HAIR = {"blond": hexc("c8a462"), "brown": hexc("5a3a22"), "black": hexc("2a2220"), "red": hexc("8a3a1e"),
        "grey": hexc("9a968e"), "white": hexc("d8d4cc"), "auburn": hexc("7a4024"), "gold": hexc("d8b860")}


def face(p, L):
    """Head base layer: hair, face, beard, long hair for women."""
    skin, hair, beard, eye = L["skin"], L["hair"], L.get("beard"), L.get("eye", hexc("3a5a7a"))
    female = L.get("female", False)
    if p.face == "top":
        return hair
    if p.face == "bottom":
        return shade(skin, 0.8) if beard is None else beard
    if p.face == "back":
        return hair if (p.y < 7 or female) else shade(skin, 0.85)
    if p.side:
        if p.y < 3 or (female and (p.x <= 3 if p.face == "right" else p.x >= 4)):
            return hair
        if beard is not None and p.y >= 5 and (p.x >= 4 if p.face == "right" else p.x <= 3):
            return beard
        if p.y < 5 and (p.x <= 2 if p.face == "right" else p.x >= 5):
            return hair
        return shade(skin, 0.95)
    x, y = p.x, p.y
    if y <= 1:
        return hair
    if y == 2 and (x == 0 or x == 7):
        return hair
    if female and x in (0, 7) and y <= 6:
        return hair
    if y == 3 and x in (1, 2, 5, 6):
        return shade(hair, 0.8)
    if y == 4:
        if x in (1, 6):
            return hexc("eeeeee") if not L.get("red_eyes") else hexc("f0d8d0")
        if x in (2, 5):
            return eye
    if L.get("paint") and y in (3, 4, 5) and x in (0, 1, 2) and not (y == 4 and x in (1, 2)):
        return L["paint"]
    if y == 5 and x in (3, 4):
        return shade(skin, 0.82)
    if beard is not None and y >= 5:
        if y == 6 and x in (3, 4):
            return shade(beard, 0.6)
        if y >= 6 or x in (0, 1, 6, 7):
            return beard
    if y == 6 and x in (2, 3, 4, 5):
        return shade(skin, 0.7) if not female else hexc("b06058")
    if y == 7:
        return shade(skin, 0.9)
    return skin


def stripes(p, a, b, period=4):
    return a if (p.y // 2 + p.x // 2) % period else b


def humanoid(L, rnd):
    """Paints a skin from a look dict (see LOOKS)."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = L["skin"]
    tunic = L["tunic"]
    trim = L.get("trim", shade(tunic, 0.7))
    pants = L.get("pants", hexc("4a4038"))
    boots = L.get("boots", hexc("3a2a1c"))
    belt = L.get("belt", hexc("4a3020"))
    robe = L.get("robe", False)
    cuirass = L.get("cuirass")
    sleeves = L.get("sleeves", "long")
    gloves = L.get("gloves")
    bare = L.get("bare_chest", False)

    def base(p):
        if p.kind == "head":
            return face(p, L)
        if p.kind == "body":
            if not robe and 11.0 <= p.h <= 12.6:
                return hexc("b89a50") if (p.face == "front" and p.x in (3, 4)) else belt
            if bare and p.h < 22:
                if (p.face in ("front", "back")) and (p.x + p.y) % 8 in (0, 1):
                    return L.get("strap", hexc("6a5a46"))
                return shade(skin, 0.97)
            if cuirass is not None and p.h > 12.5:
                c = cuirass
                if p.face == "front" and p.y in (3, 7):
                    return shade(c, 0.7)
                if p.face == "front" and p.x in (0, 7):
                    return shade(c, 0.85)
                return c
            if L.get("sash") and p.face in ("front", "back") and (p.x + p.y) % 9 in (0, 1, 2):
                return L["sash"]
            if L.get("apron") and p.face == "front" and p.y >= 3:
                return L["apron"] if p.x not in (0, 7) else shade(L["apron"], 0.85)
            if L.get("emblem") and p.face == "front" and 2 <= p.y <= 5 and 2 <= p.x <= 5:
                if (p.x + p.y) % 2 == 0 or p.y in (2, 5):
                    return L["emblem"]
            if p.face == "front":
                if p.y <= 1 and p.x in (3, 4) and not robe and not L.get("hood"):
                    return shade(skin, 0.95)  # neckline
                if robe and p.x in (3, 4):
                    return trim  # robe opening
                if p.x in (0, 7):
                    return trim
            if L.get("plaid"):
                return stripes(p, tunic, shade(tunic, 0.78))
            if L.get("rags") and rnd.random() < 0.18:
                return shade(tunic, rnd.choice((0.7, 1.2)))
            return tunic
        if p.kind == "arm":
            if gloves is not None and p.h < 14:
                return gloves
            if p.h < 12.6:
                return limb(p, skin)
            if L.get("bracers") and p.h < 15:
                return L["bracers"]
            if sleeves == "none":
                return limb(p, skin)
            if sleeves == "short" and p.h < 19:
                return limb(p, skin)
            if cuirass is not None and p.h > 20:
                return shade(cuirass, 0.95)
            if L.get("mail_sleeves"):
                return hexc("8a8e92") if (p.x + p.y) % 2 else hexc("6e7276")
            if L.get("plaid"):
                return stripes(p, tunic, shade(tunic, 0.78))
            return tunic if not robe else (trim if p.h < 14 else tunic)
        if p.kind == "leg":
            if robe:
                if p.h < 1.5:
                    return boots
                return tunic if not (p.face == "front" and p.x == 0) else shade(tunic, 0.85)
            if L.get("barefoot") and p.h < 2:
                return limb(p, skin)
            if p.h < 4:
                return boots if p.h > 1 else shade(boots, 0.75)
            if L.get("leg_wraps") and p.y % 3 == 0:
                return L["leg_wraps"]
            return pants if not (p.face == "front" and p.x == 0) else shade(pants, 0.82)
        return skin

    def over(p):
        hood = L.get("hood")
        if p.kind == "head":
            if hood is not None:
                if p.face == "top":
                    return hood
                if p.face in ("back", "left", "right"):
                    return hood if p.y < 8 else None
                if p.face == "front":
                    if p.y <= 1 or (p.x in (0, 7) and p.y <= 6):
                        return hood
                    if L.get("mask") and p.y >= 5:
                        return L["mask"]
                return None
            if L.get("mask") and p.face == "front" and p.y >= 5:
                return L["mask"]
            if L.get("cap") is not None:
                cap = L["cap"]
                if p.face == "top":
                    return cap
                if p.face != "bottom" and p.y <= 1:
                    return cap if p.y == 0 else shade(cap, 0.8)
                if L.get("feather") and p.face == "left" and p.y in (0, 1, 2) and p.x in (5, 6):
                    return L["feather"]
                return None
            if L.get("circlet") and p.face != "top" and p.face != "bottom" and p.y == 2:
                if p.face == "front" and p.x in (3, 4):
                    return hexc("c03030")
                return L["circlet"]
            if L.get("headband") and p.face not in ("top", "bottom") and p.y == 1:
                return L["headband"]
            if L.get("headdress"):
                if p.face == "top" and (p.x + p.y) % 3 == 0:
                    return hexc("e8e0d0")
                if p.face in ("back", "left", "right") and p.y <= 1 and p.x % 2 == 0:
                    return rnd.choice((hexc("e8e0d0"), hexc("2a2a2a"), hexc("8a3a2a")))
            return None
        if p.kind == "body":
            if L.get("fur") is not None and p.h > 21.5 and p.face != "bottom":
                return L["fur"]
            if L.get("cloak") is not None and p.face == "back":
                return L["cloak"] if p.y < 11 else shade(L["cloak"], 0.8)
            if L.get("satchel") and p.face in ("front", "back") and (p.x + p.y) % 9 == 4:
                return hexc("6a4a2a")
            return None
        if p.kind == "arm":
            if L.get("fur") is not None and p.h > 21 and p.face != "bottom" and L.get("pauldrons", True):
                return L["fur"]
            if L.get("plate_pauldrons") and p.h > 20 and p.face != "bottom":
                return L["plate_pauldrons"]
            return None
        if p.kind == "leg":
            if robe and p.h > 2 and p.face not in ("top", "bottom"):
                return tunic if p.h > 2.6 else trim
            if L.get("skirt") is not None and p.h > 7 and p.face not in ("top", "bottom"):
                return L["skirt"] if p.h > 8 else shade(L["skirt"], 0.75)
            if L.get("fur_boots") and 3 <= p.h < 4.5:
                return L["fur_boots"]
            return None
        return None

    paint_humanoid(img, base, over, rnd)
    return img


def limb(p, skin):
    return shade(skin, 0.95 if p.side else 1.0)


# ---------------------------------------------------------------------------------------------------- role looks

def looks(role, female):
    """Look dict for a role and gender; each role is recognizable at a glance."""
    F = female
    hair_m = {"hunter": "brown", "miner": "black", "lumberjack": "red", "bard": "auburn", "priest": "grey", "beggar": "grey",
              "mage": "brown", "adventurer": "blond", "thalmor": "gold", "imperial_soldier": "black", "stormcloak_soldier": "blond",
              "forsworn": "black", "vampire": "black", "necromancer": "black", "assassin": "black", "thug": "brown",
              "courier": "brown", "innkeeper": "brown", "jarl": "blond", "housecarl": "red", "thief": "brown"}
    L = dict(skin=SKIN["nord"], hair=HAIR[hair_m[role]], female=F)
    if not F and role in ("hunter", "lumberjack", "miner", "stormcloak_soldier", "jarl", "housecarl", "thug", "beggar", "innkeeper"):
        L["beard"] = shade(L["hair"], 0.9)
    if F:
        L["hair"] = HAIR[{"brown": "auburn", "black": "brown", "red": "red", "grey": "white", "blond": "blond",
                          "auburn": "red", "gold": "gold"}[hair_m[role]]]
    if role == "hunter":
        L.update(tunic=hexc("6b5236"), trim=hexc("4a3824"), pants=hexc("4e4436"), fur=hexc("8a7a62"), bracers=hexc("3e2c1c"),
                 sleeves="short", hood=None if not F else None, headband=hexc("4a3824"))
    elif role == "miner":
        L.update(tunic=hexc("6e6a62"), apron=hexc("5a4632"), pants=hexc("44403a"), gloves=hexc("3a3028"), sleeves="short",
                 cap=hexc("5a4e40"), skin=shade(SKIN["tan"], 0.95))
    elif role == "lumberjack":
        L.update(tunic=hexc("8a3a2a"), plaid=True, pants=hexc("4a4036"), fur=hexc("7a6a52"), pauldrons=False, gloves=hexc("4a3626"),
                 sleeves="long")
    elif role == "bard":
        L.update(tunic=hexc("6a3a7a") if not F else hexc("8a3044"), trim=hexc("c8a040"), pants=hexc("3e3450"), sash=hexc("c8a040"),
                 cap=hexc("4a2a5a") if not F else None, feather=hexc("e8e0d0"), boots=hexc("5a3a24"))
    elif role == "priest":
        L.update(tunic=hexc("d8ccb0"), trim=hexc("b89040"), robe=True, emblem=hexc("b89040"), boots=hexc("6a5a46"),
                 hood=hexc("c8bca0") if F else None)
    elif role == "beggar":
        L.update(tunic=hexc("7a6c58"), trim=hexc("5a4e40"), pants=hexc("5e5446"), rags=True, barefoot=True, sleeves="short",
                 skin=shade(SKIN["nord"], 0.88), belt=hexc("5a4e40"))
    elif role == "mage":
        L.update(tunic=hexc("2e4a8a"), trim=hexc("c8b070"), robe=True, hood=hexc("28407a"), emblem=hexc("c8b070"),
                 skin=SKIN["pale"] if not F else SKIN["dunmer"], eye=hexc("c03020") if F else hexc("3a5a7a"))
    elif role == "adventurer":
        L.update(tunic=hexc("6a4a2e"), cuirass=hexc("7a5a3a"), pants=hexc("4a3e30"), fur=hexc("9a8a70"), bracers=hexc("4a3020"),
                 fur_boots=hexc("9a8a70"))
    elif role == "thalmor":
        L.update(skin=SKIN["altmer"], hair=HAIR["gold"], tunic=hexc("1c1a1c"), trim=hexc("d4b048"), robe=True, hood=hexc("161416"),
                 emblem=hexc("d4b048"), eye=hexc("c8a030"), boots=hexc("1a1818"), plate_pauldrons=hexc("c8a440"))
        L.pop("beard", None)
    elif role == "imperial_soldier":
        L.update(tunic=hexc("8a2420"), cuirass=hexc("a8783a"), skirt=hexc("8a2420"), pants=hexc("5a2a22"), bracers=hexc("6a4a2a"),
                 boots=hexc("4a3020"), plate_pauldrons=hexc("b08848"), skin=SKIN["tan"] if not F else SKIN["pale"])
    elif role == "stormcloak_soldier":
        L.update(tunic=hexc("2e4a7a"), mail_sleeves=True, sash=hexc("3a5a9a"), pants=hexc("3e3a36"), fur=hexc("a89a80"),
                 bracers=hexc("5a4632"), fur_boots=hexc("a89a80"), skirt=hexc("2e4a7a"))
    elif role == "forsworn":
        L.update(skin=SKIN["tan"], tunic=hexc("6a5440"), bare_chest=not F, strap=hexc("e0d8c4"), pants=hexc("5a4632"),
                 leg_wraps=hexc("d8d0bc"), headdress=True, paint=hexc("8a2a20"), sleeves="none", barefoot=True,
                 fur=hexc("7a6a50"), pauldrons=True)
    elif role == "vampire":
        L.update(skin=SKIN["vampire"], tunic=hexc("2a1418"), trim=hexc("8a1a20"), pants=hexc("1e1a1c"), eye=hexc("d01818"),
                 red_eyes=True, cloak=hexc("3a0e14"), boots=hexc("161214"), hair=HAIR["black"] if not F else HAIR["white"])
        L.pop("beard", None)
    elif role == "necromancer":
        L.update(skin=SKIN["corpse"], tunic=hexc("1e1a22"), trim=hexc("5a2a6a"), robe=True, hood=hexc("141218"),
                 emblem=hexc("6a3a7a"), eye=hexc("7a4aa0"), boots=hexc("141214"))
    elif role == "assassin":
        L.update(tunic=hexc("3a1414"), trim=hexc("1a0c0c"), cuirass=hexc("2a1212"), pants=hexc("1e1414"), hood=hexc("1a0e0e"),
                 mask=hexc("4a1616"), gloves=hexc("1a1010"), boots=hexc("140e0e"), skin=SKIN["dunmer"] if not F else SKIN["pale"],
                 eye=hexc("c02020"))
    elif role == "thug":
        L.update(tunic=hexc("5a4a3a"), cuirass=hexc("6a5034"), pants=hexc("3e3830"), fur=hexc("6a5a46"), gloves=hexc("3a2a1c"),
                 sleeves="short", skin=SKIN["tan"])
    elif role == "courier":
        L.update(tunic=hexc("4a5a6e"), trim=hexc("3a4656"), pants=hexc("4e463c"), satchel=True, cap=hexc("5a3e2a"),
                 boots=hexc("5a3e26"))
    elif role == "innkeeper":
        L.update(tunic=hexc("7a5a3a") if not F else hexc("6a7a4a"), apron=hexc("d8d0bc"), pants=hexc("4a4038"), sleeves="short")
    elif role == "jarl":
        L.update(tunic=hexc("5a1e2a") if not F else hexc("2a4a3a"), trim=hexc("c8a040"), emblem=hexc("c8a040"), pants=hexc("3a2e26"),
                 fur=hexc("d8d0c0"), cloak=hexc("7a6a5a"), circlet=hexc("d8b040"), boots=hexc("3a2a1e"))
    elif role == "housecarl":
        L.update(tunic=hexc("4a4a50"), cuirass=hexc("9aa0a6"), pants=hexc("3e3e42"), fur=hexc("8a7a62"),
                 plate_pauldrons=hexc("a8aeb4"), bracers=hexc("5a5e64"), boots=hexc("3a3a3e"))
    elif role == "thief":
        L.update(tunic=hexc("3a3430"), trim=hexc("2a2622"), pants=hexc("2e2a26"), hood=hexc("2a2622"), mask=hexc("1e1c1a"),
                 gloves=hexc("1e1a16"), boots=hexc("221e1a"), satchel=True,
                 skin=SKIN["tan"] if F else SKIN["nord"])
    if F:
        L.pop("beard", None)
    return L


ROLES = ["hunter", "miner", "lumberjack", "bard", "priest", "beggar", "mage", "adventurer", "thalmor", "imperial_soldier",
         "stormcloak_soldier", "forsworn", "vampire", "necromancer", "assassin", "thug", "courier", "innkeeper", "jarl",
         "housecarl", "thief"]


# ---------------------------------------------------------------------------------------------------- cosmetics

# CosmeticLayer boxes: name -> (u, v, w, h, d)
COSMETIC_BOXES = {
    "hood": (0, 0, 8, 8, 8),
    "torso": (0, 16, 8, 12, 4),
    "skirt_front": (0, 32, 9, 9, 1),
    "skirt_back": (24, 32, 9, 9, 1),
    "cloak": (0, 44, 10, 19, 1),
    "right_pelt": (32, 0, 5, 4, 5),
    "left_pelt": (32, 10, 5, 4, 5),
}


def cosmetic(cid, rnd):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    fns = COSMETICS[cid](rnd)
    for name, (u, v, w, h, d) in COSMETIC_BOXES.items():
        fn = fns.get(name)
        if fn is None:
            continue
        paint_box(img, u, v, w, h, d, fn, rnd, noise=5, edges=True)
    return img


def fur_px(base, rnd, x, y):
    return jitter(shade(base, 1.0 + 0.12 * (((x * 7 + y * 13) % 5) - 2) / 2), rnd, 8)


def imperial_tabard(rnd):
    red, bronze, gold = hexc("8e2420"), hexc("a8783a"), hexc("d4b048")

    def torso(face, x, y, fw, fh):
        if face in ("top", "bottom"):
            return None
        if face in ("left", "right"):
            return bronze if y < 2 else None  # shoulder straps only
        if y < 1 or y == fh - 1 or x in (0, fw - 1):
            return bronze
        # golden dragon emblem on the chest
        if face == "front" and 2 <= y <= 6 and 2 <= x <= 5:
            if (y == 2 and x in (2, 5)) or (y == 3 and 3 <= x <= 4) or (y == 4 and 2 <= x <= 5) or (y == 5 and x in (3, 4)) \
                    or (y == 6 and x in (2, 5)):
                return gold
        return red

    def panel(face, x, y, fw, fh):
        if face in ("front", "back"):
            if y == fh - 1 or x in (0, fw - 1):
                return bronze
            if x in (3, 4, 5) and y < fh - 2:
                return shade(red, 1.12)
            return red
        return shade(red, 0.8)

    return {"torso": torso, "skirt_front": panel, "skirt_back": panel}


def stormcloak_cloak(rnd):
    blue, fur = hexc("2e4a80"), hexc("b0a288")

    def cloak(face, x, y, fw, fh):
        if face in ("front", "back"):
            if y < 3:
                return fur_px(fur, rnd, x, y)
            if y >= fh - 2 and (x % 2 == 0):
                return None  # ragged hem
            return shade(blue, 0.92 + 0.08 * ((x // 2) % 2))
        return shade(blue, 0.8)

    def pelt(face, x, y, fw, fh):
        return fur_px(fur, rnd, x, y)

    return {"cloak": cloak, "right_pelt": pelt, "left_pelt": pelt}


def companions_pelt(rnd):
    grey, dark = hexc("8a8478"), hexc("5a5650")

    def hood(face, x, y, fw, fh):
        # a wolf's head worn as a hood: ears on top, snout over the brow
        if face == "front":
            if y <= 1:
                return fur_px(grey, rnd, x, y)
            if y == 2 and x in (2, 5):
                return hexc("d8c040")  # glass eyes
            if y == 2:
                return fur_px(dark, rnd, x, y)
            if x in (0, 7) and y <= 6:
                return fur_px(grey, rnd, x, y)
            return None
        if face == "top":
            if (x in (1, 6)) and y in (2, 3):
                return dark  # ears
            return fur_px(grey, rnd, x, y)
        if face == "bottom":
            return None
        return fur_px(grey, rnd, x, y) if y < 7 else None

    def cloak(face, x, y, fw, fh):
        if face in ("front", "back"):
            if y >= 12 and (x + y) % 3 == 0:
                return None
            if y >= 14:
                return None if (x in (0, 1, fw - 2, fw - 1)) else fur_px(grey, rnd, x, y)
            return fur_px(grey if (x // 3 + y // 4) % 2 else shade(grey, 0.92), rnd, x, y)
        return fur_px(dark, rnd, x, y)

    def pelt(face, x, y, fw, fh):
        return fur_px(grey, rnd, x, y)

    return {"hood": hood, "cloak": cloak, "right_pelt": pelt, "left_pelt": pelt}


def college_sash(rnd):
    blue, gold = hexc("2c4c94"), hexc("d0b060")

    def torso(face, x, y, fw, fh):
        if face in ("front", "back"):
            if y == 0 and face == "front":
                return gold  # collar
            d = (x + y) if face == "front" else (fw - 1 - x + y)
            if d in (5, 6, 7):
                return blue
            if d in (4, 8):
                return gold
            return None
        if face in ("left", "right") and y in (0, 1):
            return blue
        return None

    def panel(face, x, y, fw, fh):
        # short sash tails over the hip
        if face == "front" and x in (6, 7) and y < 5:
            return blue if x == 6 else gold
        return None

    return {"torso": torso, "skirt_front": panel}


def thieves_hood(rnd):
    leather, dark = hexc("3a3430"), hexc("221e1a")

    def hood(face, x, y, fw, fh):
        if face == "front":
            if y <= 1 or (x in (0, 7) and y <= 6):
                return leather
            if y >= 5:
                return dark  # face mask
            return None
        if face == "bottom":
            return None
        return leather if (x + y) % 5 else shade(leather, 0.85)

    def pelt(face, x, y, fw, fh):
        return leather if (x + y) % 3 else hexc("6a5a3a")  # buckled leather

    return {"hood": hood, "right_pelt": pelt, "left_pelt": pelt}


def brotherhood_hood(rnd):
    black, red = hexc("1a1214"), hexc("7a1418")

    def hood(face, x, y, fw, fh):
        if face == "front":
            if y <= 1 or (x in (0, 7) and y <= 6):
                return black if y > 0 else red
            return None
        if face == "bottom":
            return None
        return black

    def cloak(face, x, y, fw, fh):
        if face == "front":
            return red  # red lining faces the body
        if face == "back":
            if y >= fh - 2 and x % 3 == 1:
                return None
            # faint black hand
            if 5 <= y <= 9 and 3 <= x <= 6 and not (y == 5 and x in (3, 6)):
                return hexc("0e0a0c")
            return black
        return shade(black, 1.2)

    return {"hood": hood, "cloak": cloak}


def bards_cape(rnd):
    purple, gold = hexc("5a2a6e"), hexc("d0b050")

    def cloak(face, x, y, fw, fh):
        if face in ("front", "back"):
            if y < 2 or y == fh - 1:
                return gold
            if x in (0, fw - 1):
                return gold
            return shade(purple, 0.92 + 0.08 * ((x // 2) % 2))
        return purple

    def torso(face, x, y, fw, fh):
        if face == "front" and y == 0:
            return gold  # clasp chain
        if face in ("left", "right") and y < 2:
            return purple
        if face == "top":
            return purple
        return None

    return {"cloak": cloak, "torso": torso}


COSMETICS = {
    "imperial_tabard": imperial_tabard,
    "stormcloak_cloak": stormcloak_cloak,
    "companions_pelt": companions_pelt,
    "college_sash": college_sash,
    "thieves_hood": thieves_hood,
    "brotherhood_hood": brotherhood_hood,
    "bards_cape": bards_cape,
}


def main():
    for i, role in enumerate(ROLES):
        for variant in (0, 1):
            rnd = random.Random(1000 + i * 17 + variant)
            save(humanoid(looks(role, variant == 1), rnd), f"{role}_{variant}.png")
    for j, cid in enumerate(COSMETICS):
        rnd = random.Random(5000 + j)
        save(cosmetic(cid, rnd), "cosmetic", f"{cid}.png")


if __name__ == "__main__":
    main()
