# JadePaperBridge

A Paper plugin that implements the server side of the [Jade](https://github.com/Snownee/Jade) mod's network protocol.

Jade is a client mod that shows a tooltip for the block or entity you are looking at. Some of its information only exists on the server: furnace progress, chest contents, villager restocks, absorption hearts, pet owners and so on. Jade normally gets this data from a server that also runs Jade. That doesn't work on a Paper server, so Jade marks these options with
*"This feature requires the server to have Jade installed"*.

JadePaperBridge answers Jade's requests from a Paper server. Jade clients connect as usual and get the same server data they would get from a modded server. Players don't need to install anything extra, and players without Jade aren't affected.

> **Unofficial.** This project is not affiliated with or endorsed by Snownee or the Jade project. Please don't report problems with this plugin to the Jade issue tracker.

## Requirements

| | |
|---|---|
| Server | [Paper](https://papermc.io) **26.3** (or a Paper fork such as Purpur) |
| Java | 25 |
| Client | Jade for Fabric **26.3.x** (network protocol version `9`) |

Spigot/CraftBukkit is **not** supported. The plugin reads Minecraft internals directly (furnace timers, villager restock state, …), and only Paper exposes them with Mojang names. Folia is untested.

## Installation

1. Download the latest `JadePaperBridge-<version>.jar` from [Releases](https://github.com/MaxXLive/jade-paper-bridge/releases), or [build it yourself](#building). Pick the release whose version starts with your server's Minecraft version.
2. Put it into your server's `plugins/` folder.
3. Restart the server.

The startup log should contain:

```
[JadePaperBridge] Serving 15 block and 15 entity data providers to Jade clients (protocol 9)
```

Join with Jade and run `/jpb status`. Your name should be listed, and the asterisks next to the server features in Jade's settings should be gone.

## Supported features

All server features of Jade's built-in vanilla plugins:

### Blocks

| Jade option | What the client shows |
|---|---|
| `minecraft:furnace` | Input, fuel, output and cooking progress (furnace, smoker, blast furnace) |
| `minecraft:brewing_stand` | Remaining fuel and brewing time |
| `minecraft:beehive` | Number of bees inside |
| `minecraft:campfire` | Items on the campfire and their remaining cooking time |
| `minecraft:command_block` | The command (operators only) |
| `minecraft:hopper_lock` | Whether a hopper is locked by redstone |
| `minecraft:jukebox` | The disc that is playing |
| `minecraft:lectern` | The book on the lectern |
| `minecraft:redstone` | Comparator output, calibrated sculk sensor input |
| `minecraft:shelf` | The book / item in the looked-at slot (chiseled bookshelf, shelf) |
| `minecraft:mob_spawner.cooldown` | Trial spawner cooldown |
| `minecraft:item_storage` | Container contents: chests (including double chests), barrels, shulker boxes, hoppers, dispensers, ender chest (your own), composter, … plus the "loot not generated yet" and "locked" hints |
| `minecraft:fluid_storage` | Cauldron contents (water, lava) |
| `jade:object_name` | Custom names of renamed block entities |

### Entities

| Jade option | What the client shows |
|---|---|
| `minecraft:entity_health` | Absorption hearts |
| `minecraft:potion_effects` | Active status effects |
| `minecraft:animal_owner` | Owner of tamed animals |
| `minecraft:mob_growth` | Time until a baby grows up (including tadpoles) |
| `minecraft:mob_breeding` | Breeding cooldown, "in love", allay duplication cooldown |
| `minecraft:villager_restock` | Restocks today and time until the next restock |
| `minecraft:next_entity_drop` | Next egg (chicken), scute (armadillo), sniff (sniffer) |
| `minecraft:zombie_villager` | Remaining curing time |
| `minecraft:pet_armor` | Body armor of pets |
| `minecraft:waxed` | Whether a copper golem is waxed |
| `minecraft:item_storage` | Inventories of chest minecarts, chest boats, donkeys, mules, llamas |

The harvest tool hint also works: the plugin sends the list of blocks that need shears, which it gets from the server's loot tables.

### Not supported

- **Status effect fade-in animation.** Jade records when an effect was added with a mixin, and a plugin can't do that. Effects still show up, just without the short fade-in.
- **Modded storages, energy and progress providers.** Paper has no mods, so there's nothing to show.
- **Jade's debug loot table option.**

## Configuration

`plugins/JadePaperBridge/config.yml`:

```yaml
# How many blocks beyond the player's interaction range a request may target.
# Same as Jade's game rule "jade:max_position_deviation" (default 21).
max-position-deviation: 21

# Provider ids that are not offered to clients.
disabled-providers: []
#  - minecraft:command_block
#  - minecraft:item_storage

# Jade plugin options forced on every client (shown as locked in Jade's settings).
# Same as Jade's config/jade/server-plugin-overrides.json.
config-overrides: []
#  - "minecraft:item_storage.normal_amount=9"
#  - "minecraft:animal_owner=false"

# Log handshakes and errors of individual providers.
debug: false
```

Run `/jpb reload` after editing. The new settings are sent to all connected Jade clients right away.

The ids in `disabled-providers` and `config-overrides` are the option ids Jade uses. You can see them in Jade's plugin settings when Jade's debug mode is enabled.

## Commands and permissions

| Command | Description |
|---|---|
| `/jadepaperbridge reload` | Reload `config.yml` and resync all Jade clients |
| `/jadepaperbridge status` | List online players that use Jade |
| `/jadepaperbridge overlay <player> <show\|hide>` | Turn a player's Jade overlay on or off (Jade's `show_overlay` packet) |

Alias: `/jpb`. All commands require `jadepaperbridge.admin` (default: op).

## How it works

Jade talks to the server over six custom payload channels:

| Direction | Channel | Content |
|---|---|---|
| client → server | `jade:client_handshake` | protocol version |
| server → client | `jade:server_handshake` | forced config values, shearable blocks, ids of the block and entity data providers |
| client → server | `jade:request_block` | hit result, "show details" flag, provider indices |
| client → server | `jade:request_entity` | entity id, part index, hit vector, provider indices |
| server → client | `jade:receive_data` | NBT compound with the provider data |
| server → client | `jade:show_overlay` | show/hide the overlay |

The plugin listens on the client channels with Bukkit's `Messenger` and decodes the payloads with Minecraft's own stream codecs (`RegistryFriendlyByteBuf`, `ItemStack.OPTIONAL_STREAM_CODEC`, …). The handshake lists only the providers the plugin implements. The client maps those ids to its own providers and skips any it doesn't know. The server half of each provider is ported from Jade, and the data uses the same NBT keys and stream codecs, so the Jade client can't tell the difference.

The same protections as in Jade apply:

- one request per player every 50 ms
- requests beyond the player's reach (plus `max-position-deviation`) get no data
- responses are limited to 16 KiB
- invisible entities without equipment are skipped
- command block contents are only sent to operators

Requests are only handled after a successful handshake. Malformed packets are ignored instead of kicking the player.

## Building

```sh
./gradlew build
```

The jar ends up in `build/libs/`. The build uses [paperweight-userdev](https://github.com/PaperMC/paperweight) and needs JDK 25. Gradle downloads the Paper dev bundle on the first run. Local builds are versioned `<minecraft>-dev`.

## Versioning and releases

Versions have the form **`<minecraft version>-patch-<n>`**, for example `26.3-patch-1`, `26.3-patch-2`, `26.4-patch-1`. The Minecraft version says which server the jar is for. The patch number counts the releases for that Minecraft version.

Releases are made by [GitHub Actions](.github/workflows/build.yml):

| Event | What happens |
|---|---|
| Push to `main` | Build, tag `v<version>`, GitHub release with the jar and a changelog |
| Pull request to `main` | Build only. The jar (`…-pr.<number>`) is attached to the workflow run as an artifact |
| Push that only changes `*.md` files | Nothing |

[`.github/scripts/release-info.sh`](.github/scripts/release-info.sh) picks the next version and writes the changelog:

- The Minecraft version comes from `minecraftVersion` in `gradle.properties`. When you change it, the patch number starts again at 1.
- The patch number continues from the highest `v<minecraft>-patch-<n>` tag.
- The changelog lists the commits since the previous release. Commits named in the [Conventional Commits](https://www.conventionalcommits.org) style are grouped: `feat:` under *Features*, `fix:`/`perf:` under *Fixes*. `docs:`, `ci:`, `test:` and `style:` commits are left out. All other commits go under *Changes*.

### Updating for a new Jade or Minecraft version

The protocol comes from Jade's `snownee.jade.network` package and the server halves of its providers (`IServerDataProvider` / `StreamServerDataProvider` in `snownee.jade.addon`). After updating `minecraftVersion` and `paperBuild` in `gradle.properties`, check:

- `Jade.PROTOCOL_VERSION`. It has to match `JadeNetworkHandler.PROTOCOL_VERSION`. If it doesn't, clients see Jade's "protocol mismatch" message.
- The packet codecs in `snownee.jade.network`.
- New or changed providers and their uids. They are registered in `JadePaperBridge#registerProviders`.

This version follows Jade `26.3.5` (commit [`3dc11bb`](https://github.com/Snownee/Jade/commit/3dc11bb55cfda18de24747adb5177097ed4081b5)).

## Credits and license

Jade is made by [Snownee](https://github.com/Snownee) and contributors and is licensed under [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/).
This plugin contains code adapted from Jade (the provider logic, item collector, view groups and the network format). It is distributed under the same license, **CC BY-NC-SA 4.0**. See [LICENSE.md](LICENSE.md).

In short: you may share and adapt this plugin, but not for commercial purposes. You must give credit and distribute your changes under the same license.
