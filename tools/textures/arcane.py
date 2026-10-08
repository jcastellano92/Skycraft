#!/usr/bin/env python3
"""Pixel-art textures for the arcane sub-module (soul gems, alchemy ingredients & plants, stations, effect icons).

Writes PNGs into mod/src/main/resources/assets/skycraft/textures/{item,block,mob_effect}/.
Run from anywhere:  python3 tools/textures/arcane.py
"""
import math
import os
import random

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
TEX = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "skycraft", "textures")


def out(kind, name, img):
    d = os.path.join(TEX, kind)
    os.makedirs(d, exist_ok=True)
    img.save(os.path.join(d, name + ".png"))


def new(size=16):
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(4))


def shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), c[3])


def put(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def blend(img, x, y, c):
    """Alpha-composite one pixel."""
    if not (0 <= x < img.width and 0 <= y < img.height):
        return
    base = img.getpixel((x, y))
    a = c[3] / 255.0
    if base[3] == 0:
        img.putpixel((x, y), c)
        return
    r = tuple(int(base[i] * (1 - a) + c[i] * a) for i in range(3))
    img.putpixel((x, y), r + (max(base[3], c[3]),))


def outline(img, color):
    """Adds a 1px outline around opaque pixels."""
    w, h = img.size
    src = img.copy()
    for y in range(h):
        for x in range(w):
            if src.getpixel((x, y))[3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and src.getpixel((nx, ny))[3] > 40:
                    img.putpixel((x, y), color)
                    break


def in_poly(x, y, pts):
    inside = False
    n = len(pts)
    j = n - 1
    for i in range(n):
        xi, yi = pts[i]
        xj, yj = pts[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi + 1e-9) + xi:
            inside = not inside
        j = i
    return inside


# ============================================================================ soul gems

GEM_SIZES = {"petty": (6, 8), "lesser": (8, 10), "common": (9, 12), "greater": (11, 13), "grand": (13, 15), "black": (13, 15)}


def gem_palette(kind, filled):
    if kind == "black":
        if filled:
            return dict(rim=hexc("1a0614"), dark=hexc("4a0f3a"), mid=hexc("8a1f6a"), light=hexc("d0409a"), hi=hexc("ffb0e0"), core=hexc("ff5ac0"), alpha=255)
        return dict(rim=hexc("0c0610"), dark=hexc("1e1426"), mid=hexc("33243f"), light=hexc("54426a"), hi=hexc("9c8cb4"), core=None, alpha=245)
    if filled:
        return dict(rim=hexc("3a1a6a"), dark=hexc("6a3ab8"), mid=hexc("a070f0"), light=hexc("d4b4ff"), hi=hexc("ffffff"), core=hexc("f0e0ff"), alpha=255)
    return dict(rim=hexc("4a3a6a", 230), dark=hexc("7a6a9a", 150), mid=hexc("a898c8", 140), light=hexc("d8d0ee", 170), hi=hexc("ffffff", 230), core=None, alpha=150)


def draw_gem(img, cx, cy, w, h, pal, filled):
    left, right = cx - w / 2.0, cx + w / 2.0
    top, bot = cy - h / 2.0, cy + h / 2.0
    shoulder = h * 0.32
    pts = [(cx, top), (right, top + shoulder), (right, bot - shoulder * 0.9), (cx, bot), (left, bot - shoulder * 0.9), (left, top + shoulder)]
    for y in range(img.height):
        for x in range(img.width):
            px, py = x + 0.5, y + 0.5
            if not in_poly(px, py, pts):
                continue
            # facets: left light, centre mid, right dark; upper crown lighter
            rel = (px - cx) / (w / 2.0)
            if rel < -0.35:
                c = pal["light"]
            elif rel > 0.35:
                c = pal["dark"]
            else:
                c = pal["mid"]
            if py < top + shoulder:
                c = mix(c, pal["hi"], 0.35)
            if py > bot - shoulder * 0.9:
                c = shade(c, 0.8)
            a = pal["alpha"] if c[3] == 255 else c[3]
            if filled and pal["core"] is not None:
                d = math.hypot((px - cx) / (w / 2.0), (py - cy) / (h / 2.0))
                c = mix(c, pal["core"], max(0.0, 0.85 - d) * 0.9)
                a = 255
            img.putpixel((x, y), c[:3] + (a,))
    outline(img, pal["rim"])
    # sparkle highlight
    hx, hy = int(cx - w * 0.22), int(top + shoulder * 0.9)
    put(img, hx, hy, pal["hi"])
    if w >= 9:
        put(img, hx, hy + 1, mix(pal["hi"], pal["light"], 0.5))
        put(img, hx + 1, hy, mix(pal["hi"], pal["light"], 0.5))


def sparkles(img, color, spots):
    for (x, y, big) in spots:
        if img.getpixel((x, y))[3] > 0:
            continue
        put(img, x, y, color)
        if big:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and img.getpixel((nx, ny))[3] == 0:
                    put(img, nx, ny, color[:3] + (130,))


def soul_gems():
    for kind, (w, h) in GEM_SIZES.items():
        for filled in (False, True):
            img = new()
            pal = gem_palette(kind, filled)
            draw_gem(img, 8, 8.5, w, h, pal, filled)
            if filled:
                glow = hexc("ff7ad0") if kind == "black" else hexc("e8d8ff")
                sparkles(img, glow, [(2, 3, True), (13, 4, False), (12, 13, True), (3, 12, False)])
            out("item", "soul_gem_" + kind + ("_filled" if filled else ""), img)


def azuras_star():
    for filled in (False, True):
        img = new()
        cx, cy = 7.5, 7.5
        gold_d, gold, gold_l = hexc("7a5414"), hexc("c8952c"), hexc("f4d77a")
        # eight-pointed star frame
        for y in range(16):
            for x in range(16):
                dx, dy = x - cx, y - cy
                r = math.hypot(dx, dy)
                ang = math.atan2(dy, dx)
                spike = 4.2 + 3.4 * max(0.0, math.cos(ang * 4)) ** 6
                if 3.3 < r < spike:
                    c = gold_l if dx + dy < -2 else gold if dx + dy < 3 else gold_d
                    img.putpixel((x, y), c)
        # central gem
        core = new()
        pal = gem_palette("grand", filled)
        pal = dict(pal)
        if filled:
            pal.update(dark=hexc("2a6ab8"), mid=hexc("60b0f8"), light=hexc("b8e8ff"), core=hexc("f0fbff"), rim=hexc("143060"))
        else:
            pal.update(dark=hexc("4a6a8a", 170), mid=hexc("8ab0c8", 160), light=hexc("c8e0ee", 180), rim=hexc("2a3a50", 230))
        draw_gem(core, 8, 8, 6, 7, pal, filled)
        for y in range(16):
            for x in range(16):
                p = core.getpixel((x, y))
                if p[3] > 0:
                    img.putpixel((x - 1 if x > 0 else x, y - 1 if y > 0 else y), p)
        outline(img, hexc("3a2408"))
        if filled:
            sparkles(img, hexc("dff4ff"), [(1, 2, True), (14, 13, True), (14, 2, False)])
        out("item", "azuras_star" + ("_filled" if filled else ""), img)


# ============================================================================ ingredients

def heap(img, base, light, dark, speck, seed, glitter=None):
    rnd = random.Random(seed)
    for y in range(16):
        for x in range(16):
            # mound: ellipse bottom half
            dx = (x + 0.5 - 8) / 6.5
            dy = (y + 0.5 - 13.5) / 7.0
            if dx * dx + dy * dy <= 1.0 and y <= 13:
                t = rnd.random()
                c = base
                if x + (13 - y) * 0.6 < 9:
                    c = light if t < 0.55 else base
                elif x > 10:
                    c = dark if t < 0.6 else base
                elif t < 0.2:
                    c = dark
                img.putpixel((x, y), c)
    for _ in range(9):
        x, y = rnd.randint(3, 12), rnd.randint(8, 13)
        if img.getpixel((x, y))[3] > 0:
            img.putpixel((x, y), speck)
    if glitter:
        for _ in range(4):
            x, y = rnd.randint(4, 11), rnd.randint(7, 12)
            if img.getpixel((x, y))[3] > 0:
                img.putpixel((x, y), glitter)
    # bottom shadow line
    for x in range(3, 13):
        if img.getpixel((x, 13))[3] > 0:
            img.putpixel((x, 13), shade(dark, 0.8))
    outline(img, shade(dark, 0.45)[:3] + (255,))


def ingredients():
    img = new()
    heap(img, hexc("dcdcd4"), hexc("f8f8f2"), hexc("a8a8a0"), hexc("ffffff"), 1, hexc("c8d8ff"))
    out("item", "salt_pile", img)

    img = new()
    heap(img, hexc("3a2050"), hexc("6a3a90"), hexc("1c0c2c"), hexc("b070ff"), 2, hexc("e8c8ff"))
    out("item", "void_salts", img)

    img = new()
    heap(img, hexc("c84818"), hexc("f08a2a"), hexc("7a1c08"), hexc("ffd040"), 3, hexc("fff4a0"))
    out("item", "fire_salts", img)

    img = new()
    heap(img, hexc("9cc8e8"), hexc("dff2ff"), hexc("5a8ab0"), hexc("ffffff"), 4, hexc("e8fbff"))
    out("item", "frost_salts", img)

    img = new()
    heap(img, hexc("6a6466"), hexc("948c90"), hexc("3a3436"), hexc("8a1010"), 5, hexc("c02020"))
    out("item", "vampire_dust", img)

    # bear claws: three curved, tapering ivory claws on a fur tuft
    img = new()
    fur, fur_l = hexc("4a3020"), hexc("6e4c34")
    for y in range(11, 16):
        for x in range(2, 15):
            if (x - 8.0) ** 2 / 36 + (y - 14.0) ** 2 / 5 <= 1:
                img.putpixel((x, y), fur_l if (x * 3 + y) % 4 == 0 else fur)
    ivory, ivory_l, ivory_d, tip = hexc("e8dcb8"), hexc("fff6dc"), hexc("b09c70"), hexc("5a4a38")
    for bx in (4.5, 8.0, 11.5):
        samples = []
        for i in range(41):
            t = i / 40.0
            # curve from the base upwards, hooking to the right
            x = bx - 1.0 + 3.2 * t * t
            y = 12.5 - 10.0 * t + 1.5 * t * t * t
            samples.append((x, y, 1.6 * (1 - t) + 0.35))
        for y in range(16):
            for x in range(16):
                for (cx, cy, r) in samples:
                    d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                    if d <= r:
                        t = (12.5 - cy) / 10.0
                        c = ivory_l if x + 0.5 < cx - 0.3 else ivory_d if x + 0.5 > cx + 0.4 else ivory
                        if t > 0.8:
                            c = tip
                        img.putpixel((x, y), c)
                        break
    outline(img, hexc("1c120a"))
    out("item", "bear_claws", img)

    # hawk feather: curved vane along a diagonal shaft, barred brown with a pale tip
    img = new()
    ax0, ay0, ax1, ay1 = 2.5, 14.0, 13.5, 2.0
    L = math.hypot(ax1 - ax0, ay1 - ay0)
    ux, uy = (ax1 - ax0) / L, (ay1 - ay0) / L
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5 - ax0, y + 0.5 - ay0
            t = (px * ux + py * uy) / L
            d = px * -uy + py * ux
            if t < 0 or t > 1:
                continue
            half = 0 if t < 0.15 else 2.7 * math.sin(math.pi * min(1.0, (t - 0.15) / 0.85)) ** 0.6
            if abs(d) <= 0.55:
                img.putpixel((x, y), hexc("f0e6d0") if t < 0.9 else hexc("d8ccb0"))
            elif abs(d) <= half:
                band = int(t * 9) % 2 == 0
                c = hexc("7a4a22") if band else hexc("c8a070")
                if t > 0.82:
                    c = hexc("f4ece0")
                if d > 0:
                    c = shade(c, 0.82)
                img.putpixel((x, y), c)
    outline(img, hexc("2a1a0a"))
    out("item", "hawk_feather", img)


# ============================================================================ plants (cross models)

def stem(img, x0, y0, x1, y1, c):
    steps = max(abs(x1 - x0), abs(y1 - y0)) + 1
    for i in range(steps):
        t = i / max(1, steps - 1)
        put(img, int(round(x0 + (x1 - x0) * t)), int(round(y0 + (y1 - y0) * t)), c)


def leaf(img, x, y, dx, c, c2):
    put(img, x, y, c)
    put(img, x + dx, y, c)
    put(img, x + 2 * dx, y - 1, c2)


def plants():
    # Nightshade: dark stems, drooping purple star flowers with yellow centres
    img = new()
    g, gd = hexc("2e5a2a"), hexc("1c3a1a")
    stem(img, 8, 15, 8, 5, g)
    stem(img, 8, 11, 4, 7, gd)
    stem(img, 8, 10, 12, 6, gd)
    for (lx, ly, d) in ((7, 13, -1), (9, 12, 1), (7, 9, -1)):
        leaf(img, lx, ly, d, g, gd)
    for (fx, fy) in ((8, 4), (4, 6), (12, 5)):
        for dx, dy in ((0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)):
            put(img, fx + dx, fy + dy, hexc("6a2a9a") if (dx or dy) else hexc("f0d040"))
        put(img, fx - 1, fy + 1, hexc("4a1a70"))
        put(img, fx + 1, fy + 1, hexc("4a1a70"))
    out("block", "nightshade", img)

    # Deathbell: tall pale stalk with dark indigo bells hanging off
    img = new()
    g, gd = hexc("5a6a4a"), hexc("3a4a30")
    stem(img, 8, 15, 8, 2, g)
    for (lx, ly, d) in ((7, 14, -1), (9, 13, 1)):
        leaf(img, lx, ly, d, g, gd)
    for i, (bx, by, side) in enumerate(((9, 3, 1), (7, 6, -1), (9, 9, 1), (7, 11, -1))):
        put(img, bx, by, gd)
        x = bx + side
        dark, mid, rim = hexc("1e1840"), hexc("3a2e78"), hexc("6a5ab0")
        put(img, x, by + 1, mid)
        put(img, x + side, by + 1, dark)
        put(img, x, by + 2, mid)
        put(img, x + side, by + 2, dark)
        put(img, x - side, by + 2, rim)
        put(img, x, by + 3, rim)
        put(img, x + side, by + 3, rim)
    out("block", "deathbell", img)

    # Nirnroot: pale glowing fronds, bulb base
    img = new()
    pale, glow, core, dk = hexc("cfe8d0"), hexc("8ff0d8"), hexc("ffffff"), hexc("6a9a80")
    for (x0, y0, x1, y1) in ((8, 14, 4, 3), (8, 14, 12, 3), (8, 14, 8, 1), (8, 14, 2, 8), (8, 14, 14, 8)):
        stem(img, x0, y0, x1, y1, pale)
    for (x, y) in ((4, 3), (12, 3), (8, 1), (2, 8), (14, 8)):
        put(img, x, y, glow)
        put(img, x, y + 1, glow)
    for x in range(6, 11):
        put(img, x, 14, dk)
        put(img, x, 13, pale if x in (7, 8, 9) else dk)
    put(img, 8, 12, core)
    put(img, 6, 6, core)
    put(img, 10, 6, core)
    out("block", "nirnroot", img)

    # Frost Mirriam: thin stems with frosted lilac flower spikes
    img = new()
    g, gd = hexc("5a7a6a"), hexc("3a5a4a")
    for (x0, x1, top) in ((5, 4, 3), (8, 8, 1), (11, 12, 4)):
        stem(img, x0, 15, x1, top + 3, g)
        for k in range(4):
            y = top + k
            c = hexc("e8f0ff") if k % 2 == 0 else hexc("b8a8e8")
            put(img, x1, y, c)
            put(img, x1 - 1 if k % 2 else x1 + 1, y, hexc("d8d0f8"))
    for (lx, ly, d) in ((5, 13, -1), (11, 12, 1), (8, 11, 1)):
        leaf(img, lx, ly, d, g, gd)
    out("block", "frost_mirriam", img)


# ============================================================================ stations

def planks(img, base, dark, light, seed, x0=0, y0=0, x1=16, y1=16, vertical=False):
    rnd = random.Random(seed)
    for y in range(y0, y1):
        for x in range(x0, x1):
            u, v = (y, x) if vertical else (x, y)
            c = base
            if v % 4 == 3:
                c = dark
            elif rnd.random() < 0.12:
                c = light
            elif rnd.random() < 0.10:
                c = shade(base, 0.88)
            if (u + (v // 4) * 5) % 9 == 0 and v % 4 != 3:
                c = dark
            img.putpixel((x, y), c)


def rune(img, cx, cy, color, glow):
    pattern = ["..#..", ".#.#.", "#.#.#", ".#.#.", "..#.."]
    for j, row in enumerate(pattern):
        for i, ch in enumerate(row):
            if ch == "#":
                put(img, cx - 2 + i, cy - 2 + j, color)
            elif glow and abs(i - 2) + abs(j - 2) <= 2:
                blend(img, cx - 2 + i, cy - 2 + j, glow)


def stations():
    wood, wood_d, wood_l = hexc("3a2232"), hexc("22121e"), hexc("543446")
    gold, gold_d = hexc("d8aa48"), hexc("8a6420")
    # ---- arcane enchanter side / front / bottom
    for name in ("side", "front", "bottom"):
        img = new()
        planks(img, wood, wood_d, wood_l, 11 + len(name), vertical=True)
        if name != "bottom":
            for x in range(16):
                put(img, x, 4, gold)
                put(img, x, 5, gold_d)
                put(img, x, 15, gold_d)
            for y in range(4, 16):
                put(img, 0, y, gold_d)
                put(img, 15, y, gold_d)
        if name == "side":
            rune(img, 8, 10, hexc("b58cff"), hexc("7a4ad0", 110))
        if name == "front":
            # inlaid soul crystal in a gold setting
            for y in range(7, 14):
                for x in range(5, 11):
                    if abs(x - 7.5) + abs(y - 10) <= 3.6:
                        put(img, x, y, gold_d)
            for y in range(8, 13):
                for x in range(6, 10):
                    if abs(x - 7.5) + abs(y - 10) <= 2.6:
                        put(img, x, y, mix(hexc("5aa8ff"), hexc("d8f0ff"), (10 - y) / 3 + 0.2))
        out("block", "arcane_enchanter_" + name, img)

    # ---- top: purple cloth, gold border, arcane circle
    img = new()
    rnd = random.Random(7)
    for y in range(16):
        for x in range(16):
            c = hexc("3c1a5a") if rnd.random() < 0.8 else hexc("341650")
            img.putpixel((x, y), c)
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            put(img, x, y, gold)
        for (x, y) in ((i, 1), (i, 14), (1, i), (14, i)):
            if 1 <= i <= 14:
                put(img, x, y, gold_d)
    for a in range(0, 360, 8):
        r = 5.5
        put(img, int(round(7.5 + r * math.cos(math.radians(a)))), int(round(7.5 + r * math.sin(math.radians(a)))), hexc("a87ae8"))
    rune(img, 8, 8, hexc("e0c8ff"), None)
    out("block", "arcane_enchanter_top", img)

    # ---- book (geometry uses region x 3..13, y 4..12; page edges in rows 13-14)
    img = new()
    cover, cover_d = hexc("6a1c1c"), hexc("3a0c0c")
    page, page_d, ink = hexc("f2e6c4"), hexc("d4c49a"), hexc("8a7a5a")
    for y in range(4, 12):
        for x in range(3, 13):
            img.putpixel((x, y), page if x not in (7, 8) else page_d)
    for x in range(3, 13):
        put(img, x, 4, cover)
        put(img, x, 11, cover)
    for y in range(4, 12):
        put(img, 3, y, cover)
        put(img, 12, y, cover)
    for y in (6, 8, 10):
        for x in list(range(4, 7)) + list(range(9, 12)):
            if (x + y) % 5 != 0:
                put(img, x, y, ink)
    put(img, 10, 7, hexc("8a40d0"))
    put(img, 5, 9, hexc("8a40d0"))
    for x in range(16):
        put(img, x, 13, page_d)
        put(img, x, 14, cover_d)
    for y in range(16):
        for x in range(16):
            if img.getpixel((x, y))[3] == 0:
                img.putpixel((x, y), cover)
    out("block", "arcane_enchanter_book", img)

    # ---- floating crystal
    img = new()
    for y in range(16):
        for x in range(16):
            t = y / 15.0
            c = mix(hexc("e8f4ff"), hexc("3a6ad8"), t)
            if x % 4 == 0:
                c = shade(c, 0.8)
            img.putpixel((x, y), c)
    out("block", "arcane_enchanter_crystal", img)

    # ---- alchemy lab
    oak, oak_d, oak_l = hexc("8a6038"), hexc("5a3c20"), hexc("a87a4a")
    img = new()
    planks(img, oak, oak_d, oak_l, 21)
    # a shelf with bottles
    for x in range(16):
        put(img, x, 9, oak_d)
        put(img, x, 10, hexc("6a4628"))
    bottle_cols = [hexc("d03a3a"), hexc("3a8ad0"), hexc("4ac04a"), hexc("d0a03a")]
    for i, bx in enumerate((2, 6, 10, 13)):
        col = bottle_cols[i % 4]
        put(img, bx, 4, hexc("c8b088"))
        for y in range(5, 9):
            put(img, bx, y, col if y > 5 else hexc("e8f4ff"))
            if bx + 1 < 16 and i != 3:
                put(img, bx + 1, y, shade(col, 0.75) if y > 5 else hexc("b8d0e0"))
    for x in range(16):
        put(img, x, 0, oak_d)
        put(img, x, 15, oak_d)
    for y in range(16):
        put(img, 0, y, oak_d)
        put(img, 15, y, oak_d)
    out("block", "alchemy_lab_side", img)

    img = new()
    planks(img, oak, oak_d, oak_l, 22)
    for (x0, y0) in ((2, 2), (9, 2), (2, 9), (9, 9)):
        for y in range(y0, y0 + 5):
            for x in range(x0, x0 + 5):
                edge = x in (x0, x0 + 4) or y in (y0, y0 + 4)
                put(img, x, y, oak_d if edge else oak_l)
        put(img, x0 + 2, y0 + 2, gold)
    for x in range(16):
        put(img, x, 0, oak_d)
        put(img, x, 15, oak_d)
    for y in range(16):
        put(img, 0, y, oak_d)
        put(img, 15, y, oak_d)
    out("block", "alchemy_lab_front", img)

    img = new()
    rnd = random.Random(31)
    for y in range(16):
        for x in range(16):
            c = hexc("8e8a84") if rnd.random() < 0.7 else hexc("7a7670")
            img.putpixel((x, y), c)
    for (sx, sy, col) in ((4, 5, hexc("4a8a3a")), (11, 10, hexc("7a3a8a")), (10, 3, hexc("a0602a"))):
        for dx in range(-1, 2):
            for dy in range(-1, 2):
                if abs(dx) + abs(dy) < 2:
                    blend(img, sx + dx, sy + dy, col[:3] + (150,))
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            put(img, x, y, oak_d)
    out("block", "alchemy_lab_top", img)

    img = new()
    planks(img, oak_d, shade(oak_d, 0.7), oak, 23)
    out("block", "alchemy_lab_bottom", img)

    # glassware atlas: x0-7 green flask, x8-15 amber retort, rows 12-15 stone mortar
    img = new()
    for y in range(12):
        for x in range(8):
            liquid = y >= 5
            c = mix(hexc("6ae07a"), hexc("2a8a3a"), (y - 5) / 6) if liquid else hexc("d8eef4")
            if x == 0 or x == 7:
                c = shade(c, 0.8)
            if x == 2 and not liquid:
                c = hexc("ffffff")
            img.putpixel((x, y), c)
        for x in range(8, 16):
            liquid = y >= 4
            c = mix(hexc("f0b040"), hexc("a05a10"), (y - 4) / 7) if liquid else hexc("e8e0d0")
            if x in (8, 15):
                c = shade(c, 0.8)
            if x == 10 and not liquid:
                c = hexc("ffffff")
            img.putpixel((x, y), c)
    for y in range(12, 16):
        for x in range(16):
            img.putpixel((x, y), hexc("9a948a") if (x + y) % 3 else hexc("7a746a"))
    out("block", "alchemy_lab_glass", img)


# ============================================================================ effect icons (18x18)

GLYPHS = {
    "plus": ["...#...", "...#...", "...#...", "#######", "...#...", "...#...", "...#..."],
    "minus": [".......", ".......", ".......", "#######", ".......", ".......", "......."],
    "drop": ["...#...", "..###..", "..###..", ".#####.", ".#####.", ".#####.", "..###.."],
    "up": ["...#...", "..###..", ".#####.", "#######", "..###..", "..###..", "..###.."],
    "down": ["..###..", "..###..", "..###..", "#######", ".#####.", "..###..", "...#..."],
    "shield": ["#######", "#######", "#######", "#######", ".#####.", "..###..", "...#..."],
    "broken": ["###.###", "##..###", "###.###", "##..###", ".#.###.", "..###..", "...#..."],
    "swirl": [".#####.", "#.....#", "#.###.#", "#.#.#.#", "#.#...#", "#.####.", "#......"],
    "bang": ["...#...", "..###..", "..###..", "..###..", "...#...", ".......", "...#..."],
    "fang": ["#.....#", "##...##", "#######", "#.#.#.#", "..#.#..", "..#.#..", "......."],
    "cross": ["..###..", "..###..", "#######", "#######", "#######", "..###..", "..###.."],
    "star": ["...#...", "...#...", "#.###.#", ".#####.", "..###..", ".##.##.", "#.....#"],
}

EFFECTS = {
    "restore_magicka": ("2E6BE6", "plus"), "restore_stamina": ("3FAF3F", "plus"),
    "damage_magicka": ("23305E", "minus"), "damage_stamina": ("4F5E23", "minus"),
    "regenerate_magicka": ("5A8CFF", "swirl"), "regenerate_stamina": ("7AD36A", "swirl"),
    "damage_magicka_regen": ("404880", "down"), "damage_stamina_regen": ("607040", "down"),
    "ravage_magicka": ("302060", "drop"), "ravage_stamina": ("505020", "drop"),
    "resist_frost": ("9AD8F0", "shield"), "resist_shock": ("D8D060", "shield"),
    "resist_poison": ("60A040", "shield"), "resist_magic": ("B080E0", "shield"),
    "weakness_to_fire": ("C04020", "broken"), "weakness_to_frost": ("6090B0", "broken"),
    "weakness_to_shock": ("909030", "broken"), "weakness_to_poison": ("406020", "broken"),
    "weakness_to_magic": ("704090", "broken"),
    "fortify_one_handed": ("C0A060", "up"), "fortify_two_handed": ("A08040", "up"),
    "fortify_archery": ("80A040", "up"), "fortify_light_armor": ("70A070", "up"),
    "fortify_heavy_armor": ("707090", "up"), "fortify_sneak": ("505050", "up"),
    "fortify_smithing": ("B06030", "up"), "fortify_enchanting": ("8060D0", "star"),
    "fortify_alchemy": ("50B080", "star"), "fortify_destruction": ("E05030", "up"),
    "fortify_restoration": ("F0E080", "up"), "fortify_conjuration": ("9050C0", "up"),
    "fortify_illusion": ("C070D0", "up"), "fortify_alteration": ("50A0C0", "up"),
    "fortify_lockpicking": ("909090", "up"), "fortify_pickpocket": ("60A060", "up"),
    "fortify_barter": ("E0C040", "up"), "fortify_carry_weight": ("A07850", "up"),
    "fear": ("502040", "bang"), "frenzy": ("C02020", "fang"), "cure_disease": ("E0E0C0", "cross"),
}


def effect_icons():
    for name, (col, glyph) in EFFECTS.items():
        img = new(18)
        base = hexc(col)
        rim = shade(base, 0.45)
        for y in range(18):
            for x in range(18):
                d = math.hypot(x - 8.5, y - 8.5)
                if d <= 8.2:
                    t = (x + y) / 34.0
                    c = mix(shade(base, 1.25), shade(base, 0.7), t)
                    if d > 7.0:
                        c = rim
                    img.putpixel((x, y), c[:3] + (255,))
        lum = 0.3 * base[0] + 0.59 * base[1] + 0.11 * base[2]
        ink = (30, 24, 20, 255) if lum > 170 else (255, 250, 235, 255)
        shadow = (0, 0, 0, 110)
        rows = GLYPHS[glyph]
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == "#":
                    blend(img, 6 + i, 6 + j, shadow)
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == "#":
                    put(img, 5 + i, 5 + j, ink)
        out("mob_effect", name, img)


if __name__ == "__main__":
    soul_gems()
    azuras_star()
    ingredients()
    plants()
    stations()
    effect_icons()
    print("arcane textures written to", TEX)
