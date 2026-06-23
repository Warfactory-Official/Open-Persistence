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

### Grave & corpse mods (GraveStone / Corpse)

[GraveStone](https://www.curseforge.com/minecraft/mc-mods/gravestone-mod) and [Corpse](https://www.curseforge.com/minecraft/mc-mods/corpse) (both by henkelmax) save a player's items into a grave block / corpse entity **when a real player dies**. A persistent body is *not* a real player — it is a custom entity — so their player-death hooks never fire for it. Without compat a killed body just scatters its items on the floor, even on a server that has a grave mod installed.

Making **persistent players spawn graves/corpses** therefore needs **explicit compat**: Open Persistence deposits the items into the grave mod itself, on the offline player's behalf. To keep that out of the shared logic, body-death drops go through a `GraveHelper` service (the same `ServiceLoader` soft-dependency pattern as Curios). `onBodyDeath` collects everything the body should drop into one list and calls `Services.GRAVE.deposit(...)`; if no grave mod handles it, the items are scattered on the ground as before.

On Forge/NeoForge the helper detects whichever mod is installed and deposits via its API:

- **Corpse** — builds a corelib `Death` from the recovered items and spawns a `CorpseEntity` at the body's position.
- **GraveStone** — builds the same `Death`, finds a free spot with `GraveUtils`, places the grave block, and stores the `Death` on its block entity.

Both mods coexist with this (if both are installed, Corpse is preferred). Each support class only references its mod's classes, so a server with neither mod — or only one — never hits a missing-class error. GraveStone/Corpse each shade their own copy of `corelib`, so no separate `corelib` dependency is needed; Corpse is pulled from Modrinth and GraveStone (CurseForge-only) from CurseMaven, both as **compile-only** soft deps.

| | Implementation | Status |
|---|---|---|
| **NeoForge** (1.21.1, 26.1) | `NeoForgeGraveHelper` → `CorpseSupport` / `GravestoneSupport` | **Wired** — Corpse and GraveStone both supported. |
| **Forge** (1.20.1) | `ForgeGraveHelper` → `CorpseSupport` / `GravestoneSupport` | **Wired** — Corpse and GraveStone both supported. |
| **Fabric** (1.20.1, 1.21.1, 26.1) | `FabricGraveHelper` (no-op) | Neither mod ships for Fabric, so Fabric always scatters. |
| **1.12.2** (Forge) | `CompatManager.depositGrave(...)` | Seam only — collects items and routes through the hook, which currently scatters (the legacy `corelib` API differs; not yet wired). |

### Testing with two clients locally

The mod only acts on a **dedicated** server (it no-ops in single-player and on a world Opened to LAN, both of which report as single-player), so testing needs a server plus two separately-authenticated clients. **Every** loader target now defines a second client run, **`runClient2`** (logged in as `Player2`, with its own `run/client2` game directory), alongside the default `runClient` — so the two clients get different offline UUIDs. Use any target:

```bash
JAVA_HOME=/usr/lib/jvm/java-25 ./gradlew :1.21.1:neoforge:runServer    # 1) dedicated dev server
JAVA_HOME=/usr/lib/jvm/java-25 ./gradlew :1.21.1:neoforge:runClient     # 2) first client
JAVA_HOME=/usr/lib/jvm/java-25 ./gradlew :1.21.1:neoforge:runClient2    # 3) second client (Player2)
```

Direct-connect both clients to `localhost`, log out on one (a body spawns where it stood), then kill the body with the other.

On the **Forge/NeoForge** targets (1.20.1, 1.21.1, 26.1) a grave mod (Corpse) is also loaded into the dev runs via `modRuntimeOnly` — dev-only, **not** bundled into the shipped jar — so killing the body produces a corpse/grave holding that player's inventory. Swap the commented `modRuntimeOnly` line in `build.gradle.kts` to test GraveStone instead. On **Fabric** (no grave mod exists) and **1.12.2** (grave compat is seam-only) there is no grave mod in dev, so `runClient2` exercises the body mechanic itself (the items scatter).

**Launching from an IDE (IntelliJ/Eclipse):** ModDevGradle generates each run's launch argfiles during the IDE's Gradle **sync**, so after pulling these changes **re-sync Gradle once** before running `client2` from the IDE — otherwise the run config points at a `client2RunVmArgs.txt` that doesn't exist yet and the JVM reports `Could not find or load main class @…client2RunVmArgs.txt`. The `runClient2` Gradle task never has this problem (it regenerates the argfiles itself), so it always works from the terminal.

> Implementation note: the second client is wired through each loader's own run system (ModDevGradle's `runs` container for NeoForge/Forge, Loom's for Fabric, and a clone of RetroFuturaGradle's `runClient` task for 1.12.2) via Prism's `rawProject` escape hatch, because Prism's cross-loader `runs {}` DSL mis-handles these in the current version. On NeoForge/Forge the `client2` run is also registered with MDG's `neoForgeIdeSync` task (via `IdeIntegration.runTaskOnProjectSync`) so its argfiles regenerate on IDE sync like the built-in runs.

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
