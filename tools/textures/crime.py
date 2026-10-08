#!/usr/bin/env python3
"""Crime module textures (16x16 pixel art).

lockpick.png: a thin steel pick running from the bottom-left (cloth-wrapped grip) to the top-right, ending in a small
hook, like Skyrim's lockpicks. The lockpicking minigame itself is drawn with GuiGraphics fills, so no GUI textures.

Run from the repository root:  python3 tools/textures/crime.py
"""
import os

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
TEX = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "skycraft", "textures", "item")

OUTLINE = (0x26, 0x22, 0x1E, 255)
STEEL_HI = (0xE6, 0xE8, 0xEA, 255)
STEEL = (0xB4, 0xB8, 0xBC, 255)
STEEL_LO = (0x7C, 0x80, 0x86, 255)
GRIP = (0x6A, 0x4A, 0x2E, 255)
GRIP_HI = (0x8E, 0x6A, 0x44, 255)
GRIP_LO = (0x4A, 0x32, 0x1E, 255)


def lockpick():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()

    def put(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = c

    # shaft: diagonal from (5,10) to (13,2), one pixel thick with a highlight above and shadow below
    for i in range(9):
        x, y = 5 + i, 10 - i
        put(x, y - 1, OUTLINE) if px[x, y - 1][3] == 0 else None
        put(x + 1, y, OUTLINE) if px[x + 1, y][3] == 0 else None
    for i in range(9):
        x, y = 5 + i, 10 - i
        put(x, y, STEEL if i % 3 else STEEL_HI)
    for i in range(8):
        put(5 + i, 11 - i, STEEL_LO)
    # hooked tip at the top-right
    put(13, 1, STEEL_HI)
    put(14, 1, STEEL)
    put(14, 2, STEEL_LO)
    put(13, 0, OUTLINE)
    put(14, 0, OUTLINE)
    put(15, 1, OUTLINE)
    put(15, 2, OUTLINE)
    put(14, 3, OUTLINE)

    # cloth-wrapped grip at the bottom-left, diagonal block from (1,14) to (5,10)
    grip = [(1, 14), (2, 13), (3, 12), (4, 11), (5, 10), (2, 14), (3, 13), (4, 12), (5, 11), (1, 13), (2, 12), (3, 11), (4, 10)]
    for (x, y) in grip:
        put(x, y, GRIP)
    for (x, y) in [(1, 13), (2, 12), (3, 11), (4, 10)]:
        put(x, y, GRIP_HI)
    for (x, y) in [(2, 14), (3, 13), (4, 12), (5, 11)]:
        put(x, y, GRIP_LO)
    # wrap bands
    put(2, 13, GRIP_HI)
    put(4, 11, GRIP_LO)
    # outline around the grip
    for (x, y) in grip:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < 16 and 0 <= ny < 16 and px[nx, ny][3] == 0:
                put(nx, ny, OUTLINE)
    # pommel ring
    put(0, 15, STEEL_LO)
    put(1, 15, OUTLINE)
    put(0, 14, OUTLINE)
    return img


def main():
    os.makedirs(TEX, exist_ok=True)
    lockpick().save(os.path.join(TEX, "lockpick.png"))
    print("wrote", os.path.join(TEX, "lockpick.png"))


if __name__ == "__main__":
    main()
