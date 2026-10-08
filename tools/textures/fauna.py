#!/usr/bin/env python3
"""Generates the fauna module's textures (PIL).

Writes PNGs into mod/src/main/resources/assets/skycraft/textures/:
  entity/fauna/deer.png, elk.png                          64x64  (DeerModel UVs; antler block at 24..48 x 36..48)
  entity/fauna/sabre_cat.png, sabre_cat_snowy.png         64x64  (SabreCatModel)
  entity/fauna/bear_brown.png, bear_cave.png, bear_snow.png 64x64 (BearModel)
  entity/fauna/horker.png                                 128x64 (HorkerModel)
  entity/fauna/mudcrab.png                                64x32  (MudcrabModel)
  entity/fauna/mammoth.png                                128x128 (MammothModel)
  entity/fauna/slaughterfish.png                          64x32  (SlaughterfishModel)
  entity/fauna/butterfly_monarch.png, butterfly_blue.png, luna_moth.png   32x32 (InsectModels.Butterfly)
  entity/fauna/dragonfly.png                              32x32  (InsectModels.Dragonfly, translucent wings)
  entity/fauna/torchbug.png, torchbug_glow.png            32x32  (InsectModels.Torchbug + emissive layer)
  item/<ingredient>.png                                   16x16 icons of the animal parts and insect ingredients
  block/<plant>_harvested.png                             16x16 bare stems of picked alchemy plants (arcane plants)

Every model box is painted face by face from the same UV layout as the Java models. Face conventions (Minecraft
box unwrap): top = (u+d, v, w, d), bottom = (u+d+w, v, w, d), right = (u, v+d, d, h), front (-z) = (u+d, v+d, w, h),
left = (u+d+w, v+d, d, h), back = (u+2d+w, v+d, w, h). Painters get the face plus normalized coordinates:
zt (0 = rear .. 1 = front of the box), ht (0 = top .. 1 = bottom), xt (0..1 across the face).
Run:  python3 tools/textures/fauna.py
"""
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEX = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "skycraft", "textures")


# ---------------------------------------------------------------------------------------------------- helpers

def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(c, f):
    return (clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), c[3] if len(c) > 3 else 255)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    a = a if len(a) == 4 else a + (255,)
    b = b if len(b) == 4 else b + (255,)
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(4))


def jitter(c, rnd, amt):
    d = rnd.uniform(-amt, amt)
    return (clamp(c[0] + d + rnd.uniform(-amt * 0.3, amt * 0.3)), clamp(c[1] + d + rnd.uniform(-amt * 0.3, amt * 0.3)),
            clamp(c[2] + d + rnd.uniform(-amt * 0.3, amt * 0.3)), c[3])


def save(img, kind, name):
    p = os.path.join(TEX, kind, name + ".png")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)
    print("wrote", os.path.relpath(p, ROOT))


def box_faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


FACE_LIGHT = {"top": 1.06, "front": 1.0, "right": 0.94, "left": 0.94, "back": 0.9, "bottom": 0.8}


def norm(i, n):
    return 0.5 if n <= 1 else i / (n - 1)


def coords(face, x, y, fw, fh):
    """(zt, ht, xt): position along the box's z (0 rear, 1 front), height (0 top, 1 bottom), across the face."""
    if face == "top":
        return norm(y, fh), 0.0, norm(x, fw)
    if face == "bottom":
        return 1.0 - norm(y, fh), 1.0, norm(x, fw)
    if face == "right":
        return norm(x, fw), norm(y, fh), norm(x, fw)
    if face == "left":
        return 1.0 - norm(x, fw), norm(y, fh), norm(x, fw)
    if face == "front":
        return 1.0, norm(y, fh), norm(x, fw)
    return 0.0, norm(y, fh), norm(x, fw)


def paint(img, rnd, u, v, w, h, d, fn, noise=7, light=True, edges=True):
    """fn(face, zt, ht, xt, x, y, fw, fh) -> rgba / rgb or None (left transparent)."""
    W, H = img.size
    for face, (x0, y0, fw, fh) in box_faces(u, v, w, h, d).items():
        for y in range(fh):
            for x in range(fw):
                px, py = x0 + x, y0 + y
                if px >= W or py >= H:
                    continue
                zt, ht, xt = coords(face, x, y, fw, fh)
                c = fn(face, zt, ht, xt, x, y, fw, fh)
                if c is None:
                    continue
                if len(c) == 3:
                    c = c + (255,)
                if light:
                    c = shade(c, FACE_LIGHT[face])
                if edges and face not in ("top", "bottom") and (x == 0 or x == fw - 1) and fw > 2:
                    c = shade(c, 0.92)
                if noise:
                    c = jitter(c, rnd, noise)
                img.putpixel((px, py), c)


def solid(color):
    return lambda face, zt, ht, xt, x, y, fw, fh: color


def fill_rect(img, rnd, x0, y0, x1, y1, color, noise=6):
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), jitter(color, rnd, noise))


def fur(base, rnd, streak=0.0):
    """Base color with occasional lighter/darker hairs."""
    r = rnd.random()
    if r < 0.12:
        return shade(base, 0.82)
    if r < 0.2 + streak:
        return shade(base, 1.12)
    return base


