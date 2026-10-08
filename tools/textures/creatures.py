"""Generates the creatures module's entity textures (PIL).

Writes PNGs into mod/src/main/resources/assets/skycraft/textures/entity/:
  bandit/bandit_0..3.png, bandit/bandit_chief.png       64x64 player-layout skins
  draugr/draugr_0..1.png, draugr/draugr_deathlord.png   64x64 (+ draugr_eyes.png glow layer)
  guard/guard_<hold>.png                               64x64, one tunic color per hold
  giant.png                                            64x64 (rendered at 2.5x)
  skeever.png                                          64x32 (SkeeverModel UVs)
  troll/troll.png, troll/frost_troll.png               128x64 (TrollModel UVs)
  dragon/<variant>.png                                 256x128 (DragonModel UVs), procedural scales

Every model box is painted face by face from the same UV layout as the Java models, so details (eyes, belly
plates, teeth, membranes) land on the right faces. Run:  python3 tools/textures/creatures.py
"""
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "skycraft", "textures", "entity")


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


def rgba(c, a=255):
    if c is None:
        return (0, 0, 0, 0)
    if len(c) == 4:
        return c
    return (c[0], c[1], c[2], a)


def box_faces(u, v, w, h, d):
    """UV rectangles of a Minecraft model box (texOffs u,v; size w,h,d). top = visually up, front = -z."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


FACE_LIGHT = {"top": 1.06, "front": 1.0, "right": 0.93, "left": 0.93, "back": 0.88, "bottom": 0.78}


def paint_box(img, u, v, w, h, d, fn, rnd, noise=6, light=True, edges=True):
    """fn(face, x, y, fw, fh) -> color (rgb / rgba) or None (transparent / untouched)."""
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
                c = c[:3]
                if light:
                    c = shade(c, FACE_LIGHT[face])
                if edges and (x == 0 or x == fw - 1 or y == fh - 1) and face not in ("top",):
                    c = shade(c, 0.9)
                if noise:
                    c = jitter(c, rnd, noise)
                img.putpixel((px, py), rgba(c, alpha))


def save(img, *path):
    p = os.path.join(OUT, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)
    print("wrote", os.path.relpath(p, ROOT))


# ---------------------------------------------------------------------------------------------------- humanoid skins

# name: (u, v, w, h, d, height of the part's top in "body pixels" (feet = 0, head top = 32), kind)
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
    """A pixel query: which part/face, local x/y on the face, and the height above the feet in body pixels."""
    __slots__ = ("part", "kind", "face", "x", "y", "fw", "fh", "h", "side")

    def __init__(self, part, kind, face, x, y, fw, fh, h):
        self.part, self.kind, self.face, self.x, self.y, self.fw, self.fh, self.h = part, kind, face, x, y, fw, fh, h
        self.side = face in ("left", "right")


def paint_humanoid(img, base_fn, over_fn, rnd, noise=7):
    for parts, fn, is_over in ((BASE_PARTS, base_fn, False), (OVER_PARTS, over_fn, True)):
        if fn is None:
            continue
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


SKIN = {
    "nord": hexc("e0b79a"), "pale": hexc("e8cbb6"), "tan": hexc("c69072"), "dark": hexc("8a5a3e"),
    "giant": hexc("cdb69c"), "withered": hexc("6f7a78"), "withered2": hexc("5e6b72"),
}
HAIR = {"blond": hexc("c8a462"), "brown": hexc("5a3a22"), "black": hexc("2a2220"), "red": hexc("8a3a1e"),
        "grey": hexc("9a968e"), "white": hexc("d8d4cc")}


def face_features(p, skin, hair, beard=None, eye=hexc("3a5a7a"), brows=True):
    """Head base layer: hair on top/back/upper sides, a face on the front."""
    if p.face == "top":
        return hair
    if p.face == "bottom":
        return shade(skin, 0.8) if beard is None else beard
    if p.face == "back":
        return hair if p.y < 7 else shade(skin, 0.85)
    if p.side:
        if p.y < 3:
            return hair
        if beard is not None and p.y >= 5 and (p.x >= 4 if p.face == "right" else p.x <= 3):
            return beard
        if p.y < 5 and (p.x <= 2 if p.face == "right" else p.x >= 5):
            return hair
        return shade(skin, 0.95)
    # front
    x, y = p.x, p.y
    if y <= 1:
        return hair
    if y == 2 and (x == 0 or x == 7):
        return hair
    if brows and y == 3 and x in (1, 2, 5, 6):
        return shade(hair, 0.8)
    if y == 4:
        if x in (1, 6):
            return hexc("eeeeee")
        if x in (2, 5):
            return eye
    if y == 5 and x in (3, 4):
        return shade(skin, 0.82)  # nose shadow
    if beard is not None and y >= 5:
        if y == 6 and x in (3, 4):
            return shade(beard, 0.6)  # mouth
        if y >= 6 or x in (0, 1, 6, 7):
            return beard
    if y == 6 and x in (2, 3, 4, 5):
        return shade(skin, 0.7)
    if y == 7:
        return shade(skin, 0.9)
    return skin


def limb_skin(p, skin):
    return shade(skin, 0.95 if p.side else 1.0)


# ---- bandits

BANDIT_LOOKS = [
    # skin, hair, beard, leather, pants, hood
    dict(skin="nord", hair="brown", beard="brown", leather=hexc("5a3a22"), dark=hexc("3a2616"), pants=hexc("4a3e34"), hood="hood"),
    dict(skin="tan", hair="black", beard=None, leather=hexc("6b4a2e"), dark=hexc("3e2a18"), pants=hexc("3c3a36"), hood="fur"),
    dict(skin="pale", hair="red", beard="red", leather=hexc("4a3526"), dark=hexc("2c1f14"), pants=hexc("524436"), hood="mask"),
    dict(skin="dark", hair="black", beard="black", leather=hexc("55463a"), dark=hexc("30261e"), pants=hexc("3a3028"), hood="paint"),
]


def bandit_skin(look, rnd, chief=False):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = SKIN[look["skin"]]
    hair = HAIR[look["hair"]]
    beard = HAIR[look["beard"]] if look["beard"] else None
    leather, dark, pants = look["leather"], look["dark"], look["pants"]
    fur = hexc("cfc6b4") if look["hood"] != "fur" else hexc("8a7a62")
    steel = hexc("8c9196")

    def base(p):
        if p.kind == "head":
            c = face_features(p, skin, hair, beard)
            if look["hood"] == "paint" and p.face == "front" and p.y in (3, 4, 5) and p.x in (0, 1, 2) and c == skin:
                return hexc("7a1e1e")  # war paint
            return c
        if p.kind == "body":
            if 11.0 <= p.h <= 13.0:
                if p.face == "front" and p.x in (3, 4):
                    return hexc("b89a50")  # buckle
                return dark
            if chief:
                if p.h > 21:
                    return fur
                if p.face == "front" and (p.x in (0, 7) or p.y in (3, 6)):
                    return shade(steel, 0.75)
                return steel
            # leather cuirass with a diagonal strap
            if p.face in ("front", "back") and (p.x + p.y) % 9 in (0, 1):
                return dark
            if p.face == "front" and p.y in (4, 5) and p.x in (1, 2, 5, 6):
                return shade(leather, 1.2)  # stitched panels
            return leather
        if p.kind == "arm":
            if p.h > 19:
                return steel if chief else leather
            if p.h > 14:
                return limb_skin(p, skin) if not chief else shade(steel, 0.9)
            if p.h > 12.5:
                return dark  # bracer
            return limb_skin(p, skin)
        if p.kind == "leg":
            if p.h < 4:
                return hexc("3a2a1c") if p.h > 1 else hexc("2a1e14")  # boots
            if chief and p.h > 6:
                return shade(steel, 0.85)
            return pants if not (p.face == "front" and p.x == 0) else shade(pants, 0.8)
        return skin

    def over(p):
        if p.kind == "head":
            if look["hood"] in ("hood", "mask") or chief:
                hood = shade(leather, 0.85) if not chief else steel
                if p.face == "top":
                    return hood
                if p.face in ("back", "left", "right"):
                    if chief and p.side and p.y in (0, 1) and p.x in (3, 4):
                        return hexc("d8cfb0")  # little horns
                    return hood if p.y < 7 or not chief else None
                if p.face == "front":
                    if p.y <= 1 or (p.x in (0, 7) and p.y <= 5):
                        return hood
                    if look["hood"] == "mask" and p.y >= 5:
                        return hexc("3a2e28")
                    if chief and p.x in (3, 4) and p.y <= 5:
                        return shade(steel, 0.8)  # nose guard
                return None
            if look["hood"] == "fur":
                if p.face != "bottom" and p.y in (1, 2) and p.face != "top":
                    return fur  # fur headband
            return None
        if p.kind == "body":
            if p.h > 22 and p.face != "bottom":
                return fur  # fur collar
            return None
        if p.kind == "arm":
            if p.h > 21 and p.face != "bottom":
                return fur if look["hood"] in ("fur", "hood") or chief else None  # fur pauldrons
            return None
        if p.kind == "leg":
            if 3 <= p.h < 4.5:
                return shade(fur, 0.9)  # fur boot tops
            return None
        return None

    paint_humanoid(img, base, over, rnd)
    return img


# ---- draugr

def draugr_skin(variant, rnd, deathlord=False):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = SKIN["withered"] if variant == 0 else SKIN["withered2"]
    bone = hexc("b8b098")
    wrap = hexc("4a4238")
    bronze = hexc("7a6a48") if not deathlord else hexc("3a3f44")
    bronze_dark = shade(bronze, 0.65)
    hair = HAIR["white"] if variant == 1 else HAIR["grey"]

    def base(p):
        if p.kind == "head":
            if p.face == "front":
                x, y = p.x, p.y
                if y == 4 and x in (1, 2, 5, 6):
                    return hexc("101418") if x in (1, 6) else hexc("8fd8ff")  # sunken sockets, faint eyes
                if y == 3 and x in (1, 2, 5, 6):
                    return shade(skin, 0.55)
                if y == 5 and x in (3, 4):
                    return hexc("1a1a1a")  # rotted nose
                if y == 6 and 1 <= x <= 6:
                    return bone if x % 2 == 0 else hexc("2a2420")  # bared teeth
                if y == 7 and 2 <= x <= 5:
                    return shade(skin, 0.7)
                if y <= 1:
                    return hair
                c = skin
                if (x * 7 + y * 3) % 5 == 0:
                    c = shade(skin, 0.82)  # withered blotches
                return c
            if p.face == "top" or (p.face == "back" and p.y < 6) or (p.side and p.y < 2):
                return hair
            return shade(skin, 0.92)
        if p.kind == "body":
            if p.face == "front" and 4 <= p.y <= 8 and p.x in (2, 3, 4, 5) and not deathlord:
                return bone if p.y % 2 == 0 else shade(skin, 0.6)  # exposed ribs
            if 11.0 <= p.h <= 12.5:
                return hexc("2c2620")
            return wrap if (p.x + p.y) % 4 else shade(wrap, 0.75)
        if p.kind == "arm":
            if p.h > 20:
                return bronze
            if p.h < 13.5:
                return shade(skin, 0.85)
            return skin if (p.y % 3) else shade(wrap, 0.9)  # wrapped forearms
        if p.kind == "leg":
            if p.h < 3.5:
                return hexc("2e2a26")
            return wrap if (p.y + p.x) % 3 else shade(wrap, 0.8)
        return skin

    def over(p):
        if p.kind == "head":
            if variant == 1 and not deathlord:
                # long white hair hanging at the back and sides
                if p.face == "back" or (p.side and p.y < 7 and (p.x <= 2 if p.face == "right" else p.x >= 5)):
                    return hair
                return None
            helm = bronze
            if p.face == "top":
                return helm
            if p.face in ("back", "left", "right"):
                if deathlord and p.side and p.y <= 2 and p.x in (2, 3, 4, 5):
                    return hexc("d0c8b0") if p.y < 2 else hexc("a09880")  # great horns
                if p.y <= 3 or deathlord:
                    return helm if p.y != 3 else bronze_dark
                return None
            if p.face == "front":
                if p.y <= 2:
                    return helm if p.y < 2 else bronze_dark
                if deathlord and (p.x in (0, 7) or (p.x in (3, 4) and p.y <= 5)):
                    return helm  # face plate edges and nose guard; eyes stay open
                if deathlord and p.y == 7:
                    return bronze_dark
            return None
        if p.kind == "body":
            if deathlord:
                if p.face == "bottom":
                    return None
                return bronze if p.y % 4 else bronze_dark
            if p.h > 21 and p.face != "bottom":
                return bronze  # gorget
            return None
        if p.kind == "arm":
            if (p.h > 20 or (deathlord and p.h > 13)) and p.face != "bottom":
                return bronze if p.y % 3 else bronze_dark
            return None
        if p.kind == "leg":
            if deathlord and p.h > 3 and p.face != "top":
                return bronze if p.y % 4 else bronze_dark
            if 6 < p.h < 11 and p.face == "front" and not deathlord:
                return hexc("5a4a34")  # loincloth flap
            return None
        return None

    paint_humanoid(img, base, over, rnd)
    return img


def draugr_eyes():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    glow, soft = (120, 220, 255, 255), (60, 140, 220, 200)
    # head front face is at (8,8); eye row 4 -> y = 12
    for x in (10, 13):
        img.putpixel((x, 12), glow)
    for x in (9, 14):
        img.putpixel((x, 12), soft)
    return img


# ---- guards

HOLD_COLORS = {
    "whiterun": "c8a23a", "the_rift": "6b3a7a", "eastmarch": "2e4c8a", "the_pale": "5c7a99", "winterhold": "8fa6b8",
    "haafingar": "9a2222", "hjaalmarch": "4e5f3c", "the_reach": "6e2a20", "falkreath": "2f5a2e",
}


def guard_skin(hold, rnd):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    tunic = hexc(HOLD_COLORS[hold])
    trim = shade(tunic, 0.6)
    emblem = mix(tunic, hexc("f0e6c8"), 0.6)
    mail = hexc("8a8e92")
    steel = hexc("9aa0a6")
    leather = hexc("5a3e26")
    skin = SKIN["nord"]

    def base(p):
        if p.kind == "head":
            return face_features(p, skin, HAIR["brown"], HAIR["brown"])
        if p.kind == "body":
            if 11.0 <= p.h <= 12.5:
                return leather if not (p.face == "front" and p.x in (3, 4)) else hexc("b0a070")
            if p.face in ("front", "back"):
                # hold emblem on the chest
                if p.face == "front" and 2 <= p.y <= 7 and 2 <= p.x <= 5:
                    if (p.y in (2, 7) and p.x in (3, 4)) or (3 <= p.y <= 6 and 2 <= p.x <= 5 and not (p.y in (3, 6) and p.x in (2, 5))):
                        return emblem
                if p.x in (0, 7):
                    return trim
                return tunic
            return mail if (p.y + p.x) % 2 else shade(mail, 0.8)
        if p.kind == "arm":
            if p.h > 15:
                return mail if (p.y + p.x) % 2 else shade(mail, 0.8)  # chainmail sleeves
            if p.h > 12.5:
                return leather  # bracers
            return hexc("4a3a2a")  # gloves
        if p.kind == "leg":
            if p.h < 4:
                return hexc("3a2a1c")
            return hexc("4a4038") if p.face != "front" or p.x else hexc("3a322c")
        return skin

    def over(p):
        if p.kind == "head":
            # closed helmet: the face is hidden, only a dark T-shaped visor
            if p.face == "front":
                if p.y == 3 and 1 <= p.x <= 6:
                    return hexc("16181a")
                if p.y in (4, 5) and p.x in (3, 4):
                    return hexc("16181a")
                if p.y == 0 or p.x in (0, 7):
                    return shade(steel, 0.8)
                return steel
            if p.face == "top":
                return steel if (p.x not in (3, 4)) else shade(steel, 1.15)  # crest ridge
            if p.face == "bottom":
                return shade(steel, 0.7)
            return steel if p.y != 7 else shade(steel, 0.75)
        if p.kind == "body":
            if p.h > 22 and p.face != "bottom":
                return hexc("6a5a46")  # leather collar
            return None
        if p.kind == "leg":
            # tunic skirt over the thighs
            if p.h > 7 and p.face != "top" and p.face != "bottom":
                return trim if p.h < 8 else tunic
            return None
        if p.kind == "arm":
            if p.h > 21 and p.face != "bottom":
                return steel  # pauldrons
            return None
        return None

    paint_humanoid(img, base, over, rnd)
    return img


# ---- giant

def giant_skin(rnd):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = SKIN["giant"]
    hair = hexc("8a8478")
    hide = hexc("6a4e32")
    paint = hexc("4a5a8a")

    def base(p):
        if p.kind == "head":
            c = face_features(p, skin, hair, beard=hexc("7a7468"), eye=hexc("2a2a2a"))
            if p.face == "front" and p.y == 3 and p.x in (0, 1, 2, 5, 6, 7) and c == skin:
                return paint
            return c
        if p.kind == "body":
            if p.h < 14.5:
                return hide if not (p.face == "front" and p.x in (3, 4) and p.h < 13) else hexc("8a6a44")
            if p.face == "front" and p.y in (2, 3) and p.x in (1, 2, 5, 6):
                return paint  # blue clan marks on the chest
            if p.face == "front" and p.y == 5 and 2 <= p.x <= 5:
                return shade(skin, 0.85)
            return skin
        if p.kind == "arm":
            if 13.5 < p.h < 15.5:
                return hexc("5a4632")  # wrist wraps
            if p.h > 21 and p.face == "front":
                return paint
            return limb_skin(p, skin)
        if p.kind == "leg":
            if p.h > 7.5:
                return hide  # loincloth
            if p.h < 1.0:
                return shade(skin, 0.75)
            return limb_skin(p, skin)
        return skin

    def over(p):
        if p.kind == "head":
            # long shaggy hair
            if p.face == "back" or p.face == "top":
                return hair
            if p.side and (p.y < 6) and (p.x <= 3 if p.face == "right" else p.x >= 4):
                return hair
            return None
        if p.kind == "leg" and 6.5 < p.h <= 8 and p.face != "top" and p.face != "bottom":
            return shade(hide, 0.8) if (p.x % 2) else None  # ragged hide edge
        return None

    paint_humanoid(img, base, over, rnd, noise=8)
    return img


# ---------------------------------------------------------------------------------------------------- fur helpers

def fur(base, rnd, x, y, strength=0.12):
    stripe = 1.0 - strength * ((x * 3 + y * 7 + rnd.randint(0, 2)) % 4 == 0)
    return shade(base, stripe * rnd.uniform(0.92, 1.06))


# ---- skeever (64x32, SkeeverModel)

def skeever(rnd):
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    furc = hexc("6b5a4a")
    belly = hexc("a08a76")
    pink = hexc("d9a08c")

    def body(face, x, y, fw, fh):
        if face == "bottom":
            return belly
        if face in ("left", "right") and y >= fh - 2:
            return mix(furc, belly, 0.5)
        if face == "top" and x in (2, 3) and y % 3 == 0:
            return shade(furc, 0.7)  # mangy spine
        return fur(furc, rnd, x, y, 0.2)

    paint_box(img, 0, 0, 6, 6, 12, body, rnd)

    def head(face, x, y, fw, fh):
        if face == "front":
            if y == 1 and x in (0, 4):
                return hexc("c02020")  # beady red eyes
            return fur(furc, rnd, x, y)
        if face in ("left", "right") and y == 1 and (x == 3 if face == "right" else x == 1):
            return hexc("c02020")
        if face == "bottom":
            return belly
        return fur(furc, rnd, x, y)

    paint_box(img, 36, 0, 5, 5, 5, head, rnd)

    def snout(face, x, y, fw, fh):
        if face == "front":
            if y == 0 and x == 1:
                return hexc("2a1a1a")  # nose
            if y == 2:
                return hexc("e8e0c8") if x != 1 else hexc("3a1414")  # buck teeth
            return pink
        if face == "bottom":
            return shade(pink, 0.9)
        return mix(furc, pink, 0.4)

    paint_box(img, 36, 10, 3, 3, 3, snout, rnd)

    def ear(face, x, y, fw, fh):
        return pink if face == "front" else shade(furc, 0.9)

    paint_box(img, 48, 10, 2, 2, 1, ear, rnd)

    def tail(face, x, y, fw, fh):
        ring = (y if face not in ("top", "bottom") else y) % 2 == 0
        return shade(pink, 0.85 if ring else 1.0)

    paint_box(img, 0, 18, 2, 2, 8, tail, rnd)
    paint_box(img, 20, 18, 1, 1, 8, tail, rnd)

    def leg(face, x, y, fw, fh):
        if y >= fh - 1 or face == "bottom":
            return pink
        return fur(furc, rnd, x, y)

    paint_box(img, 40, 16, 2, 4, 2, leg, rnd)
    return img


# ---- trolls (128x64, TrollModel)

def troll(rnd, frost=False):
    img = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    furc = hexc("d6cfc0") if not frost else hexc("e6edf2")
    fur_dark = shade(furc, 0.7) if not frost else hexc("aebccc")
    skinc = hexc("7a6a5e") if not frost else hexc("6e7c8c")
    eye = hexc("ffb030") if not frost else hexc("a0e8ff")

    def shag(x, y):
        c = fur(furc, rnd, x, y, 0.18)
        if (x * 5 + y * 3) % 7 == 0:
            c = fur_dark
        return c

    def body(face, x, y, fw, fh):
        if face == "front" and 6 <= y <= 17 and 4 <= x <= 13:
            return mix(skinc, furc, 0.25) if (x + y) % 5 else shade(skinc, 0.85)  # bare chest & belly
        if face == "bottom":
            return skinc
        return shag(x, y)

    paint_box(img, 0, 0, 18, 22, 12, body, rnd, noise=8)

    def head(face, x, y, fw, fh):
        if face == "front":
            if y <= 1:
                return shag(x, y)
            if y == 3 and x in (4, 5):
                return eye  # the third eye
            if y == 4 and x in (2, 7):
                return eye
            if y == 4 and x in (1, 3, 6, 8):
                return shade(skinc, 0.5)
            if y == 6 and x in (4, 5):
                return hexc("2a1e1a")  # nostrils
            if y == 8:
                return hexc("e8e2cc") if x in (1, 3, 6, 8) else hexc("3a1a14")  # fangs
            if y == 9:
                return hexc("3a1a14") if 2 <= x <= 7 else skinc
            return skinc
        if face == "top" or face == "back":
            return shag(x, y)
        if face == "bottom":
            return shade(skinc, 0.8)
        return shag(x, y) if y < 4 else skinc

    paint_box(img, 60, 0, 10, 10, 8, head, rnd, noise=7)

    def arm(face, x, y, fw, fh):
        if y >= fh - 4:
            return skinc if y < fh - 1 else hexc("2a2420")  # huge hands, dark claws
        return shag(x, y)

    paint_box(img, 60, 18, 6, 26, 6, arm, rnd, noise=8)

    def leg(face, x, y, fw, fh):
        if y >= fh - 2 or face == "bottom":
            return skinc
        return shag(x, y)

    paint_box(img, 84, 18, 6, 16, 6, leg, rnd, noise=8)
    return img


# ---------------------------------------------------------------------------------------------------- dragons

DRAGON_VARIANTS = {
    # base scale color, highlight, belly, membrane, bone (horns/spikes), eye
    "dragon": dict(base="6b5a3a", hi="8e7a4e", belly="b8a478", mem="5a4632", bone="d8cfb0", eye="f0c030"),
    "blood": dict(base="7a2a22", hi="a8443a", belly="c08a6a", mem="5a1e1a", bone="d0c4a4", eye="ffd040"),
    "frost": dict(base="7d97ac", hi="b0c8d8", belly="dce8ee", mem="5a7288", bone="eef2f4", eye="80e0ff"),
    "elder": dict(base="3a3a36", hi="5a5850", belly="7a766a", mem="2e2c2a", bone="bab2a0", eye="ff9020"),
    "ancient": dict(base="1c1a1a", hi="3a3426", belly="8a7440", mem="241e1a", bone="d8b040", eye="ffe060"),
}


def scale_at(px, py, pal, rnd):
    """Overlapping scale pattern in texture space: rows of 4x3 scales, offset every other row."""
    row = py // 3
    off = 2 if row % 2 else 0
    sx = (px + off) % 4
    sy = py % 3
    inner = sx in (1, 2)
    # rounded scale: bright crown, darker flanks, deep shadow where the next row overlaps its edges
    shape = {
        0: (pal["hi"], shade(pal["base"], 0.82)),
        1: (pal["base"], shade(pal["base"], 0.7)),
        2: (shade(pal["base"], 0.8), shade(pal["base"], 0.5)),
    }
    c = shape[sy][0 if inner else 1]
    if sy == 0 and sx == 1:
        c = mix(pal["hi"], (255, 255, 255), 0.12)  # glint
    if "ancient" == pal.get("name") and (row + (px + off) // 4) % 7 == 0 and sy == 0:
        c = hexc("c09030")     # gold-tipped scales
    return jitter(c, rnd, 5)


def belly_at(px, py, pal, rnd):
    c = pal["belly"] if py % 3 else shade(pal["belly"], 0.72)  # horizontal plates
    return jitter(c, rnd, 4)


def dragon_texture(name, rnd):
    raw = DRAGON_VARIANTS[name]
    pal = {k: hexc(v) for k, v in raw.items()}
    pal["name"] = name
    img = Image.new("RGBA", (256, 128), (0, 0, 0, 0))

    def scaled(u, v, w, h, d, belly_bottom=True, belly_sides=0, spine=False):
        def f(face, x, y, fw, fh):
            px, py = (box_faces(u, v, w, h, d)[face][0] + x, box_faces(u, v, w, h, d)[face][1] + y)
            if face == "bottom" and belly_bottom:
                return belly_at(px, py, pal, rnd)
            if face in ("left", "right") and belly_sides and y >= fh - belly_sides:
                return belly_at(px, py, pal, rnd)
            if face == "top" and spine and x in (fw // 2 - 1, fw // 2):
                return shade(pal["base"], 0.75)
            return scale_at(px, py, pal, rnd)
        paint_box(img, u, v, w, h, d, f, rnd, noise=0, light=True, edges=True)

    # body: belly plates underneath and on the lower flanks
    scaled(0, 0, 14, 12, 28, belly_sides=3, spine=True)
    # neck: belly on the bottom
    scaled(84, 0, 6, 6, 7, belly_sides=1)

    # head with eyes on both sides
    def head(face, x, y, fw, fh):
        fx, fy = box_faces(110, 0, 8, 6, 10)[face][:2]
        if face == "right" and y == 2 and x in (7, 8):
            return pal["eye"] if x == 7 else hexc("101010")
        if face == "left" and y == 2 and x in (1, 2):
            return pal["eye"] if x == 2 else hexc("101010")
        if face in ("left", "right") and y == 1 and (x in (6, 7, 8, 9) if face == "right" else x in (0, 1, 2, 3)):
            return shade(pal["base"], 0.5)  # heavy brow
        if face == "bottom":
            return hexc("6a1e1e")  # roof of the mouth behind the snout
        return scale_at(fx + x, fy + y, pal, rnd)

    paint_box(img, 110, 0, 8, 6, 10, head, rnd, noise=0)

    def snout(face, x, y, fw, fh):
        fx, fy = box_faces(146, 0, 6, 4, 8)[face][:2]
        if face == "bottom":
            if x in (0, fw - 1):
                return hexc("ece6d0") if y % 2 == 0 else hexc("c8c0a8")  # upper teeth
            return hexc("7a2424")
        if face == "front":
            if y == 1 and x in (1, 4):
                return hexc("140c0c")  # nostrils
            if y == fh - 1:
                return hexc("ece6d0") if x % 2 == 0 else shade(pal["base"], 0.7)
        if face in ("left", "right") and y == fh - 1:
            return hexc("ece6d0") if x % 2 == 0 else shade(pal["base"], 0.7)  # fangs along the lip
        return scale_at(fx + x, fy + y, pal, rnd)

    paint_box(img, 146, 0, 6, 4, 8, snout, rnd, noise=0)

    def jaw(face, x, y, fw, fh):
        fx, fy = box_faces(84, 16, 6, 2, 16)[face][:2]
        if face == "top":
            if x in (0, fw - 1):
                return hexc("ece6d0") if y % 2 == 0 else hexc("c8c0a8")  # lower teeth
            return hexc("8a2a2a") if x not in (2, 3) else hexc("a83a3a")  # tongue
        if face == "bottom":
            return belly_at(fx + x, fy + y, pal, rnd)
        return scale_at(fx + x, fy + y, pal, rnd)

    paint_box(img, 84, 16, 6, 2, 16, jaw, rnd, noise=0)

    def bone(face, x, y, fw, fh):
        t = (x + y) / max(1, fw + fh)
        return shade(pal["bone"], 1.0 - 0.35 * t) if face != "front" else shade(pal["bone"], 0.8)

    paint_box(img, 174, 0, 2, 2, 8, bone, rnd, noise=4)   # horns
    paint_box(img, 194, 0, 1, 1, 6, bone, rnd, noise=4)   # small horns
    paint_box(img, 208, 0, 2, 3, 2, bone, rnd, noise=4)   # dorsal spikes

    # wing bones: scaled, darker
    def wingbone(u, v, w, h, d):
        def f(face, x, y, fw, fh):
            fx, fy = box_faces(u, v, w, h, d)[face][:2]
            c = scale_at(fx + x, fy + y, pal, rnd)
            if face == "front" and x == 0:
                return pal["bone"]  # wing claw
            return shade(c, 0.9)
        paint_box(img, u, v, w, h, d, f, rnd, noise=0)

    wingbone(128, 16, 20, 3, 4)
    wingbone(128, 24, 22, 2, 2)

    # membranes: leathery, veined, with a scalloped (transparent) trailing edge
    def membrane(u, v, w, d, tip):
        for face in ("top", "bottom"):
            x0, y0, fw, fh = box_faces(u, v, w, 0, d)[face]
            for y in range(fh):
                for x in range(fw):
                    # distance from the leading edge (y = 0 nearest the bone after mirroring either way)
                    along = x / max(1, fw - 1)
                    depth = y / max(1, fh - 1)
                    # trailing edge: scallops between the fingers, the tip tapers
                    limit = 1.0 - 0.18 * abs(math.sin(along * math.pi * 3))
                    if tip:
                        limit -= 0.55 * (1.0 - along) if face == "top" else 0.55 * along
                    if depth > limit:
                        continue
                    c = pal["mem"]
                    if (x % 7 == 0) or abs(depth - along * 0.8) < 0.03:
                        c = shade(pal["mem"], 0.7)  # veins
                    c = shade(c, 1.1 - 0.25 * depth)
                    if depth > limit - 0.06:
                        c = shade(c, 0.75)  # darker rim
                    img.putpixel((x0 + x, y0 + y), rgba(jitter(c, rnd, 5)))

    membrane(0, 40, 20, 24, tip=False)
    membrane(88, 40, 22, 26, tip=True)

    # tail segments
    for u, w, h in ((0, 8, 6), (32, 7, 5), (62, 6, 4), (90, 5, 4), (116, 4, 3), (140, 3, 2)):
        scaled(u, 66, w, h, 8, belly_sides=1, spine=True)

    # tail spade fin
    for face in ("top", "bottom"):
        x0, y0, fw, fh = box_faces(162, 66, 8, 0, 6)[face]
        for y in range(fh):
            for x in range(fw):
                cx = abs(x - (fw - 1) / 2) / (fw / 2)
                if cx > 1.0 - y / (fh * 1.3):
                    continue
                img.putpixel((x0 + x, y0 + y), rgba(jitter(shade(pal["bone"], 0.75), rnd, 6)))

    # legs and claws
    scaled(0, 80, 5, 7, 6, belly_sides=0)
    scaled(22, 80, 4, 4, 4, belly_sides=0)

    def claw(face, x, y, fw, fh):
        if face == "front":
            return pal["bone"] if x % 2 == 0 else hexc("1a1a1a")
        return shade(pal["base"], 0.8)

    paint_box(img, 38, 80, 4, 1, 3, claw, rnd, noise=3)
    return img


# ---------------------------------------------------------------------------------------------------- main

def main():
    for i, look in enumerate(BANDIT_LOOKS):
        save(bandit_skin(look, random.Random(100 + i)), "bandit", "bandit_%d.png" % i)
    chief = dict(skin="nord", hair="blond", beard="blond", leather=hexc("4a3526"), dark=hexc("2a1e14"),
                 pants=hexc("3a3430"), hood="hood")
    save(bandit_skin(chief, random.Random(150), chief=True), "bandit", "bandit_chief.png")

    for i in range(2):
        save(draugr_skin(i, random.Random(200 + i)), "draugr", "draugr_%d.png" % i)
    save(draugr_skin(0, random.Random(250), deathlord=True), "draugr", "draugr_deathlord.png")
    save(draugr_eyes(), "draugr", "draugr_eyes.png")

    for i, hold in enumerate(HOLD_COLORS):
        save(guard_skin(hold, random.Random(300 + i)), "guard", "guard_%s.png" % hold)

    save(giant_skin(random.Random(400)), "giant.png")
    save(skeever(random.Random(500)), "skeever.png")
    save(troll(random.Random(600)), "troll", "troll.png")
    save(troll(random.Random(601), frost=True), "troll", "frost_troll.png")
    for i, name in enumerate(DRAGON_VARIANTS):
        save(dragon_texture(name, random.Random(700 + i)), "dragon", "%s.png" % name)


if __name__ == "__main__":
    main()
