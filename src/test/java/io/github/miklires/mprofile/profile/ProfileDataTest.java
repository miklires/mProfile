package io.github.miklires.mprofile.profile;

import io.github.miklires.mprofile.api.ProfileVisibility;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProfileDataTest {
    @Test void preferenceChangeKeepsCollectedStatistics() {
        ProfileData original = new ProfileData(UUID.randomUUID(), "Player", "", ProfileVisibility.PUBLIC,
                "BLUE", Instant.EPOCH, Instant.EPOCH, 400, 12, 3);
        ProfileData changed = original.preferences("Hello", ProfileVisibility.LIMITED, "GREEN");
        assertEquals(400, changed.playtimeTicks());
        assertEquals(12, changed.playerKills());
        assertEquals("Hello", changed.biography());
        assertEquals(ProfileVisibility.LIMITED, changed.visibility());
    }

    @Test void extendedPreferencesKeepStatisticsAndAppearance() {
        ProfileData original = new ProfileData(UUID.randomUUID(), "Player", "", ProfileVisibility.PUBLIC,
                "BLUE", Instant.EPOCH, Instant.EPOCH, 400, 12, 3);
        ProfileData changed = original.preferences("Hello", ProfileVisibility.LIMITED, "GREEN", "FOREST",
                false, false, true, false);
        assertEquals(400, changed.playtimeTicks());
        assertEquals("FOREST", changed.cover());
        assertFalse(changed.showStatistics());
        assertFalse(changed.showActivity());
        assertFalse(changed.allowMessages());
    }
}
