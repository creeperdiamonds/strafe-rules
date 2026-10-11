# StrafeRules

A small Spigot/Paper plugin that turns off [Strafe Client](https://strafeclient.com) modules you don't allow on your
server. One jar runs on every server from **1.8 to 26.x**.

Players on Strafe see a blocked module greyed out as **BLOCKED** and can't switch it back on while they're on your
server. Everything comes back when they leave. Players without Strafe never get anything from this plugin.

Download the jar from [strafeclient.com/modules](https://strafeclient.com/modules), which also lists every module ID.

## Setup

1. Put `StrafeRules-<version>.jar` in `plugins/` and restart.
2. Edit `plugins/StrafeRules/config.yml`:

   ```yaml
   # turned off for everyone
   blocked-modules: [perspective, zoom]

   # extra modules turned off in specific worlds
   worlds:
     pvp_arena: [cps, keystrokes]
   ```
3. `/straferules reload`. `/straferules list` shows what's blocked where.

## Permissions

| Permission | Default | What it does |
|---|---|---|
| `straferules.admin` | op | `/straferules reload` and `list` |
| `straferules.bypass` | nobody | keeps every module on for this player |
| `straferules.bypass.<id>` | nobody | keeps one module on, e.g. `straferules.bypass.zoom` |

Bypass permissions aren't given to ops by default.

## How it works

When a Strafe player joins, the client registers the `strafe:rules` plugin channel. Bukkit only sends to channels a
client has registered, so the plugin answers just those players, and again when they change world. The message is:

```
VarInt protocol (1) | bool trusted (always false) | VarInt count | count x String (VarInt length + UTF-8 module ID)
```

Servers can only add blocks. Rules built into the client (such as Perspective being off on Hypixel) can't be lifted
by a server. If you run the Basalt anticheat, use its client integration *or* StrafeRules, not both.

## Building

Java 8+ and Maven:

```
mvn clean package
```

The jar is in `target/`. It's compiled against the Spigot 1.8.8 API with Java 8 bytecode, so the same jar works on
old and new servers.

## License

Copyright (C) 2026 Creeperdiamonds Studios.

StrafeRules is free software under the [GNU Affero General Public License v3.0 or later](LICENSE). You may use,
modify and share it. If you distribute a modified version, or run one for others, you must make its source available
under the same license.

This license covers the StrafeRules plugin only. Strafe Client itself is not open source.
