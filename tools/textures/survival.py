#!/usr/bin/env python3
"""Pixel-art textures for the survival module: Skyrim food & drink, crops, the cooking pot, cheese wheel, shrines of
the Nine Divines, depleted ore veins and the disease / blessing / food effect icons.

Writes PNGs into mod/src/main/resources/assets/skycraft/textures/{item,block,mob_effect}/.
Run from anywhere:  python3 tools/textures/survival.py
"""
import math
import os
import random

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
TEX = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "skycraft", "textures")


# ============================================================================ helpers

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


def get(img, x, y):
    if 0 <= x < img.width and 0 <= y < img.height:
        return img.getpixel((x, y))
    return (0, 0, 0, 0)


def blend(img, x, y, c):
    if not (0 <= x < img.width and 0 <= y < img.height):
        return
    base = img.getpixel((x, y))
    a = c[3] / 255.0
    if base[3] == 0:
        img.putpixel((x, y), c)
        return
    r = tuple(int(base[i] * (1 - a) + c[i] * a) for i in range(3))
    img.putpixel((x, y), r + (max(base[3], c[3]),))


def outline(img, color, diagonal=False):
    """Adds a 1px outline around opaque pixels."""
    w, h = img.size
    src = img.copy()
    dirs = [(1, 0), (-1, 0), (0, 1), (0, -1)]
    if diagonal:
        dirs += [(1, 1), (-1, -1), (1, -1), (-1, 1)]
    for y in range(h):
        for x in range(w):
            if src.getpixel((x, y))[3] != 0:
                continue
            for dx, dy in dirs:
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and src.getpixel((nx, ny))[3] > 40:
                    img.putpixel((x, y), color)
                    break


def in_ellipse(x, y, cx, cy, rx, ry):
    return ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1.0


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


def fill_poly(img, pts, color_fn):
    for y in range(img.height):
        for x in range(img.width):
            if in_poly(x + 0.5, y + 0.5, pts):
                img.putpixel((x, y), color_fn(x, y))


def line(img, x0, y0, x1, y1, c):
    steps = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(steps + 1):
        t = i / steps
        put(img, int(round(x0 + (x1 - x0) * t)), int(round(y0 + (y1 - y0) * t)), c)


def seed_of(text):
    """Deterministic seed (Python's str hash is randomized per run)."""
    return sum(ord(c) * (i + 1) for i, c in enumerate(text))


def lit(base, x, y, cx, cy, r, amount=0.35):
    """Simple top-left lighting on a round shape."""
    t = ((x - cx) + (y - cy)) / (2.0 * r)
    return shade(base, 1.0 + amount * -t) if t < 0 else shade(base, 1.0 - amount * t)


# ============================================================================ produce

def cabbage_item():
    img = new()
    rnd = random.Random(11)
    dark, mid, light, vein = hexc("2f6b28"), hexc("4f9a3a"), hexc("9bd37a"), hexc("d8f0c0")
    for y in range(16):
        for x in range(16):
            if in_ellipse(x, y, 8, 8.5, 6.6, 6.2):
                d = math.hypot(x + 0.5 - 8, y + 0.5 - 8.5) / 6.4
                c = mix(light, mid, d * 1.2)
                c = lit(c, x, y, 8, 8.5, 6.5, 0.25)
                if rnd.random() < 0.08:
                    c = shade(c, 0.9)
                img.putpixel((x, y), c)
    # outer leaf folds
    for (x0, y0, x1, y1) in [(3, 6, 6, 13), (13, 6, 10, 13), (8, 3, 8, 8)]:
        line(img, x0, y0, x1, y1, shade(mid, 0.8))
    for (x, y) in [(7, 8), (8, 9), (9, 8), (8, 10), (7, 11), (9, 11)]:
        put(img, x, y, vein)
    outline(img, hexc("1c3d18"))
    out("item", "cabbage", img)


def tomato_item():
    img = new()
    red, dark = hexc("d8382a"), hexc("8a1c14")
    for y in range(16):
        for x in range(16):
            if in_ellipse(x, y, 8, 9, 6.2, 5.6):
                c = lit(red, x, y, 8, 9, 6, 0.4)
                img.putpixel((x, y), c)
    for (x, y) in [(5, 6), (5, 7), (6, 6)]:
        put(img, x, y, hexc("ffb0a0"))
    green, gdark = hexc("4f9a3a"), hexc("2f6b28")
    for (x, y) in [(8, 3), (8, 4), (7, 4), (9, 4), (6, 5), (10, 5), (8, 5), (7, 5), (9, 5), (5, 4), (11, 4)]:
        put(img, x, y, green)
    put(img, 8, 2, gdark)
    outline(img, hexc("4a0e0a"))
    out("item", "tomato", img)


