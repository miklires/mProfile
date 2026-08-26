package io.github.miklires.mprofile.api;

import java.time.Instant;
import java.util.UUID;

public record ProfileSnapshot(
        UUID playerId,
        String lastName,
        String biography,
        ProfileVisibility visibility,
        String theme,
        Instant firstSeen,
        Instant lastSeen,
        long playtimeTicks,
        int playerKills,
        int deaths
) {}