# ---------------------------------------------------------------------------------------------------- deer & elk

def deer(elk):
    rnd = random.Random(11 if elk else 7)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    coat = hexc("6a4a2e") if elk else hexc("8f5e34")
    dark = hexc("3e2a1a") if elk else hexc("5e3c22")
    belly = hexc("d8c8a0") if elk else hexc("eadcc0")
    rump = hexc("cdb688") if elk else hexc("f2ece0")

    def body(face, zt, ht, xt, x, y, fw, fh):
        if face == "bottom":
            return belly
        if face == "back":
            # pale rump patch
            return rump if 0.15 < xt < 0.85 and ht < 0.8 else coat
        if face == "top":
            return shade(coat, 0.86) if 0.35 < xt < 0.65 else coat  # darker dorsal stripe
        if face == "front":
            return dark if elk else shade(coat, 0.95)
        c = coat
        if ht > 0.75:
            c = mix(coat, belly, (ht - 0.75) * 4)
        if elk and zt > 0.75:
            c = dark  # shaggy shoulders
        if not elk and 0.3 < zt < 0.7 and 0.3 < ht < 0.55 and rnd.random() < 0.06:
            c = mix(c, belly, 0.5)  # faint spots
        return fur(c, rnd)

    paint(img, rnd, 0, 0, 7, 8, 16, body)

    def neck(face, zt, ht, xt, x, y, fw, fh):
        if face == "front":
            return mix(dark, belly, 0.3) if not elk else dark
        return fur(dark if elk else coat, rnd)

    paint(img, rnd, 46, 0, 3, 10, 4, neck)

    def skull(face, zt, ht, xt, x, y, fw, fh):
        c = coat
        if face in ("right", "left"):
            # eye near the front, upper half
            if (face == "right" and x == fw - 2 or face == "left" and x == 1) and y == 1:
                return hexc("101010")
            if ht > 0.7:
                c = mix(coat, belly, 0.6)
        if face == "bottom":
            c = belly
        if face == "front" and ht < 0.4:
            c = shade(coat, 0.9)
        return fur(c, rnd)

    paint(img, rnd, 0, 24, 5, 5, 6, skull)

    def snout(face, zt, ht, xt, x, y, fw, fh):
        if face == "front":
            return hexc("1a1410") if ht < 0.6 else hexc("e8dccc")
        if face == "bottom":
            return hexc("e8dccc")
        return mix(coat, hexc("2a1e14"), 0.3) if zt > 0.7 else coat

    paint(img, rnd, 22, 24, 3, 3, 4, snout)

    def ear(face, zt, ht, xt, x, y, fw, fh):
        if face == "front":
            return hexc("c8a088") if 0 < x < fw - 1 and y > 0 else coat
        return shade(coat, 0.9)

    paint(img, rnd, 36, 24, 2, 3, 1, ear, noise=4)

    def tail(face, zt, ht, xt, x, y, fw, fh):
        if face in ("bottom", "back") or (not elk and face == "front"):
            return rump
        return coat

    paint(img, rnd, 56, 24, 2, 3, 1, tail, noise=4)

    def leg(face, zt, ht, xt, x, y, fw, fh):
        if ht > 0.88:
            return hexc("2a2018")  # hoof
        c = coat if ht < 0.45 else mix(coat, dark if elk else hexc("6a4a2e"), (ht - 0.45) * 2)
        if face in ("top", "bottom"):
            return c
        return fur(c, rnd)

    paint(img, rnd, 0, 36, 2, 12, 2, leg)
    paint(img, rnd, 8, 36, 3, 12, 3, leg)

    # antler block: bone with darker grooves and pale tips
    bone, groove, tip = hexc("cbb88e"), hexc("8a7650"), hexc("f2ead8")
    for y in range(36, 48):
        for x in range(24, 48):
            c = bone
            r = rnd.random()
            if r < 0.15:
                c = groove
            elif r < 0.25:
                c = tip
            img.putpixel((x, y), jitter(c, rnd, 5))
    save(img, "entity/fauna", "elk" if elk else "deer")


# ---------------------------------------------------------------------------------------------------- sabre cats

