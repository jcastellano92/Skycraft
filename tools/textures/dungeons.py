"""Generates the dungeons module's Dwemer automaton textures (PIL).

Writes PNGs into mod/src/main/resources/assets/skycraft/textures/entity/dwemer/:
  dwarven_spider.png      64x32   (DwarvenSpiderModel UVs)
  dwarven_sphere.png      64x64   (DwarvenSphereModel UVs)
  dwarven_centurion.png   128x64  (DwarvenCenturionModel UVs)

Every model box is painted face by face from the same UV layout as the Java models: brushed bronze with darker
seams, rivets along the edges, verdigris in the crevices and soul-blue / furnace-orange glows where the eyes and
boilers are. Deterministic (fixed seeds). Run:  python3 tools/textures/dungeons.py
"""
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "skycraft", "textures", "entity", "dwemer")

BRONZE = (178, 128, 60)
BRONZE_DARK = (112, 72, 32)
BRONZE_LIGHT = (232, 186, 102)
VERDIGRIS = (92, 150, 128)
IRON = (70, 66, 64)
SOUL = (96, 224, 255)
FURNACE = (255, 168, 64)


def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(c, f):
    return tuple(clamp(x * f) for x in c[:3])


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def box_faces(u, v, w, h, d):
    """UV rectangles (x, y, width, height) of a Minecraft model box at texOffs (u, v) with size (w, h, d)."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def metal(img, rect, rnd, base=BRONZE, rivets=True, patina=0.08, light=1.0):
    """Brushed metal panel: horizontal brushing, bevelled edges, rivets in the corners, a little verdigris."""
    x0, y0, w, h = rect
    px = img.load()
    for y in range(h):
        streak = rnd.uniform(-0.06, 0.06)
        for x in range(w):
            f = light * (1.0 + streak + rnd.uniform(-0.05, 0.05))
            c = shade(base, f)
            if y == 0 or x == 0:
                c = mix(c, BRONZE_LIGHT, 0.45)
            elif y == h - 1 or x == w - 1:
                c = mix(c, BRONZE_DARK, 0.55)
            if rnd.random() < patina and 0 < x < w - 1 and 0 < y < h - 1:
                c = mix(c, VERDIGRIS, rnd.uniform(0.4, 0.8))
            px[x0 + x, y0 + y] = c + (255,)
    if rivets and w >= 4 and h >= 4:
        for rx, ry in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
            px[x0 + rx, y0 + ry] = BRONZE_LIGHT + (255,)


def seam(img, rect, horizontal=True, at=0.5, color=BRONZE_DARK):
    x0, y0, w, h = rect
    px = img.load()
    if horizontal:
        y = y0 + int(h * at)
        for x in range(x0, x0 + w):
            px[x, y] = color + (255,)
    else:
        x = x0 + int(w * at)
        for y in range(y0, y0 + h):
            px[x, y] = color + (255,)


def glow(img, rect, color, core=True):
    x0, y0, w, h = rect
    px = img.load()
    for y in range(h):
        for x in range(w):
            edge = x in (0, w - 1) or y in (0, h - 1)
            c = mix(color, (255, 255, 255), 0.45) if core and not edge else color
            px[x0 + x, y0 + y] = c + (255,)


def grate(img, rect, color):
    """Dark vertical slats over a glowing back (boilers, helm grills)."""
    x0, y0, w, h = rect
    px = img.load()
    for y in range(h):
        for x in range(w):
            px[x0 + x, y0 + y] = (IRON if x % 2 == 0 else color) + (255,)


def paint_box(img, u, v, w, h, d, rnd, **kw):
    for name, rect in box_faces(u, v, w, h, d).items():
        light = 1.12 if name == "top" else 0.78 if name == "bottom" else 0.95 if name in ("left", "right") else 1.0
        metal(img, rect, rnd, light=light, **kw)


def spider():
    rnd = random.Random(1701)
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    paint_box(img, 0, 0, 6, 5, 7, rnd)                       # body
    f = box_faces(0, 0, 6, 5, 7)
    seam(img, f["top"], horizontal=False)
    seam(img, f["left"], at=0.6)
    seam(img, f["right"], at=0.6)
    paint_box(img, 0, 12, 4, 2, 4, rnd, base=BRONZE_LIGHT)   # dome
    dome = box_faces(0, 12, 4, 2, 4)
    glow(img, (dome["top"][0] + 1, dome["top"][1] + 1, 2, 2), SOUL)
    paint_box(img, 26, 0, 4, 3, 3, rnd)                       # head
    paint_box(img, 26, 6, 2, 1, 1, rnd, rivets=False)        # lens
    glow(img, box_faces(26, 6, 2, 1, 1)["front"], SOUL, core=False)
    head = box_faces(26, 0, 4, 3, 3)["front"]
    img.putpixel((head[0] + 1, head[1] + 1), SOUL + (255,))
    img.putpixel((head[0] + 2, head[1] + 1), SOUL + (255,))
    paint_box(img, 0, 20, 5, 1, 1, rnd, rivets=False, base=BRONZE_DARK)   # upper leg
    paint_box(img, 0, 24, 8, 1, 1, rnd, rivets=False, base=BRONZE)        # lower leg
    return img


def sphere():
    rnd = random.Random(1702)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    paint_box(img, 0, 0, 10, 10, 10, rnd)                     # rolled ball
    for name, rect in box_faces(0, 0, 10, 10, 10).items():
        seam(img, rect, horizontal=True, at=0.5)
        seam(img, rect, horizontal=False, at=0.5)
    front = box_faces(0, 0, 10, 10, 10)["front"]
    glow(img, (front[0] + 4, front[1] + 3, 2, 2), SOUL)
    paint_box(img, 0, 20, 9, 9, 9, rnd, base=BRONZE_DARK)     # wheel
    for name, rect in box_faces(0, 20, 9, 9, 9).items():
        if name in ("left", "right"):
            x0, y0, w, h = rect
            for i in range(1, w - 1):
                img.putpixel((x0 + i, y0 + h // 2), IRON + (255,))
                img.putpixel((x0 + w // 2, y0 + i), IRON + (255,))
    paint_box(img, 0, 38, 8, 9, 5, rnd)                       # torso
    t = box_faces(0, 38, 8, 9, 5)["front"]
    glow(img, (t[0] + 3, t[1] + 2, 2, 2), SOUL)
    seam(img, t, at=0.7)
    paint_box(img, 40, 0, 5, 5, 5, rnd, base=BRONZE_LIGHT)   # head
    hf = box_faces(40, 0, 5, 5, 5)["front"]
    for x in range(hf[0] + 1, hf[0] + 4):
        img.putpixel((x, hf[1] + 2), SOUL + (255,))
    paint_box(img, 40, 10, 3, 8, 3, rnd)                      # arm
    paint_box(img, 40, 22, 1, 10, 2, rnd, rivets=False, base=(200, 200, 196))   # blade (steel)
    paint_box(img, 46, 22, 1, 1, 6, rnd, rivets=False, base=BRONZE_DARK)        # crossbow stock
    paint_box(img, 26, 52, 7, 1, 2, rnd, rivets=False, base=BRONZE_DARK)        # crossbow limbs
    return img


def centurion():
    rnd = random.Random(1703)
    img = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    paint_box(img, 0, 0, 6, 12, 6, rnd)                       # leg
    for rect in box_faces(0, 0, 6, 12, 6).values():
        seam(img, rect, at=0.45)
    paint_box(img, 0, 18, 12, 4, 8, rnd, base=BRONZE_DARK)    # hips
    paint_box(img, 0, 30, 14, 12, 8, rnd)                     # chest
    chest = box_faces(0, 30, 14, 12, 8)
    seam(img, chest["front"], horizontal=False)
    seam(img, chest["back"], at=0.3)
    paint_box(img, 44, 34, 6, 6, 3, rnd, base=BRONZE_LIGHT)   # boiler
    bf = box_faces(44, 34, 6, 6, 3)["front"]
    grate(img, (bf[0] + 1, bf[1] + 1, bf[2] - 2, bf[3] - 2), FURNACE)
    paint_box(img, 104, 0, 2, 8, 2, rnd, rivets=False, base=IRON)   # exhaust stacks
    st = box_faces(104, 0, 2, 8, 2)["top"]
    glow(img, st, (40, 36, 34), core=False)
    paint_box(img, 48, 0, 8, 7, 8, rnd, base=BRONZE_LIGHT)    # helm
    hf = box_faces(48, 0, 8, 7, 8)["front"]
    for x in range(hf[0] + 1, hf[0] + 7):
        img.putpixel((x, hf[1] + 2), SOUL + (255,))
    grate(img, (hf[0] + 2, hf[1] + 4, 4, 2), FURNACE)
    paint_box(img, 80, 0, 6, 14, 6, rnd)                      # arm
    for rect in box_faces(80, 0, 6, 14, 6).values():
        seam(img, rect, at=0.55)
    paint_box(img, 0, 50, 8, 4, 8, rnd, base=BRONZE_LIGHT)    # pauldron
    paint_box(img, 48, 15, 6, 6, 10, rnd, base=BRONZE_DARK)   # hammer
    paint_box(img, 80, 20, 1, 10, 4, rnd, rivets=False, base=(200, 200, 196))   # blade
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    spider().save(os.path.join(OUT, "dwarven_spider.png"))
    sphere().save(os.path.join(OUT, "dwarven_sphere.png"))
    centurion().save(os.path.join(OUT, "dwarven_centurion.png"))
    print("wrote", OUT)


if __name__ == "__main__":
    main()
