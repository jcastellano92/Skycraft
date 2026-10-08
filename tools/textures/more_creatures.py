#!/usr/bin/env python3
"""Generates textures for the new Skyrim creatures:
- Falmer (64x64)
- Frostbite Spider (64x32)
- Ice Wraith (64x32)
- Spriggan (64x64)
- Hagraven (64x64)
- Wispmother (64x64)
"""
import os
import random
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "skycraft", "textures", "entity", "creatures")
os.makedirs(OUT, exist_ok=True)

def clamp(v):
    return max(0, min(255, int(round(v))))

def shade(c, f):
    return tuple(clamp(x * f) for x in c[:3]) + ((c[3],) if len(c) > 3 else (255,))

def jitter(c, rnd, amt):
    d = rnd.uniform(-amt, amt)
    return tuple(clamp(x + d) for x in c[:3]) + ((c[3],) if len(c) > 3 else (255,))

def generate_humanoid(base_rgb, accent_rgb, eye_rgb, cloth_rgb, path, rnd_seed):
    rnd = random.Random(rnd_seed)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for y in range(64):
        for x in range(64):
            # Head (0..31, 0..15)
            if 0 <= y < 16 and 0 <= x < 32:
                c = jitter(base_rgb, rnd, 8)
                # Face area: (8..15, 8..15)
                if 8 <= x < 16 and 8 <= y < 16:
                    if (x in (9, 10, 13, 14)) and y == 12:
                        c = eye_rgb # eyes
                    elif 10 <= x <= 13 and y == 14:
                        c = shade(base_rgb, 0.75) # mouth
                img.putpixel((x, y), c)
            # Torso (16..39, 16..31)
            elif 16 <= y < 32 and 16 <= x < 40:
                c = jitter(cloth_rgb if y > 24 else base_rgb, rnd, 10)
                img.putpixel((x, y), c)
            # Right arm (40..55, 16..31)
            elif 16 <= y < 32 and 40 <= x < 56:
                c = jitter(base_rgb, rnd, 8)
                img.putpixel((x, y), c)
            # Right leg (0..15, 16..31)
            elif 16 <= y < 32 and 0 <= x < 16:
                c = jitter(shade(base_rgb, 0.9), rnd, 8)
                img.putpixel((x, y), c)
            # Left leg (16..31, 48..63)
            elif 48 <= y < 64 and 16 <= x < 32:
                c = jitter(shade(base_rgb, 0.9), rnd, 8)
                img.putpixel((x, y), c)
            # Left arm (32..47, 48..63)
            elif 48 <= y < 64 and 32 <= x < 48:
                c = jitter(base_rgb, rnd, 8)
                img.putpixel((x, y), c)
            # Torso outer / overlay (16..39, 32..47)
            elif 32 <= y < 48 and 16 <= x < 40:
                if y >= 36:
                    c = jitter(cloth_rgb, rnd, 12)
                    img.putpixel((x, y), c)

    img.save(path)
    print("Saved", path)

def generate_spider(path):
    rnd = random.Random(404)
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    # Chitin colors: dark bluish grey with frost patches
    base = (45, 55, 68, 255)
    frost = (190, 220, 240, 255)
    eye = (80, 200, 255, 255)
    for y in range(32):
        for x in range(64):
            # Spider UVs: Head 32..63, 4..15; Body/Abdomen 0..31, 12..31; Legs 16..63, 0..15
            is_frost = rnd.random() < 0.25
            c = frost if is_frost else base
            c = jitter(c, rnd, 10)
            # Red/cyan eyes on head front
            if 36 <= x <= 44 and 8 <= y <= 11 and rnd.random() < 0.6:
                c = eye
            img.putpixel((x, y), c)
    img.save(path)
    print("Saved", path)

def generate_wraith(path):
    rnd = random.Random(777)
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    frost = (200, 235, 255, 220)
    ice = (140, 195, 240, 200)
    core = (255, 255, 255, 255)
    for y in range(32):
        for x in range(64):
            if rnd.random() < 0.15:
                c = core
            elif rnd.random() < 0.5:
                c = frost
            else:
                c = ice
            img.putpixel((x, y), jitter(c, rnd, 15))
    img.save(path)
    print("Saved", path)

def main():
    # 1. Falmer: pale sickly subterranean skin, chitin loincloth, milky eyes
    generate_humanoid(
        base_rgb=(185, 175, 155),
        accent_rgb=(140, 130, 115),
        eye_rgb=(240, 240, 235),
        cloth_rgb=(85, 55, 40),
        path=os.path.join(OUT, "falmer.png"),
        rnd_seed=101
    )

    # 2. Spriggan: dark brown bark, bright green/amber leaves and heart
    generate_humanoid(
        base_rgb=(75, 52, 34),
        accent_rgb=(45, 30, 20),
        eye_rgb=(120, 220, 50),
        cloth_rgb=(50, 120, 40),
        path=os.path.join(OUT, "spriggan.png"),
        rnd_seed=202
    )

    # 3. Hagraven: feathered raven plumage, haggard skin, raven eyes
    generate_humanoid(
        base_rgb=(145, 140, 135),
        accent_rgb=(90, 85, 80),
        eye_rgb=(220, 180, 40),
        cloth_rgb=(35, 35, 40),
        path=os.path.join(OUT, "hagraven.png"),
        rnd_seed=303
    )

    # 4. Wispmother: ethereal white-cyan spectral robes, glowing blue eyes
    generate_humanoid(
        base_rgb=(210, 230, 245),
        accent_rgb=(170, 200, 225),
        eye_rgb=(100, 220, 255),
        cloth_rgb=(190, 215, 235),
        path=os.path.join(OUT, "wispmother.png"),
        rnd_seed=404
    )

    # 5. Frostbite Spider
    generate_spider(os.path.join(OUT, "frostbite_spider.png"))

    # 6. Ice Wraith
    generate_wraith(os.path.join(OUT, "ice_wraith.png"))

if __name__ == "__main__":
    main()
