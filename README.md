<div align="center">
  <h1>mProfile</h1>
  <p>Private-by-design player profiles and a configurable social interaction hub.</p>

  <p>
    <a href="https://papermc.io/software/paper"><img alt="Paper" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/paper_vector.svg"></a>
    <a href="https://purpurmc.org"><img alt="Purpur" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/purpur_vector.svg"></a>
    <a href="https://papermc.io/software/folia"><img alt="Folia" height="56" src="https://raw.githubusercontent.com/miklires/mCommand/main/docs/assets/folia-available.png"></a>
  </p>
  <p>
    <a href="https://github.com/miklires/mProfile"><img alt="GitHub" src="https://tr7zw.github.io/uikit/social_buttons_icon/Github-Button-64.png"></a>
    <a href="https://modrinth.com/plugin/mprofile"><img alt="Modrinth" src="https://tr7zw.github.io/uikit/social_buttons_icon/Modrinth-Button-64.png"></a>
    <a href="https://discord.gg/pes25cnWKy"><img alt="Discord" src="https://tr7zw.github.io/uikit/social_buttons_icon/Discord-Button-64.png"></a>
  </p>
  <p>
    <a href="https://github.com/miklires/mProfile/releases"><img alt="Release" src="https://img.shields.io/github/v/release/miklires/mProfile?style=for-the-badge"></a>
    <a href="https://modrinth.com/plugin/VIEc33gz"><img alt="Modrinth downloads" src="https://img.shields.io/modrinth/dt/VIEc33gz?style=for-the-badge&logo=modrinth"></a>
    <img alt="Java 25" src="https://img.shields.io/badge/Java-25-5382A1?style=for-the-badge">
  </p>
</div>

## Features

- Profile GUI for online players and everyone previously seen by the server.
- Biography, configurable color theme, and `PUBLIC`, `LIMITED`, or `PRIVATE` visibility.
- Playtime, player kills, deaths, first-seen, last-seen, and online status.
- Shift-right-click access with a short configurable anti-spam cooldown.
- Fully configurable GUI size, item slots, materials, and theme accent colors.
- Optional message, report, and ignore buttons. Suggest mode lets the player confirm or complete a command before it runs.
- Optional summaries from mBadges, mReputation, and mColor, without hard dependencies.
- English by default and a complete Russian translation selected in `config.yml`.
- Asynchronous local H2 storage and a public Bukkit Services API.
- Native Paper and Folia schedulers; player and inventory work stays on the owning entity thread.

## Requirements and installation

- Java 25
- Paper, Purpur, or Folia 26.2

1. Put `mProfile-1.1.0.jar` in the server's `plugins` directory.
2. Start the server once.
3. Edit `plugins/mProfile/config.yml` if desired.
4. Run `/profile reload`.

The generated defaults work without another plugin. Set `language.default: ru_RU` and reload to switch messages and the GUI to Russian. Missing translation keys fall back to bundled English automatically.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/profile` | `mprofile.use` | Open your profile |
| `/profile <player>` | `mprofile.use` | Open an online or stored profile |
| `/profile bio <text>` | `mprofile.edit` | Set a biography; omit text to clear it |
| `/profile visibility <PUBLIC\|LIMITED\|PRIVATE>` | `mprofile.edit` | Set profile privacy |
| `/profile theme <name>` | `mprofile.edit` | Select a configured theme |
| `/profile reload` | `mprofile.reload` | Reload configuration and language files |

`mprofile.view.private` lets trusted staff inspect private profiles. `mprofile.cooldown.bypass` bypasses the viewing cooldown. `mprofile.admin` grants the staff permissions.

## Privacy model

- `PUBLIC` allows other players to view the profile and its statistics.
- `LIMITED` allows the profile to open but hides detailed statistics.
- `PRIVATE` blocks other players entirely unless they have the explicit bypass permission.

The owner can always view their own profile. mProfile stores only the Minecraft UUID, last name, biography, privacy/theme preferences, and displayed gameplay statistics. It does not store IP addresses, chat, inventory contents, or location history.

## GUI and interactions

Slots in `config.yml` use human-friendly numbering from `1` through the configured inventory size. Invalid materials and slots fall back or are skipped safely; duplicate slots never create multiple actions.

```yaml
language:
  default: en_US

access:
  view-cooldown-millis: 750
  right-click:
    enabled: true
    require-sneaking: true

interactions:
  enabled: true
  actions:
    message:
      enabled: true
      slot: 47
      material: PAPER
      mode: SUGGEST
      command: "msg {player} "
      required-plugin: ""
```

`SUGGEST` sends a clickable prompt that prepares the command. `RUN` executes it immediately and should only be used for commands that already perform their own permission and confirmation checks. Actions with `required-plugin` are hidden while that plugin is unavailable.

## Storage and safety

Profile data is kept in `plugins/mProfile/profiles.mv.db`. Database access is serialized on a dedicated virtual thread and never blocks a server tick. Failed writes cannot roll back a newer cached update, corrupted privacy values fail closed, and shutdown waits at most five seconds before cancelling remaining storage tasks.

Configuration input is bounded: biographies fit the database schema, control characters are removed, player names and locale paths are validated, cooldowns are clamped, and custom action commands reject control characters. Dynamic player text is inserted without MiniMessage tag interpretation.

Back up the plugin directory before moving data between servers. Do not edit the database while the server is running.

## API

Other plugins can load `MProfileApi` from Bukkit's Services Manager. Profile reads return `CompletableFuture<Optional<ProfileSnapshot>>`; GUI opens return `CompletableFuture<Boolean>` and are automatically handed to the correct player scheduler. Compile against the lightweight API JAR produced in `api/build/libs`.

## Telemetry and updates

bStats is disabled by default until a public project ID is assigned. If enabled later, it contains no UUIDs, names, biographies, or profile statistics. The asynchronous Modrinth update check is separate and can be disabled with `updates.enabled: false`.

## Build

```bash
./gradlew clean build
```

The plugin JAR is written to `build/libs`; API artifacts are written to `api/build/libs`.

Licensed under the MIT License.
