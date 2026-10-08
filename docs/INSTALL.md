# Installing and playing Skycraft

Skycraft runs on **Minecraft 1.20.1 with Forge 47.4.10** and **Java 17**. It needs **6–8 GB of RAM** for the game
(Distant Horizons and about 60 mods).

## 1. Get the files

Open the repository on GitHub → **Actions** → the latest green **Build** run → **Artifacts**:

* `Skycraft-client-instance`: the ready-made Prism Launcher / MultiMC instance (recommended).
* `Skycraft-server`: a complete dedicated server (Forge plus server-side mods).
* `Skycraft-exports`: `Skycraft.mrpack` for the Modrinth App and `Skycraft-curseforge.zip` for the CurseForge App.

GitHub wraps each artifact in a zip. Unzip it once to get the file named above.

## 2. Install on Prism Launcher (recommended)

1. Install [Prism Launcher](https://prismlauncher.org/) and sign in with your Microsoft account.
2. Click **Add Instance → Import**, choose `Skycraft-client-instance.zip`, then **OK**.
   Prism downloads Minecraft 1.20.1 and Forge 47.4.10 itself; all mods, configs and shaders are already inside.
3. Right-click the instance → **Edit → Settings → Java**:
   * Java: 17 (Prism can download it for you: *Auto-detect* or *Download Java*).
   * Memory: minimum 4096 MB, maximum 8192 MB (already set by the instance).
4. Click **Launch**. The first start takes a few minutes while Forge prepares everything.

Alternative: point Prism at the packwiz manifest so updates are automatic. Add the pre-launch command
`"$INST_JAVA" -jar packwiz-installer-bootstrap.jar https://raw.githubusercontent.com/jcastellano92/Skycraft/main/pack/pack.toml`
(this works once the repository is public, and needs packwiz-installer-bootstrap.jar in the instance's `.minecraft` folder).

## 3. Play with friends using Essential (host on your own PC)

Essential is included. Nobody needs to port-forward or rent a server.

1. Everyone installs the same Skycraft instance (step 2) and adds each other as friends in the Essential menu
   (the Essential button on the title and pause screens).
2. The host creates or opens a singleplayer world, then opens **Essential → Host World / Invite Friends**
   and invites their friends.
3. Friends accept the invite and join directly.
4. On joining, everyone is asked whether to **fast travel to the party leader** or stay where they are. Create a
   party with **U** (or `/party create`, `/party invite <name>`) so you share quests, rewards and map markers.
   Party members always show on the Skyrim map (**N**) and compass. Click a member on the map to fast travel to them.

The host's PC runs the server, so give the host at least 8 GB of RAM in Prism. Guests can use 6 GB.

## 4. Dedicated server (always-on world)

1. Unzip `Skycraft-server.zip` onto the server machine. It contains Forge, all server-side mods, a tuned
   `server.properties` (flight allowed, because giants launch players into the air) and JVM flags in `user_jvm_args.txt`.
2. Read the [Minecraft EULA](https://aka.ms/MinecraftEULA) and set `eula=true` in `eula.txt`.
3. Start it with `./run.sh` (Linux/macOS) or `run.bat` (Windows). Give it 6–10 GB of RAM in `user_jvm_args.txt`.
4. Recommended before inviting players: pre-generate the world so Distant Horizons and the road network are ready:
   `/chunky radius 3000` then `/chunky start`.
5. Players join with the client instance from step 2. Essential isn't needed for a dedicated server, but is harmless.

## 5. Controls

Skycraft binds its keys on first launch. If another mod already uses one of them, Skycraft moves its own key to a
free one. Everything can be changed in **Options → Controls → Key Binds → Skycraft**.

| Key | Action |
| --- | --- |
| E | Skyrim inventory (C inside it opens the vanilla inventory for armor slots and crafting) |
| K | Skills: look to the heavens, spend perk points, level up |
| J | Journal: quests, factions, stats |
| G | Magic & shouts menu (left-click equips the right hand, right-click the left hand) |
| R / right-click | Cast the right-hand / left-hand spell (hold for concentration spells) |
| Z | Shout (hold for more words) |
| H | Racial power |
| O | Standing Stone power |
| X | Favorites |
| Left Alt (hold) | Power attack. Sprinting attacks are power attacks too. |
| V (hold) | Block with your weapon |
| I | Wait. Right-click any bed to sleep. |
| N | Skyrim map and fast travel. Xaero's World Map is on M. |
| U | Party |
| Y | Reputation and faction regalia |
| Sneak + use NPC | Pickpocket |
| Sneak + use corpse | Drag a body |

## 6. Performance tips

* Distant Horizons: open its settings (Video Settings → Distant Horizons) and lower the LOD
  render distance on weaker PCs. 64–128 chunks looks great and is cheap.
* Shaders (Complementary Reimagined) are optional: **Options → Video Settings → Shader Packs**. Skycraft's sky
  hands the sky over to the shader when one is active.
* If you stutter while exploring new land, pre-generate with Chunky (see the dedicated server section).

## 7. Troubleshooting

* **"Mod requires forge 47.4.10 or above"**: the instance must use Forge 47.4.10 (Edit → Version).
* **Game closes on launch**: give it more memory (at least 6 GB) and make sure it uses Java 17, not Java 21.
* **Kicked for flying on a server**: set `allow-flight=true` in `server.properties`.
* Logs are in the instance's `.minecraft/logs/latest.log`. Crash reports are in `.minecraft/crash-reports/`.
