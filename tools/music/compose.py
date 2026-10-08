#!/usr/bin/env python3
"""
Skycraft original soundtrack: a small procedural composer + synthesiser.

Every piece is written by a seeded composition engine (modal chord progressions, voice-led pads, motif
development for the melodies, pattern libraries for drums and ostinati) and synthesised from scratch:

  * strings / drones .... detuned polyBLEP saw ensembles, slow attacks, zero-phase low-pass filtering
  * pads ................ triangle + saw, dark filter
  * choir ............... saw sources with vibrato/jitter through vowel formant filters ("ah", "oh", "oo")
  * flute / horn / fiddle additive synthesis with delayed vibrato, portamento, breath noise, brightness envelopes
  * harp / lute ......... block-vectorised Karplus-Strong with fractional delay and body resonances
  * drums ............... modal timpani, taiko, frame drum, rims, metallic hits, cymbal swells, sub booms
  * space ............... Freeverb (evaluated in closed form in the frequency domain and FFT-convolved)

Nothing is sampled or transcribed: all melodies come from the random motif generator, so the music is original
to Skycraft and ships with the mod.  It is *not* Skyrim's soundtrack; players who own Skyrim can map their own
copy of it onto the same events with tools/music/import_skyrim_ost.py (see docs/MUSIC.md).

Usage:
    python3 tools/music/compose.py                 # render every track to assets/skycraft/sounds/music/*.ogg
    python3 tools/music/compose.py town_1 combat_2 # only these
    python3 tools/music/compose.py --wav out/      # also keep 16-bit WAVs
    python3 tools/music/compose.py --list

Requires Python 3.9+, numpy, and ffmpeg built with libvorbis (Minecraft needs Ogg Vorbis).
"""
from __future__ import annotations

import argparse
import math
import pathlib
import shutil
import subprocess
import sys
import time
import wave

import numpy as np

SR = 44100
REPO = pathlib.Path(__file__).resolve().parents[2]
OUT_DIR = REPO / "mod/src/main/resources/assets/skycraft/sounds/music"


# =====================================================================================================  basics

def ns(t: float) -> int:
    return max(0, int(round(t * SR)))


def hz(m):
    return 440.0 * 2.0 ** ((np.asarray(m, dtype=np.float64) - 69.0) / 12.0)


def pow2(n: int) -> int:
    return 1 << int(math.ceil(math.log2(max(2, n))))


