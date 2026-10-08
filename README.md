# Skycraft

**Skycraft turns Minecraft into The Elder Scrolls V: Skyrim.** You start as a nobody, unable to do much more than
fight and mine an ore vein. From there you level up skills by using them, look to the heavens to unlock perks,
learn spells from tomes, absorb dragon souls, and shout. Along the way you join factions, take radiant quests and
bounties, trade with merchants, get thrown in jail, and adventure with your friends.

* **Minecraft 1.20.1, Forge 47.4.10, Java 17**: the most complete RPG mod ecosystem.
* **Skycraft Core** (`mod/`) is a custom mod that builds the Skyrim rules into Minecraft.
* About 60 well-known mods (`tools/modlist.txt`) supply towns, dungeons, NPCs, companions, vampires, werewolves,
  Distant Horizons, and performance.

## Install

Every CI run on GitHub Actions produces downloads (Actions → latest run → *Artifacts*):

| Artifact | Use |
| --- | --- |
| `Skycraft-client-instance` | Zip for **Prism Launcher / MultiMC**: *Add Instance → Import → zip*. Includes Forge, all mods, configs and shaders. Give it 6–8 GB RAM. |
| `Skycraft-server` | Ready Forge server with all server-side mods. Accept the EULA, then run `run.sh` / `run.bat`. |
| `Skycraft-exports` | `Skycraft.mrpack` (Modrinth App) and `Skycraft-curseforge.zip` (CurseForge App). Mods that are only on one platform can be missing from the other platform's export, so the Prism instance is the complete one. |
| `skycraft-core-jar` | Just the Skycraft Core mod. |

Full instructions, including hosting with friends through **Essential** and running a dedicated server, are in
[docs/INSTALL.md](docs/INSTALL.md).

You can also install with [packwiz-installer](https://packwiz.infra.link/tutorials/installing/packwiz-installer/)
pointed at `pack/pack.toml`.

## Controls (rebind in Options → Controls → Key Binds → Skycraft)

Skycraft moves any of its keys that clash with another mod's to a free key on first launch.

| Key | Action |
| --- | --- |
| **E** | Skyrim inventory (C inside it opens the vanilla inventory for armor slots and crafting) |
| **K** | Skills: look to the heavens, spend perk points, choose Health/Magicka/Stamina on level up |
| **J** | Journal: quests, factions, stats, bounties |
| **G** | Magic & shouts menu (left-click equips the right hand, right-click the left hand) |
| **R** / right-click | Cast the right-hand / left-hand spell (hold for concentration spells) |
| **Z** | Shout (hold longer for more Words of Power) |
| **H** | Racial greater power |
| **O** | Standing Stone power |
| **X** | Favorites |
| **Left Alt** (hold) | Power attack; sprinting attacks are also power attacks |
| **V** (hold) | Block with your weapon (shields block with right-click as usual) |
| **I** | Wait (beds also open the sleep menu, at any time of day) |
| **N** | Skyrim map: fast travel to discovered locations and party members |
| **M** | Xaero's terrain world map |
| **U** | Party |
| **Y** | Reputation and faction regalia |
| Sneak + use on an NPC | Pickpocket |
| Sneak + use on a corpse | Drag the body |
| Use on an NPC | Talk: trade, train, work, factions, bounty |

## How Skyrim maps onto Minecraft

See [docs/FEATURES.md](docs/FEATURES.md) for the full feature guide.

## Developing

See [docs/DEVELOPING.md](docs/DEVELOPING.md). `cd mod && ./gradlew build` builds the mod; CI builds everything on push.
