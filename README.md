# Open Persistence

An open-source reimplementation of *Persistent Players*, rebuilt for modern Minecraft with bug fixes and mod compatibility.

When a player logs out of a multiplayer server, Open Persistence leaves behind a **persistent body** — an entity that looks like the player and holds all of their gear — standing where they disconnected. When they log back in, their state is transferred back and the body is removed. While they are away, the body can be killed by other players or mobs, dropping the player's inventory (and updating their offline save data so the loss sticks).

This makes logging out a meaningful decision on survival/PvP servers: your character stays in the world and remains vulnerable until you return.

## Features

- **Persistent logout bodies** — a player-shaped entity spawns on logout, wearing and holding the player's full inventory and equipment.
- **State round-trip on login** — inventory, armor, and accessory slots are restored when the player rejoins; the body is then despawned.
- **Killable bodies** — the body has health and can be slain; doing so drops the player's items and writes the change back to their offline player data.
- **Despawn-proof** — the body will **not** vanish on peaceful difficulty or when `doMobSpawning` / mob-spawning gamerules are disabled. It ignores despawn distance and persistence culling.
- **Accessory support** — extra equipment slots are preserved across logout/login (see [Compatibility](#compatibility)).
- **Configurable** — see [Configuration](#configuration).

## Supported versions

Open Persistence is built as a single multiversion project (using the [Prism](https://github.com/Leclowndu93150/Prism) build system) and ships a separate jar per Minecraft version and mod loader.

| Minecraft | Loaders | Loader versions | Accessory compat |
|-----------|---------|-----------------|------------------|
| **1.12.2** | Forge | Forge `14.23.5.2847` | ModularWarfare (Shining fork) |
| **1.20.1** | Forge · Fabric | Forge `47.4.18` · Fabric loader `0.18.6` (API `0.92.7+1.20.1`) | Curios (Forge) |
| **1.21.1** | NeoForge · Fabric | NeoForge `21.1.222` · Fabric loader `0.18.6` (API `0.116.9+1.21.1`) | Curios (NeoForge) |
| **26.1** | NeoForge · Fabric | NeoForge `26.1.1.0-beta` · Fabric loader `0.18.6` (API `0.145.2+26.1.1`) | Curios (NeoForge) |

Download the jar that matches **both** your Minecraft version and your loader, e.g. `openpersistence-1.21.1-NeoForge-1.0.0.jar` for NeoForge on 1.21.1.

## Compatibility

Accessory/extra-slot mods are integrated as **optional soft dependencies** — Open Persistence works fine without them, and detects them at runtime when present.

- **Curios** (1.20.1+ on Forge/NeoForge) — items in Curios slots are copied onto the body and restored on login. Curios is a Forge/NeoForge-only mod; on **Fabric** there is no Curios integration and accessory handling is a no-op.
- **ModularWarfare** (1.12.2, Shining fork) — the original mod's extra-slot compatibility is preserved on the legacy build.

## Configuration

| Option | Default | Description |
|--------|---------|-------------|
| `persistCreativePlayers` | `true` | Whether players in creative mode also leave a persistent body behind. |
| `offlinePlayersSleep` | `false` | Whether offline bodies lie down (sleeping pose, smaller hitbox). |
| `debug` | `false` | Verbose logging for troubleshooting. |

Each loader exposes these through its native config system — a Forge/NeoForge config spec on those platforms, and a small JSON config file on Fabric.

## Building from source

The build requires a **JDK 25** to run the Gradle daemon (the Prism plugin needs Java 21+, and the bundled legacy-Forge tooling needs 25). Per-version toolchains (e.g. Java 8 for 1.12.2) are provisioned automatically.

```bash
# Build everything (all versions and loaders):
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew build

# Build a single target:
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew :1.21.1:neoforge:build

# Run a dev client:
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew :1.21.1:neoforge:runClient
```

Output jars land in `versions/<mc>/<loader>/build/libs/` (or `versions/1.12.2/build/libs/` for the single-loader legacy build).

## Project structure

```
versions/
  1.12.2/            single-loader legacy Forge build (sources directly under src/)
  1.20.1/  ┐
  1.21.1/  ├─ common/   shared logic compiled into each loader
  26.1/    ┘ fabric/ forge/ neoforge/   loader-specific entry points
```

Shared code lives in each version's `common/` module and is compiled directly into every loader. Platform-specific behavior (mod detection, accessory handling) is resolved through Java's `ServiceLoader`, so there is no hard dependency on any one loader's APIs.

## License

Licensed under the **GPL-3.0**.

## Authors

MrNorwood · dudumalah · fyrstikkeske
