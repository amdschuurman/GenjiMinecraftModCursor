# Genji Mod

Overwatch's Genji as a Minecraft Forge mod. Faithful to late-OW1 / early-OW2 with pre-Season-9 dragonblade slash timing.

## Abilities

| Key            | Ability             |
|----------------|---------------------|
| `LMB` (held)   | Shuriken — 3-shot burst |
| `RMB` (held)   | Shuriken — fan of 3 |
| `Q`            | Deflect             |
| `LEFT_ALT`     | Swift Strike (dash) |
| `B`            | Nano-Boost (when 100%) |
| `V`            | Dragonblade (when 100%) |
| `Space` (mid-air) | Double-jump (one per airborne stretch) |
| `Space` (vs. wall, mid-air) | Wall-climb |

Headshots with shurikens deal 1.5× damage and play a distinct hit sound + CRIT particle burst.

## Install

1. Install **Minecraft 1.20.1** with **Forge 47.x** (or later in the 47 line).
2. Install **GeckoLib 4.7.x** (mandatory dependency — animations break without it). Get it from CurseForge / Modrinth.
3. Drop `genji-mod-X.X.X.jar` into your `mods/` folder.

Optional: **Better Combat** is supported as a soft dependency — the dragonblade gets a katana stance pose if Better Combat is installed, but the mod runs fine without it.

## Configuration

`config/genji-common.toml` is generated on first run. Notable knobs:

| Key                                | Default | Description                              |
|------------------------------------|---------|------------------------------------------|
| `Charge.UltimateDamageForFull`     | 50.0    | Raw HP of damage to fill the ult meter   |
| `Charge.NanoDamageForFull`         | 120.0   | Raw HP of damage to fill the nano meter  |
| `Deflect.DurationSeconds`          | 2       | Active duration                          |
| `Deflect.CooldownSeconds`          | 8       | Cooldown after deflect ends              |
| `Dash.CooldownSeconds`             | 8       | Dash cooldown                            |
| `Dragonblade.DurationSeconds`      | 6       | Active duration                          |
| `DragonbladeCombat.Reach`          | 5.0     | Effective slash range (blocks)           |
| `DragonbladeCombat.ComboWindowTicks` | 10    | LEFT → RIGHT chain window                |
| `NanoBoost.DurationSeconds`        | 9       | Nano-Boost duration                      |
| `NanoBoost.DamageMultiplier`       | 1.5     | Damage multiplier while nano is active   |

## Build from source

Requires JDK 17.

```bash
git clone https://github.com/<your-fork>/GenjiMinecraftModCursor
cd GenjiMinecraftModCursor
./gradlew build
```

The output JAR ends up in `build/libs/genji-mod-<version>.jar`. The first build downloads Minecraft + Forge mappings and decompiles MC sources — expect ~10 – 15 minutes once. Subsequent builds are seconds.

```bash
./gradlew runClient   # boot a dev client
./gradlew runServer   # boot a dev dedicated server
```

## Architecture

Server-authoritative. The server owns every cooldown, charge, hit registration, and FSM transition; the client is animations + HUD + input. State lives in a `GenjiData` capability attached to every player. See `CHANGELOG.md` for the per-version history.

## Acknowledgements

- **Blizzard / Overwatch** — for the source material.
- **MinecraftForge** — modding framework.
- **GeckoLib** — mandatory animation runtime.
- **Better Combat** — soft integration target.

## License

MIT. See `LICENSE` if present.
