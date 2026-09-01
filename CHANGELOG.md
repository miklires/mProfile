# Changelog

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
