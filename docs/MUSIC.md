# Skycraft music

Skycraft replaces vanilla background music with a situational soundtrack (see the world module's
`world/client/MusicController.java`): exploration by day and night, towns, dungeons, combat, Oblivion (Nether),
Sovngarde (End), plus a sting when a location is discovered and a fanfare when you level up.

## The original Skycraft soundtrack

Every track that ships with the mod is **original music written for Skycraft**. It is not Skyrim's soundtrack and
does not quote it. The pieces are composed and synthesised by `tools/music/compose.py`, a small seeded program. It
writes modal chord progressions and develops random motifs into melodies, then renders every instrument from
scratch:

* string drones from detuned band-limited saws,
* choir from vowel formant filters,
* additive flute, horn and fiddle with vibrato and portamento,
* Karplus-Strong harp and lute,
* modal timpani, taiko and metallic percussion,
* Freeverb.

| File (`assets/skycraft/sounds/music/`) | Event | Character |
| --- | --- | --- |
| `explore_day_1..3.ogg` | `skycraft:music.explore` | D Dorian / A Aeolian / G Mixolydian, flute, horn and fiddle over drones, harp, choir |
| `explore_night_1..2.ogg` | `skycraft:music.explore_night` | slow, sparse, wind, glassy bells, low flute and whistle |
| `town_1..2.ogg` | `skycraft:music.town` | warm folk dance: lute, frame drum, flute and fiddle |
| `dungeon_1..2.ogg` | `skycraft:music.dungeon` | Phrygian / harmonic-minor drones, distant metal, low choir |
| `combat_1..2.ogg` | `skycraft:music.combat` | 104–112 BPM taiko and timpani, low brass and string ostinati, horn melody |
| `boss_dragon.ogg` | `skycraft:music.boss` (also in the combat pool) | war drums, male chant, brass |
| `oblivion_1.ogg` | `skycraft:music.oblivion` | dissonant, ring-modulated, distorted |
| `sovngarde_1.ogg` | `skycraft:music.sovngarde` | bright Lydian choir, horns, harp |
| `discovery.ogg` | `skycraft:music.discovery` | 9 s sting |
| `level_up.ogg` | `skycraft:music.level_up` | 3 s fanfare (played by the atmosphere module) |

To regenerate the tracks (about 10 minutes on 4 cores), you need Python 3 with numpy and an `ffmpeg` that has
libvorbis. Minecraft only plays Ogg Vorbis.

```
python3 tools/music/compose.py                    # all tracks, Vorbis quality 2
python3 tools/music/compose.py town_1 --wav /tmp  # one track, and keep a WAV copy
python3 tools/music/compose.py --list
```

The seeds are fixed, so a run reproduces the same pieces. To get a new piece, change a track's seed or its section
list in `compose.py`.

## Replacing music with your own resource pack

Any resource pack can override the events in `assets/skycraft/sounds.json`, for example:

```json
{ "music.combat": { "replace": true, "sounds": [ { "name": "mypack:music/battle", "stream": true } ] } }
```

## Using your own copy of the Skyrim soundtrack (personal use)

If you own *The Elder Scrolls V: Skyrim*, you can hear its real soundtrack in Skycraft. Skycraft can't ship that music
because it is copyrighted. `tools/music/import_skyrim_ost.py` turns the files from **your own installation** into a
private resource pack. It never downloads anything.

1. **Export the music from your game.** Skyrim stores its music as `.xwm` files named like
   `mus_explore_day_01`, `mus_town_day_01`, `mus_dungeon_01`, `mus_combat_boss`, `mus_discover_*` and
   `mus_levelup_*`. Most of them are inside the `Skyrim - Sounds.bsa` archive in the game's `Data` folder. Open the
   archive with a BSA extraction tool, such as Bethesda Archive Extractor or Cathedral Assets Optimizer. Extract its
   `music` folder somewhere, for example `~/SkyrimMusic`. Files you already converted to `.wav`, `.mp3`, `.flac` or
   `.ogg` also work.
2. **Install ffmpeg.** It decodes `.xwm` and encodes Ogg Vorbis.
3. **Build the pack:**
   ```
   python3 tools/music/import_skyrim_ost.py ~/SkyrimMusic --dry-run        # check which file goes where
   python3 tools/music/import_skyrim_ost.py ~/SkyrimMusic -o ~/.minecraft/resourcepacks/skycraft_personal_ost.zip
   ```
   The script matches files by name. For example, `combat*boss` maps to `music.boss`, `discover` to
   `music.discovery`, `town` to `music.town`, `explore*night` to `music.explore_night` and `explore`/`day` to
   `music.explore`. If no file matches an event, that event keeps Skycraft's original track. Use
   `--map 'regex=music.event'` to add rules, for example `--map 'mus_special_.*=music.sovngarde'`. Use
   `--quality` to set the Vorbis quality (default 5).
4. In Minecraft, open *Options → Resource Packs* and enable **"Skycraft – Personal Skyrim OST"**.

**Legal note.** The Skyrim soundtrack is © Bethesda Softworks and its composer. The pack you build holds a copy of
music you own, for your personal use only. Do not share, upload or redistribute it, and don't put it in modpacks.
Skycraft does not include or link to any of that music.
