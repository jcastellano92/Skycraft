#!/usr/bin/env python3
"""Generates the Skyrim sky textures of the atmosphere module.

  assets/minecraft/textures/environment/moon_phases.png   Masser (replaces the vanilla moon), 8 phases in a 4x2 grid
  assets/skycraft/textures/environment/secunda_phases.png Secunda, 8 phases in a 4x2 grid (drawn by SkyRenderer)
  assets/skycraft/textures/environment/masser_halo.png    soft red glow drawn around Masser at night
  assets/skycraft/textures/environment/galaxy.png         horizontally tiling galaxy band

Phase order follows vanilla: 0 full, 1-3 waning, 4 new, 5-7 waxing. Requires numpy and Pillow.
"""
import pathlib

import numpy as np
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2] / "mod/src/main/resources/assets"
SKY = ROOT / "skycraft/textures/environment"
VANILLA = ROOT / "minecraft/textures/environment"


def periodic_noise(h, w, rng, falloff=1.8, low=1.0):
    """Tileable fractal noise in [0, 1] (FFT-filtered white noise is periodic in both axes)."""
    f = np.fft.fft2(rng.standard_normal((h, w)))
    fy = np.fft.fftfreq(h)[:, None] * h
    fx = np.fft.fftfreq(w)[None, :] * w
    k = np.sqrt(fx ** 2 + fy ** 2)
    k[0, 0] = 1.0
    f *= 1.0 / np.maximum(k, low) ** falloff
    f[0, 0] = 0
    n = np.real(np.fft.ifft2(f))
    n -= n.min()
    return n / max(n.max(), 1e-9)


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def surface(size, rng, base, dark, bright, craters, maria=0.5):
    """Albedo map of a moon (size x size, unit disc mapped to the square)."""
    n1 = periodic_noise(size, size, rng, 2.2)
    n2 = periodic_noise(size, size, rng, 1.4)
    mare = smoothstep(0.45, 0.7, n1) * maria
    alb = np.ones((size, size, 3)) * np.array(base, float)
    alb = alb * (1 - mare[..., None]) + np.array(dark, float) * mare[..., None]
    alb *= (0.85 + 0.3 * n2)[..., None]
    yy, xx = np.mgrid[0:size, 0:size] / size * 2 - 1
    for _ in range(craters):
        cx, cy = rng.uniform(-0.9, 0.9, 2)
        r = rng.uniform(0.03, 0.16) ** 1.2
        d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / r
        floor = np.clip(1 - d, 0, 1) ** 0.5 * 0.22
        rim = np.exp(-((d - 1.0) / 0.18) ** 2) * 0.18
        alb *= (1 - floor)[..., None]
        alb += (rim[..., None] * np.array(bright, float) * 0.6)
    return np.clip(alb, 0, 255)


def moon_phases(cell, radius, albedo, dark_side, rays=False):
    """4x2 grid of phases; returns RGBA uint8 array."""
    img = np.zeros((cell * 2, cell * 4, 4))
    size = albedo.shape[0]
    yy, xx = (np.mgrid[0:cell, 0:cell] + 0.5 - cell / 2) / radius
    rr = np.sqrt(xx ** 2 + yy ** 2)
    mask = np.clip((1.0 - rr) * radius, 0, 1)  # 1-pixel antialiased edge
    z = np.sqrt(np.clip(1 - rr ** 2, 0, 1))
    # sample the albedo map by disc coordinates
    ix = np.clip(((xx + 1) / 2 * (size - 1)).astype(int), 0, size - 1)
    iy = np.clip(((yy + 1) / 2 * (size - 1)).astype(int), 0, size - 1)
    alb = albedo[iy, ix]
    for p in range(8):
        phi = p * np.pi / 4
        lx, lz = np.sin(phi), np.cos(phi)
        lit = xx * lx + z * lz
        light = smoothstep(-0.06, 0.12, lit) * (0.55 + 0.45 * np.clip(lit, 0, 1) ** 0.5)
        limb = 0.7 + 0.3 * z
        col = alb * (light * limb)[..., None] + np.array(dark_side, float) * (1 - light)[..., None]
        a = mask * 255
        r, c = divmod(p, 4)
        img[r * cell:(r + 1) * cell, c * cell:(c + 1) * cell, :3] = col
        img[r * cell:(r + 1) * cell, c * cell:(c + 1) * cell, 3] = a
    img[..., :3] *= (img[..., 3:4] > 0)
    return np.clip(img, 0, 255).astype(np.uint8)


