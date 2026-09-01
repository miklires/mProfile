package io.github.miklires.mprofile.profile;

import io.github.miklires.mprofile.MProfilePlugin;
import io.github.miklires.mprofile.api.ProfileSnapshot;
import io.github.miklires.mprofile.api.ProfileVisibility;
import io.github.miklires.mprofile.storage.ProfileRepository;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

public final class ProfileService {
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private final MProfilePlugin plugin;
    private final ProfileRepository repository;
    private final Map<UUID, ProfileData> cache = new ConcurrentHashMap<>();

    public ProfileService(MProfilePlugin plugin, ProfileRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public CompletableFuture<Optional<ProfileSnapshot>> profile(UUID playerId) {
        return find(playerId).thenApply(profile -> profile.map(ProfileData::snapshot));
    }

    public CompletableFuture<Optional<ProfileData>> find(UUID playerId) {
        ProfileData cached = cache.get(playerId);
        if (cached != null) return CompletableFuture.completedFuture(Optional.of(cached));
        return repository.find(playerId).thenApply(found -> {
            found.ifPresent(value -> cache.putIfAbsent(playerId, value));
            return Optional.ofNullable(cache.get(playerId));
        });
    }

    public CompletableFuture<Optional<ProfileData>> findByName(String name) {
        if (name == null || !PLAYER_NAME.matcher(name).matches()) return CompletableFuture.completedFuture(Optional.empty());
        Optional<ProfileData> cached = cache.values().stream()
                .filter(value -> value.lastName().equalsIgnoreCase(name)).findFirst();
        if (cached.isPresent()) return CompletableFuture.completedFuture(cached);
        return repository.findByName(name).thenApply(found -> {
            found.ifPresent(value -> cache.putIfAbsent(value.playerId(), value));
            return found.map(value -> cache.getOrDefault(value.playerId(), value));
        });
    }

    public CompletableFuture<Void> capture(Player player) {
        ProfileData previous = cache.get(player.getUniqueId());
        Instant now = Instant.now();
        Instant firstSeen = player.getFirstPlayed() > 0 ? Instant.ofEpochMilli(player.getFirstPlayed()) : now;
        ProfileVisibility defaultVisibility = configuredVisibility();
        ProfileData data = new ProfileData(player.getUniqueId(), player.getName(),
                previous == null ? "" : previous.biography(), previous == null ? defaultVisibility : previous.visibility(),
                previous == null ? plugin.getConfig().getString("profile.default-theme", "BLUE") : previous.theme(),
                previous == null ? firstSeen : previous.firstSeen(), now, player.getStatistic(Statistic.PLAY_ONE_MINUTE),
                player.getStatistic(Statistic.PLAYER_KILLS), player.getStatistic(Statistic.DEATHS));
        cache.put(data.playerId(), data);
        return repository.save(data).whenComplete((ignored, error) -> {
            if (error != null) {
                if (previous == null) cache.remove(data.playerId(), data);
                else cache.replace(data.playerId(), data, previous);
                plugin.getLogger().warning("Could not capture profile " + data.playerId() + ": " + error.getMessage());
            }
        });
    }

    public CompletableFuture<Boolean> update(Player player, UnaryOperator<ProfileData> update) {
        ProfileData current = cache.get(player.getUniqueId());
        if (current == null) return CompletableFuture.completedFuture(false);
        ProfileData changed = update.apply(current);
        cache.put(changed.playerId(), changed);
        return repository.save(changed).handle((ignored, error) -> {
            if (error == null) return true;
            cache.replace(current.playerId(), changed, current);
            plugin.getLogger().warning("Could not save profile " + current.playerId() + ": " + error.getMessage());
            return false;
        });
    }

    public void removeFromCache(UUID playerId) { cache.remove(playerId); }

    private ProfileVisibility configuredVisibility() {
        try {
            return ProfileVisibility.valueOf(plugin.getConfig().getString("profile.default-visibility", "PUBLIC")
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ProfileVisibility.PUBLIC;
        }
    }
}
