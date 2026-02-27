# SXY Baritone Extras

A client-side [Fabric](https://fabricmc.net/) mod that extends [Baritone](https://github.com/cabaletta/baritone) with survival automation features.

**Minecraft 1.21.11 | Fabric Loader 0.18.1+ | Java 21+**

## Features

| Feature | Command | Description |
|---|---|---|
| **Torch Placer** | `#torchplacer` | Auto-places torches while pathfinding through dark areas. Adjusts spacing based on corridor width. Supports floor and wall placement. |
| **Auto Eater** | `#autoeater` | Eats food automatically when hunger drops below a threshold. Selects best food by saturation, nutrition, or availability. |
| **Mob Avoidance** | `#mobavoid` | Detects hostile mobs and responds — flees from creepers, seeks cover from skeletons, engages weaker mobs in combat. |
| **Room Lighter** | `#lightroom` | Scans the room around you, plans optimal torch positions, and places them. Supports dry-run scanning. |

Every feature can be toggled with its base command (e.g. `#torchplacer`) and configured with subcommands. Run `#<command> status` to see current settings.

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

[LGPL-3.0](https://www.gnu.org/licenses/lgpl-3.0.html)
