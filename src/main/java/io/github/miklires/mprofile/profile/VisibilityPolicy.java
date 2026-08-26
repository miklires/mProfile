package io.github.miklires.mprofile.profile;

import io.github.miklires.mprofile.api.ProfileVisibility;

import java.util.UUID;

public final class VisibilityPolicy {
    private VisibilityPolicy() {}

    public static boolean canOpen(ProfileVisibility visibility, UUID owner, UUID viewer, boolean bypass) {
        return visibility != ProfileVisibility.PRIVATE || owner.equals(viewer) || bypass;
    }

    public static boolean canViewStatistics(ProfileVisibility visibility, UUID owner, UUID viewer, boolean bypass) {
        return visibility == ProfileVisibility.PUBLIC || owner.equals(viewer) || bypass;
    }
}