def sabre_cat(snowy):
    rnd = random.Random(23 if snowy else 21)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    coat = hexc("dcdce2") if snowy else hexc("c8924a")
    stripe = hexc("4e5560") if snowy else hexc("4a2e16")
    belly = hexc("f6f6f8") if snowy else hexc("efe2c8")

    def striped(zt, ht, freq=7.0, phase=0.0):
        s = math.sin((zt * freq + phase) * math.pi * 2 + ht * 1.5)
        return s > 0.72

    def body(face, zt, ht, xt, x, y, fw, fh):
        if face == "bottom":
            return belly
        if face == "front":
            return mix(coat, belly, 0.5)
        if face == "back":
            return coat
        if face == "top":
            if striped(zt, abs(xt - 0.5) * 2, 6.0):
                return stripe
            return shade(coat, 0.92) if 0.4 < xt < 0.6 else coat
        if ht > 0.72:
            return mix(coat, belly, (ht - 0.72) * 3.5)
        if striped(zt, ht, 6.0) and ht < 0.65:
            return stripe
        return fur(coat, rnd)

    paint(img, rnd, 0, 0, 8, 8, 18, body)

    def skull(face, zt, ht, xt, x, y, fw, fh):
        if face == "front":
            # face: eyes, forehead stripes, pale cheeks
            if y == 2 and x in (1, 5):
                return hexc("d8e040") if not snowy else hexc("80c8e8")
            if y == 2 and x in (2, 4):
                return hexc("101010")
            if y <= 1 and x in (2, 4):
                return stripe
            if y >= 4:
                return belly
            return coat
        if face == "bottom":
            return belly
        if face in ("right", "left") and ht > 0.6:
            return mix(coat, belly, 0.6)
        if face in ("right", "left") and striped(zt, ht, 2.0, 0.3) and ht < 0.6:
            return stripe
        if face == "top" and (x % 3 == 1) and zt < 0.8:
            return stripe
        return fur(coat, rnd)

    paint(img, rnd, 0, 26, 7, 6, 6, skull)

    def muzzle(face, zt, ht, xt, x, y, fw, fh):
        if face == "front":
            if y == 0 and 1 <= x <= 2:
                return hexc("2a1a14")  # nose
            return belly
        if face == "top":
            return mix(coat, belly, 0.4)
        return belly

    paint(img, rnd, 26, 26, 4, 3, 3, muzzle)
    paint(img, rnd, 26, 32, 3, 1, 3, solid(belly))
    paint(img, rnd, 46, 26, 1, 4, 1, lambda f, zt, ht, xt, x, y, fw, fh: hexc("f4ecd8") if ht < 0.8 else hexc("fffaf0"), noise=3)

    def ear(face, zt, ht, xt, x, y, fw, fh):
        if face == "front":
            return hexc("e8c8b8") if y > 0 else coat
        if face == "back":
            return stripe if y == 0 else coat
        return coat

    paint(img, rnd, 40, 26, 2, 2, 1, ear, noise=4)

    def tail(face, zt, ht, xt, x, y, fw, fh):
        if face in ("top", "right", "left") and striped(zt, 0, 3.0):
            return stripe
        if face == "bottom":
            return belly
        if face == "back":
            return stripe
        return coat

    paint(img, rnd, 0, 38, 2, 2, 8, tail)
    paint(img, rnd, 20, 38, 2, 2, 7, lambda f, zt, ht, xt, x, y, fw, fh: stripe if (f == "back" or zt < 0.25) else tail(f, zt, ht, xt, x, y, fw, fh))

    def leg(face, zt, ht, xt, x, y, fw, fh):
        if ht > 0.85:
            return belly if face != "bottom" else hexc("3a2a22")  # paws
        if face in ("right", "left") and 0.3 < ht < 0.4:
            return stripe
        return fur(coat, rnd)

    paint(img, rnd, 0, 48, 3, 9, 3, leg)
    paint(img, rnd, 12, 48, 3, 9, 3, leg)
    save(img, "entity/fauna", "sabre_cat_snowy" if snowy else "sabre_cat")


# ---------------------------------------------------------------------------------------------------- bears

def bear(name, coat, muzzle_c, seed):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    dark = shade(coat, 0.7)
    light = shade(coat, 1.18)

    def body(face, zt, ht, xt, x, y, fw, fh):
        if face == "bottom":
            return dark
        c = coat
        if face in ("right", "left"):
            c = mix(coat, dark, max(0.0, ht - 0.6) * 2)
        if face == "top" and zt > 0.6:
            c = light
        return fur(c, rnd, 0.06)

    paint(img, rnd, 0, 0, 10, 10, 18, body, noise=9)
    paint(img, rnd, 0, 28, 8, 3, 7, lambda f, zt, ht, xt, x, y, fw, fh: fur(light, rnd), noise=9)

    def skull(face, zt, ht, xt, x, y, fw, fh):
        if face == "front":
            if y == 2 and x in (1, 5):
                return hexc("0c0806")
            if y >= 4 and 1 <= x <= 5:
                return mix(coat, muzzle_c, 0.5)
        return fur(coat, rnd)

    paint(img, rnd, 30, 28, 7, 7, 5, skull, noise=8)

    def muzzle(face, zt, ht, xt, x, y, fw, fh):
        if face == "front" and y == 0 and 1 <= x <= 2:
            return hexc("141010")
        if face == "front" and y == 2 and x in (1, 2):
            return hexc("6a4a4a")  # mouth line
        return muzzle_c

    paint(img, rnd, 0, 38, 4, 3, 3, muzzle, noise=5)
    paint(img, rnd, 14, 38, 2, 2, 1, lambda f, zt, ht, xt, x, y, fw, fh: dark if f == "front" else coat, noise=5)
    paint(img, rnd, 32, 44, 2, 2, 2, solid(dark), noise=5)

    def leg(face, zt, ht, xt, x, y, fw, fh):
        if ht > 0.9:
            if face == "front" and x % 2 == 0:
                return hexc("e8e0d0")  # claws
            return shade(dark, 0.8)
        return fur(mix(coat, dark, ht * 0.6), rnd)

    paint(img, rnd, 0, 44, 4, 9, 4, leg, noise=8)
    paint(img, rnd, 16, 44, 4, 9, 4, leg, noise=8)
    save(img, "entity/fauna", name)