def box(x: np.ndarray, length: int, causal: bool = False) -> np.ndarray:
    """Moving average (O(n) via cumulative sums); centred unless causal."""
    length = int(length)
    if length <= 1 or len(x) == 0:
        return x.copy()
    c = np.concatenate(([0.0], np.cumsum(x)))
    n = len(x)
    idx = np.arange(n)
    if causal:
        lo = np.maximum(0, idx + 1 - length)
        hi = idx + 1
    else:
        lo = np.clip(idx - length // 2, 0, n)
        hi = np.clip(idx - length // 2 + length, 0, n)
    return (c[hi] - c[lo]) / np.maximum(1, hi - lo)


def smooth_noise(n: int, rate: float, rng) -> np.ndarray:
    """Smooth random curve, roughly in [-1, 1], changing about `rate` times per second."""
    if n <= 0:
        return np.zeros(0)
    step = max(2, int(SR / rate))
    k = n // step + 3
    pts = rng.uniform(-1.0, 1.0, k)
    y = np.interp(np.arange(n), np.arange(k) * step, pts)
    return box(y, step) * 1.6


# ------------------------------------------------------------------------------------------------  filters

def ffilt(x: np.ndarray, H) -> np.ndarray:
    """Zero-phase FFT filter along the last axis. H(freqs_hz) -> magnitude response."""
    n = x.shape[-1]
    if n == 0:
        return x
    nfft = pow2(n + 8192)
    f = np.fft.rfftfreq(nfft, 1.0 / SR)
    X = np.fft.rfft(x, nfft, axis=-1)
    X *= H(f)
    return np.fft.irfft(X, nfft, axis=-1)[..., :n]


def lp(fc, order=2):
    return lambda f: 1.0 / np.sqrt(1.0 + (f / fc) ** (2 * order))


def hp(fc, order=2):
    return lambda f: 1.0 / np.sqrt(1.0 + (fc / np.maximum(f, 1e-3)) ** (2 * order))


def bp(lo, hi, order=2):
    a, b = hp(lo, order), lp(hi, order)
    return lambda f: a(f) * b(f)


def chain(*hs):
    def h(f):
        out = np.ones_like(f)
        for g in hs:
            out = out * g(f)
        return out
    return h


def peaks(spec, floor=0.0):
    """Sum of Lorentzian resonances: spec = [(freq, bandwidth, gain), ...]."""
    def h(f):
        out = np.full_like(f, floor)
        for fc, bw, g in spec:
            out += g / (1.0 + ((f - fc) / (bw * 0.5)) ** 2)
        return out
    return h


def shelf(fc, gain):
    """Smooth high shelf: 1 below fc, `gain` above."""
    return lambda f: 1.0 + (gain - 1.0) / (1.0 + (fc / np.maximum(f, 1e-3)) ** 2)


# ------------------------------------------------------------------------------------------------  oscillators

def saw(freq: np.ndarray, phase0: float = 0.0) -> np.ndarray:
    """Band-limited (polyBLEP) sawtooth for a per-sample frequency array."""
    dt = np.minimum(np.asarray(freq, dtype=np.float64) / SR, 0.45)
    ph = (np.cumsum(dt) + phase0) % 1.0
    y = 2.0 * ph - 1.0
    m = ph < dt
    t = ph[m] / dt[m]
    y[m] -= t + t - t * t - 1.0
    m = ph > 1.0 - dt
    t = (ph[m] - 1.0) / dt[m]
    y[m] -= t * t + t + t + 1.0
    return y


def tri(freq: np.ndarray, phase0: float = 0.0) -> np.ndarray:
    ph = (np.cumsum(np.asarray(freq) / SR) + phase0) % 1.0
    return 2.0 * np.abs(2.0 * ph - 1.0) - 1.0


def env_note(dur: float, attack: float, release: float, decay_to: float = 1.0, decay_time: float = 1.0) -> np.ndarray:
    """Raised-cosine attack, optional exponential settle to `decay_to`, raised-cosine release after `dur`."""
    n = ns(dur + release)
    d = min(n, max(1, ns(dur)))
    e = np.ones(n)
    a = min(n, max(1, ns(attack)))
    e[:a] = 0.5 - 0.5 * np.cos(np.linspace(0.0, np.pi, a))
    if decay_to != 1.0 and a < n:
        t = np.arange(n - a) / SR
        e[a:] = decay_to + (1.0 - decay_to) * np.exp(-t / max(1e-3, decay_time))
    r = n - d
    if r > 0:
        level = e[d - 1]
        e[d:] = level * (0.5 + 0.5 * np.cos(np.linspace(0.0, np.pi, r)))
    return e


def pan2(x: np.ndarray, p: float) -> np.ndarray:
    a = (max(-1.0, min(1.0, p)) + 1.0) * math.pi / 4.0
    return np.vstack((x * math.cos(a), x * math.sin(a)))


def softclip(x, drive=1.0):
    return np.tanh(x * drive) / math.tanh(drive)


# =====================================================================================================  mixing

class Mix:
    """Stereo dry bus + reverb send bus."""

    def __init__(self, seconds: float):
        self.n = ns(seconds)
        self.dry = np.zeros((2, self.n))
        self.send = np.zeros((2, self.n))

    def add(self, sig: np.ndarray, t: float, gain: float = 1.0, send: float = 0.3, pan: float = 0.0):
        if sig.ndim == 1:
            sig = pan2(sig, pan)
        s = ns(t)
        if s >= self.n or gain == 0.0:
            return
        e = min(self.n, s + sig.shape[1])
        seg = sig[:, : e - s] * gain
        self.dry[:, s:e] += seg
        if send > 0:
            self.send[:, s:e] += seg * send


COMBS = [1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617]
ALLPASSES = [556, 441, 341, 225]
_IR_CACHE: dict = {}


def freeverb_ir(room: float, damp: float, seconds: float = 6.0, predelay: float = 0.02, tone: float = 9000.0):
    """Stereo impulse response of Jezar's Freeverb (8 damped combs + 4 allpasses per side, stereo spread 23),
    computed exactly from its transfer function in the frequency domain, energy-normalised."""
    key = (room, damp, seconds, predelay, tone)
    if key in _IR_CACHE:
        return _IR_CACHE[key]
    length = ns(seconds)
    nfft = pow2(length * 2)
    w = 2.0 * np.pi * np.arange(nfft // 2 + 1) / nfft
    zi = np.exp(-1j * w)
    fb = room * 0.28 + 0.7
    d = damp * 0.4
    irs = []
    for ch in range(2):
        H = np.zeros_like(zi)
        for c in COMBS:
            D = int(round((c + 23 * ch) * SR / 44100))
            zD = np.exp(-1j * w * D)
            H += zD / (1.0 - fb * (1.0 - d) * zD / (1.0 - d * zi))
        for a in ALLPASSES:
            D = int(round((a + 23 * ch) * SR / 44100))
            zD = np.exp(-1j * w * D)
            H *= (-1.0 + 1.5 * zD) / (1.0 - 0.5 * zD)
        ir = np.fft.irfft(H * 0.015, nfft)[:length]
        irs.append(ir)
    ir = np.vstack(irs)
    ir = ffilt(ir, chain(lp(tone, 1), hp(120, 1)))
    fade = ns(seconds * 0.25)
    ir[:, -fade:] *= np.linspace(1.0, 0.0, fade) ** 2
    ir = np.concatenate((np.zeros((2, ns(predelay))), ir), axis=1)
    ir /= math.sqrt(np.sum(ir ** 2) / 2.0)
    _IR_CACHE[key] = ir
    return ir


def fft_convolve(x: np.ndarray, ir: np.ndarray) -> np.ndarray:
    nfft = pow2(len(x) + len(ir))
    return np.fft.irfft(np.fft.rfft(x, nfft) * np.fft.rfft(ir, nfft), nfft)[: len(x)]


def render_mix(mix: Mix, room=0.85, damp=0.45, wet=0.35, width=1.0, tone=9000.0, predelay=0.025) -> np.ndarray:
    ir = freeverb_ir(room, damp, predelay=predelay, tone=tone)
    wl = fft_convolve(mix.send[0], ir[0])
    wr = fft_convolve(mix.send[1], ir[1])
    w1 = 0.5 + width / 2.0
    w2 = (1.0 - width) / 2.0
    wet_l = wl * w1 + wr * w2
    wet_r = wr * w1 + wl * w2
    # gentle M/S widening of the reverb return
    mid = (wet_l + wet_r) * 0.5
    side = (wet_l - wet_r) * 0.5 * 1.25
    return mix.dry + wet * np.vstack((mid + side, mid - side))


def master(x: np.ndarray, target_db: float = -20.0, max_len: float = 0.0) -> np.ndarray:
    """High-pass, loudness normalisation (loud passages at target RMS), slow compression, soft peak limiting."""
    x = ffilt(x, hp(28.0, 2))
    mono = np.mean(x, axis=0)
    env = np.sqrt(box(mono ** 2, ns(0.4)) + 1e-12)
    loud = np.percentile(env, 92)
    x = x * (10 ** (target_db / 20.0) / max(loud, 1e-9))
    env = env * (10 ** (target_db / 20.0) / max(loud, 1e-9))
    thr = 10 ** ((target_db + 2.0) / 20.0)
    gain = np.where(env > thr, (env / thr) ** (1.0 / 2.2 - 1.0), 1.0)
    gain = box(gain, ns(0.25))
    x = x * gain
    k, c = 0.55, 0.94
    a = np.abs(x)
    over = a > k
    x[over] = np.sign(x[over]) * (k + (c - k) * np.tanh((a[over] - k) / (c - k)))
    # trim the reverb tail once it is inaudible, fade both ends
    level = box(np.max(np.abs(x), axis=0), ns(0.05))
    alive = np.nonzero(level > 10 ** (-66 / 20.0))[0]
    end = min(x.shape[1], (alive[-1] if len(alive) else x.shape[1]) + ns(0.3))
    if max_len > 0:
        end = min(end, ns(max_len))
    x = x[:, :end]
    f_in, f_out = ns(0.01), min(ns(1.5), end // 4)
    x[:, :f_in] *= np.linspace(0, 1, f_in)
    x[:, -f_out:] *= np.linspace(1, 0, f_out) ** 1.5
    return x


# =====================================================================================================  instruments

VOWELS_HIGH = {
    "a": [(800, 80, 1.0), (1150, 90, 0.5), (2900, 120, 0.25), (3900, 130, 0.1), (4950, 140, 0.05)],
    "o": [(450, 70, 1.0), (800, 80, 0.3), (2830, 100, 0.08), (3800, 130, 0.06), (4950, 135, 0.02)],
    "u": [(350, 50, 1.0), (600, 60, 0.12), (2700, 170, 0.04), (2900, 180, 0.03), (3300, 200, 0.01)],
    "e": [(400, 60, 1.0), (1600, 80, 0.25), (2700, 120, 0.18), (3300, 150, 0.1), (4950, 200, 0.02)],
}
VOWELS_LOW = {
    "a": [(600, 60, 1.0), (1040, 70, 0.45), (2250, 110, 0.35), (2450, 120, 0.35), (2750, 130, 0.1)],
    "o": [(400, 40, 1.0), (750, 80, 0.28), (2400, 100, 0.09), (2600, 120, 0.1), (2900, 120, 0.01)],
    "u": [(250, 60, 1.0), (595, 40, 0.12), (2400, 100, 0.03), (2675, 120, 0.02), (2950, 120, 0.01)],
    "e": [(400, 40, 1.0), (1620, 80, 0.25), (2400, 100, 0.35), (2800, 120, 0.25), (3100, 120, 0.1)],
}


def strings_note(midi, dur, rng, attack=1.2, release=2.0, bright=1.0, voices=4, detune=9.0, vib=0.09,
                 spread=0.8, short=False):
    """Ensemble of detuned band-limited saws, low-passed: bowed strings / drones."""
    n = ns(dur + release)
    t = np.arange(n) / SR
    f0 = float(hz(midi))
    out = np.zeros((2, n))
    for v in range(voices):
        cents = rng.uniform(-detune, detune)
        rate = rng.uniform(4.4, 5.6)
        vibenv = np.clip((t - 0.35) / 1.2, 0.0, 1.0) * vib if not short else 0.0
        drift = smooth_noise(n, 0.6, rng) * 0.035
        m = midi + cents / 100.0 + vibenv * np.sin(2 * np.pi * rate * t + rng.uniform(0, 6.28)) + drift
        y = saw(hz(m), rng.random())
        p = (-1.0 + 2.0 * v / max(1, voices - 1)) * spread if voices > 1 else 0.0
        out += pan2(y, p)
    cutoff = min(6500.0, f0 * (2.5 + 5.0 * bright) + 300.0 * bright)
    out = ffilt(out, chain(lp(cutoff, 2), hp(max(30.0, f0 * 0.6), 1), peaks([(450, 500, 0.35), (1400, 900, 0.25)], 1.0)))
    out *= env_note(dur, attack, release) / math.sqrt(voices)
    return out


def pad_note(midi, dur, rng, attack=2.0, release=3.0, cutoff=1100.0):
    n = ns(dur + release)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    for v, p in enumerate((-0.6, 0.6)):
        drift = smooth_noise(n, 0.4, rng) * 0.05 + (0.06 if v else -0.06)
        f = hz(midi + drift + 0.03 * np.sin(2 * np.pi * 0.3 * t + v))
        y = tri(f, rng.random()) + 0.35 * saw(f * 1.003, rng.random())
        out += pan2(y, p)
    out = ffilt(out, chain(lp(cutoff, 2), hp(60, 1)))
    return out * env_note(dur, attack, release) * 0.6


def choir_note(midi, dur, rng, vowel="a", voices=4, attack=1.0, release=1.8, bright=1.0, breath=0.05,
               vib=0.16, low=None):
    """Ensemble 'ahh'/'ooh': saw sources with independent vibrato & jitter through vowel formants."""
    n = ns(dur + release)
    t = np.arange(n) / SR
    low = midi < 58 if low is None else low
    table = (VOWELS_LOW if low else VOWELS_HIGH)[vowel]
    formant = peaks([(f, bw * 1.7, g) for f, bw, g in table], 0.015)
    out = np.zeros((2, n))
    for v in range(voices):
        cents = rng.uniform(-11, 11)
        rate = rng.uniform(4.8, 6.0)
        depth = vib * rng.uniform(0.7, 1.3)
        vibenv = np.clip((t - 0.2) / 0.8, 0.0, 1.0) * depth
        jitter = smooth_noise(n, 3.0, rng) * 0.05 + smooth_noise(n, 0.5, rng) * 0.04
        m = midi + cents / 100.0 + vibenv * np.sin(2 * np.pi * rate * t + rng.uniform(0, 6.28)) + jitter
        y = saw(hz(m), rng.random())
        amp = 1.0 + 0.15 * smooth_noise(n, 1.5, rng)
        out += pan2(y * amp, rng.uniform(-0.8, 0.8))
    out += breath * pan2(rng.standard_normal(n), 0.0) * 0.5
    out = ffilt(out, chain(formant, lp(2500 + 2500 * bright, 1), hp(max(80.0, float(hz(midi)) * 0.7), 2)))
    return out * env_note(dur, attack, release) / math.sqrt(voices) * 1.4


def ks_pluck(midi, dur, rng, decay=2.0, bright=0.6, pick=0.14):
    """Karplus-Strong string, vectorised one period at a time; linear fractional delay keeps it in tune."""
    f = float(hz(midi))
    n = ns(dur)
    period = SR / f
    P = int(period - 0.5)
    frac = period - 0.5 - P
    if P < 4:
        P, frac = 4, 0.0
    y = np.zeros(n + P + 2)
    exc = rng.uniform(-1.0, 1.0, P)
    exc = box(exc, 1 + int((1.0 - bright) * 7))
    k = max(1, int(pick * P))
    exc = exc - np.roll(exc, k)
    exc -= exc.mean()
    y[2:2 + P] = exc
    g = 10 ** (-3.0 * period / (decay * SR))
    a0, a1, a2 = 0.5 * (1.0 - frac), 0.5, 0.5 * frac
    s = 2 + P
    total = len(y)
    while s < total:
        e = min(s + P, total)
        y[s:e] = g * (a0 * y[s - P:e - P] + a1 * y[s - P - 1:e - P - 1] + a2 * y[s - P - 2:e - P - 2])
        s = e
    out = y[2:2 + n]
    fade = min(n, ns(0.05))
    out[-fade:] *= np.linspace(1, 0, fade)
    return out


def harp(midi, dur, rng, bright=0.7):
    y = ks_pluck(midi, dur, rng, decay=2.5 + max(0.0, (70 - midi) * 0.06), bright=bright, pick=0.18)
    return ffilt(y, chain(hp(90, 1), lp(5000, 1))) * 0.5


def lute(midi, dur, rng, bright=0.55):
    y = ks_pluck(midi, dur, rng, decay=1.1 + max(0.0, (64 - midi) * 0.04), bright=bright, pick=0.11)
    return ffilt(y, chain(hp(85, 1), lp(4200, 1), peaks([(230, 120, 0.8), (480, 200, 0.4), (2600, 900, 0.25)], 0.7))) * 0.6


def bell(midi, dur, rng, bright=0.5):
    """Glassy celesta/bell: few inharmonic partials with exponential decays."""
    n = ns(dur)
    t = np.arange(n) / SR
    f = float(hz(midi))
    y = np.zeros(n)
    for r, a, d in ((1.0, 1.0, 2.2), (2.0, 0.35 * bright, 1.2), (3.01, 0.12 * bright, 0.6), (4.17, 0.08 * bright, 0.4)):
        if f * r < SR / 2.2:
            y += a * np.sin(2 * np.pi * f * r * t + rng.random() * 6.28) * np.exp(-t / d)
    y *= np.minimum(1.0, t / 0.003)
    return y * 0.4


def modal_hit(f0, ratios, amps, decays, dur, rng, drop=0.0, drop_time=0.05):
    n = ns(dur)
    t = np.arange(n) / SR
    bend = 1.0 + drop * np.exp(-t / drop_time)
    y = np.zeros(n)
    for r, a, d in zip(ratios, amps, decays):
        if f0 * r * (1 + drop) >= SR / 2.2:
            continue
        ph = 2 * np.pi * np.cumsum(f0 * r * bend) / SR + rng.random() * 6.28
        y += a * np.sin(ph) * np.exp(-t / d)
    return y


def noise_burst(dur, rng, band, decay):
    n = ns(dur)
    t = np.arange(n) / SR
    return ffilt(rng.standard_normal(n), bp(*band)) * np.exp(-t / decay)


def timpani(midi, vel, rng, dur=3.0):
    f = float(hz(midi))
    y = modal_hit(f, [1.0, 1.504, 1.742, 2.0, 2.245, 2.494], [1.0, 0.55, 0.3, 0.35, 0.18, 0.12],
                  [1.8, 1.1, 0.7, 0.8, 0.5, 0.4], dur, rng, drop=0.04, drop_time=0.06)
    y += noise_burst(dur, rng, (60, 900), 0.03) * 0.5
    return softclip(y * vel * 0.6, 1.2)


def taiko(vel, rng, f=56.0, dur=1.6):
    y = modal_hit(f, [1.0, 1.58, 2.14], [1.0, 0.35, 0.15], [0.55, 0.25, 0.15], dur, rng, drop=0.6, drop_time=0.035)
    y += noise_burst(dur, rng, (90, 1400), 0.05) * 0.7
    return softclip(y * vel, 1.8) * 0.8


def small_drum(vel, rng, f=150.0):
    y = modal_hit(f, [1.0, 1.6, 2.3], [1.0, 0.4, 0.2], [0.22, 0.12, 0.08], 0.6, rng, drop=0.25, drop_time=0.02)
    y += noise_burst(0.6, rng, (300, 4000), 0.025) * 0.6
    return y * vel * 0.6


def frame_drum(vel, rng, f=105.0, slap=False):
    y = modal_hit(f, [1.0, 1.59, 2.28], [1.0, 0.3, 0.12], [0.35, 0.15, 0.1], 0.8, rng, drop=0.12, drop_time=0.02)
    y += noise_burst(0.8, rng, (800, 6000) if slap else (200, 2500), 0.02) * (0.9 if slap else 0.4)
    return y * vel * 0.55


def rim(vel, rng):
    return noise_burst(0.15, rng, (1800, 7000), 0.012) * vel * 0.5 + modal_hit(
        820, [1, 1.7], [0.3, 0.15], [0.03, 0.02], 0.15, rng) * vel


def metal_hit(rng, f0=None, dur=6.0, bright=1.0):
    f0 = f0 or rng.uniform(170, 420)
    ratios = [1.0, 2.32, 4.25, 6.63, 9.38, 12.1]
    ratios = [r * rng.uniform(0.985, 1.015) for r in ratios]
    amps = [1.0, 0.6 * bright, 0.4 * bright, 0.25 * bright, 0.15 * bright, 0.08 * bright]
    decays = [dur * 0.55, dur * 0.35, dur * 0.22, dur * 0.14, dur * 0.08, dur * 0.05]
    y = modal_hit(f0, ratios, amps, decays, dur, rng)
    nb = noise_burst(0.3, rng, (2000, 9000), 0.01) * 0.3 * bright
    y[: len(nb)] += nb[: len(y)]
    return y * 0.35


def boom(rng, f=38.0, dur=4.0):
    y = modal_hit(f, [1.0, 2.01], [1.0, 0.2], [1.4, 0.5], dur, rng, drop=1.0, drop_time=0.08)
    y += noise_burst(dur, rng, (40, 500), 0.12) * 0.6
    return softclip(y, 1.5) * 0.9


def cymbal_swell(dur, rng):
    n = ns(dur)
    t = np.arange(n) / SR
    y = ffilt(rng.standard_normal((2, n)), chain(hp(3500, 2), lp(14000, 1)))
    return y * ((t / dur) ** 3)[None, :] * 0.18


def wind(dur, rng):
    n = ns(dur)
    a = ffilt(rng.standard_normal(n), bp(250, 900, 2)) * (0.55 + 0.45 * smooth_noise(n, 0.15, rng))
    b = ffilt(rng.standard_normal(n), bp(700, 2600, 2)) * (0.5 + 0.5 * smooth_noise(n, 0.25, rng)) * 0.5
    sig = np.vstack((a + 0.6 * b, 0.6 * a + b))
    return sig * 0.25


# ------------------------------------------------------------------------------------------------  lead voices

LEAD_TIMBRES = {
    # harmonics, attack, release, vibrato depth (semitones), vibrato rate, breath, glide (s), brightness spread
    "flute": dict(harm=[1.0, 0.42, 0.18, 0.1, 0.05, 0.025], attack=0.09, release=0.25, vib=0.22, rate=5.0,
                  breath=0.04, glide=0.05, add=False),
    "whistle": dict(harm=[1.0, 0.05, 0.02], attack=0.12, release=0.4, vib=0.35, rate=4.2, breath=0.03,
                    glide=0.18, add=False),
    "horn": dict(harm=None, attack=0.12, release=0.35, vib=0.14, rate=4.8, breath=0.0, glide=0.06, add=True,
                 bright=(2.2, 4.5)),
    "brass_low": dict(harm=None, attack=0.03, release=0.12, vib=0.0, rate=5.0, breath=0.0, glide=0.0, add=True,
                      bright=(2.0, 7.0)),
    "fiddle": dict(harm=None, attack=0.06, release=0.2, vib=0.18, rate=6.0, breath=0.0, glide=0.04, add=True,
                   bright=(6.0, 6.0), fiddle=True),
}


def render_lead(notes, timbre: str, rng) -> tuple[float, np.ndarray]:
    """One continuous monophonic line (notes = [(t, dur, midi, vel)], contiguous) with portamento & vibrato."""
    spec = LEAD_TIMBRES[timbre]
    t0 = notes[0][0]
    end = notes[-1][0] + notes[-1][1]
    n = ns(end - t0 + spec["release"] + 0.05)
    pitch = np.zeros(n)
    vib_env = np.zeros(n)
    amp = np.zeros(n)
    onsets = np.zeros(n)
    bright = np.zeros(n)
    for i, (t, d, m, v) in enumerate(notes):
        s = ns(t - t0)
        e = ns(notes[i + 1][0] - t0) if i + 1 < len(notes) else n
        pitch[s:e] = m
        if i == 0:
            pitch[:s] = m
        ln = e - s
        if d > 0.35 and ln > 0:
            tt = np.arange(ln) / SR
            vib_env[s:e] = np.clip((tt - 0.22) / 0.5, 0.0, 1.0)
        en = env_note(d, spec["attack"], spec["release"], decay_to=0.8 if spec["add"] else 0.92, decay_time=0.8)
        en = en * v
        seg = amp[s:s + len(en)]
        amp[s:s + len(en)] = np.maximum(seg, en[: len(seg)])
        k = min(n - s, ns(0.06))
        onsets[s:s + k] += v * np.exp(-np.arange(k) / (SR * 0.015))
        b = env_note(d, 0.02, spec["release"], decay_to=0.55, decay_time=0.35) * v
        seg = bright[s:s + len(b)]
        bright[s:s + len(b)] = np.maximum(seg, b[: len(seg)])
    if spec["glide"] > 0:
        g = ns(spec["glide"])
        pitch = box(box(pitch, g, causal=True), g, causal=True)
    t = np.arange(n) / SR
    rate = spec["rate"] * (1.0 + 0.06 * smooth_noise(n, 0.5, rng))
    vib = spec["vib"] * vib_env * np.sin(2 * np.pi * np.cumsum(rate) / SR)
    drift = smooth_noise(n, 0.8, rng) * 0.04
    m = pitch + vib + drift
    if spec["add"]:
        # lip scoop at onsets for brass
        m = m - 0.25 * onsets / max(1e-6, onsets.max() or 1.0) * (timbre != "fiddle")
    f = hz(m)
    phase = 2 * np.pi * np.cumsum(f) / SR
    y = np.zeros(n)
    if not spec["add"]:
        for k, a in enumerate(spec["harm"], start=1):
            y += a * np.sin(k * phase + 0.3 * k)
        y *= amp
        nb = rng.standard_normal(n)
        breath = ffilt(nb, bp(1500, 7000, 1)) * (spec["breath"] * amp + 0.18 * spec["breath"] * 8 * onsets)
        y += breath
    else:
        lo, span = spec["bright"]
        fmax = float(np.max(f))
        kmax = int(min(40, (SR * 0.45) / max(fmax, 20.0)))
        fc = f * (lo + span * bright)
        for k in range(1, kmax + 1):
            a = (k ** -0.9) / np.sqrt(1.0 + (k * f / fc) ** 4)
            y += a * np.sin(k * phase + 0.5 * k)
        y *= amp
        if spec.get("fiddle"):
            y = ffilt(y, chain(peaks([(290, 150, 0.8), (1000, 400, 0.4), (2800, 900, 0.7)], 0.6), lp(6000, 1)))
            y += ffilt(rng.standard_normal(n), bp(2000, 6000, 1)) * amp * 0.015
        else:
            y = ffilt(y, lp(5000, 1))
    return t0, y


def lead_groups(notes, max_gap=0.12):
    groups, cur = [], []
    for nt in notes:
        if cur and nt[0] - (cur[-1][0] + cur[-1][1]) > max_gap:
            groups.append(cur)
            cur = []
        cur.append(nt)
    if cur:
        groups.append(cur)
    return groups


# =====================================================================================================  theory

MODES = {
    "ionian": [0, 2, 4, 5, 7, 9, 11],
    "dorian": [0, 2, 3, 5, 7, 9, 10],
    "phrygian": [0, 1, 3, 5, 7, 8, 10],
    "lydian": [0, 2, 4, 6, 7, 9, 11],
    "mixolydian": [0, 2, 4, 5, 7, 9, 10],
    "aeolian": [0, 2, 3, 5, 7, 8, 10],
    "harmonic": [0, 2, 3, 5, 7, 8, 11],
}

# chord-to-chord preferences (scale degree -> [(next, weight)])
TRANS_MINOR = {0: [(5, 3), (6, 3), (3, 2), (2, 2), (4, 1)], 1: [(4, 2), (6, 2), (0, 1)],
               2: [(6, 3), (5, 2), (3, 2), (0, 1)], 3: [(0, 3), (5, 2), (6, 2), (4, 1)],
               4: [(0, 3), (5, 2), (3, 1)], 5: [(6, 3), (2, 2), (3, 2), (0, 2)], 6: [(0, 3), (2, 3), (5, 2), (3, 1)]}
TRANS_MAJOR = {0: [(3, 3), (4, 2), (5, 3), (1, 1), (6, 1)], 1: [(4, 3), (6, 1), (3, 1)],
               2: [(5, 2), (3, 2)], 3: [(0, 3), (4, 2), (1, 1), (5, 1)], 4: [(0, 3), (5, 2), (3, 1)],
               5: [(3, 3), (1, 2), (4, 1), (2, 1)], 6: [(0, 2), (3, 2)]}


class Key:
    def __init__(self, tonic: int, mode: str):
        self.tonic = tonic
        self.mode = mode
        self.steps = MODES[mode]

    def m(self, d) -> int:
        o, i = divmod(int(d), 7)
        return self.tonic + 12 * o + self.steps[i]

    def dim(self, d) -> bool:
        return (self.m(d + 4) - self.m(d)) != 7

    def triad(self, d, kind="triad"):
        if kind == "sus2":
            return [d, d + 1, d + 4]
        if kind == "sus4":
            return [d, d + 3, d + 4]
        if kind == "add9":
            return [d, d + 2, d + 4, d + 8]
        if kind == "power":
            return [d, d + 4]
        if kind == "seventh":
            return [d, d + 2, d + 4, d + 6]
        return [d, d + 2, d + 4]


def weighted(rng, options):
    total = sum(w for _, w in options)
    r = rng.uniform(0, total)
    for v, w in options:
        r -= w
        if r <= 0:
            return v
    return options[-1][0]


def progression(rng, key: Key, n: int, start=0, end=None):
    trans = TRANS_MAJOR if key.mode in ("ionian", "lydian", "mixolydian") else TRANS_MINOR
    seq = [start]
    while len(seq) < n:
        opts = [(d, w) for d, w in trans[seq[-1] % 7] if not key.dim(d) and d != seq[-1]]
        if key.mode == "dorian":
            opts = [(d, w * (2 if d == 3 else 1)) for d, w in opts]
        if key.mode == "phrygian":
            opts = [(d, w * (3 if d == 1 else 1)) for d, w in opts]
        if not opts:
            opts = [(0, 1)]
        seq.append(weighted(rng, opts))
    if end is not None and n > 1:
        seq[-1] = end
        if seq[-2] == end:
            seq[-2] = 6 if end == 0 and not key.dim(6) else (3 if not key.dim(3) else 5)
    return seq


def voice_chord(key: Key, tones, prev, nvoices, lo, hi):
    pcs = sorted({key.m(t) % 12 for t in tones})
    cands = [m for m in range(lo, hi + 1) if m % 12 in pcs]
    if not cands:
        return []
    if not prev:
        root_pc = key.m(tones[0]) % 12
        bottom = next((m for m in cands if m % 12 == root_pc), cands[0])
        out = [bottom]
        for pc in pcs * 3:
            if len(out) >= nvoices:
                break
            nxt = [m for m in cands if m > out[-1] + 2 and m % 12 == pc]
            if nxt:
                out.append(nxt[0])
        while len(out) < nvoices:
            out.append(cands[min(len(cands) - 1, len(out) * 2)])
        return sorted(set(out))[:nvoices]
    out = []
    for p in prev[:nvoices]:
        best = min((m for m in cands if m not in out), key=lambda m: abs(m - p), default=None)
        if best is not None:
            out.append(best)
    missing = [pc for pc in pcs if pc not in {m % 12 for m in out}]
    for pc in missing[: max(0, len(out) - 1)]:
        counts = {}
        for m in out:
            counts[m % 12] = counts.get(m % 12, 0) + 1
        dup = [i for i, m in enumerate(out) if counts[m % 12] > 1]
        if not dup:
            break
        i = dup[-1]
        opts = [m for m in cands if m % 12 == pc]
        out[i] = min(opts, key=lambda m: abs(m - out[i]))
    while len(out) < nvoices and len(out) < len(cands):
        out.append(next(m for m in cands if m not in out))
    return sorted(out)


# ------------------------------------------------------------------------------------------------  melody

RHYTHMS = {
    4: [[2, 1, 1], [1.5, 0.5, 2], [1, 1, 2], [3, 1], [1, 0.5, 0.5, 2], [0.5, 0.5, 1, 2], [1.5, 0.5, 1, 1],
        [1, 1, 1, 1], [2, 0.5, 0.5, 1]],
    3: [[2, 1], [1, 1, 1], [1.5, 0.5, 1], [1, 2], [0.5, 0.5, 2], [1, 0.5, 0.5, 1]],
}
STEPS = [(-1, 28), (1, 28), (-2, 12), (2, 12), (3, 6), (-3, 5), (4, 4), (-4, 3), (0, 2)]


class Motif:
    def __init__(self, rng, meter: int, bars: int = 1):
        for _ in range(40):  # retry until the motif has a real contour (spans at least a third)
            self._make(rng, meter, bars)
            walk = np.cumsum(self.intervals)
            if walk.max() - walk.min() >= 2 and len(set(walk.tolist())) >= 3:
                break

    def _make(self, rng, meter, bars):
        self.rhythm = []
        for _ in range(bars):
            self.rhythm += list(RHYTHMS[meter][rng.integers(len(RHYTHMS[meter]))])
        self.intervals = [0]
        for _ in range(len(self.rhythm) - 1):
            step = weighted(rng, STEPS)
            if abs(self.intervals[-1]) >= 3:  # recover from a leap by step in the other direction
                step = -int(np.sign(self.intervals[-1]))
            self.intervals.append(step)
        if all(i == 0 for i in self.intervals[1:]):
            self.intervals[-1] = 1

    def variant(self, rng, kind):
        r, iv = list(self.rhythm), list(self.intervals)
        if kind == "invert":
            iv = [0] + [-i for i in iv[1:]]
        elif kind == "rhythm" and len(r) > 2:
            i = int(rng.integers(len(r) - 1))
            r[i], r[i + 1] = r[i + 1], r[i]
        elif kind == "fragment":
            h = max(2, len(r) // 2)
            r, iv = r[:h] * 2, iv[:h] + [weighted(rng, STEPS[:4])] + iv[1:h]
        elif kind == "ornament":
            r2, iv2 = [], []
            for d, i in zip(r, iv):
                if d >= 1 and rng.random() < 0.4:
                    r2 += [d - 0.5, 0.5]
                    iv2 += [i, int(rng.choice([-1, 1]))]
                else:
                    r2.append(d)
                    iv2.append(i)
            r, iv = r2, iv2
        return r, iv


def nearest_tone(deg, tones, lo, hi):
    pcs = {t % 7 for t in tones}
    best = None
    for d in range(lo, hi + 1):
        if d % 7 in pcs and (best is None or abs(d - deg) < abs(best - deg)):
            best = d
    return best if best is not None else deg


def section_melody(rng, motif: Motif, chords, bpc, meter, cur, lo, hi, density=1.0, ornament=False, long_end=True):
    """chords = [(beat, tones)]. Returns ([(beat, dur_beats, degree, vel)], last_degree)."""
    notes = []
    nch = len(chords)
    for ci, (b0, tones) in enumerate(chords):
        role = ci % 4
        if ci == nch - 1 and long_end:
            role = 3
        if role != 0 and rng.random() > density:
            continue
        shift = 0
        if role == 0:
            r, iv = motif.variant(rng, "ornament" if ornament else "none")
        elif role == 1:
            r, iv = motif.variant(rng, "rhythm" if rng.random() < 0.5 else "none")
            shift = int(rng.choice([-1, 1, 2]))
        elif role == 2:
            r, iv = motif.variant(rng, "invert" if rng.random() < 0.5 else "fragment")
        else:
            # cadence: approach a chord tone by step, then hold
            r = [1.0, 1.0, max(2.0, bpc - 3.0)] if bpc >= 6 else [1.0, max(1.0, bpc - 2.0)]
            iv = [0] + [int(rng.choice([-1, 1]))] * (len(r) - 1)
        offset = 0.0 if role in (0, 3) or rng.random() < 0.6 else 0.5 * int(rng.integers(1, 3))
        start = nearest_tone(cur + shift, tones, lo, hi)
        p = start
        beat = b0 + offset
        limit = b0 + bpc - (0.0 if role == 3 else min(1.0, bpc * 0.15))
        for j, (d, i) in enumerate(zip(r, iv)):
            if beat >= limit - 0.25:
                break
            if j > 0:
                p += i
                if p > hi:
                    p -= 2 * abs(i) if abs(i) > 0 else 1
                if p < lo:
                    p += 2 * abs(i) if abs(i) > 0 else 1
                p = min(hi, max(lo, p))
            d = min(d, limit - beat)
            strong = (beat - b0) % meter == 0 or d >= 2
            if strong:
                p = nearest_tone(p, tones, lo, hi)
            vel = 0.75 + 0.2 * (1.0 - abs((beat - b0) / max(1, bpc) - 0.45)) + rng.uniform(-0.05, 0.05)
            notes.append([beat, d, p, vel])
            beat += d
        if notes and role == 3:
            notes[-1][2] = nearest_tone(notes[-1][2], [tones[0], tones[0] + 4], lo, hi)
            notes[-1][1] = max(notes[-1][1], b0 + bpc - notes[-1][0] - 0.25)
        elif notes and notes[-1][0] + notes[-1][1] < b0 + bpc - 1.5 and rng.random() < 0.5:
            # let the phrase ring rather than leave a gap
            notes[-1][1] += min(2.0, b0 + bpc - (notes[-1][0] + notes[-1][1]) - 0.5)
        if notes:
            cur = notes[-1][2]
    return [tuple(x) for x in notes], cur


# =====================================================================================================  songs

class Song:
    def __init__(self, name, seed, tonic, mode, bpm, meter, room=0.86, damp=0.45, wet=0.38, target=-20.0, tone=9000.0):
        self.name = name
        self.rng = np.random.default_rng(seed)
        self.key = Key(tonic, mode)
        self.bpm = bpm
        self.spb = 60.0 / bpm
        self.meter = meter
        self.lead_in = 0.4
        self.room, self.damp, self.wet, self.target, self.tone = room, damp, wet, target, tone
        self.sections = []
        self.motif = Motif(self.rng, meter, bars=2 if meter == 3 else 1)
        self.motif2 = Motif(self.rng, meter, bars=1)
        self.mel_cur = 4
        self.pad_prev = None
        self.str_prev = None
        self.choir_prev = None

    def t(self, beat):
        return self.lead_in + beat * self.spb

    def section(self, name, nchords, bpc, layers, start=0, end=None, prog=None, opts=None):
        opts = opts or {}
        prog = prog or progression(self.rng, self.key, nchords, start, end)
        kinds = []
        for d in prog:
            r = self.rng.random()
            kinds.append("sus2" if r < 0.12 and not self.key.dim(d) else ("add9" if r < 0.25 else "triad"))
        self.sections.append(dict(name=name, prog=prog, bpc=bpc, layers=layers, kinds=kinds, opts=opts))

    def render(self):
        beats = sum(len(s["prog"]) * s["bpc"] for s in self.sections)
        mix = Mix(self.t(beats) + 9.0)
        b = 0.0
        for si, sec in enumerate(self.sections):
            sec["beat"] = b
            sec["chords"] = [(b + i * sec["bpc"], self.key.triad(d, k)) for i, (d, k) in enumerate(zip(sec["prog"], sec["kinds"]))]
            sec["last"] = si == len(self.sections) - 1
            for layer, lvl in sec["layers"].items():
                fn = LAYERS[layer.split("#")[0]]
                fn(self, mix, sec, lvl, **sec["opts"].get(layer, {}))
            b += len(sec["prog"]) * sec["bpc"]
        out = render_mix(mix, self.room, self.damp, self.wet, tone=self.tone)
        return master(out, self.target)


def sec_len(song, sec):
    return len(sec["prog"]) * sec["bpc"] * song.spb


# ------------------------------------------------------------------------------------------------  layers

def L_drone(song, mix, sec, lvl, octave=-2, fifth=True, bright=0.35, choir=False):
    k = song.key
    dur = sec_len(song, sec)
    t = song.t(sec["beat"])
    root = k.tonic + 12 * octave
    rel = 4.0 if sec["last"] else 3.0
    sig = strings_note(root, dur, song.rng, attack=min(4.0, dur * 0.3), release=rel, bright=bright, voices=7, vib=0.03, detune=3.0)
    mix.add(sig, t, lvl * 0.42, send=0.35)
    if fifth:
        sig = strings_note(root + 7, dur, song.rng, attack=min(5.0, dur * 0.35), release=rel, bright=bright * 0.8, voices=3, vib=0.03,
                           detune=5.0)
        mix.add(sig, t, lvl * 0.3, send=0.35)
    if choir:
        sig = choir_note(root + 12, dur, song.rng, vowel="u", attack=min(5.0, dur * 0.3), release=rel, low=True)
        mix.add(sig, t, lvl * 0.25, send=0.5)


def _chord_iter(song, sec):
    for i, (b, tones) in enumerate(sec["chords"]):
        dur = sec["bpc"] * song.spb
        last = sec["last"] and i == len(sec["chords"]) - 1
        yield i, b, tones, dur, last


def L_pad(song, mix, sec, lvl, lo=50, hi=69, voices=4, cutoff=1100.0):
    for i, b, tones, dur, last in _chord_iter(song, sec):
        v = voice_chord(song.key, tones, song.pad_prev, voices, lo, hi)
        song.pad_prev = v
        for m in v:
            sig = pad_note(m, dur + 0.3, song.rng, attack=min(2.5, dur * 0.4), release=5.0 if last else 2.5, cutoff=cutoff)
            mix.add(sig, song.t(b), lvl * 0.3, send=0.45)


def L_strings(song, mix, sec, lvl, lo=52, hi=76, voices=4, bass=True, bright=0.8, attack=1.2):
    for i, b, tones, dur, last in _chord_iter(song, sec):
        v = voice_chord(song.key, tones, song.str_prev, voices, lo, hi)
        song.str_prev = v
        for m in v:
            sig = strings_note(m, dur + 0.2, song.rng, attack=min(attack, dur * 0.4), release=4.0 if last else 1.6, bright=bright)
            mix.add(sig, song.t(b), lvl * 0.22, send=0.4)
        if bass:
            root = song.key.m(tones[0]) % 12
            bm = 38 + (root - 38) % 12
            sig = strings_note(bm, dur + 0.2, song.rng, attack=min(attack, dur * 0.4), release=4.0 if last else 1.6, bright=0.4, voices=3)
            mix.add(sig, song.t(b), lvl * 0.3, send=0.3)


def L_choir(song, mix, sec, lvl, lo=55, hi=74, voices=4, vowel="a", low=None, attack=1.4):
    for i, b, tones, dur, last in _chord_iter(song, sec):
        v = voice_chord(song.key, tones, song.choir_prev, voices, lo, hi)
        song.choir_prev = v
        for m in v:
            sig = choir_note(m, dur + 0.2, song.rng, vowel=vowel, attack=min(attack, dur * 0.4), release=4.0 if last else 1.8, low=low)
            mix.add(sig, song.t(b), lvl * 0.3, send=0.55)


def L_harp(song, mix, sec, lvl, lo=55, hi=81, step=0.5, pattern="up", instr="harp", density=1.0):
    play = harp if instr == "harp" else (lute if instr == "lute" else bell)
    for i, b, tones, dur, last in _chord_iter(song, sec):
        pcs = sorted({song.key.m(t) % 12 for t in tones})
        notes = [m for m in range(lo, hi + 1) if m % 12 in pcs]
        if not notes:
            continue
        count = int(sec["bpc"] / step)
        seq = notes[: max(3, min(len(notes), 6))]
        if pattern == "updown":
            seq = seq + seq[-2:0:-1]
        elif pattern == "wave":
            seq = seq[::2] + seq[1::2][::-1]
        offset = int(song.rng.integers(len(seq)))
        for j in range(count):
            if song.rng.random() > density:
                continue
            m = seq[(j + offset * (i % 2)) % len(seq)]
            vel = 0.8 if (j * step) % song.meter == 0 else 0.55 + 0.15 * song.rng.random()
            sig = play(m, 3.0, song.rng)
            mix.add(sig, song.t(b + j * step) + song.rng.uniform(-0.008, 0.008), lvl * vel * 0.55, send=0.4,
                    pan=-0.5 + (m - lo) / max(1, hi - lo))


def L_lute(song, mix, sec, lvl, patterns=None):
    """Folk accompaniment: bass on the downbeat, broken chord on the off-beats."""
    meter = song.meter
    pats = patterns or ([[0, 2, 1, 2, 3, 2]] if meter == 3 else [[0, 2, 1, 2, 3, 2, 1, 2]])
    pat = pats[int(song.rng.integers(len(pats)))]
    for i, b, tones, dur, last in _chord_iter(song, sec):
        k = song.key
        root = k.m(tones[0])
        bass = 43 + (root - 43) % 12
        chord = sorted({55 + (k.m(t) - 55) % 12 for t in tones})
        voices = [bass] + chord
        bars = int(sec["bpc"] // meter)
        for bar in range(bars):
            for j, idx in enumerate(pat):
                beat = b + bar * meter + j * 0.5
                m = voices[min(idx, len(voices) - 1)]
                vel = 0.85 if j == 0 else 0.5 + 0.12 * song.rng.random()
                sig = lute(m, 1.6, song.rng)
                mix.add(sig, song.t(beat) + song.rng.uniform(-0.01, 0.01), lvl * vel * 1.0, send=0.3,
                        pan=-0.25 + 0.5 * (idx / 3.0))


def L_lead(song, mix, sec, lvl, instr="flute", lo=0, hi=11, octave=0, density=1.0, motif=1, ornament=False, pan=0.1,
           send=0.45, double=None):
    m = song.motif if motif == 1 else song.motif2
    chords = sec["chords"]
    notes, song.mel_cur = section_melody(song.rng, m, chords, sec["bpc"], song.meter, min(hi, max(lo, song.mel_cur)), lo, hi,
                                         density=density, ornament=ornament)
    timed = []
    for beat, d, deg, vel in notes:
        tt = song.t(beat) + song.rng.uniform(-0.012, 0.012)
        timed.append((tt, d * song.spb * 0.97, song.key.m(deg) + 12 * octave, vel))
    for grp in lead_groups(timed):
        t0, y = render_lead(grp, instr, song.rng)
        mix.add(y, t0, lvl * 0.32, send=send, pan=pan)
        if double is not None:
            g2 = [(a, b, c + double, v) for a, b, c, v in grp]
            t1, y2 = render_lead(g2, instr, song.rng)
            mix.add(y2, t1, lvl * 0.2, send=send, pan=-pan)


def L_chant(song, mix, sec, lvl, lo=-3, hi=5, octave=-1, vowels="aoae", density=1.0):
    """Male choir singing the melody (unison + octave), syllable by syllable."""
    m = song.motif2
    notes, song.mel_cur = section_melody(song.rng, m, sec["chords"], sec["bpc"], song.meter, min(hi, max(lo, song.mel_cur)), lo, hi,
                                         density=density)
    for idx, (beat, d, deg, vel) in enumerate(notes):
        midi = song.key.m(deg) + 12 * octave
        dur = d * song.spb * 0.92
        v = vowels[idx % len(vowels)]
        sig = choir_note(midi, dur, song.rng, vowel=v, voices=5, attack=0.08, release=0.5, low=True, vib=0.1)
        sig += choir_note(midi - 12, dur, song.rng, vowel=v, voices=3, attack=0.08, release=0.5, low=True, vib=0.06) * 0.7
        mix.add(sig, song.t(beat), lvl * vel * 0.5, send=0.5)


def L_timp(song, mix, sec, lvl, every=1, roll_in=True):
    k = song.key
    for i, b, tones, dur, last in _chord_iter(song, sec):
        if i % every:
            continue
        root = k.m(tones[0])
        midi = 38 + (root - 38) % 12 if (root - 38) % 12 <= 7 else 26 + (root - 26) % 12
        mix.add(timpani(midi, 0.9 if i == 0 else 0.6, song.rng), song.t(b), lvl * 0.7, send=0.35)
    if roll_in and sec["beat"] > 0:
        # crescendo roll into the section
        root = 38 + (k.tonic - 38) % 12
        start = sec["beat"] - song.meter
        steps = int(song.meter / 0.125)
        for j in range(steps):
            vel = 0.12 + 0.6 * (j / steps) ** 2
            mix.add(timpani(root, vel, song.rng, dur=1.5), song.t(start + j * 0.125) + song.rng.uniform(-0.006, 0.006),
                    lvl * 0.5, send=0.3, pan=0.15 * (1 if j % 2 else -1))


def L_swell(song, mix, sec, lvl):
    if sec["beat"] <= 0:
        return
    d = song.meter * song.spb * 2
    mix.add(cymbal_swell(d, song.rng), song.t(sec["beat"]) - d, lvl, send=0.4)


TAIKO_PATTERNS = [
    {0: 1.0, 6: 0.55, 8: 0.9, 11: 0.5, 12: 0.8, 14: 0.6},
    {0: 1.0, 3: 0.5, 4: 0.7, 8: 1.0, 10: 0.5, 12: 0.7, 13: 0.5, 14: 0.8},
    {0: 1.0, 8: 0.8, 10: 0.45, 12: 0.6, 14: 0.6, 15: 0.5},
    {0: 1.0, 2: 0.4, 4: 0.8, 7: 0.5, 8: 0.9, 12: 0.8, 14: 0.5},
    {0: 1.0, 5: 0.6, 6: 0.6, 8: 0.9, 12: 0.7, 13: 0.4, 15: 0.6},
]
TAIKO_FILL = {0: 1.0, 2: 0.6, 4: 0.8, 6: 0.6, 8: 0.9, 10: 0.7, 11: 0.7, 12: 0.8, 13: 0.8, 14: 0.9, 15: 1.0}


def L_taiko(song, mix, sec, lvl, patterns=(0, 1), half=False, rims=False, fill_every=4):
    bars_total = int(len(sec["prog"]) * sec["bpc"] / 4)
    pats = [TAIKO_PATTERNS[p] for p in patterns]
    for bar in range(bars_total):
        is_fill = (bar + 1) % fill_every == 0 and not half
        pat = TAIKO_FILL if is_fill else pats[bar % len(pats)]
        if half:
            pat = {0: 1.0, 8: 0.7} if bar % 2 == 0 else {0: 0.8, 12: 0.5}
        for step, vel in pat.items():
            beat = sec["beat"] + bar * 4 + step * 0.25
            t = song.t(beat) + song.rng.uniform(-0.006, 0.006)
            if vel >= 0.75:
                sig = taiko(vel, song.rng, f=song.rng.uniform(52, 60))
                mix.add(sig, t, lvl * 0.9, send=0.25, pan=song.rng.uniform(-0.15, 0.15))
            else:
                sig = small_drum(vel, song.rng, f=song.rng.uniform(120, 170))
                mix.add(sig, t, lvl * 0.8, send=0.25, pan=song.rng.uniform(-0.5, 0.5))
        if rims:
            for s in range(16):
                if s % 4 == 2 or (s % 2 == 1 and song.rng.random() < 0.3):
                    mix.add(rim(0.5 + 0.3 * (s % 4 == 2), song.rng), song.t(sec["beat"] + bar * 4 + s * 0.25), lvl * 0.5,
                            send=0.2, pan=0.4)


OSTINATI = [[1, 0, 1, 1, 0, 1, 1, 0], [1, 1, 0, 1, 1, 0, 1, 0], [1, 0, 0, 1, 0, 0, 1, 0], [1, 1, 1, 0, 1, 1, 1, 0]]


def L_brass_ost(song, mix, sec, lvl, pattern=0, lo=38):
    pat = OSTINATI[pattern]
    k = song.key
    for i, b, tones, dur, last in _chord_iter(song, sec):
        root = lo + (k.m(tones[0]) - lo) % 12
        fifth = root + (k.m(tones[0] + 4) - k.m(tones[0]))
        notes = []
        for bar in range(int(sec["bpc"] // 4)):
            for j, on in enumerate(pat):
                if not on:
                    continue
                m = root if j not in (3, 6) else (fifth if j == 3 else root + 12)
                vel = 1.0 if j == 0 else 0.7
                notes.append((song.t(b + bar * 4 + j * 0.5), 0.5 * song.spb * 0.8, m, vel))
        for grp in lead_groups(notes, max_gap=0.0):
            t0, y = render_lead(grp, "brass_low", song.rng)
            mix.add(y, t0, lvl * 0.3, send=0.25, pan=-0.1)


def L_string_ost(song, mix, sec, lvl, lo=50):
    k = song.key
    for i, b, tones, dur, last in _chord_iter(song, sec):
        root = lo + (k.m(tones[0]) - lo) % 12
        third = k.m(tones[0] + 2) - k.m(tones[0])
        fig = [0, 12, 7, 12, 0, 12, third + 12, 12]
        for j in range(int(sec["bpc"] / 0.25)):
            m = root + fig[j % len(fig)]
            vel = 1.0 if j % 4 == 0 else 0.65
            sig = strings_note(m, 0.12, song.rng, attack=0.008, release=0.09, bright=0.9, voices=2, short=True)
            mix.add(sig, song.t(b + j * 0.25), lvl * vel * 0.25, send=0.25)


def L_horns(song, mix, sec, lvl, lo=50, hi=67):
    """Sustained brass chords."""
    prev = None
    for i, b, tones, dur, last in _chord_iter(song, sec):
        v = voice_chord(song.key, tones, prev, 3, lo, hi)
        prev = v
        for j, m in enumerate(v):
            t0, y = render_lead([(song.t(b), dur * 0.95, m, 0.7)], "horn", song.rng)
            mix.add(y, t0, lvl * 0.18, send=0.45, pan=-0.4 + 0.4 * j)


def L_metal(song, mix, sec, lvl, rate=0.15, bright=0.8):
    dur = sec_len(song, sec)
    t = 0.0
    while True:
        t += song.rng.exponential(1.0 / rate)
        if t > dur:
            break
        sig = metal_hit(song.rng, dur=song.rng.uniform(4, 8), bright=bright)
        if song.rng.random() < 0.35:
            sig = sig[::-1].copy()
            mix.add(sig, song.t(sec["beat"]) + t - len(sig) / SR, lvl * 0.6, send=0.6, pan=song.rng.uniform(-0.8, 0.8))
        else:
            mix.add(sig, song.t(sec["beat"]) + t, lvl * 0.5, send=0.6, pan=song.rng.uniform(-0.8, 0.8))


def L_wind(song, mix, sec, lvl):
    dur = sec_len(song, sec) + 3.0
    sig = wind(dur, song.rng)
    n = sig.shape[1]
    fade = ns(2.5)
    sig[:, :fade] *= np.linspace(0, 1, fade)
    sig[:, -fade:] *= np.linspace(1, 0, fade)
    mix.add(sig, song.t(sec["beat"]), lvl, send=0.3)


def L_bells(song, mix, sec, lvl, rate=0.25, lo=72, hi=91):
    k = song.key
    pent = [0, 1, 2, 4, 5] if k.mode in ("ionian", "lydian", "mixolydian") else [0, 2, 3, 4, 6]
    for i, b, tones, dur, last in _chord_iter(song, sec):
        t = 0.0
        while True:
            t += song.rng.exponential(1.0 / rate)
            if t > dur:
                break
            deg = int(song.rng.choice(pent + [d % 7 for d in tones])) + 7 * int(song.rng.integers(1, 3))
            m = k.m(deg)
            while m > hi:
                m -= 12
            while m < lo:
                m += 12
            mix.add(bell(m, 4.0, song.rng), song.t(b) + t, lvl * 0.35, send=0.7, pan=song.rng.uniform(-0.7, 0.7))


def L_frame(song, mix, sec, lvl):
    meter = song.meter
    pats = {3: [(0, 1.0, False), (1.5, 0.4, True), (2, 0.6, False)],
            4: [(0, 1.0, False), (1.5, 0.4, True), (2, 0.7, False), (3, 0.5, True), (3.5, 0.35, True)]}[meter]
    bars = int(len(sec["prog"]) * sec["bpc"] / meter)
    for bar in range(bars):
        for off, vel, slap in pats:
            t = song.t(sec["beat"] + bar * meter + off) + song.rng.uniform(-0.01, 0.01)
            mix.add(frame_drum(vel * song.rng.uniform(0.85, 1.0), song.rng, slap=slap), t, lvl * 0.6, send=0.25, pan=0.2)


def L_boom(song, mix, sec, lvl, every=2):
    for i, b, tones, dur, last in _chord_iter(song, sec):
        if i % every == 0:
            mix.add(boom(song.rng), song.t(b), lvl * 0.7, send=0.3)


def L_dist_drone(song, mix, sec, lvl, cluster=(0, 1, 6), octave=-2, ring=31.0, drive=3.0):
    """Oblivion: detuned cluster drone, ring-modulated and overdriven, slowly breathing."""
    k = song.key
    dur = sec_len(song, sec)
    root = k.tonic + 12 * octave
    acc = None
    for c in cluster:
        sig = strings_note(root + c, dur, song.rng, attack=min(4.0, dur * 0.3), release=3.0, bright=0.6, voices=4, detune=22)
        acc = sig if acc is None else acc + sig
    n = acc.shape[1]
    t = np.arange(n) / SR
    rm = np.sin(2 * np.pi * ring * t + 2.0 * smooth_noise(n, 0.2, song.rng))
    acc = acc * (0.6 + 0.4 * rm)[None, :]
    acc = softclip(acc * drive, 1.0)
    breathe = 0.6 + 0.4 * smooth_noise(n, 0.12, song.rng)
    acc = ffilt(acc * breathe[None, :], chain(lp(1800, 2), hp(40, 1)))
    mix.add(acc, song.t(sec["beat"]), lvl * 0.35, send=0.45)


def L_cluster_choir(song, mix, sec, lvl, lo=55):
    """Detuned, bending choir clusters."""
    dur = sec_len(song, sec)
    k = song.key
    base = lo + (k.tonic - lo) % 12
    for c in (0, 1, 6, 11):
        sig = choir_note(base + c, dur, song.rng, vowel="u" if c % 2 else "o", attack=dur * 0.4, release=3.0, vib=0.35)
        n = sig.shape[1]
        sig = sig * (0.5 + 0.5 * smooth_noise(n, 0.2, song.rng))[None, :]
        mix.add(sig, song.t(sec["beat"]), lvl * 0.28, send=0.7)


def L_dist_hits(song, mix, sec, lvl, rate=0.3):
    dur = sec_len(song, sec)
    t = 0.0
    while True:
        t += song.rng.exponential(1.0 / rate)
        if t > dur:
            break
        sig = softclip(taiko(1.0, song.rng, f=song.rng.uniform(40, 50), dur=2.5) * 2.5, 2.0)
        sig = ffilt(sig, lp(3000, 1))
        mix.add(sig, song.t(sec["beat"]) + t, lvl * 0.5, send=0.45, pan=song.rng.uniform(-0.3, 0.3))


def L_stabs(song, mix, sec, lvl, every=1):
    """Dissonant overdriven brass stabs on chord changes."""
    k = song.key
    for i, b, tones, dur, last in _chord_iter(song, sec):
        if i % every:
            continue
        root = 46 + (k.m(tones[0]) - 46) % 12
        acc = None
        for c in (0, 1, 6):
            t0, y = render_lead([(0.0, 0.9, root + c, 1.0)], "brass_low", song.rng)
            acc = y if acc is None else acc + y
        acc = softclip(acc * 2.5, 1.5)
        mix.add(acc, song.t(b), lvl * 0.3, send=0.5, pan=song.rng.uniform(-0.3, 0.3))


def L_riser(song, mix, sec, lvl):
    if sec["beat"] <= 0:
        return
    d = sec["bpc"] * song.spb
    n = ns(d)
    t = np.arange(n) / SR
    y = ffilt(song.rng.standard_normal(n), bp(400, 3000, 2)) * (t / d) ** 2
    f = 80 * 2 ** (3 * t / d)
    y = y + 0.4 * softclip(saw(f) * (t / d) ** 2, 2.0)
    mix.add(ffilt(y, lp(4000, 1)), song.t(sec["beat"]) - d, lvl * 0.35, send=0.5)


LAYERS = {name[2:]: fn for name, fn in globals().items() if name.startswith("L_")}


# =====================================================================================================  tracks

def explore_day_1():
    s = Song("explore_day_1", 1101, 62, "dorian", 66, 4, room=0.88, wet=0.4)
    s.section("intro", 2, 8, {"drone": 0.8, "pad": 0.6}, start=0)
    s.section("a", 4, 8, {"drone": 0.6, "pad": 0.7, "harp": 0.6, "lead": 0.9},
              opts={"lead": dict(instr="flute", lo=0, hi=10), "harp": dict(pattern="up")})
    s.section("b", 4, 8, {"strings": 0.8, "choir": 0.6, "lead": 0.9, "timp": 0.6},
              opts={"lead": dict(instr="horn", lo=-5, hi=5, octave=0), "choir": dict(vowel="a")})
    s.section("a2", 4, 8, {"pad": 0.7, "harp": 0.6, "lead": 0.9, "choir": 0.35},
              opts={"lead": dict(instr="flute", lo=2, hi=12, ornament=True), "choir": dict(vowel="o"), "harp": dict(pattern="updown")})
    s.section("outro", 2, 8, {"drone": 0.8, "pad": 0.6, "lead": 0.5}, end=0,
              opts={"lead": dict(instr="flute", lo=0, hi=7, density=0.6)})
    return s


def explore_day_2():
    s = Song("explore_day_2", 1202, 57, "aeolian", 72, 3, room=0.9, wet=0.42)
    s.section("intro", 2, 9, {"drone": 0.8, "choir": 0.4}, opts={"choir": dict(vowel="u")})
    s.section("a", 4, 9, {"drone": 0.5, "strings": 0.6, "harp": 0.6, "lead": 0.9},
              opts={"lead": dict(instr="horn", lo=0, hi=8, octave=-1), "harp": dict(pattern="wave", step=0.5)})
    s.section("b", 4, 9, {"strings": 0.8, "choir": 0.6, "lead": 0.9, "timp": 0.5},
              opts={"lead": dict(instr="flute", lo=2, hi=11, motif=2), "choir": dict(vowel="a")})
    s.section("c", 4, 9, {"pad": 0.6, "harp": 0.5, "bells": 0.4, "lead": 0.8},
              opts={"lead": dict(instr="flute", lo=0, hi=9, density=0.7)})
    s.section("outro", 2, 9, {"drone": 0.8, "choir": 0.4, "pad": 0.5}, end=0, opts={"choir": dict(vowel="u")})
    return s


def explore_day_3():
    s = Song("explore_day_3", 1303, 55, "mixolydian", 63, 4, room=0.9, wet=0.42)
    s.section("intro", 2, 8, {"drone": 0.7, "pad": 0.6, "bells": 0.3})
    s.section("a", 4, 8, {"pad": 0.6, "harp": 0.6, "lead": 0.9, "drone": 0.4},
              opts={"lead": dict(instr="fiddle", lo=0, hi=9), "harp": dict(pattern="updown", step=0.5)})
    s.section("b", 4, 8, {"strings": 0.8, "choir": 0.7, "lead": 0.85, "timp": 0.5},
              opts={"lead": dict(instr="horn", lo=-4, hi=5), "choir": dict(vowel="a")})
    s.section("a2", 4, 8, {"pad": 0.6, "harp": 0.6, "lead": 0.85, "choir": 0.3},
              opts={"lead": dict(instr="flute", lo=3, hi=12, motif=1, ornament=True), "choir": dict(vowel="o")})
    s.section("outro", 2, 8, {"drone": 0.8, "pad": 0.6}, end=0)
    return s


def explore_night_1():
    s = Song("explore_night_1", 2101, 62, "aeolian", 54, 3, room=0.92, damp=0.55, wet=0.48, target=-22.0, tone=7000)
    s.section("intro", 2, 9, {"drone": 0.8, "wind": 0.35})
    s.section("a", 4, 9, {"drone": 0.5, "pad": 0.6, "bells": 0.4, "lead": 0.75},
              opts={"lead": dict(instr="flute", lo=-3, hi=6, density=0.7), "pad": dict(cutoff=800)})
    s.section("b", 4, 9, {"pad": 0.5, "choir": 0.55, "harp": 0.35, "lead": 0.7},
              opts={"choir": dict(vowel="u"), "harp": dict(step=1.0, density=0.6), "lead": dict(instr="horn", lo=-5, hi=3, density=0.6)})
    s.section("outro", 3, 9, {"drone": 0.7, "wind": 0.3, "bells": 0.3}, end=0)
    return s


def explore_night_2():
    s = Song("explore_night_2", 2202, 64, "dorian", 52, 4, room=0.93, damp=0.55, wet=0.5, target=-22.0, tone=7000)
    s.section("intro", 2, 8, {"drone": 0.8, "choir": 0.35}, opts={"choir": dict(vowel="u")})
    s.section("a", 4, 8, {"pad": 0.6, "harp": 0.4, "lead": 0.7},
              opts={"harp": dict(step=1.0, density=0.7, lo=52, hi=76), "lead": dict(instr="whistle", lo=0, hi=7, density=0.7)})
    s.section("b", 4, 8, {"strings": 0.55, "choir": 0.5, "bells": 0.35, "lead": 0.65},
              opts={"strings": dict(bright=0.4), "choir": dict(vowel="o"), "lead": dict(instr="flute", lo=-2, hi=7, density=0.6, motif=2)})
    s.section("outro", 3, 8, {"drone": 0.7, "pad": 0.5, "wind": 0.25}, end=0)
    return s


def town_1():
    s = Song("town_1", 3101, 62, "dorian", 96, 3, room=0.6, damp=0.5, wet=0.28, target=-19.5)
    s.section("intro", 2, 6, {"lute": 0.8})
    s.section("a", 8, 6, {"lute": 0.8, "pad": 0.4, "lead": 0.9, "frame": 0.5},
              opts={"lead": dict(instr="flute", lo=0, hi=10, ornament=True)})
    s.section("b", 8, 6, {"lute": 0.8, "pad": 0.4, "lead": 0.85, "frame": 0.6},
              opts={"lead": dict(instr="fiddle", lo=0, hi=9, motif=2, ornament=True)})
    s.section("a2", 8, 6, {"lute": 0.8, "strings": 0.35, "lead": 0.9, "frame": 0.5},
              opts={"lead": dict(instr="flute", lo=2, hi=11, ornament=True, double=-3), "strings": dict(bright=0.5)})
    s.section("outro", 2, 6, {"lute": 0.7, "pad": 0.4}, end=0)
    return s


def town_2():
    s = Song("town_2", 3202, 57, "mixolydian", 88, 4, room=0.6, damp=0.5, wet=0.28, target=-19.5)
    s.section("intro", 2, 8, {"lute": 0.8, "pad": 0.3})
    s.section("a", 6, 8, {"lute": 0.8, "pad": 0.35, "lead": 0.9, "frame": 0.45},
              opts={"lead": dict(instr="fiddle", lo=0, hi=9, ornament=True)})
    s.section("b", 6, 8, {"lute": 0.7, "harp": 0.4, "lead": 0.85, "frame": 0.5, "strings": 0.3},
              opts={"lead": dict(instr="flute", lo=2, hi=11, motif=2, ornament=True), "strings": dict(bright=0.5)})
    s.section("a2", 6, 8, {"lute": 0.8, "pad": 0.35, "lead": 0.9, "frame": 0.5},
              opts={"lead": dict(instr="fiddle", lo=0, hi=9, ornament=True)})
    s.section("outro", 2, 8, {"lute": 0.7, "pad": 0.4}, end=0)
    return s


def dungeon_1():
    s = Song("dungeon_1", 4101, 62, "phrygian", 50, 4, room=0.95, damp=0.65, wet=0.5, target=-23.0, tone=5000)
    s.section("intro", 1, 16, {"drone": 0.9, "wind": 0.3}, opts={"drone": dict(choir=True, bright=0.25)})
    s.section("a", 3, 12, {"drone": 0.7, "metal": 0.5, "pad": 0.4}, opts={"pad": dict(lo=45, hi=60, cutoff=600), "metal": dict(rate=0.12)})
    s.section("b", 3, 12, {"drone": 0.6, "choir": 0.4, "metal": 0.45, "lead": 0.55, "boom": 0.5},
              opts={"choir": dict(vowel="u", lo=48, hi=62, low=True), "lead": dict(instr="horn", lo=-7, hi=0, density=0.5),
                    "boom": dict(every=3)})
    s.section("c", 3, 12, {"drone": 0.8, "metal": 0.5, "wind": 0.3}, end=0, opts={"drone": dict(choir=True, bright=0.2)})
    return s


def dungeon_2():
    s = Song("dungeon_2", 4202, 57, "harmonic", 46, 3, room=0.95, damp=0.6, wet=0.52, target=-23.0, tone=5500)
    s.section("intro", 1, 15, {"drone": 0.9}, opts={"drone": dict(bright=0.2, fifth=False)})
    s.section("a", 3, 12, {"drone": 0.6, "strings": 0.35, "metal": 0.45},
              opts={"strings": dict(lo=45, hi=62, bright=0.3, attack=3.0), "metal": dict(rate=0.1, bright=0.5)})
    s.section("b", 3, 12, {"drone": 0.6, "choir": 0.35, "bells": 0.25, "lead": 0.5, "boom": 0.45},
              opts={"choir": dict(vowel="o", lo=50, hi=64, low=True), "lead": dict(instr="whistle", lo=-2, hi=5, density=0.4),
                    "bells": dict(rate=0.1, lo=60, hi=79)})
    s.section("c", 3, 12, {"drone": 0.8, "metal": 0.4, "wind": 0.35}, end=0, opts={"drone": dict(choir=True)})
    return s


def combat_1():
    s = Song("combat_1", 5101, 50, "aeolian", 112, 4, room=0.75, damp=0.5, wet=0.25, target=-17.0)
    loop = progression(s.rng, s.key, 4, 0)
    s.section("intro", 4, 4, {"taiko": 0.8, "brass_ost": 0.7, "swell": 0.0}, prog=loop, opts={"taiko": dict(half=True)})
    s.section("a", 8, 4, {"taiko": 0.9, "brass_ost": 0.8, "string_ost": 0.7, "timp": 0.6, "swell": 0.8}, prog=loop * 2,
              opts={"timp": dict(every=4)})
    s.section("b", 8, 4, {"taiko": 0.9, "brass_ost": 0.7, "strings": 0.6, "lead": 1.0, "swell": 0.8}, prog=loop * 2,
              opts={"lead": dict(instr="horn", lo=0, hi=9, octave=0, motif=2), "taiko": dict(patterns=(2, 3))})
    s.section("c", 8, 4, {"taiko": 0.7, "choir": 0.7, "string_ost": 0.7, "boom": 0.7, "swell": 0.8},
              opts={"taiko": dict(half=True), "choir": dict(vowel="a", attack=0.6)})
    s.section("d", 8, 4, {"taiko": 0.9, "brass_ost": 0.7, "horns": 0.6, "string_ost": 0.6, "swell": 0.8},
              opts={"brass_ost": dict(pattern=3), "taiko": dict(patterns=(4, 2))})
    s.section("a2", 8, 4, {"taiko": 1.0, "brass_ost": 0.8, "string_ost": 0.6, "lead": 1.0, "choir": 0.5, "timp": 0.6, "swell": 0.8},
              prog=loop * 2, opts={"lead": dict(instr="horn", lo=2, hi=11), "taiko": dict(rims=True), "timp": dict(every=4)})
    s.section("outro", 2, 4, {"timp": 0.9, "strings": 0.7, "boom": 0.8, "choir": 0.5}, prog=[5, 0],
              opts={"boom": dict(every=1)})
    return s


def combat_2():
    s = Song("combat_2", 5202, 52, "phrygian", 104, 4, room=0.78, damp=0.5, wet=0.25, target=-17.0)
    loop = progression(s.rng, s.key, 4, 0)
    s.section("intro", 4, 4, {"string_ost": 0.7, "taiko": 0.6, "swell": 0.0}, prog=loop, opts={"taiko": dict(half=True)})
    s.section("a", 8, 4, {"taiko": 0.9, "string_ost": 0.7, "brass_ost": 0.7, "swell": 0.8, "boom": 0.5}, prog=loop * 2,
              opts={"brass_ost": dict(pattern=1), "taiko": dict(patterns=(4, 0)), "boom": dict(every=4)})
    s.section("b", 8, 4, {"taiko": 0.9, "string_ost": 0.6, "horns": 0.6, "lead": 1.0, "timp": 0.6, "swell": 0.8}, prog=loop * 2,
              opts={"lead": dict(instr="horn", lo=0, hi=9), "timp": dict(every=2)})
    s.section("c", 6, 4, {"taiko": 0.7, "choir": 0.6, "brass_ost": 0.6, "swell": 0.7},
              opts={"taiko": dict(half=True), "brass_ost": dict(pattern=2), "choir": dict(vowel="o", attack=0.5)})
    s.section("d", 8, 4, {"taiko": 0.9, "string_ost": 0.7, "lead": 0.9, "timp": 0.6, "swell": 0.8}, prog=loop * 2,
              opts={"lead": dict(instr="horn", lo=-2, hi=7, motif=1), "taiko": dict(patterns=(0, 3)), "timp": dict(every=4)})
    s.section("a2", 8, 4, {"taiko": 1.0, "string_ost": 0.6, "brass_ost": 0.8, "lead": 1.0, "choir": 0.5, "swell": 0.8},
              prog=loop * 2, opts={"lead": dict(instr="horn", lo=2, hi=11, motif=2), "brass_ost": dict(pattern=1),
                                   "taiko": dict(patterns=(1, 4), rims=True)})
    s.section("outro", 2, 4, {"timp": 0.9, "horns": 0.7, "boom": 0.8}, prog=[5, 0], opts={"boom": dict(every=1)})
    return s


def boss_dragon():
    s = Song("boss_dragon", 6101, 50, "aeolian", 96, 4, room=0.85, damp=0.45, wet=0.3, target=-16.5)
    loop = progression(s.rng, s.key, 4, 0)
    s.section("intro", 4, 4, {"boom": 0.8, "drone": 0.7, "taiko": 0.6, "swell": 0.0}, prog=loop,
              opts={"taiko": dict(half=True), "boom": dict(every=2), "drone": dict(choir=True)})
    s.section("a", 8, 4, {"taiko": 0.9, "brass_ost": 0.7, "string_ost": 0.6, "chant": 0.9, "swell": 0.8}, prog=loop * 2,
              opts={"taiko": dict(patterns=(3, 0)), "chant": dict(lo=-3, hi=5, octave=0)})
    s.section("b", 8, 4, {"taiko": 1.0, "horns": 0.7, "choir": 0.6, "lead": 1.0, "timp": 0.7, "boom": 0.6, "swell": 0.8},
              prog=loop * 2, opts={"lead": dict(instr="horn", lo=2, hi=11), "choir": dict(vowel="a", attack=0.5), "boom": dict(every=4)})
    s.section("c", 8, 4, {"taiko": 0.7, "chant": 0.9, "strings": 0.6, "swell": 0.8},
              opts={"taiko": dict(half=True), "chant": dict(lo=0, hi=7, octave=0, vowels="oaoe")})
    s.section("a2", 8, 4, {"taiko": 1.0, "brass_ost": 0.8, "string_ost": 0.6, "chant": 0.9, "horns": 0.6, "timp": 0.6, "swell": 0.8},
              prog=loop * 2, opts={"taiko": dict(rims=True), "timp": dict(every=2)})
    s.section("outro", 4, 4, {"choir": 0.8, "horns": 0.7, "boom": 0.9, "timp": 0.8}, end=0,
              opts={"boom": dict(every=1), "choir": dict(vowel="o", lo=45, hi=64, low=True)})
    return s


def oblivion_1():
    s = Song("oblivion_1", 7101, 50, "harmonic", 60, 4, room=0.93, damp=0.35, wet=0.45, target=-20.0, tone=7000)
    s.section("intro", 1, 12, {"dist_drone": 0.7, "metal": 0.5}, opts={"metal": dict(rate=0.3, bright=1.0)})
    s.section("a", 3, 8, {"dist_drone": 0.8, "dist_hits": 0.7, "cluster_choir": 0.5, "riser": 0.6},
              opts={"dist_hits": dict(rate=0.35)})
    s.section("b", 4, 8, {"dist_drone": 0.6, "stabs": 0.8, "dist_hits": 0.7, "metal": 0.5, "lead": 0.6, "riser": 0.6},
              opts={"lead": dict(instr="whistle", lo=-2, hi=8, density=0.6), "dist_drone": dict(cluster=(0, 6), ring=47.0)})
    s.section("c", 3, 8, {"cluster_choir": 0.7, "dist_drone": 0.5, "metal": 0.6, "wind": 0.4},
              opts={"metal": dict(rate=0.4, bright=1.0)})
    s.section("d", 4, 8, {"dist_drone": 0.8, "stabs": 0.9, "dist_hits": 0.9, "taiko": 0.6, "riser": 0.6},
              opts={"taiko": dict(half=True), "dist_drone": dict(drive=5.0)})
    s.section("outro", 2, 8, {"dist_drone": 0.6, "cluster_choir": 0.6, "metal": 0.4}, end=0)
    return s


def sovngarde_1():
    s = Song("sovngarde_1", 8101, 62, "lydian", 60, 4, room=0.94, damp=0.3, wet=0.5, target=-19.5, tone=11000)
    s.section("intro", 2, 8, {"choir": 0.7, "drone": 0.5, "bells": 0.3}, opts={"choir": dict(vowel="a", attack=3.0)})
    s.section("a", 4, 8, {"choir": 0.6, "strings": 0.6, "harp": 0.6, "lead": 0.9},
              opts={"lead": dict(instr="horn", lo=-2, hi=7), "harp": dict(pattern="updown", lo=57, hi=86)})
    s.section("b", 4, 8, {"choir": 0.8, "strings": 0.7, "horns": 0.6, "lead": 0.9, "timp": 0.6, "bells": 0.3},
              opts={"lead": dict(instr="flute", lo=4, hi=13, motif=2), "choir": dict(lo=57, hi=79, voices=5)})
    s.section("a2", 4, 8, {"choir": 0.8, "strings": 0.6, "harp": 0.6, "chant": 0.6, "timp": 0.5},
              opts={"chant": dict(lo=-2, hi=5, octave=0, vowels="a"), "harp": dict(pattern="wave", lo=57, hi=86)})
    s.section("outro", 2, 8, {"choir": 0.8, "drone": 0.6, "bells": 0.3}, end=0, opts={"choir": dict(vowel="a", attack=2.0)})
    return s


def discovery():
    """6-10 s sting: timpani, low swell, rising horn call, choir bloom, harp run."""
    rng = np.random.default_rng(9101)
    k = Key(62, "dorian")
    mix = Mix(14.0)
    mix.add(timpani(38, 1.0, rng), 0.05, 0.8, send=0.4)
    mix.add(boom(rng, f=36.0), 0.05, 0.4, send=0.3)
    for m in (38, 45, 50):
        mix.add(strings_note(m, 3.5, rng, attack=1.2, release=3.0, bright=0.6), 0.05, 0.22, send=0.45)
    call = [(0.35, 0.45, k.m(-7 + 0), 0.8), (0.82, 0.4, k.m(-7 + 4), 0.85), (1.24, 0.35, k.m(-7 + 3), 0.8),
            (1.6, 0.4, k.m(-7 + 4), 0.85), (2.02, 0.5, k.m(-7 + 6), 0.9), (2.55, 2.4, k.m(-7 + 7), 1.0)]
    t0, y = render_lead(call, "horn", rng)
    mix.add(y, t0, 0.42, send=0.5, pan=-0.1)
    for j, m in enumerate((62, 66, 69, 74)):  # bright D major bloom (Picardy lift)
        mix.add(choir_note(m, 4.0, rng, vowel="a", attack=1.4, release=3.0), 2.4, 0.25, send=0.6)
        mix.add(strings_note(m - 12, 4.0, rng, attack=1.0, release=3.0, bright=0.8), 2.4, 0.12, send=0.5)
    for j, m in enumerate((62, 66, 69, 74, 78, 81, 86)):
        mix.add(harp(m, 4.0, rng), 2.5 + j * 0.11, 0.35, send=0.5, pan=-0.6 + 0.2 * j)
    mix.add(cymbal_swell(2.4, rng), 0.05, 0.6, send=0.4)
    out = render_mix(mix, 0.9, 0.35, 0.45, tone=10000)
    return master(out, -18.0, max_len=9.0)


def level_up():
    """3 s sting: bright harp arpeggio, choir swell, chimes."""
    rng = np.random.default_rng(9202)
    mix = Mix(7.0)
    for j, m in enumerate((62, 66, 69, 74, 78, 81)):
        mix.add(harp(m, 3.5, rng, bright=0.8), 0.03 + j * 0.07, 0.4, send=0.45, pan=-0.5 + 0.2 * j)
    for m in (62, 69, 74, 78):
        mix.add(choir_note(m, 1.6, rng, vowel="a", attack=0.35, release=1.4), 0.15, 0.25, send=0.6)
    mix.add(strings_note(50, 1.6, rng, attack=0.3, release=1.4, bright=0.5), 0.1, 0.25, send=0.4)
    for j, m in enumerate((86, 90, 93)):
        mix.add(bell(m, 3.0, rng, bright=0.8), 0.5 + j * 0.09, 0.3, send=0.6, pan=0.3 - 0.3 * j)
    mix.add(timpani(38, 0.7, rng, dur=2.0), 0.03, 0.5, send=0.3)
    out = render_mix(mix, 0.82, 0.35, 0.4, tone=11000)
    return master(out, -18.0, max_len=3.4)


TRACKS = {
    "explore_day_1": explore_day_1, "explore_day_2": explore_day_2, "explore_day_3": explore_day_3,
    "explore_night_1": explore_night_1, "explore_night_2": explore_night_2,
    "town_1": town_1, "town_2": town_2,
    "dungeon_1": dungeon_1, "dungeon_2": dungeon_2,
    "combat_1": combat_1, "combat_2": combat_2,
    "boss_dragon": boss_dragon, "oblivion_1": oblivion_1, "sovngarde_1": sovngarde_1,
    "discovery": discovery, "level_up": level_up,
}


# =====================================================================================================  output

def find_encoder():
    ff = shutil.which("ffmpeg")
    if ff:
        enc = subprocess.run([ff, "-hide_banner", "-encoders"], capture_output=True, text=True).stdout
        if "libvorbis" in enc:
            return ("ffmpeg", ff)
    oe = shutil.which("oggenc")
    if oe:
        return ("oggenc", oe)
    try:
        import soundfile  # noqa: F401
        return ("soundfile", None)
    except ImportError:
        pass
    return (None, None)


def to_pcm16(x: np.ndarray, rng) -> bytes:
    d = (rng.random(x.shape) - rng.random(x.shape)) / 32768.0
    return (np.clip(x + d, -1.0, 1.0).T * 32767.0).astype("<i2").tobytes()


def write_wav(path, pcm: bytes):
    with wave.open(str(path), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm)


def encode(name: str, x: np.ndarray, quality: float, encoder, wav_dir=None):
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    out = OUT_DIR / f"{name}.ogg"
    pcm = to_pcm16(x, np.random.default_rng(1))
    if wav_dir:
        pathlib.Path(wav_dir).mkdir(parents=True, exist_ok=True)
        write_wav(pathlib.Path(wav_dir) / f"{name}.wav", pcm)
    kind, exe = encoder
    title = f"Skycraft - {name.replace('_', ' ').title()}"
    if kind == "ffmpeg":
        subprocess.run([exe, "-y", "-hide_banner", "-loglevel", "error", "-f", "s16le", "-ar", str(SR), "-ac", "2", "-i", "-",
                        "-c:a", "libvorbis", "-q:a", str(quality), "-metadata", f"title={title}",
                        "-metadata", "artist=Skycraft (procedural, original)", str(out)], input=pcm, check=True)
    elif kind == "oggenc":
        subprocess.run([exe, "-Q", "-r", "-B", "16", "-C", "2", "-R", str(SR), "-q", str(quality), "-t", title,
                        "-a", "Skycraft (procedural, original)", "-o", str(out), "-"], input=pcm, check=True)
    elif kind == "soundfile":
        import soundfile
        soundfile.write(str(out), x.T, SR, format="OGG", subtype="VORBIS")
    else:
        raise SystemExit("No Ogg Vorbis encoder found (install ffmpeg with libvorbis, or vorbis-tools).")
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("tracks", nargs="*", help="track names (default: all)")
    ap.add_argument("--quality", type=float, default=2.0, help="Vorbis quality (-1..10, default 2)")
    ap.add_argument("--wav", metavar="DIR", help="also write 16-bit WAVs to DIR")
    ap.add_argument("--list", action="store_true")
    args = ap.parse_args()
    if args.list:
        print("\n".join(TRACKS))
        return
    names = args.tracks or list(TRACKS)
    for n in names:
        if n not in TRACKS:
            raise SystemExit(f"unknown track {n!r}; use --list")
    encoder = find_encoder()
    if encoder[0] is None and not args.wav:
        raise SystemExit("No Ogg Vorbis encoder found (ffmpeg+libvorbis / oggenc / python soundfile); use --wav DIR.")
    total = 0
    for n in names:
        t0 = time.time()
        fn = TRACKS[n]
        res = fn()
        x = res.render() if isinstance(res, Song) else res
        if not np.all(np.isfinite(x)):
            raise SystemExit(f"{n}: non-finite samples")
        if encoder[0] is None:
            write_wav(pathlib.Path(args.wav) / f"{n}.wav", to_pcm16(x, np.random.default_rng(1)))
            print(f"{n:16s} {x.shape[1] / SR:6.1f}s  (wav only)")
            continue
        out = encode(n, x, args.quality, encoder, args.wav)
        size = out.stat().st_size
        total += size
        peak = 20 * math.log10(max(1e-9, float(np.max(np.abs(x)))))
        rms = 20 * math.log10(max(1e-9, float(np.sqrt(np.mean(x ** 2)))))
        print(f"{n:16s} {x.shape[1] / SR:6.1f}s  peak {peak:5.1f} dB  rms {rms:5.1f} dB  {size / 1e6:5.2f} MB  "
              f"({time.time() - t0:4.1f}s)", flush=True)
    if total:
        print(f"total {total / 1e6:.2f} MB -> {OUT_DIR.relative_to(REPO)}")


if __name__ == "__main__":
    sys.exit(main())
