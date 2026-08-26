package io.github.miklires.mprofile.api;

import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface MProfileApi {
    CompletableFuture<Optional<ProfileSnapshot>> profile(UUID playerId);
    CompletableFuture<Boolean> openProfile(Player viewer, UUID playerId);
}
