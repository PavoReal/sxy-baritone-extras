# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SXY Baritone Extras is a client-side Fabric mod addon for Baritone (Minecraft pathfinding AI) that adds automatic torch placement during pathfinding. Licensed under LGPL-3.0.

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

This is a single-feature Fabric mod with optional Mod Menu/YACL integration. All source code is under `src/main/java/sxy/baritoneextras/`.

### Entry Points (declared in `fabric.mod.json`)

- **`BaritoneExtras`** — `ClientModInitializer` entry point. Loads config, registers the `TorchPlacerProcess` with Baritone's pathing control manager, and registers `TorchPlacerCommand` with Baritone's command registry.
- **`ModMenuIntegration`** — Optional `ModMenuApi` entry point. Returns the YACL config screen if YACL is loaded, otherwise null.

### Core Module: `torchplacer/`

- **`TorchPlacerProcess`** — Implements `IBaritoneProcess`. State machine (IDLE → AIMING → PLACING) that monitors light levels during Baritone pathfinding and automatically places torches. Handles corridor width analysis, placement side logic, inventory management, and Baritone rotation control. This is the bulk of the logic (~460 lines).
- **`TorchPlacerCommand`** — Baritone command (`#torchplacer`) with subcommands: toggle, `side`, `spacing`, `threshold`, `margin`, `status`. Includes tab completion.
- **`TorchPlacerConfig`** — Properties-file persistence to `config/sxy-baritone-extras.properties`. Manages: enabled, lightLevelThreshold, placementSide, minSpacing, safetyMargin.
- **`TorchPlacerConfigScreen`** — YACL3-based GUI config screen.
- **`TorchPlacementSide`** — Enum: FLOOR, LEFT, RIGHT.

### Dependencies

Baritone API is a local JAR in `deps/` (not from Maven). Mod Menu and YACL are `modCompileOnly` — optional at runtime with graceful degradation. Mappings use official Mojang + Parchment layered mappings.

### Adding New Features

Follow the `torchplacer/` pattern: create a sub-package with a `IBaritoneProcess` implementation, a Baritone command, and a config class. Register them in `BaritoneExtras.onInitializeClient()`.