def leek_item(name="leek", charred=False):
    img = new()
    white, pale, green, dark = hexc("f2f0dc"), hexc("cfe6a0"), hexc("5aa040"), hexc("2f6b28")
    if charred:
        white, pale, green, dark = hexc("e0c890"), hexc("b8a860"), hexc("6a7a30"), hexc("3a4018")
    stalks = [(0, 0)] if not charred else [(-2, 1), (2, -1)]
    for ox, oy in stalks:
        for i in range(12):
            # diagonal stalk from bottom-left to top-right
            x = 3 + i * 0.75 + ox
            y = 14 - i + oy
            col = white if i < 4 else pale if i < 6 else green
            for w in range(2):
                put(img, int(x) + w, int(y), shade(col, 1.0 if w == 0 else 0.85))
        # leaves fanning out at the top
        lx, ly = int(3 + 11 * 0.75 + ox), 14 - 11 + oy
        for (dx, dy) in [(-1, -1), (-2, -2), (1, -1), (2, -2), (0, -2), (0, -3), (3, -2), (-3, -1)]:
            put(img, lx + dx, ly + dy, dark if abs(dx) > 1 else green)
        # roots
        for (dx, dy) in [(-1, 1), (0, 1), (1, 1)]:
            put(img, 3 + ox + dx, 14 + oy + dy, hexc("c8b890"))
    if charred:
        for (x, y) in [(5, 10), (6, 9), (9, 7), (10, 6), (7, 11), (11, 5)]:
            put(img, x, y, hexc("2a1a0a"))
    outline(img, hexc("1c3018") if not charred else hexc("20140a"))
    out("item", name, img)


def garlic_item():
    img = new()
    base, shadow, line_c = hexc("f4eee0"), hexc("d4c8b4"), hexc("c0a8b8")
    for y in range(16):
        for x in range(16):
            if in_ellipse(x, y, 8, 10, 5.6, 4.6):
                img.putpixel((x, y), lit(base, x, y, 8, 10, 5.5, 0.25))
    # clove segments
    for x in (6, 8, 10):
        for y in range(7, 14):
            if img.getpixel((x, y))[3]:
                put(img, x, y, line_c if x != 8 else shadow)
    # tip
    for (x, y) in [(8, 5), (8, 4), (7, 3), (8, 3), (8, 6), (9, 6), (7, 6)]:
        put(img, x, y, shadow if y > 4 else hexc("b8a888"))
    for (x, y) in [(6, 15), (8, 15), (10, 15)]:
        put(img, x, y, hexc("a89070"))
    outline(img, hexc("5a4a3a"))
    out("item", "garlic", img)


def seeds_item(name, color):
    img = new()
    rnd = random.Random(seed_of(name))
    spots = [(5, 6), (9, 4), (10, 9), (6, 11), (12, 12), (3, 10), (8, 8)]
    for (x, y) in spots:
        c = shade(color, 0.85 + rnd.random() * 0.3)
        put(img, x, y, c)
        put(img, x + 1, y, shade(c, 0.8))
        put(img, x, y + 1, shade(c, 0.7))
    outline(img, shade(color, 0.35))
    out("item", name, img)


# ============================================================================ meat & fish

def steak(name, base, fat, cooked=False, seed=1, shape="steak"):
    img = new()
    rnd = random.Random(seed)
    if shape == "steak":
        pts = [(2, 7), (5, 3), (10, 2.5), (14, 5), (14.5, 10), (11, 14), (5, 14), (2, 11)]
    elif shape == "loaf":
        pts = [(2, 6), (4, 4), (12, 4), (14, 6), (14, 12), (12, 14), (4, 14), (2, 12)]
    else:  # snout: curved trunk chunk
        pts = [(1.5, 9), (4, 5), (9, 3), (14, 2), (14.5, 6), (10, 7), (7, 10), (6, 14), (2, 14)]
    fill_poly(img, pts, lambda x, y: lit(base, x, y, 8, 8, 7, 0.3))
    # marbling / grill marks
    if cooked:
        for k in range(3):
            y0 = 5 + k * 3
            for x in range(3, 14):
                y = y0 + (x - 3) // 3
                if get(img, x, y)[3]:
                    put(img, x, y, shade(base, 0.55))
    else:
        for _ in range(9):
            x, y = rnd.randint(3, 12), rnd.randint(4, 12)
            if get(img, x, y)[3]:
                put(img, x, y, fat)
                if get(img, x + 1, y)[3]:
                    put(img, x + 1, y, mix(fat, base, 0.4))
    # fat rim along the top edge
    src = img.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x, y))[3] and not get(src, x, y - 1)[3]:
                put(img, x, y, fat)
    outline(img, shade(base, 0.35))
    out("item", name, img)


def leg_roast(name, meat, bone):
    img = new()
    # meaty end (round) bottom-left, bone sticking out top-right
    for y in range(16):
        for x in range(16):
            if in_ellipse(x, y, 6.5, 9.5, 5.2, 5.0):
                img.putpixel((x, y), lit(meat, x, y, 6.5, 9.5, 5, 0.35))
    for i in range(6):
        put(img, 10 + i, 6 - i, bone)
        put(img, 10 + i, 5 - i, shade(bone, 0.9))
    for (x, y) in [(14, 0), (15, 1), (15, 0)]:
        put(img, x, y, bone)
    for (x, y) in [(4, 8), (6, 10), (8, 8), (5, 12)]:
        put(img, x, y, shade(meat, 0.65))
    outline(img, hexc("2a140a"))
    out("item", name, img)


