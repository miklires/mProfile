# Changelog

## 1.3.0 - 2026-10-11

- Added full-width illustrated Forest, Nether, Ocean, End, and Space covers to native profile dialogs.
- Added a bundled Minecraft 26.3 resource pack using isolated `mprofile:covers` bitmap-font glyphs.
- Added optional automatic resource-pack delivery with URL, SHA-1, prompt, and required-pack controls.
- Kept text covers as a safe fallback until the exact pack reports a successful client load.
- Extracted the matching standalone resource-pack ZIP into the plugin data folder on first start.

## 1.2.0 - 2026-10-11

- Added native Paper 26.3 profile and settings dialogs with a configurable inventory fallback.
- Added curated profile covers and personal visibility controls for statistics, activity, integrations, and messages.
- Added optional mReputation endorsements with an evidence form; controls appear only when voting is enabled.
- Migrated existing H2 databases in place while keeping the public profile snapshot API binary-compatible.
- Kept online-player capture on the owning entity scheduler for Paper and Folia safety.
- Fixed the update checker treating an older Modrinth release as a newer version.

## 1.1.1 - 2026-10-10

- Added Minecraft 26.3 compatibility, including the updated checked-exception contract in Paper's command API, and verified the build and tests.

## 1.1.0

### Added

- Configurable GUI slots, materials, theme accents, and localized relative activity times.
- Shift-right-click profile opening with a bounded per-viewer cooldown.
- Optional message, report, and ignore buttons with safe command suggestion mode.
- Complete English and Russian GUI translations with English fallback keys.

### Fixed

- Prevented stale database reads and failed writes from overwriting newer cached profile state.
- Validated locale names, player lookups, themes, biography text, materials, slots, and update project IDs.
- Moved public API inventory opens onto the player's entity scheduler.
- Added bounded database shutdown and deterministic duplicate-name lookup.

## 1.0.0

### Added

- Offline-safe profile GUI with native activity and statistics.
- Biography, theme and three privacy modes.
- Versioned H2 storage and public Bukkit Services API.
- Optional mBadges, mReputation and mColor summaries.
- English and Russian messages.
