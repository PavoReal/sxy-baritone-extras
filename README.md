# SXY Baritone Extras

A client-side [Fabric](https://fabricmc.net/) mod that extends [Baritone](https://github.com/cabaletta/baritone) with survival automation features.

**Minecraft 1.21.11 | Fabric Loader 0.18.1+ | Java 21+**

## Features

### Torch Placer — `#torchplacer`

Auto-places torches while Baritone is pathfinding through dark areas. Dynamically adjusts spacing based on corridor width. Supports floor and wall (left/right) placement.

| Subcommand | Description |
|---|---|
| `#torchplacer` | Toggle on/off |
| `#torchplacer side <floor\|left\|right>` | Placement surface relative to movement direction |
| `#torchplacer spacing <n>` | Minimum blocks between torches |
| `#torchplacer threshold <n>` | Light level below which torches are placed |
| `#torchplacer margin <n>` | Safety margin subtracted from computed spacing |
| `#torchplacer status` | Show current settings |

### Auto Eater — `#autoeater`

Eats food automatically when hunger drops below a threshold. Picks the best food by saturation, nutrition, or first available. Pauses pathing on tricky terrain.

| Subcommand | Description |
|---|---|
| `#autoeater` | Toggle on/off |
| `#autoeater threshold <1-20>` | Hunger level that triggers eating |
| `#autoeater priority <saturation\|nutrition\|any>` | Food selection strategy |
| `#autoeater goldenapples` | Toggle golden apple usage |
| `#autoeater walking` | Toggle eating while walking |
| `#autoeater status` | Show current settings |

### Mob Avoidance — `#mobavoid`

Detects hostile mobs and responds per threat type — flees from creepers/witches, seeks cover from skeletons/pillagers, engages zombies/spiders in melee. Disabled by default.

| Subcommand | Description |
|---|---|
| `#mobavoid` | Toggle on/off |
| `#mobavoid radius <n>` | Mob scan radius in blocks |
| `#mobavoid safe <n>` | Flee distance before resuming |
| `#mobavoid health <n>` | Health (half-hearts) to force retreat |
| `#mobavoid combat [on\|off]` | Toggle melee engagement |
| `#mobavoid maxmobs <n>` | Max mobs before fleeing instead of fighting |
| `#mobavoid status` | Show current settings |

### Room Lighter — `#lightroom`

Scans the room around you with BFS flood-fill, plans optimal torch positions using a greedy set-cover algorithm, then walks to each spot and places torches. Supports dry-run scanning.

| Subcommand | Description |
|---|---|
| `#lightroom` | Start lighting the room |
| `#lightroom stop` | Cancel and report progress |
| `#lightroom scan` | Dry run — report how many torches are needed |
| `#lightroom threshold <n>` | Light level below which spots need torches |
| `#lightroom radius <n>` | Maximum scan radius |
| `#lightroom status` | Show current state and settings |

> Every feature can be toggled with its base command and configured via subcommands. Run `#<command> status` to see current settings.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) (0.18.1+) for Minecraft 1.21.11
2. Install [Baritone](https://github.com/cabaletta/baritone) for Fabric 1.21.11
3. Download `sxy-baritone-extras-1.0.0.jar` from [Releases](../../releases)
4. Place the JAR in your `.minecraft/mods/` folder

### Optional

- [Mod Menu](https://modrinth.com/mod/modmenu) + [YACL](https://modrinth.com/mod/yacl) (v3) — Adds an in-game GUI config screen

Without these, all settings are configurable via chat commands or by editing the properties files in `.minecraft/config/`.

## Building from Source

```bash
git clone https://github.com/your-username/sxy-baritone-extras.git
cd sxy-baritone-extras
./gradlew build
```

Output: `dist/sxy-baritone-extras-1.0.0.jar`

## License

TBD
