# CommandAlias

A single plugin jar that lets you alias any command to any other command — on a Paper server, on a Velocity proxy, or both at once — without touching permissions at all. The alias always defers to whatever permission check the *target* command already performs, so there's nothing to keep in sync in LuckPerms when you add, rename, or remove an alias.

## Why "transparent permissions" matters

Most command-alias plugins make you assign a permission node to the alias itself, which then has to be kept in sync with whatever the real command's permission is. CommandAlias doesn't do this. When someone runs `/ah`, the plugin doesn't check any `commandalias.*` permission for it — it re-dispatches the command as `/ahouse` through the server's normal command handling, and `/ahouse` runs its own permission check exactly as if the player had typed it directly. If a rank can already use `/ahouse`, they can use `/ah`. If you later change who can use `/ahouse`, `/ah` follows automatically — there's no separate alias permission to remember to update.

The same logic runs on both sides of the plugin: `DynamicAliasCommand`/`MultiSubAliasCommand` on Paper, and `VelocityAliasCommand` on Velocity, both just forward the invocation and let the target command decide.

## One jar, two plugins

The jar bundles both a `plugin.yml` (Paper) and a `velocity-plugin.json` (Velocity). Paper loads `CommandAliasPlugin` and ignores the Velocity descriptor; Velocity loads `CommandAliasVelocityPlugin` and ignores `plugin.yml`. Drop the exact same jar into `plugins/` on your proxy **and** on every backend server that needs aliases — each side only sees its own half, and they're configured completely independently (see below).

## Installation

1. Grab the jar from [Releases](../../releases).
2. Copy it into `plugins/` on the Velocity proxy, and into `plugins/` on every backend Paper server that needs aliases.
3. Start each server once to generate its default config, then edit and reload/restart as described below.

## Configuring aliases (Paper side — `config.yml`)

Paper-side aliases live in `config.yml` under an `aliases:` section, and support three shapes:

**Simple alias** — `/ah` runs `/ahouse`, and anything typed after the alias is appended (`/ah sell` → `/ahouse sell`):
```yaml
aliases:
  ah: ahouse
```

**Fixed-subcommand alias** — pins extra arguments onto the target, so `/lphelp` runs `/lp help`, and `/lphelp user` runs `/lp help user`:
```yaml
aliases:
  lphelp: lp help
```

**Multi-word alias** — quote the key; the first word becomes the registered command, and everything sharing that first word is grouped under it automatically:
```yaml
aliases:
  "warp pvpblue": tp 12 12 12
  "warp pvpred": tp 22 22 22
  "warp spawn": tp 0 0 0
  "warp audience left": tp 2 2 2
  "warp audience right": tp 4 4 4
```
This registers a single `/warp` command that routes to the right target based on the sub-word(s) typed. `/warp pvpblue` runs `/tp 12 12 12`; extra arguments after a match are appended to the target; and `/warp <tab>` correctly suggests `pvpblue`, `pvpred`, `spawn`, and `audience`, walking further into `audience left`/`audience right` as you keep typing.

Apply changes with `/commandalias reload` — no server restart needed.

## Configuring aliases (Velocity side — `aliases.properties`)

The proxy has its own separate config, `aliases.properties`, in the plugin's data folder — it only knows about proxy commands (`server`, `send`, `glist`, etc.), not backend commands like `/ahouse` or `/lp`:
```properties
s=server
#hub=server lobby
```
There's currently no `/commandalias reload` equivalent on the proxy — restart the proxy after editing `aliases.properties`.

## Commands & permissions

| Command | What it does | Permission | Default |
| --- | --- | --- | --- |
| `/commandalias` or `/commandalias help` | Lists every currently active alias (both simple and multi-word), and flags any whose target command isn't currently loaded | `commandalias.use` | true |
| `/commandalias reload` | Unregisters all current aliases, reloads `config.yml`, and re-registers them | `commandalias.admin` | op |

These two permissions only gate the `/commandalias` management command itself — they have nothing to do with whether a given alias works for a given player. As above, each alias's usability is entirely controlled by the permission on its target command.

## Things worth knowing

- **Load order matters.** If an alias's target command belongs to a plugin that loads *after* CommandAlias, the alias will still register, but `/commandalias help` will flag it as "target not currently loaded" until that plugin starts. Once the target plugin registers its command, the alias resolves normally — no reload needed, since resolution happens at execution time, not registration time.
- **Name collisions are refused, not overwritten.** If an alias name already matches an existing command from another plugin, CommandAlias logs a warning and skips registering that one alias rather than shadowing it.
- **Already-connected players get their command list refreshed** automatically after a reload, so a newly added alias shows up in tab-complete without needing to relog.
- **The Paper side uses reflection** to pull the server's internal `CommandMap`, since Bukkit/Paper doesn't expose a public API for registering commands at runtime. This is a long-standing, stable trick, but if a future Paper version renames that internal field, CommandAlias will log a severe error on startup and aliasing will be disabled until it's updated — it won't silently do nothing.
- Simple and multi-word aliases can't share the same first word — if you already have a simple alias called `warp`, any `"warp ..."` multi-word entries will be skipped with a warning telling you to rename one of them.

## License

Licensed under the [Apache License 2.0](./LICENSE).