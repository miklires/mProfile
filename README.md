<div align="center">
  <h1>mProfile</h1>
  <p>Player profiles and an interaction hub for modern Minecraft servers.</p>

  <p>
    <a href="https://papermc.io/software/paper"><img alt="Paper" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/paper_vector.svg"></a>
    <a href="https://purpurmc.org"><img alt="Purpur" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/purpur_vector.svg"></a>
    <a href="https://papermc.io/software/folia"><img alt="Folia" height="56" src="https://raw.githubusercontent.com/miklires/mCommand/main/docs/assets/folia-available.png"></a>
  </p>
</div>

## What it does

- Opens a profile GUI for online and previously seen players.
- Stores biographies, themes and `PUBLIC`, `LIMITED` or `PRIVATE` visibility.
- Shows playtime, kills, deaths and activity without blocking the server thread.
- Reads optional reputation, badge and color information from other mPlugins.
- Exposes profile reads and GUI opening through Bukkit Services.

## Requirements

- Java 25
- Paper, Purpur or Folia 26.2

mBadges, mReputation and mColor are optional. mProfile starts normally without them.

## Install

1. Put `mProfile-1.0.0.jar` in `plugins`.
2. Start the server once.
3. Edit `plugins/mProfile/config.yml` if needed.

Profile data uses a local versioned H2 database. Database work runs outside server tick threads.

## Commands

- `/profile [player]` — open a profile.
- `/profile bio <text>` — update your biography.
- `/profile visibility <PUBLIC|LIMITED|PRIVATE>` — change privacy.
- `/profile theme <name>` — select a configured theme.
- `/profile reload` — reload safe configuration and messages.

Permissions are `mprofile.use`, `mprofile.edit`, `mprofile.view.private`, `mprofile.reload` and `mprofile.admin`.

## Telemetry and updates

Anonymous bStats metrics can be disabled with `metrics.enabled: false` after a project ID is assigned. No UUIDs, names, biographies or statistics are collected. The asynchronous update check is separate and can be disabled with `updates.enabled: false`.

## Build

```bash
./gradlew clean build
```

The plugin JAR is written to `build/libs`; API artifacts are written to `api/build/libs`.

Licensed under the MIT License.
