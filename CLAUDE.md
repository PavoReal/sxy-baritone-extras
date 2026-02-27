# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SXY Baritone Extras is a client-side Fabric mod addon for Baritone (Minecraft pathfinding AI) that adds survival automation features: automatic torch placement, auto eating, mob avoidance, and room lighting.

- **Minecraft:** 1.21.11
- **Fabric Loader:** 0.18.1
- **Java:** 21
- **Gradle:** 9.3.1 (wrapper included)
- **Baritone API:** 1.15.0 (local JAR in `deps/`)

## Build Commands

```bash
./gradlew build          # Build the mod (output: dist/sxy-baritone-extras-<version>.jar)
./gradlew clean          # Clean build artifacts
./gradlew runClient      # Launch Minecraft with the mod loaded (Fabric Loom dev environment)
```

There are no tests or linting configured.

## Architecture

Multi-feature Fabric mod with optional Mod Menu/YACL integration. All source code is under `src/main/java/sxy/baritoneextras/`.

### Entry Points (declared in `fabric.mod.json`)

- **`BaritoneExtras`** — `ClientModInitializer` entry point. Loads configs and registers all processes/commands with Baritone.
- **`ModMenuIntegration`** — Optional `ModMenuApi` entry point. Returns the YACL config screen if YACL is loaded, otherwise null.

### Shared

- **`GeneralConfig`** — `config/sxy-baritone-extras-general.properties`. Currently just `debugEnabled` (default `false`).
- **`TorchPlacerConfigScreen`** — YACL3-based GUI config screen (torch placer settings).

### Module: `torchplacer/`

- **`TorchPlacerProcess`** — `IBaritoneProcess` (priority 4). State machine (IDLE → AIMING → PLACING) that monitors light levels during pathfinding and auto-places torches. Handles corridor width analysis, placement side logic, inventory management. Suppresses itself while Room Lighter is active.
- **`TorchPlacerCommand`** — `#torchplacer` with subcommands: toggle, `side`, `spacing`, `threshold`, `margin`, `status`.
- **`TorchPlacerConfig`** — Properties-file persistence to `config/sxy-baritone-extras.properties`. Manages: enabled, lightLevelThreshold, placementSide, minSpacing, safetyMargin.
- **`TorchPlacementSide`** — Enum: FLOOR, LEFT, RIGHT.

### Module: `autoeater/`

- **`AutoEaterProcess`** — `IBaritoneProcess` (priority 5). State machine (IDLE → SELECTING → EATING → FINISHING) that eats food when hunger drops below threshold. Pauses pathing on vertical movements. Skips chorus fruit; golden apples are configurable.
- **`AutoEaterCommand`** — `#autoeater` with subcommands: toggle, `threshold`, `priority`, `goldenapples`, `walking`, `status`.
- **`AutoEaterConfig`** — Properties in shared config file. Manages: enabled, hungerThreshold, foodPriority (SATURATION/NUTRITION/ANY), allowGoldenApples, eatWhileWalking.

### Module: `mobavoidance/`

- **`MobAvoidanceProcess`** — `IBaritoneProcess` (priority 5). State machine (IDLE → ASSESSING → FLEEING/ENGAGING/SEEKING_COVER). Scans for hostiles, computes threat scores, and responds per mob type (flee creepers, seek cover from skeletons, engage zombies, etc.).
- **`MobAvoidanceCommand`** — `#mobavoid` with subcommands: toggle, `radius`, `safe`, `health`, `combat`, `maxmobs`, `status`.
- **`MobAvoidanceConfig`** — Separate file `config/sxy-baritone-extras-mobavoidance.properties`. Manages: enabled (default false), scanRadius, safeDistance, retreatHealthThreshold, engageMaxMobs, engageEnabled, per-mob-type toggles.
- **`ThreatType`** — Enum mapping mob types to avoidance radii, base threat scores, and default responses.

### Module: `roomlighter/`

- **`RoomLighterProcess`** — `IBaritoneProcess` (priority 2, permanent). State machine (IDLE → SCANNING → PLANNING → PATHING → APPROACHING → AIMING → PLACING → DONE). Orchestrates room scan, torch planning, and placement loop. Re-plans up to 3 passes.
- **`RoomScanner`** — BFS flood-fill to discover air/floor blocks within radius.
- **`TorchPlanner`** — Greedy set-cover algorithm to compute optimal torch positions.
- **`RoomLighterCommand`** — `#lightroom` with subcommands: `start`, `stop`, `scan` (dry run), `threshold`, `radius`, `status`.
- **`RoomLighterConfig`** — Properties in shared config file. Manages: lightLevelThreshold, maxRadius, maxVolume.

### Dependencies

Baritone API is a local JAR in `deps/` (not from Maven). Mod Menu and YACL are `modCompileOnly` — optional at runtime with graceful degradation. Mappings use official Mojang + Parchment layered mappings.

### Adding New Features

Follow the existing module pattern: create a sub-package with an `IBaritoneProcess` implementation, a Baritone command, and a config class. Register them in `BaritoneExtras.onInitializeClient()`.