def masser(rng):
    cell, radius = 64, 20.0
    alb = surface(256, rng, base=(222, 132, 98), dark=(140, 62, 48), bright=(255, 190, 150), craters=55, maria=0.65)
    return moon_phases(cell, radius, alb, dark_side=(16, 5, 4))


def secunda(rng):
    cell, radius = 32, 10.5
    alb = surface(128, rng, base=(232, 236, 242), dark=(168, 172, 186), bright=(255, 255, 255), craters=25, maria=0.4)
    return moon_phases(cell, radius, alb, dark_side=(6, 6, 8))


def halo():
    s = 128
    yy, xx = (np.mgrid[0:s, 0:s] + 0.5 - s / 2) / (s / 2)
    r = np.sqrt(xx ** 2 + yy ** 2)
    inner = 0.35
    g = np.exp(-np.clip(r - inner, 0, None) / 0.16) * smoothstep(inner - 0.03, inner + 0.03, r) * (1 - smoothstep(0.8, 1.0, r))
    img = np.zeros((s, s, 4))
    img[..., 0] = 255
    img[..., 1] = 120
    img[..., 2] = 90
    img[..., 3] = np.clip(g * 200, 0, 255)
    img[..., :3] *= (img[..., 3:4] > 0)
    return img.astype(np.uint8)


def galaxy(rng):
    h, w = 128, 1024
    v = (np.arange(h) + 0.5) / h
    n_big = periodic_noise(h, w, rng, 2.0)
    n_mid = periodic_noise(h, w, rng, 1.5)
    n_dust = periodic_noise(h, w, rng, 1.7)
    wobble = (periodic_noise(1, w, rng, 2.0)[0] - 0.5) * 0.18
    centre = 0.5 + wobble[None, :]
    width = 0.17 * (0.8 + 0.5 * periodic_noise(1, w, rng, 2.0)[0])[None, :]
    band = np.exp(-((v[:, None] - centre) / width) ** 2)
    core = np.exp(-((v[:, None] - centre) / (width * 0.45)) ** 2)
    lane = 1 - 0.75 * np.exp(-((v[:, None] - centre - 0.03) / (width * 0.18)) ** 2) * smoothstep(0.35, 0.7, n_dust)
    bright = band * (0.45 + 0.55 * n_big) * (0.6 + 0.4 * n_mid) * lane + 0.35 * core * n_big * lane
    # resolved stars and knots
    stars = (rng.random((h, w)) < 0.004 * (0.3 + band)).astype(float) * rng.uniform(0.4, 1.0, (h, w))
    bright = np.clip(bright * 0.85 + stars * 0.9, 0, 1)
    edge = smoothstep(0.0, 0.12, v) * smoothstep(0.0, 0.12, 1 - v)
    bright *= edge[:, None]
    warm = smoothstep(0.4, 0.8, n_mid) * core
    rgb = np.empty((h, w, 3))
    rgb[..., 0] = 185 + 70 * warm
    rgb[..., 1] = 195 + 40 * warm
    rgb[..., 2] = 255 - 60 * warm
    img = np.zeros((h, w, 4))
    img[..., :3] = rgb
    img[..., 3] = bright * 255
    img[..., 3][img[..., 3] < 1.0] = 0
    return np.clip(img, 0, 255).astype(np.uint8)


def main():
    rng = np.random.default_rng(1771)
    SKY.mkdir(parents=True, exist_ok=True)
    VANILLA.mkdir(parents=True, exist_ok=True)
    Image.fromarray(masser(rng), "RGBA").save(VANILLA / "moon_phases.png")
    Image.fromarray(secunda(rng), "RGBA").save(SKY / "secunda_phases.png")
    Image.fromarray(halo(), "RGBA").save(SKY / "masser_halo.png")
    Image.fromarray(galaxy(rng), "RGBA").save(SKY / "galaxy.png")
    print("wrote", VANILLA / "moon_phases.png", "and", SKY)


if __name__ == "__main__":
    main()
