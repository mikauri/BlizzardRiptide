# BlizzardRiptide

A Paper/Spigot plugin that lets **Riptide** tridents launch you during **snowstorms**,
not just in water or rain.

[Download on Modrinth](https://modrinth.com/plugin/blizzardriptide)

## The problem it solves

In vanilla, Riptide only works when you're in water or standing in rain. Snow doesn't
count, even though it's the same storm. In cold biomes, mountains and snowy
taigas, your Riptide trident is dead weight all winter.

BlizzardRiptide treats falling snow like rain: if it's snowing where you're standing
and you have a clear view of the sky, right-click your trident and you launch.

## How it works

- The world has to be storming, and the biome at your spot has to be cold enough
  for snow (vanilla's own snow threshold by default).
- You need open sky overhead (configurable), so it won't fire indoors or under trees.
- Launch power matches vanilla Riptide I, II and III, with the matching sounds.
- A short wind-up before the launch keeps it feeling like a charged throw.
- In water or rain, vanilla handles it as usual. The plugin only fills the snow gap.

## Commands

| Command | Description |
|---|---|
| `/blizzardriptide` | Show help |
| `/blizzardriptide status` | Show settings, and whether it's snowing where you stand |
| `/blizzardriptide reload` | *(op)* Reload the config |
| `/blizzardriptide toggle` | *(op)* Turn the plugin on or off |

Aliases: `/br`, `/snowriptide`, `/snowrip`.

## Permissions

| Node | Default | Grants |
|---|---|---|
| `blizzardriptide.admin` | op | `reload` and `toggle` |

Everyone can use the snow launch itself.

## Configuration

```yaml
enabled: true
charge_ticks: 10                  # wind-up before launch (20 = 1s, 0 = instant)
velocity_multiplier: 1.0          # 1.0 = vanilla Riptide power
snow_temperature_threshold: 0.15  # biome temperature below which it counts as snow
require_sky_access: true          # must have open sky overhead
cooldown_ticks: 10                # per-player cooldown (0 = off)
play_sound: true
show_particles: true
worlds_whitelist: []              # only these worlds; empty = all
```

## Building

Requires JDK 21+. The Gradle wrapper is included, so no local Gradle install is needed:

```
./gradlew build
```

The jar lands in `build/libs/`. Built against the Spigot 26.3 API; runs on 26.2 and 26.3.

## Reporting bugs

Open an issue on this repository using the bug report form.

## License

MIT, see [LICENSE](LICENSE).
