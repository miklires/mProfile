package io.github.miklires.mprofile.profile;

import io.github.miklires.mprofile.api.ProfileVisibility;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisibilityPolicyTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID viewer = UUID.randomUUID();

    @Test void privateProfileAllowsOwnerAndStaffOnly() {
        assertTrue(VisibilityPolicy.canOpen(ProfileVisibility.PRIVATE, owner, owner, false));
        assertTrue(VisibilityPolicy.canOpen(ProfileVisibility.PRIVATE, owner, viewer, true));
        assertFalse(VisibilityPolicy.canOpen(ProfileVisibility.PRIVATE, owner, viewer, false));
    }

    @Test void limitedProfileOpensButHidesStatistics() {
        assertTrue(VisibilityPolicy.canOpen(ProfileVisibility.LIMITED, owner, viewer, false));
        assertFalse(VisibilityPolicy.canViewStatistics(ProfileVisibility.LIMITED, owner, viewer, false));
        assertTrue(VisibilityPolicy.canViewStatistics(ProfileVisibility.LIMITED, owner, owner, false));
    }
}
