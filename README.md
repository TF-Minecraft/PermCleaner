# PermCleaner

> Seasonal permission resets for TF-Minecraft.

PermCleaner clears selected permissions assigned directly to players in LuckPerms when a new season begins. It keeps permissions that match the server's preservation rules, while retaining group membership and other non-permission data.

Players are processed as they join or enter the relevant world. A saved season record prevents the same player from being automatically cleaned again during that season.

## Features

- **Season-based cleanup** — returning players receive the permission reset for the current season.
- **Preserved permissions** — exact names and permission families can be kept through the reset.
- **Focused changes** — removes direct permission nodes while preserving groups, prefixes, suffixes, and other LuckPerms node types.
- **World-aware processing** — automatic cleanup can be limited to players entering a particular world.
- **Staff inspection** — preview which nodes would be removed or kept before applying an individual cleanup.
- **Persistent season tracking** — completed cleanup records survive server restarts.

The plugin supports seasonal progression resets while keeping the permissions and account metadata that should carry forward.

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/PermCleaner/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).
