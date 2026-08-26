package io.github.miklires.mprofile.profile;

import io.github.miklires.mprofile.MProfilePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ProfileListener implements Listener {
    private final MProfilePlugin plugin;
    private final ProfileService profiles;

    public ProfileListener(MProfilePlugin plugin, ProfileService profiles) {
        this.plugin = plugin;
        this.profiles = profiles;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        profiles.find(event.getPlayer().getUniqueId()).whenComplete((ignored, error) ->
                plugin.scheduler().player(event.getPlayer(), () -> profiles.capture(event.getPlayer())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        profiles.capture(event.getPlayer()).whenComplete((ignored, error) ->
                profiles.removeFromCache(event.getPlayer().getUniqueId()));
    }
}
