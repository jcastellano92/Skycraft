#!/usr/bin/env python3
"""
Procedural synthesizer for Skyrim-styled sound effects:
Thu'um shouting voices, Word Wall choir chanting, Dragon Soul absorption,
Shrine blessings, Standing Stone resonance, Lockpicking ratchets/snaps,
and Skyrim UI audio.
"""
import os
import math
import subprocess
import wave
import numpy as np
from pathlib import Path

SR = 44100
FFMPEG = r"C:\Program Files\Streamlabs OBS\resources\app.asar.unpacked\node_modules\obs-studio-node\ffmpeg.exe"
REPO = Path(__file__).resolve().parents[2]
SOUNDS_DIR = REPO / "mod/src/main/resources/assets/skycraft/sounds"

def write_ogg(samples: np.ndarray, rel_path: str):
    out_file = SOUNDS_DIR / rel_path
    out_file.parent.mkdir(parents=True, exist_ok=True)
    wav_path = out_file.with_suffix(".wav")
    
    # Normalize and clip
    samples = np.nan_to_num(samples)
    peak = np.max(np.abs(samples))
    if peak > 0:
        samples = samples / max(peak, 1.0) * 0.95
    int_samples = (samples * 32767).astype(np.int16)
    
    with wave.open(str(wav_path), "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(SR)
        wf.writeframes(int_samples.tobytes())
        
    cmd = [FFMPEG, "-y", "-i", str(wav_path), "-c:a", "libvorbis", "-q:a", "4", str(out_file)]
    subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
    wav_path.unlink()
    print(f"Generated {rel_path} ({out_file.stat().st_size} bytes)")

def synth_all():
    # 1. UI Menu Open (heavy stone whoosh + air)
    t = np.linspace(0, 0.45, int(SR * 0.45), False)
    noise = np.random.uniform(-1, 1, len(t))
    # low pass sweep
    env = np.exp(-t * 8) * (1 - np.exp(-t * 40))
    low_freq = 90 + 60 * np.exp(-t * 10)
    sine = np.sin(2 * np.pi * low_freq * t)
    menu_open = (noise * 0.35 + sine * 0.65) * env
    write_ogg(menu_open, "ui/menu_open.ogg")

    # 2. UI Menu Close (lighter parchment whoosh)
    t = np.linspace(0, 0.25, int(SR * 0.25), False)
    noise = np.random.uniform(-1, 1, len(t))
    env = np.exp(-t * 16) * (1 - np.exp(-t * 60))
    menu_close = noise * env * 0.5
    write_ogg(menu_close, "ui/menu_close.ogg")

    # 3. UI Menu Click (crisp tactile tap)
    t = np.linspace(0, 0.08, int(SR * 0.08), False)
    click = np.sin(2 * np.pi * 320 * t) * np.exp(-t * 60) + np.sin(2 * np.pi * 780 * t) * np.exp(-t * 90) * 0.5
    write_ogg(click, "ui/menu_click.ogg")

    # 4. Lockpick Turn (ratchet / tumbler micro-clicks)
    t = np.linspace(0, 0.12, int(SR * 0.12), False)
    ratchet = np.zeros_like(t)
    for offset in [0.0, 0.03, 0.06, 0.09]:
        idx = int(offset * SR)
        span = len(t) - idx
        sub_t = t[:span]
        ratchet[idx:] += np.sin(2 * np.pi * 1400 * sub_t) * np.exp(-sub_t * 120) * 0.5
    write_ogg(ratchet, "ui/lockpick_turn.ogg")

    # 5. Lockpick Strain (metal scraping / friction tension)
    t = np.linspace(0, 0.2, int(SR * 0.2), False)
    noise = np.random.uniform(-1, 1, len(t))
    strain = (np.sin(2 * np.pi * 920 * t) + 0.4 * np.sin(2 * np.pi * 1840 * t) + noise * 0.2) * np.exp(-t * 12)
    write_ogg(strain, "ui/lockpick_strain.ogg")

    # 6. Lockpick Break (sharp metallic snap)
    t = np.linspace(0, 0.35, int(SR * 0.35), False)
    noise = np.random.uniform(-1, 1, len(t))
    snap = (np.sin(2 * np.pi * 2200 * t) * np.exp(-t * 40) + noise * 0.8 * np.exp(-t * 25))
    write_ogg(snap, "ui/lockpick_break.ogg")

    # 7. Lockpick Unlock (heavy bolt release + chime)
    t = np.linspace(0, 0.6, int(SR * 0.6), False)
    thump = np.sin(2 * np.pi * 110 * t) * np.exp(-t * 8)
    chime = np.sin(2 * np.pi * 1050 * t) * np.exp(-t * 5) * 0.5 + np.sin(2 * np.pi * 1580 * t) * np.exp(-t * 6) * 0.3
    write_ogg(thump + chime, "ui/lockpick_unlock.ogg")

    # 8. Skill Up Chime (celestial ascending harmony)
    t = np.linspace(0, 1.4, int(SR * 1.4), False)
    c1 = np.sin(2 * np.pi * 523.25 * t) * np.exp(-t * 2.5)  # C5
    e1 = np.sin(2 * np.pi * 659.25 * t) * np.exp(-t * 2.2)  # E5
    g1 = np.sin(2 * np.pi * 783.99 * t) * np.exp(-t * 2.0)  # G5
    c2 = np.sin(2 * np.pi * 1046.50 * t) * np.exp(-t * 1.8) # C6
    skill_up = (c1 * 0.3 + e1 * 0.3 + g1 * 0.3 + c2 * 0.4)
    write_ogg(skill_up, "ui/skill_up.ogg")

    # 9. Quest Update (deep Skyrim war drum hit)
    t = np.linspace(0, 1.2, int(SR * 1.2), False)
    drum = np.sin(2 * np.pi * 65 * np.exp(-t * 3) * t) * np.exp(-t * 3.5)
    body = np.random.uniform(-1, 1, len(t)) * 0.15 * np.exp(-t * 12)
    write_ogg(drum + body, "ui/quest_update.ogg")

    # 10. Shouting: Fus Ro Dah (booming Thu'um voice burst)
    t = np.linspace(0, 1.8, int(SR * 1.8), False)
    # Formants simulating deep male shout syllables "FUS... RO DAH"
    vowel1 = np.sin(2 * np.pi * 130 * t) * 0.5 + np.sin(2 * np.pi * 390 * t) * 0.3 + np.sin(2 * np.pi * 750 * t) * 0.2
    shockwave = np.sin(2 * np.pi * 55 * t) * np.exp(-t * 1.8)
    sub = np.sin(2 * np.pi * 38 * t) * np.exp(-t * 1.2) * 0.8
    noise = np.random.uniform(-1, 1, len(t)) * np.exp(-t * 2.2) * 0.35
    fus = (vowel1 * 0.5 + shockwave * 0.8 + sub * 0.6 + noise) * (1 - np.exp(-t * 30))
    write_ogg(fus, "shout/fus_ro_dah.ogg")

    # 11. Shouting: Whirlwind Sprint (rapid sonic displacement)
    t = np.linspace(0, 0.9, int(SR * 0.9), False)
    freq = 300 + 1200 * np.exp(-t * 4)
    whoosh = np.sin(2 * np.pi * freq * t) * np.exp(-t * 3) + np.random.uniform(-1, 1, len(t)) * np.exp(-t * 3.5) * 0.6
    write_ogg(whoosh, "shout/wuld_nah_kest.ogg")

    # 12. Shouting: Fire Breath (incinerating dragon blast)
    t = np.linspace(0, 1.6, int(SR * 1.6), False)
    noise = np.random.uniform(-1, 1, len(t))
    roar = np.sin(2 * np.pi * 75 * t) * np.exp(-t * 1.5)
    fire = (noise * 0.7 + roar * 0.5) * (1 - np.exp(-t * 20)) * np.exp(-t * 1.8)
    write_ogg(fire, "shout/yol_toor_shul.ogg")

    # 13. Shouting: Generic Thu'um Voice
    t = np.linspace(0, 1.3, int(SR * 1.3), False)
    voice = np.sin(2 * np.pi * 120 * t) * 0.6 + np.sin(2 * np.pi * 240 * t) * 0.4
    boom = np.sin(2 * np.pi * 60 * t) * np.exp(-t * 2.5)
    shout_gen = (voice * 0.4 + boom * 0.8) * np.exp(-t * 2)
    write_ogg(shout_gen, "shout/generic.ogg")

    # 14. Word Wall Choir Chant (deep looping Nordic throat drone)
    t = np.linspace(0, 4.0, int(SR * 4.0), False)
    drone1 = np.sin(2 * np.pi * 65.4 * t)   # C2
    drone2 = np.sin(2 * np.pi * 98.0 * t)   # G2
    drone3 = np.sin(2 * np.pi * 130.8 * t)  # C3
    formant = np.sin(2 * np.pi * 600 * t) * 0.2 + np.sin(2 * np.pi * 900 * t) * 0.15
    # Slow breathing modulation
    breath = 0.7 + 0.3 * np.sin(2 * np.pi * 0.5 * t)
    wall_chant = (drone1 * 0.4 + drone2 * 0.3 + drone3 * 0.3 + formant * 0.25) * breath
    write_ogg(wall_chant, "wordwall/chant.ogg")

    # 15. Word Wall Learn (absorption flash & revelation)
    t = np.linspace(0, 2.0, int(SR * 2.0), False)
    rise = np.sin(2 * np.pi * (100 + 400 * (t / 2.0) ** 2) * t) * (t / 2.0) * np.exp(-t * 1.2)
    shimmer = np.sin(2 * np.pi * 1200 * t) * np.exp(-t * 2.5) * 0.4 + np.sin(2 * np.pi * 1800 * t) * np.exp(-t * 3.0) * 0.3
    write_ogg(rise + shimmer, "wordwall/learn.ogg")

    # 16. Dragon Soul Absorption (crackling flame vortex + celestial hum)
    t = np.linspace(0, 3.2, int(SR * 3.2), False)
    sub = np.sin(2 * np.pi * 50 * t) * (1 - np.exp(-t * 2)) * np.exp(-t * 0.8)
    roar = np.random.uniform(-1, 1, len(t)) * 0.35 * (1 - np.exp(-t * 4)) * np.exp(-t * 1.0)
    chime = np.sin(2 * np.pi * 880 * t) * np.exp(-t * 1.5) * 0.3 + np.sin(2 * np.pi * 1320 * t) * np.exp(-t * 1.8) * 0.2
    write_ogg(sub + roar + chime, "magic/dragon_soul.ogg")

    # 17. Shrine Pray (sacred temple bell + divine echo)
    t = np.linspace(0, 2.5, int(SR * 2.5), False)
    bell1 = np.sin(2 * np.pi * 440 * t) * np.exp(-t * 1.2)
    bell2 = np.sin(2 * np.pi * 880 * t) * np.exp(-t * 1.8) * 0.5
    bell3 = np.sin(2 * np.pi * 1320 * t) * np.exp(-t * 2.4) * 0.3
    hum = np.sin(2 * np.pi * 220 * t) * np.exp(-t * 0.9) * 0.4
    write_ogg(bell1 * 0.4 + bell2 * 0.3 + bell3 * 0.2 + hum * 0.3, "magic/shrine_pray.ogg")

    # 18. Standing Stone (celestial hum & harmonic pulse)
    t = np.linspace(0, 2.8, int(SR * 2.8), False)
    freqs = [196.0, 293.66, 392.0, 587.33] # G3, D4, G4, D5
    harmonics = sum(np.sin(2 * np.pi * f * t) * (0.3 / (i + 1)) for i, f in enumerate(freqs))
    pulse = (1 - np.exp(-t * 4)) * np.exp(-t * 1.1)
    write_ogg(harmonics * pulse, "magic/standing_stone.ogg")

    # 19. Nirnroot Shimmering Hum (high-pitched bell loop)
    t = np.linspace(0, 3.0, int(SR * 3.0), False)
    mod = np.sin(2 * np.pi * 4.5 * t) # 4.5 Hz tremolo
    f1 = 2093.0 # C7
    f2 = 2793.8 # F7
    f3 = 3135.9 # G7
    nirn = (np.sin(2 * np.pi * f1 * t) * 0.4 + np.sin(2 * np.pi * f2 * t) * 0.3 + np.sin(2 * np.pi * f3 * t) * 0.3) * (0.7 + 0.3 * mod)
    write_ogg(nirn * 0.4, "world/nirnroot_hum.ogg")

if __name__ == "__main__":
    synth_all()

