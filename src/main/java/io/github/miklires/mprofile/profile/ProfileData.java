package io.github.miklires.mprofile.profile;

import io.github.miklires.mprofile.api.ProfileSnapshot;
import io.github.miklires.mprofile.api.ProfileVisibility;

import java.time.Instant;
import java.util.UUID;

public record ProfileData(
        UUID playerId,
        String lastName,
        String biography,
        ProfileVisibility visibility,
        String theme,
        Instant firstSeen,
        Instant lastSeen,
        long playtimeTicks,
        int playerKills,
        int deaths,
        String cover,
        boolean showStatistics,
        boolean showActivity,
        boolean showIntegrations,
        boolean allowMessages
) {
    public ProfileData(UUID playerId, String lastName, String biography, ProfileVisibility visibility, String theme,
                       Instant firstSeen, Instant lastSeen, long playtimeTicks, int playerKills, int deaths) {
        this(playerId, lastName, biography, visibility, theme, firstSeen, lastSeen, playtimeTicks, playerKills,
                deaths, "NONE", true, true, true, true);
    }

    public ProfileSnapshot snapshot() {
        return new ProfileSnapshot(playerId, lastName, biography, visibility, theme, firstSeen, lastSeen,
                playtimeTicks, playerKills, deaths);
    }

    public ProfileData preferences(String biography, ProfileVisibility visibility, String theme) {
        return new ProfileData(playerId, lastName, biography, visibility, theme, firstSeen, lastSeen,
                playtimeTicks, playerKills, deaths, cover, showStatistics, showActivity, showIntegrations, allowMessages);
    }

    public ProfileData preferences(String biography, ProfileVisibility visibility, String theme, String cover,
                                   boolean showStatistics, boolean showActivity, boolean showIntegrations,
                                   boolean allowMessages) {
        return new ProfileData(playerId, lastName, biography, visibility, theme, firstSeen, lastSeen,
                playtimeTicks, playerKills, deaths, cover, showStatistics, showActivity, showIntegrations, allowMessages);
    }
}