def salmon_steak():
    img = new()
    flesh, stripe, skin = hexc("f08860"), hexc("ffd0b8"), hexc("8a8a90")
    pts = [(3, 4), (12, 3), (14, 8), (12, 13), (4, 13), (2, 8)]
    fill_poly(img, pts, lambda x, y: lit(flesh, x, y, 8, 8, 6, 0.25))
    for k, y0 in enumerate((5, 8, 11)):
        for x in range(3, 13):
            if get(img, x, y0 + (x % 3 == 0))[3]:
                put(img, x, y0 + (x % 3 == 0), stripe)
    for x in range(3, 13):
        if get(img, x, 13)[3]:
            put(img, x, 13, skin)
    # browned top from the pan
    for (x, y) in [(5, 4), (6, 4), (9, 4), (10, 4)]:
        put(img, x, y, hexc("b86030"))
    outline(img, hexc("5a2a18"))
    out("item", "salmon_steak", img)


# ============================================================================ stews & soups

def bowl_dish(name, soup, chunks, seed):
    img = new()
    rnd = random.Random(seed)
    wood, wood_d, wood_l = hexc("8a5a30"), hexc("5a3a1c"), hexc("b08050")
    cx = 8.0
    # bowl body
    for y in range(8, 15):
        t = (y - 7.5) / 7.0
        half = 7.2 * math.sqrt(max(0.0, 1 - t * t))
        for x in range(16):
            if abs(x + 0.5 - cx) <= half:
                c = mix(wood_l, wood_d, (x + 0.5 - (cx - half)) / (2 * half + 0.01))
                if y == 14:
                    c = wood_d
                img.putpixel((x, y), c)
    # soup surface (ellipse)
    for y in range(4, 11):
        for x in range(16):
            if in_ellipse(x, y, cx, 7.5, 7.3, 2.9):
                inner = in_ellipse(x, y, cx, 7.6, 6.2, 2.1)
                if inner:
                    c = lit(soup, x, y, cx, 7.5, 6, 0.2)
                    if rnd.random() < 0.08:
                        c = shade(c, 1.15)
                else:
                    c = wood_l if y < 8 else wood
                img.putpixel((x, y), c)
    # chunks
    spots = [(4, 7), (6, 6), (9, 7), (11, 6), (7, 8), (10, 8), (5, 8), (12, 7)]
    rnd.shuffle(spots)
    for i, (x, y) in enumerate(spots[: 2 * len(chunks) + 1]):
        c = chunks[i % len(chunks)]
        put(img, x, y, c)
        if rnd.random() < 0.5:
            put(img, x + 1, y, shade(c, 0.85))
    # steam
    for (x, y) in [(6, 2), (6, 1), (9, 3), (10, 2)]:
        put(img, x, y, (255, 255, 255, 110))
    outline(img, hexc("2e1c0c"))
    out("item", name, img)


# ============================================================================ baked goods & dairy

def sweetroll():
    img = new()
    dough, crust, icing = hexc("e0b070"), hexc("a8682c"), hexc("fbf6ea")
    for y in range(16):
        for x in range(16):
            if in_ellipse(x, y, 8, 9.5, 6.8, 5.2):
                img.putpixel((x, y), lit(dough, x, y, 8, 9.5, 6.5, 0.3))
    # spiral
    for (x, y) in [(8, 9), (9, 9), (9, 10), (8, 11), (7, 11), (6, 10), (6, 9), (7, 8), (8, 7), (10, 8), (11, 10)]:
        put(img, x, y, crust)
    # icing on top
    for y in range(4, 9):
        for x in range(16):
            if in_ellipse(x, y, 8, 7.2, 5.4, 2.8) and get(img, x, y)[3]:
                put(img, x, y, icing)
    for (x, y) in [(4, 9), (12, 9), (5, 10), (11, 10), (12, 10)]:
        put(img, x, y, icing)
    for (x, y) in [(7, 5), (10, 6)]:
        put(img, x, y, hexc("ffffff"))
    outline(img, hexc("5a3210"))
    out("item", "sweetroll", img)