# ---------------------------------------------------------------------------------------------------- horker

def horker():
    rnd = random.Random(31)
    img = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    skin, wrinkle, belly, blotch = hexc("6e5c50"), hexc("524238"), hexc("8e7c6a"), hexc("8a6a62")

    def body(face, zt, ht, xt, x, y, fw, fh):
        if face == "bottom":
            return belly
        c = skin
        if face in ("right", "left") and ht > 0.7:
            c = mix(skin, belly, (ht - 0.7) * 3)
        if (y + x // 5) % 4 == 0 and rnd.random() < 0.5:
            c = wrinkle  # folds of blubber
        if rnd.random() < 0.05:
            c = blotch
        return c

    paint(img, rnd, 0, 0, 14, 12, 22, body, noise=6)

    def head(face, zt, ht, xt, x, y, fw, fh):
        if face == "front" and y == 2 and x in (2, 7):
            return hexc("0c0a08")  # small eyes
        c = skin
        if face == "bottom":
            c = belly
        if y % 3 == 0 and rnd.random() < 0.4:
            c = wrinkle
        return c

    paint(img, rnd, 72, 0, 10, 9, 8, head)

    def snout(face, zt, ht, xt, x, y, fw, fh):
        base = hexc("9a8676")
        if face == "front":
            if y == 0 and x in (2, 5):
                return hexc("1a1210")  # nostrils
            if y >= 1 and (x + y) % 2 == 0:
                return hexc("4a3a30")  # whisker pits
        return base

    paint(img, rnd, 72, 17, 8, 5, 3, snout, noise=5)
    paint(img, rnd, 94, 17, 1, 6, 1, lambda f, zt, ht, xt, x, y, fw, fh: mix(hexc("e6dcc0"), hexc("fbf6ea"), ht), noise=3)
    paint(img, rnd, 0, 34, 6, 2, 4, lambda f, zt, ht, xt, x, y, fw, fh: wrinkle if f in ("top", "front") else skin, noise=5)
    paint(img, rnd, 20, 34, 4, 2, 6, lambda f, zt, ht, xt, x, y, fw, fh: wrinkle if f == "top" and x % 2 == 0 else skin, noise=5)
    save(img, "entity/fauna", "horker")


# ---------------------------------------------------------------------------------------------------- mudcrab

def mudcrab():
    rnd = random.Random(41)
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    shell, mottle, under, edge = hexc("5e6a46"), hexc("7a7a50"), hexc("b89a60"), hexc("3a4630")

    def body(face, zt, ht, xt, x, y, fw, fh):
        if face == "bottom":
            return under
        if face == "top":
            return mottle if rnd.random() < 0.25 else shell
        if ht > 0.7:
            return under
        if face == "front" and ht > 0.4 and x % 2 == 0:
            return hexc("8a6a3a")  # mouth parts
        return edge if ht < 0.25 else shell

    paint(img, rnd, 0, 0, 10, 4, 8, body)
    paint(img, rnd, 0, 12, 8, 1, 6, lambda f, zt, ht, xt, x, y, fw, fh: (mottle if (x + y) % 3 == 0 else shade(shell, 1.1)) if f == "top" else shell)
    paint(img, rnd, 36, 0, 1, 3, 1, lambda f, zt, ht, xt, x, y, fw, fh: hexc("101010") if (ht < 0.3 or f == "top") else edge, noise=3)

    def claw(face, zt, ht, xt, x, y, fw, fh):
        if face == "bottom":
            return under
        return mottle if rnd.random() < 0.3 else shell

    paint(img, rnd, 28, 12, 3, 3, 4, claw)
    paint(img, rnd, 42, 0, 2, 2, 3, lambda f, zt, ht, xt, x, y, fw, fh: hexc("2a2a1e") if zt > 0.6 else mix(shell, under, 0.4))

    def leg(face, zt, ht, xt, x, y, fw, fh):
        return hexc("4a5236") if (x // 2) % 2 == 0 else hexc("6a6a44")

    paint(img, rnd, 0, 19, 6, 1, 1, leg, noise=4, edges=False)
    save(img, "entity/fauna", "mudcrab")


# ---------------------------------------------------------------------------------------------------- mammoth

def mammoth():
    rnd = random.Random(51)
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    hair, dark, tip = hexc("4c3020"), hexc("2e1c12"), hexc("7a5434")

    # shaggy hair: vertical streaks per column, longer and darker toward the bottom
    streak = {}

    def shag(face, zt, ht, xt, x, y, fw, fh):
        key = (face, fw, fh, x)
        if key not in streak:
            streak[key] = rnd.uniform(-0.12, 0.12)
        c = hair
        s = streak[key]
        c = shade(c, 1.0 + s)
        if ht > 0.65:
            c = mix(c, dark, (ht - 0.65) * 2.2)
        if rnd.random() < 0.08:
            c = tip
        return c

    def body(face, zt, ht, xt, x, y, fw, fh):
        if face == "bottom":
            return dark
        if face == "top":
            return shade(hair, 1.08) if rnd.random() > 0.1 else tip
        if face in ("right", "left", "front", "back") and ht > 0.85 and (x * 7 + y) % 3 == 0:
            return dark  # ragged fringe
        return shag(face, zt, ht, xt, x, y, fw, fh)

    paint(img, rnd, 0, 0, 16, 16, 26, body, noise=8)
    paint(img, rnd, 0, 42, 12, 3, 12, lambda f, zt, ht, xt, x, y, fw, fh: tip if rnd.random() < 0.2 else shade(hair, 1.1), noise=8)

    def head(face, zt, ht, xt, x, y, fw, fh):
        if face == "front":
            if y == 4 and x in (2, 7):
                return hexc("0a0806")
            if y == 3 and x in (2, 7):
                return shade(hair, 0.7)
            c = shade(hair, 1.1) if ht < 0.5 else hair
            return c if rnd.random() > 0.1 else tip
        return shag(face, zt, ht, xt, x, y, fw, fh)

    paint(img, rnd, 48, 42, 10, 11, 8, head, noise=7)
    paint(img, rnd, 84, 0, 4, 7, 1, lambda f, zt, ht, xt, x, y, fw, fh: shade(hair, 0.85) if f == "front" else hair, noise=6)

    def trunk(face, zt, ht, xt, x, y, fw, fh):
        c = mix(hexc("5a3c28"), hexc("6a4a34"), ht)
        if face != "top" and face != "bottom" and y % 2 == 0:
            c = shade(c, 0.85)  # ringed trunk skin
        return c

    paint(img, rnd, 84, 8, 4, 6, 4, trunk, noise=5)
    paint(img, rnd, 100, 8, 3, 6, 3, trunk, noise=5)
    paint(img, rnd, 112, 8, 2, 5, 2, lambda f, zt, ht, xt, x, y, fw, fh: hexc("3a2a20") if ht > 0.8 else trunk(f, zt, ht, xt, x, y, fw, fh), noise=5)
    ivory = lambda f, zt, ht, xt, x, y, fw, fh: mix(hexc("d8caa4"), hexc("f6f0e0"), ht)
    paint(img, rnd, 84, 18, 2, 8, 2, ivory, noise=3)
    paint(img, rnd, 92, 18, 2, 7, 2, lambda f, zt, ht, xt, x, y, fw, fh: mix(hexc("f0e8d4"), hexc("fffcf4"), ht), noise=3)
    paint(img, rnd, 48, 61, 1, 6, 1, lambda f, zt, ht, xt, x, y, fw, fh: dark if ht > 0.6 else hair, noise=4)

    def leg(face, zt, ht, xt, x, y, fw, fh):
        if ht > 0.88:
            if face == "front" and x in (1, 4):
                return hexc("cfc4a8")  # toenails
            return hexc("2a1c14")
        return shag(face, zt, ht * 0.8, xt, x, y, fw, fh)

    paint(img, rnd, 0, 57, 6, 11, 6, leg, noise=7)
    paint(img, rnd, 24, 57, 6, 11, 6, leg, noise=7)
    save(img, "entity/fauna", "mammoth")


# ---------------------------------------------------------------------------------------------------- slaughterfish

def slaughterfish():
    rnd = random.Random(61)
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    back, side, belly, fin = hexc("34442e"), hexc("5e6e4e"), hexc("b0b498"), hexc("8a3424")

    def body(face, zt, ht, xt, x, y, fw, fh):
        if face == "top":
            return back
        if face == "bottom":
            return belly
        c = back if ht < 0.3 else (side if ht < 0.7 else belly)
        if face in ("right", "left") and 0.3 <= ht < 0.7 and (x + y) % 3 == 0:
            c = shade(side, 1.15)  # scales glint
        if face in ("right", "left") and zt > 0.9:
            c = hexc("6a2a20")  # gill slit
        return c

    paint(img, rnd, 0, 0, 3, 5, 8, body, noise=5)

    def head(face, zt, ht, xt, x, y, fw, fh):
        if face in ("right", "left") and y == 1 and (x == fw - 2 if face == "right" else x == 1):
            return hexc("e83020")  # red eye
        if face == "front":
            return hexc("f4f0e0") if y == fh - 1 and x % 2 == 0 else back  # upper teeth
        if face == "top":
            return back
        if face == "bottom":
            return hexc("4a1a14")
        return side if ht > 0.4 else back

    paint(img, rnd, 22, 0, 3, 3, 4, head, noise=4)

    def jaw(face, zt, ht, xt, x, y, fw, fh):
        if face == "top":
            return hexc("f4f0e0") if (x + y) % 2 == 0 and zt > 0.4 else hexc("5a1a14")  # rows of teeth
        if face == "front":
            return hexc("f4f0e0") if y == 0 and x % 2 == 1 else belly
        return belly

    paint(img, rnd, 36, 0, 3, 2, 4, jaw, noise=4)

    def finfn(face, zt, ht, xt, x, y, fw, fh):
        if (x + y) % 2 == 0:
            return shade(fin, 0.75)
        return fin

    paint(img, rnd, 0, 13, 0, 5, 5, lambda f, zt, ht, xt, x, y, fw, fh: None if (zt < 0.3 and 0.3 < ht < 0.7) else finfn(f, zt, ht, xt, x, y, fw, fh), light=False, edges=False)
    paint(img, rnd, 10, 13, 0, 2, 6, lambda f, zt, ht, xt, x, y, fw, fh: (None if (y == 0 and x % 2 == 1) else finfn(f, zt, ht, xt, x, y, fw, fh)), light=False, edges=False)
    paint(img, rnd, 22, 13, 3, 0, 2, finfn, light=False, edges=False)
    save(img, "entity/fauna", "slaughterfish")


# ---------------------------------------------------------------------------------------------------- insects

# wing mask (x: 0 at the body .. 6 at the tip; y: 0 = rear .. 7 = front), 7x8
WING_ROWS = {7: 5, 6: 6, 5: 6, 4: 5, 3: 4, 2: 4, 1: 3, 0: 2}


def in_wing(x, y):
    return x <= WING_ROWS.get(y, -1)


def wing_edge(x, y):
    if not in_wing(x, y):
        return False
    for dx, dy in ((1, 0), (0, 1), (0, -1)):
        if not in_wing(x + dx, y + dy):
            return True
    return False


def paint_wing(img, rnd, x0, y0, fn):
    for y in range(8):
        for x in range(7):
            if in_wing(x, y):
                img.putpixel((x0 + x, y0 + y), fn(x, y))


def butterfly(name, main, vein, border, dots, body_c, seed, moth=False):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    paint(img, rnd, 0, 0, 1, 1, 4, solid(body_c), noise=4, edges=False)
    # antennae (front plane at 10,0 3x2): a V
    for (x, y) in ((10, 0), (12, 0), (11, 1)):
        img.putpixel((x, y), body_c)
    for (x, y) in ((13, 0), (15, 0), (14, 1)):
        img.putpixel((x, y), body_c)

    def wing(x, y):
        if moth:
            if y == 7 and x <= 4:
                return hexc("b07080")  # pink-brown leading edge
            if (x, y) in ((3, 5), (2, 1)):
                return hexc("e8c040")  # eyespots
            if (x, y) in ((4, 5), (3, 4), (2, 5), (3, 6), (1, 1), (2, 2), (3, 1)):
                return hexc("7a8a50")
            c = main if x > 0 else shade(main, 0.85)
            return jitter(c, rnd, 6)
        if wing_edge(x, y):
            return dots if (x + y) % 2 == 0 and x > 1 else border
        if x == 0:
            return shade(border, 1.2)
        if (x == 2 and y >= 2) or (y == 4 and x <= 3) or (x == 4 and y >= 5) or (y == 1 and x == 1):
            return vein
        return jitter(main, rnd, 8)

    paint_wing(img, rnd, 8, 6, wing)
    paint_wing(img, rnd, 15, 6, lambda x, y: shade(wing(x, y), 0.82))
    save(img, "entity/fauna", name)


def dragonfly():
    rnd = random.Random(71)
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    blue, black = hexc("2a6ad8"), hexc("101820")

    def body(face, zt, ht, xt, x, y, fw, fh):
        seg = int((1 - zt) * 9)
        return black if seg % 2 == 1 and zt < 0.8 else blue

    paint(img, rnd, 0, 0, 1, 1, 9, body, noise=5, edges=False)

    def head(face, zt, ht, xt, x, y, fw, fh):
        if face in ("right", "left", "front", "top"):
            return hexc("3ac8b8") if (x + y) % 2 == 0 else hexc("2a8ab8")  # compound eyes
        return black

    paint(img, rnd, 20, 0, 2, 2, 2, head, noise=4, edges=False)
    membrane, veinc = (205, 230, 250, 120), (60, 80, 110, 200)
    for (x0, y0, w) in ((2, 12, 7), (9, 12, 7), (2, 15, 6), (8, 15, 6)):
        for y in range(2):
            for x in range(w):
                c = membrane
                if y == 1 and x < w:
                    c = veinc  # leading edge vein
                if x == w - 2:
                    c = (40, 40, 60, 230)  # stigma
                img.putpixel((x0 + x, y0 + y), c)
    save(img, "entity/fauna", "dragonfly")


def torchbug():
    rnd = random.Random(81)
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    glow = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    shell, abdomen = hexc("2e2218"), hexc("d8d050")

    def thorax(face, zt, ht, xt, x, y, fw, fh):
        if face == "front" and y == 0:
            return hexc("6a5a40")
        return shell

    paint(img, rnd, 0, 0, 2, 2, 3, thorax, noise=4, edges=False)

    def abd(face, zt, ht, xt, x, y, fw, fh):
        if face == "top" and zt > 0.7:
            return shell
        return abdomen if (y + x) % 3 else shade(abdomen, 0.85)

    paint(img, rnd, 0, 5, 3, 3, 3, abd, noise=4, edges=False)
    paint(glow, rnd, 0, 5, 3, 3, 3, lambda f, zt, ht, xt, x, y, fw, fh: None if (f == "top" and zt > 0.7) else hexc("fff59a"),
          noise=6, light=False, edges=False)
    for (x0, y0) in ((15, 0), (19, 0)):
        for y in range(3):
            for x in range(4):
                img.putpixel((x0 + x, y0 + y), jitter(hexc("a89a80") if x < 3 else hexc("7a6a54"), rnd, 5))
    save(img, "entity/fauna", "torchbug")
    save(glow, "entity/fauna", "torchbug_glow")


# ---------------------------------------------------------------------------------------------------- item icons

def icon():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def put(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c)


def line(img, x0, y0, x1, y1, c, width=1):
    steps = max(abs(x1 - x0), abs(y1 - y0)) * 2 + 1
    for i in range(steps + 1):
        t = i / steps
        x = x0 + (x1 - x0) * t
        y = y0 + (y1 - y0) * t
        for dx in range(width):
            put(img, int(round(x)) + dx, int(round(y)), c)


def outline(img, color):
    src = img.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x, y))[3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and src.getpixel((nx, ny))[3] > 200:
                    img.putpixel((x, y), color)
                    break


def antlers(large):
    img = icon()
    bone, dark = hexc("d2c098"), hexc("9a8458")
    if large:
        line(img, 3, 14, 6, 3, bone, 2)
        line(img, 6, 3, 12, 1, bone)
        line(img, 5, 9, 11, 7, bone)
        line(img, 8, 7, 9, 3, bone)
        line(img, 10, 7, 13, 4, bone)
        line(img, 4, 12, 9, 12, bone)
        for (x, y) in ((12, 1), (9, 3), (13, 4), (9, 12)):
            put(img, x, y, hexc("f4eedc"))
        line(img, 3, 14, 4, 11, dark)
    else:
        line(img, 5, 14, 8, 4, bone, 2)
        line(img, 8, 4, 11, 1, bone)
        line(img, 7, 8, 12, 6, bone)
        line(img, 6, 11, 3, 8, bone)
        for (x, y) in ((11, 1), (12, 6), (3, 8)):
            put(img, x, y, hexc("f4eedc"))
        line(img, 5, 14, 6, 12, dark)
    outline(img, hexc("3a2e1a"))
    return img


def fang(points, base, tipc, width=2):
    img = icon()
    n = len(points) - 1
    for i in range(n):
        t = i / max(1, n - 1)
        c = mix(base, tipc, t)
        w = width if i < n * 0.6 else 1
        line(img, points[i][0], points[i][1], points[i + 1][0], points[i + 1][1], c, w)
    outline(img, hexc("4a3e2a"))
    return img


def chitin():
    img = icon()
    rnd = random.Random(5)
    for y in range(3, 14):
        for x in range(2, 14):
            dx, dy = (x - 7.5) / 6.0, (y - 8.5) / 5.5
            if dx * dx + dy * dy <= 1.0 and not (y > 11 and x > 10):
                c = hexc("5e6a46")
                if dy < -0.4:
                    c = hexc("7a8656")
                if rnd.random() < 0.18:
                    c = hexc("8a8a5a")
                if dx * dx + dy * dy > 0.75:
                    c = hexc("3e4a30")
                put(img, x, y, c)
    outline(img, hexc("1e2618"))
    return img


def scales():
    img = icon()
    rnd = random.Random(9)
    for (cx, cy) in ((5, 5), (10, 5), (7, 9), (12, 10), (4, 11), (9, 13)):
        for y in range(cy - 2, cy + 2):
            for x in range(cx - 2, cx + 3):
                if abs(x - cx) + abs(y - cy) <= 2:
                    c = mix(hexc("8a9a80"), hexc("d8e0d0"), (cy + 1 - y) / 4.0)
                    if rnd.random() < 0.15:
                        c = hexc("e8f0e8")
                    put(img, x, y, c)
        put(img, cx, cy + 1, hexc("6a1e18"))
    outline(img, hexc("2a3228"))
    return img


def wing_icon(main, vein, border, dots, moth=False):
    img = icon()
    rnd = random.Random(13)
    # one forewing and hindwing seen from above, body at the left
    for y in range(16):
        for x in range(16):
            fx, fy = (x - 2) / 12.0, (y - 6.5) / 6.0           # forewing
            hx, hy = (x - 2) / 8.5, (y - 12) / 3.8             # hindwing
            fore = fx >= 0 and fx * fx + fy * fy <= 1.0 and y <= 9
            hind = hx >= 0 and hx * hx + hy * hy <= 1.0
            if moth and 4 <= x <= 6 and 13 <= y <= 15:
                hind = True                                    # luna moth tail
            if not (fore or hind):
                continue
            edge = (fore and fx * fx + fy * fy > 0.72) or (hind and not fore and hx * hx + hy * hy > 0.6)
            if moth:
                c = main
                if y == 1 and x < 12:
                    c = hexc("b07080")
                if (x, y) in ((8, 5), (6, 12)):
                    c = hexc("e8c040")
                elif abs(x - 8) + abs(y - 5) == 1 or abs(x - 6) + abs(y - 12) == 1:
                    c = hexc("7a8a50")
            elif edge:
                c = dots if (x + y) % 3 == 0 else border
            elif x % 4 == 1 or (y == 9 and x < 10):
                c = vein
            else:
                c = main
            put(img, x, y, jitter(c, rnd, 6))
    for y in range(3, 15):
        put(img, 1, y, hexc("201a14"))
    outline(img, hexc("1a1410"))
    return img


def dartwing_icon():
    img = icon()
    blue, black = hexc("2a6ad8"), hexc("101820")
    for i in range(10):
        put(img, 3 + i, 12 - i, black if i % 2 else blue)
        put(img, 4 + i, 12 - i, blue)
    put(img, 13, 3, hexc("3ac8b8"))
    put(img, 14, 2, hexc("3ac8b8"))
    put(img, 13, 2, hexc("2a8ab8"))
    wingc = (210, 232, 250, 255)
    line(img, 10, 6, 4, 2, wingc, 1)
    line(img, 10, 6, 15, 10, wingc, 1)
    line(img, 9, 7, 3, 4, wingc, 1)
    line(img, 9, 7, 14, 12, wingc, 1)
    outline(img, hexc("1a2430"))
    return img


def thorax_icon():
    img = icon()
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 5.0, (y - 8.5) / 6.0
            d = dx * dx + dy * dy
            if d <= 1.0:
                c = mix(hexc("fff59a"), hexc("c8b030"), d)
                if y < 5:
                    c = hexc("3a2c1e")
                if y in (8, 11) and d < 0.9:
                    c = shade(c, 0.8)
                put(img, x, y, c)
    put(img, 6, 6, hexc("ffffff"))
    outline(img, hexc("2a1e10"))
    return img


def items():
    save(antlers(False), "item", "small_antlers")
    save(antlers(True), "item", "large_antlers")
    save(fang([(11, 2), (10, 5), (9, 8), (8, 10), (6, 12), (4, 13)], hexc("e8dcc0"), hexc("fffaf0")), "item", "sabre_cat_tooth")
    save(fang([(12, 2), (12, 5), (11, 8), (9, 11), (6, 13), (3, 13)], hexc("d8c8a0"), hexc("f8f2e2"), 3), "item", "horker_tusk")
    save(fang([(14, 1), (12, 3), (9, 5), (6, 8), (4, 11), (3, 14), (5, 15)], hexc("d0c098"), hexc("fcf6e8"), 3), "item", "mammoth_tusk")
    save(chitin(), "item", "mudcrab_chitin")
    save(scales(), "item", "slaughterfish_scales")
    save(wing_icon(hexc("f08a20"), hexc("2a1a10"), hexc("18120e"), hexc("f8f4ec")), "item", "monarch_wing")
    save(wing_icon(hexc("3a8ae8"), hexc("1a3a7a"), hexc("101828"), hexc("e8f0ff")), "item", "blue_butterfly_wing")
    save(wing_icon(hexc("b8f0c0"), hexc("7aa880"), hexc("7aa880"), hexc("7aa880"), moth=True), "item", "luna_moth_wing")
    save(dartwing_icon(), "item", "blue_dartwing")
    save(thorax_icon(), "item", "torchbug_thorax")


# ---------------------------------------------------------------------------------------------------- harvested plants

def stems(name, color, dark, tops, leaves, base=None):
    img = icon()
    for (x0, x1, top) in tops:
        line(img, x0, 15, x1, top, color)
        put(img, x1, top, dark)          # cut end
    for (x, y, d) in leaves:
        put(img, x, y, color)
        put(img, x + d, y, color)
        put(img, x + 2 * d, y - 1, dark)
    if base:
        for (x, y, c) in base:
            put(img, x, y, c)
    save(img, "block", name + "_harvested")


def harvested_plants():
    g, gd = hexc("2e5a2a"), hexc("1c3a1a")
    stems("nightshade", g, gd, [(8, 8, 9), (8, 5, 11), (8, 11, 10)], [(7, 13, -1), (9, 12, 1)])
    g, gd = hexc("5a6a4a"), hexc("3a4a30")
    stems("deathbell", g, gd, [(8, 8, 7)], [(7, 14, -1), (9, 13, 1)])
    pale, dk = hexc("cfe8d0"), hexc("6a9a80")
    stems("nirnroot", pale, dk, [(8, 7, 12), (8, 9, 12)], [],
          [(x, 14, dk) for x in range(6, 11)] + [(x, 13, dk) for x in (6, 10)] + [(x, 13, pale) for x in (7, 8, 9)])
    g, gd = hexc("5a7a6a"), hexc("3a5a4a")
    stems("frost_mirriam", g, gd, [(5, 4, 9), (8, 8, 8), (11, 12, 10)], [(5, 13, -1), (11, 12, 1), (8, 11, 1)])


def main():
    deer(False)
    deer(True)
    sabre_cat(False)
    sabre_cat(True)
    bear("bear_brown", hexc("5e3c22"), hexc("9a7a5a"), 91)
    bear("bear_cave", hexc("3e3430"), hexc("6e625a"), 92)
    bear("bear_snow", hexc("e4e0d8"), hexc("c8c0b4"), 93)
    horker()
    mudcrab()
    mammoth()
    slaughterfish()
    butterfly("butterfly_monarch", hexc("f08a20"), hexc("2a1a10"), hexc("18120e"), hexc("f8f4ec"), hexc("1a1410"), 101)
    butterfly("butterfly_blue", hexc("3a8ae8"), hexc("1a3a7a"), hexc("101828"), hexc("e8f0ff"), hexc("141a24"), 102)
    butterfly("luna_moth", hexc("b8f0c0"), hexc("7aa880"), hexc("7aa880"), hexc("7aa880"), hexc("e8f0d8"), 103, moth=True)
    dragonfly()
    torchbug()
    items()
    harvested_plants()


if __name__ == "__main__":
    main()
