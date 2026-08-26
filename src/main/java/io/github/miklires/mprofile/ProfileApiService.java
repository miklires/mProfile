package io.github.miklires.mprofile;

import io.github.miklires.mprofile.api.MProfileApi;
import io.github.miklires.mprofile.api.ProfileSnapshot;
import io.github.miklires.mprofile.gui.ProfileGui;
import io.github.miklires.mprofile.profile.ProfileService;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ProfileApiService implements MProfileApi {
    private final ProfileService profiles;
    private final ProfileGui gui;

    public ProfileApiService(ProfileService profiles, ProfileGui gui) { this.profiles = profiles; this.gui = gui; }
    @Override public CompletableFuture<Optional<ProfileSnapshot>> profile(UUID playerId) { return profiles.profile(playerId); }
    @Override public CompletableFuture<Boolean> openProfile(Player viewer, UUID playerId) { return gui.open(viewer, playerId); }
}