def garlic_bread():
    img = new()
    bread, crust, butter, herb = hexc("e8c47a"), hexc("a0602a"), hexc("f4dc70"), hexc("4a8a30")
    pts = [(2, 7), (5, 3), (11, 2), (14, 5), (14, 9), (11, 13), (4, 14), (2, 11)]
    fill_poly(img, pts, lambda x, y: bread)
    src = img.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x, y))[3]:
                edge = any(not get(src, x + dx, y + dy)[3] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if edge:
                    put(img, x, y, crust)
    rnd = random.Random(5)
    for _ in range(14):
        x, y = rnd.randint(4, 12), rnd.randint(4, 12)
        if get(img, x, y) == bread:
            put(img, x, y, butter if rnd.random() < 0.5 else herb)
    outline(img, hexc("4a2a0c"))
    out("item", "garlic_bread", img)


def honey_nut_treat():
    img = new()
    honey, dark, nut = hexc("e8a830"), hexc("a86a14"), hexc("8a5a30")
    for (x0, y0) in [(2, 3), (9, 5), (4, 9)]:
        for y in range(y0, y0 + 5):
            for x in range(x0, x0 + 5):
                put(img, x, y, lit(honey, x, y, x0 + 2, y0 + 2, 3, 0.3))
        for (dx, dy) in [(1, 1), (3, 2), (2, 3)]:
            put(img, x0 + dx, y0 + dy, nut)
        put(img, x0, y0, hexc("ffe0a0"))
    outline(img, shade(dark, 0.6))
    out("item", "honey_nut_treat", img)


def boiled_creme_treat():
    img = new()
    creme, caramel, plate = hexc("f8ecc0"), hexc("c88030"), hexc("c8c8d0")
    for y in range(16):
        for x in range(16):
            if in_ellipse(x, y, 8, 12.5, 7.2, 2.4):
                img.putpixel((x, y), plate if y < 13 else shade(plate, 0.8))
            if in_ellipse(x, y, 8, 9, 5.0, 4.8) and y <= 12:
                img.putpixel((x, y), lit(creme, x, y, 8, 9, 5, 0.25))
    for y in range(3, 8):
        for x in range(16):
            if in_ellipse(x, y, 8, 6, 3.8, 2.0) and get(img, x, y)[3]:
                put(img, x, y, caramel)
    for (x, y) in [(5, 9), (5, 10), (11, 8), (11, 9), (11, 10)]:
        put(img, x, y, caramel)
    outline(img, hexc("5a4a3a"))
    out("item", "boiled_creme_treat", img)


def cheese_wheel_item():
    img = new()
    top, side, rind = hexc("f4d060"), hexc("e0a838"), hexc("b87a20")
    cx, rx, ry, y_top, y_bot = 8.0, 7.2, 3.0, 6.0, 10.5
    for y in range(16):
        for x in range(16):
            inside_side = abs(x + 0.5 - cx) <= rx and y_top <= y + 0.5 <= y_bot
            if inside_side or in_ellipse(x, y, cx, y_bot, rx, ry):
                img.putpixel((x, y), mix(side, rind, abs(x + 0.5 - cx) / rx * 0.8 + (y + 0.5 - y_top) / 12.0))
    for y in range(16):
        for x in range(16):
            if in_ellipse(x, y, cx, y_top, rx, ry):
                img.putpixel((x, y), lit(top, x, y, cx, y_top, rx, 0.2))
    # cut wedge showing the paste
    for (x, y) in [(9, 5), (10, 5), (11, 5), (10, 6), (11, 6), (12, 6), (11, 7), (12, 7), (12, 8), (12, 9), (12, 10)]:
        put(img, x, y, hexc("fff0a0"))
    outline(img, hexc("5a3a0a"))
    out("item", "cheese_wheel", img)


def cheese_wedge(name, paste, rind, holes):
    img = new()
    pts = [(2, 12), (13, 4), (14, 13), (2, 14)]
    fill_poly(img, pts, lambda x, y: lit(paste, x, y, 9, 10, 6, 0.2))
    for x in range(2, 15):
        for y in (13,):
            if get(img, x, y)[3]:
                put(img, x, y, rind)
    for (x, y) in [(12, 6), (13, 7), (13, 9), (13, 11)]:
        if get(img, x, y)[3]:
            put(img, x, y, rind)
    for (x, y) in holes:
        put(img, x, y, shade(paste, 0.75))
    outline(img, shade(rind, 0.5))
    out("item", name, img)


def sliced_goat_cheese():
    img = new()
    paste, rind = hexc("f8f6ec"), hexc("d8d0b0")
    for k, (ox, oy) in enumerate([(2, 5), (6, 8)]):
        pts = [(ox, oy + 2), (ox + 6, oy), (ox + 8, oy + 4), (ox + 2, oy + 6)]
        fill_poly(img, pts, lambda x, y: lit(paste, x, y, ox + 4, oy + 3, 4, 0.2))
        line(img, ox + 2, oy + 6, ox + 8, oy + 4, rind)
    outline(img, hexc("7a7060"))
    out("item", "sliced_goat_cheese", img)


# ============================================================================ drinks

def bottle(name, glass, liquid, label, cork, shape="bottle"):
    img = new()
    if shape == "bottle":
        body = lambda x, y: 4 <= x <= 11 and 6 <= y <= 14 and not ((x in (4, 11)) and y in (6, 14))
        neck = lambda x, y: 7 <= x <= 8 and 2 <= y <= 5
        shoulder = lambda x, y: y == 5 and 5 <= x <= 10
    elif shape == "jug":  # squat mead jug
        body = lambda x, y: in_ellipse(x, y, 8, 10.5, 5.6, 4.6)
        neck = lambda x, y: 7 <= x <= 8 and 3 <= y <= 6
        shoulder = lambda x, y: False
    else:  # tall wine bottle
        body = lambda x, y: 5 <= x <= 10 and 7 <= y <= 14 and not ((x in (5, 10)) and y in (7, 14))
        neck = lambda x, y: 7 <= x <= 8 and 1 <= y <= 6
        shoulder = lambda x, y: y == 6 and 6 <= x <= 9
    for y in range(16):
        for x in range(16):
            if body(x, y) or neck(x, y) or shoulder(x, y):
                c = glass
                if body(x, y) and y >= 8:
                    c = mix(glass, liquid, 0.65)
                c = lit(c, x, y, 8, 9, 5, 0.35)
                img.putpixel((x, y), c)
    # label
    if label is not None:
        for y in range(9, 12):
            for x in range(16):
                if body(x, y) and img.getpixel((x, y))[3]:
                    put(img, x, y, label if y != 10 else shade(label, 0.85))
    # cork
    top = min(y for y in range(16) for x in range(16) if neck(x, y))
    put(img, 7, top - 1, cork)
    put(img, 8, top - 1, shade(cork, 0.8))
    # highlight
    for y in range(7, 13):
        if get(img, 5 if shape != "wine" else 6, y)[3]:
            put(img, 5 if shape != "wine" else 6, y, (255, 255, 255, 150))
    outline(img, shade(glass, 0.3))
    out("item", name, img)


def skooma():
    img = new()
    glass, liquid = hexc("d8c8f0"), hexc("e040c0")
    for y in range(16):
        for x in range(16):
            if in_ellipse(x, y, 8, 10.5, 3.6, 4.2):
                img.putpixel((x, y), lit(mix(glass, liquid, 0.75 if y > 8 else 0.2), x, y, 8, 10.5, 3.5, 0.4))
            if 7 <= x <= 8 and 3 <= y <= 6:
                img.putpixel((x, y), glass)
    put(img, 7, 2, hexc("6a3a20"))
    put(img, 8, 2, hexc("4a2814"))
    put(img, 6, 9, (255, 255, 255, 200))
    for (x, y) in [(3, 4), (12, 5), (13, 11)]:
        put(img, x, y, hexc("ff9af0", 180))
    outline(img, hexc("40104a"))
    out("item", "skooma", img)


# ============================================================================ crops (cross models)

def crop_stage(name, stage):
    img = new()
    rnd = random.Random(seed_of(name) * 7 + stage)
    green, dark, light = hexc("4f9a3a"), hexc("2f6b28"), hexc("8cc860")
    soil_y = 15
    if name == "cabbage_crop":
        r = [1.8, 3.2, 4.6, 6.2][stage]
        cy = soil_y - r * 0.8
        for y in range(16):
            for x in range(16):
                if in_ellipse(x, y, 8, cy, r, r * 0.85):
                    d = math.hypot(x + 0.5 - 8, y + 0.5 - cy) / r
                    c = mix(light if stage == 3 else green, dark, d)
                    img.putpixel((x, y), c)
        if stage >= 1:  # outer leaves
            for (dx, dy) in [(-1, 0), (1, 0)]:
                for i in range(int(r) + 2):
                    put(img, int(8 + dx * (r + i * 0.4)), int(cy + 1 - i * 0.3 * (stage - 0.5)), dark)
        if stage == 3:
            for (x, y) in [(8, int(cy)), (7, int(cy) + 1), (9, int(cy) - 1)]:
                put(img, x, y, hexc("d8f0c0"))
    elif name == "leek_crop":
        h = [4, 7, 10, 13][stage]
        for sx in (4, 8, 12) if stage >= 1 else (8,):
            for i in range(h):
                y = soil_y - i
                c = hexc("f2f0dc") if (stage == 3 and i < 4) else light if i < h // 2 else green
                put(img, sx, y, c)
                if stage >= 2:
                    put(img, sx + 1, y, shade(c, 0.85))
            put(img, sx - 1, soil_y - h, dark)
            put(img, sx + 2, soil_y - h, dark)
            put(img, sx - 2, soil_y - h - 1 + (stage < 2), dark)
    elif name == "tomato_crop":
        h = [4, 8, 12, 14][stage]
        # stake
        for y in range(soil_y - h, 16):
            put(img, 8, y, hexc("8a6a40"))
        for i in range(h):
            y = soil_y - i
            for dx in ((-1, 1) if i % 2 == 0 else (-2, 2)):
                if i > 0 and rnd.random() < 0.85:
                    put(img, 8 + dx, y, green if abs(dx) == 1 else dark)
        for i in range(0, h, 3):
            put(img, 5, soil_y - i, dark)
            put(img, 11, soil_y - i - 1, dark)
            put(img, 6, soil_y - i, green)
            put(img, 10, soil_y - i - 1, green)
        if stage >= 2:
            fruit = hexc("d8382a") if stage == 3 else hexc("8ac040")
            for (x, y) in [(5, 9), (11, 7), (6, 4), (10, 12)][: 2 + (stage == 3) * 2]:
                for (dx, dy) in [(0, 0), (1, 0), (0, 1), (1, 1)]:
                    put(img, x + dx, y + dy, fruit if (dx, dy) != (0, 0) else shade(fruit, 1.3))
    else:  # garlic_crop: grass-like leaves, bulbs peeking out when ripe
        h = [3, 6, 9, 11][stage]
        for sx in (5, 8, 11):
            for i in range(h):
                y = soil_y - i
                x = sx + (1 if i > h * 0.6 and sx != 8 else 0) * (1 if sx > 8 else -1)
                put(img, x, y, green if i < h - 2 else light)
        if stage == 3:
            for sx in (5, 8, 11):
                for (dx, dy) in [(-1, 0), (0, 0), (1, 0), (0, -1)]:
                    put(img, sx + dx, soil_y + dy, hexc("f4eee0"))
    out("block", f"{name}_stage{stage}", img)


# ============================================================================ blocks

def iron(seed, base=hexc("3a3a3e")):
    rnd = random.Random(seed)
    img = new()
    for y in range(16):
        for x in range(16):
            c = shade(base, 0.9 + rnd.random() * 0.2)
            img.putpixel((x, y), c)
    return img


def cooking_pot_textures():
    side = iron(1)
    for y in range(16):
        for x in range(16):
            c = side.getpixel((x, y))
            # vertical curvature shading + soot towards the bottom
            f = 1.15 - abs(x - 7.5) / 16.0
            c = shade(c, f * (0.75 if y > 11 else 1.0))
            side.putpixel((x, y), c)
    for x in (2, 13):
        put(side, x, 4, hexc("6a6a70"))
        put(side, x, 12, hexc("6a6a70"))
    for x in range(16):
        put(side, x, 1, hexc("5a5a60"))
    out("block", "cooking_pot_side", side)

    top = iron(2)
    rnd = random.Random(3)
    stew, chunk = hexc("8a4a20"), [hexc("c87830"), hexc("5a8a30"), hexc("e8d8b0")]
    for y in range(16):
        for x in range(16):
            if 4 <= x <= 11 and 4 <= y <= 11:
                c = lit(stew, x, y, 8, 8, 5, 0.25)
                if rnd.random() < 0.15:
                    c = chunk[rnd.randint(0, 2)]
                top.putpixel((x, y), c)
            elif 3 <= x <= 12 and 3 <= y <= 12:
                top.putpixel((x, y), hexc("1e1e22"))
    for (x, y) in [(6, 6), (9, 8), (7, 10)]:
        put(top, x, y, hexc("d89050"))
    out("block", "cooking_pot_top", top)
    out("block", "cooking_pot_bottom", iron(4, hexc("2a2a2e")))
    out("block", "cooking_pot_rim", iron(5, hexc("4a4a50")))


def cheese_block_textures():
    top = new()
    rnd = random.Random(8)
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            c = hexc("b87a20") if edge < 2 else hexc("f4d060")
            if edge >= 2 and rnd.random() < 0.05:
                c = hexc("e0b848")
            top.putpixel((x, y), c)
    out("block", "cheese_wheel_top", top)
    side = new()
    for y in range(16):
        for x in range(16):
            c = mix(hexc("e0a838"), hexc("b87a20"), y / 15.0)
            if rnd.random() < 0.06:
                c = shade(c, 0.9)
            side.putpixel((x, y), c)
    out("block", "cheese_wheel_side", side)


def stone_bricks(seed, base=hexc("8a8a86"), moss=True):
    rnd = random.Random(seed)
    img = new()
    mortar = shade(base, 0.6)
    for y in range(16):
        for x in range(16):
            row = y // 4
            off = 4 if row % 2 else 0
            mortar_px = y % 4 == 3 or (x + off) % 8 == 7
            c = mortar if mortar_px else shade(base, 0.88 + rnd.random() * 0.22)
            if not mortar_px and y % 4 == 0:
                c = shade(c, 1.08)
            if moss and rnd.random() < 0.05:
                c = hexc("5a7a40")
            img.putpixel((x, y), c)
    return img


SYMBOLS = {
    # 8x8 glyphs, '#' = glowing ink
    "akatosh": ["########",
                ".######.",
                "..####..",
                "...##...",
                "...##...",
                "..####..",
                ".######.",
                "########"],
    "arkay": ["..####..",
              ".#....#.",
              "#..##..#",
              "#.#..#.#",
              "#.#..#.#",
              "#..##..#",
              ".#....#.",
              "..####.."],
    "dibella": ["...##...",
                ".#.##.#.",
                "..####..",
                "########",
                "########",
                "..####..",
                ".#.##.#.",
                "...##..."],
    "julianos": ["........",
                 "###..###",
                 "#..##..#",
                 "#..##..#",
                 "#..##..#",
                 "#..##..#",
                 "###..###",
                 "...##..."],
    "kynareth": [".......#",
                 "......##",
                 ".....##.",
                 "....###.",
                 "...###..",
                 "..###...",
                 ".##.....",
                 "#......."],
    "mara": [".##..##.",
             "########",
             "########",
             "########",
             ".######.",
             "..####..",
             "...##...",
             "........"],
    "stendarr": ["########",
                 "#..##..#",
                 "#..##..#",
                 "########",
                 "#..##..#",
                 ".#.##.#.",
                 "..####..",
                 "...##..."],
    "talos": ["...##...",
              "...##...",
              "...##...",
              "...##...",
              "...##...",
              "########",
              "...##...",
              "...##..."],
    "zenithar": ["########",
                 "########",
                 "...##...",
                 "...##...",
                 "...##...",
                 ".######.",
                 "########",
                 "........"],
}
DIVINE_COLORS = {"akatosh": "e8b040", "arkay": "e0e0c8", "dibella": "f08ab0", "julianos": "5a8cff", "kynareth": "7ad36a",
                 "mara": "e05a6a", "stendarr": "f0e6a0", "talos": "d0a030", "zenithar": "c08040"}


def glyph(img, name, x0, y0, color, glow=True):
    rows = SYMBOLS[name]
    halo = color[:3] + (90,)
    if glow:
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == "#":
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        nx, ny = x0 + i + dx, y0 + j + dy
                        if 0 <= ny - y0 < 8 and 0 <= nx - x0 < 8 and rows[ny - y0][nx - x0] == "#":
                            continue
                        blend(img, nx, ny, halo)
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "#":
                put(img, x0 + i, y0 + j, mix(color, hexc("ffffff"), 0.25 if (i + j) % 3 == 0 else 0.0))


def shrine_textures():
    stone = stone_bricks(21, hexc("9a968c"))
    out("block", "shrine_stone", stone)
    for name, col in DIVINE_COLORS.items():
        color = hexc(col)
        # column face (the model uses uv 3..13 x 3..12): a recessed panel with the glyph centered
        side = stone_bricks(31 + len(name), hexc("8e8a80"), moss=False)
        for y in range(3, 12):
            for x in range(3, 13):
                put(side, x, y, shade(hexc("4a4640"), 0.9 + 0.1 * ((x + y) % 2)))
        glyph(side, name, 4, 3, color)
        out("block", f"shrine_{name}", side)
        top = stone_bricks(41 + len(name), hexc("a29e94"), moss=False)
        for y in range(3, 13):
            for x in range(3, 13):
                put(top, x, y, hexc("5a564e"))
        glyph(top, name, 4, 4, color)
        out("block", f"shrine_{name}_top", top)


def host_rock(host, seed):
    rnd = random.Random(seed)
    img = new()
    if host == "stone":
        base = hexc("7f7f7f")
    elif host == "deepslate":
        base = hexc("4d4d52")
    else:
        base = hexc("6e3434")
    for y in range(16):
        for x in range(16):
            n = 0.85 + rnd.random() * 0.25
            if host == "deepslate" and y % 4 == 0:
                n *= 0.88
            if host == "netherrack" and rnd.random() < 0.15:
                n *= 0.75
            img.putpixel((x, y), shade(base, n))
    return img


def depleted_ore_textures():
    for i, host in enumerate(["stone", "deepslate", "netherrack"]):
        img = host_rock(host, 70 + i)
        rnd = random.Random(90 + i)
        # picked-over pockets: dark pits with a dull, chipped rim where the ore used to be
        pockets = [(3, 4), (10, 3), (6, 9), (12, 10), (3, 12), (9, 13)]
        for (px, py) in pockets:
            for (dx, dy) in [(0, 0), (1, 0), (0, 1), (1, 1)]:
                put(img, px + dx, py + dy, shade(get(img, px + dx, py + dy), 0.45))
            for (dx, dy) in [(-1, 0), (0, -1), (2, 1), (1, 2)]:
                if rnd.random() < 0.8:
                    put(img, px + dx, py + dy, mix(get(img, px + dx, py + dy), hexc("9a8a70"), 0.35))
        # a few dull flecks of what's left of the vein
        for _ in range(5):
            x, y = rnd.randint(1, 14), rnd.randint(1, 14)
            put(img, x, y, hexc("8a7a62") if host != "netherrack" else hexc("a08060"))
        out("block", f"depleted_ore_{host}", img)


# ============================================================================ effect icons (18x18)

def icon_base(color):
    img = new(18)
    base = hexc(color)
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
    return img


def ink_for(color):
    base = hexc(color)
    lum = 0.3 * base[0] + 0.59 * base[1] + 0.11 * base[2]
    return (30, 24, 20, 255) if lum > 170 else (255, 250, 235, 255)


GERM = ["..#..#..",
        "#.####.#",
        ".######.",
        "########",
        "########",
        ".######.",
        "#.####.#",
        "..#..#.."]
DROP = ["...#....",
        "...##...",
        "..####..",
        "..####..",
        ".######.",
        ".######.",
        "..####..",
        "........"]
BOWL = ["........",
        "..#..#..",
        ".#..#...",
        "........",
        "########",
        ".######.",
        "..####..",
        "........"]


def draw_rows(img, rows, color, x0=5, y0=5):
    shadow = (0, 0, 0, 110)
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "#":
                blend(img, x0 + 1 + i, y0 + 1 + j, shadow)
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "#":
                put(img, x0 + i, y0 + j, color)


DISEASES = {"ataxia": "7a8a5a", "bone_break_fever": "b8a890", "brain_rot": "8a6a8a", "rattles": "9a9a60",
            "rockjoint": "707070", "witbane": "5a6a9a", "swamp_rot": "4a6a3a", "collywobbles": "a0a040",
            "greenspore": "5aa05a", "droops": "8a7a6a"}


def effect_icons():
    for name, col in DISEASES.items():
        img = icon_base(col)
        draw_rows(img, GERM, ink_for(col))
        out("mob_effect", name, img)
    for name, col in DIVINE_COLORS.items():
        img = icon_base(col)
        rows = SYMBOLS[name]
        draw_rows(img, rows, ink_for(col))
        out("mob_effect", "blessing_" + name, img)
    img = icon_base("8ccb5e")
    draw_rows(img, DROP, ink_for("8ccb5e"))
    out("mob_effect", "regenerate_stamina_food", img)
    img = icon_base("c8a050")
    draw_rows(img, BOWL, ink_for("c8a050"))
    out("mob_effect", "fortify_stamina_regen", img)


# ============================================================================ main

def items():
    cabbage_item()
    tomato_item()
    leek_item()
    leek_item("grilled_leeks", charred=True)
    garlic_item()
    seeds_item("cabbage_seeds", hexc("6a8a3a"))
    seeds_item("leek_seeds", hexc("2a2a22"))
    seeds_item("tomato_seeds", hexc("e8d8a0"))

    steak("raw_venison", hexc("a82a2a"), hexc("f0d8d0"), seed=1)
    steak("raw_horker_meat", hexc("b04858"), hexc("f4e8e0"), seed=2, shape="loaf")
    steak("raw_mammoth_snout", hexc("9a6a6a"), hexc("d8c0b8"), seed=3, shape="snout")
    steak("venison_chop", hexc("8a4a24"), hexc("c88a50"), cooked=True, seed=4)
    steak("horker_loaf", hexc("9a5a34"), hexc("d0a070"), cooked=True, seed=5, shape="loaf")
    steak("mammoth_steak", hexc("7a3e1e"), hexc("b87848"), cooked=True, seed=6)
    leg_roast("raw_goat_meat", hexc("c05050"), hexc("f0ead8"))
    leg_roast("leg_of_goat_roast", hexc("9a5228"), hexc("f0ead8"))
    salmon_steak()

    green, orange, brown, white, red = hexc("5a9a3a"), hexc("e08a30"), hexc("6a3a1a"), hexc("ece4c8"), hexc("c83a2a")
    bowl_dish("apple_cabbage_stew", hexc("c8b060"), [hexc("d84a3a"), green, white], 1)
    bowl_dish("beef_stew", hexc("7a3a1a"), [brown, orange, white], 2)
    bowl_dish("venison_stew", hexc("6a3418"), [hexc("4a2010"), hexc("d8c8a0"), green], 3)
    bowl_dish("horker_stew", hexc("8a5a3a"), [hexc("c8a080"), white, green], 4)
    bowl_dish("vegetable_soup", hexc("a8a050"), [green, orange, red, white], 5)
    bowl_dish("tomato_soup", hexc("c83a24"), [hexc("e86a50"), green], 6)
    bowl_dish("potato_soup", hexc("e0d0a0"), [hexc("c8a860"), green], 7)
    bowl_dish("elsweyr_fondue", hexc("f0c850"), [hexc("fff0a0"), hexc("e8a020")], 8)

    sweetroll()
    garlic_bread()
    honey_nut_treat()
    boiled_creme_treat()
    cheese_wheel_item()
    cheese_wedge("eidar_cheese_wedge", hexc("f4d060"), hexc("b87a20"), [(6, 11), (9, 9), (11, 11)])
    cheese_wedge("goat_cheese_wedge", hexc("f8f6ec"), hexc("c8c0a0"), [])
    sliced_goat_cheese()

    bottle("nord_mead", hexc("8a6a3a"), hexc("d89a30"), hexc("e8dcb8"), hexc("6a4a2a"), shape="jug")
    bottle("honningbrew_mead", hexc("c89a40"), hexc("f0b840"), hexc("2a2a6a"), hexc("6a4a2a"), shape="jug")
    bottle("black_briar_mead", hexc("3a2a3a"), hexc("6a2a4a"), hexc("1a1a1a"), hexc("6a4a2a"), shape="jug")
    bottle("ale", hexc("5a3a1a"), hexc("a86a20"), hexc("d8c8a0"), hexc("8a6a4a"))
    bottle("alto_wine", hexc("2a4a2a"), hexc("8a1a2a"), hexc("e8e0c8"), hexc("8a6a4a"), shape="wine")
    bottle("spiced_wine", hexc("4a2a1a"), hexc("aa3a1a"), hexc("d8b060"), hexc("8a6a4a"), shape="wine")
    skooma()


def blocks():
    for crop in ("cabbage_crop", "leek_crop", "tomato_crop", "garlic_crop"):
        for s in range(4):
            crop_stage(crop, s)
    cooking_pot_textures()
    cheese_block_textures()
    shrine_textures()
    depleted_ore_textures()


if __name__ == "__main__":
    items()
    blocks()
    effect_icons()
    print("survival textures written to", TEX)
