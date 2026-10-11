package io.github.miklires.mprofile.resourcepack;

import io.github.miklires.mprofile.MProfilePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProfileResourcePack implements Listener {
    private static final UUID PACK_ID = UUID.fromString("e9657bbc-1cc7-4b81-9d95-dfb88c68bdaa");

    private final MProfilePlugin plugin;
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();

    public ProfileResourcePack(MProfilePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean visualCoversAvailable(Player player) {
        if (!plugin.getConfig().getBoolean("profile.cover-images.enabled", false)) return false;
        return plugin.getConfig().getBoolean("profile.cover-images.assume-loaded", false)
                || loaded.contains(player.getUniqueId());
    }

    public void reload() {
        loaded.clear();
        if (autoSendEnabled()) plugin.getServer().getOnlinePlayers().forEach(this::send);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (autoSendEnabled()) send(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) return;
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            loaded.add(event.getPlayer().getUniqueId());
        } else if (event.getStatus() == PlayerResourcePackStatusEvent.Status.DECLINED
                || event.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD
                || event.getStatus() == PlayerResourcePackStatusEvent.Status.INVALID_URL
                || event.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_RELOAD
                || event.getStatus() == PlayerResourcePackStatusEvent.Status.DISCARDED) {
            loaded.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        loaded.remove(event.getPlayer().getUniqueId());
    }

    private boolean autoSendEnabled() {
        return plugin.getConfig().getBoolean("profile.cover-images.enabled", false)
                && plugin.getConfig().getBoolean("profile.cover-images.auto-send", false);
    }

    private void send(Player player) {
        String url = plugin.getConfig().getString("profile.cover-images.url", "");
        String sha1 = plugin.getConfig().getString("profile.cover-images.sha1", "");
        if (url == null || (!url.startsWith("https://") && !url.startsWith("http://"))) {
            plugin.getLogger().warning("Visual covers are enabled, but profile.cover-images.url is not an HTTP(S) URL");
            return;
        }
        if (sha1 == null || !sha1.toLowerCase(Locale.ROOT).matches("[0-9a-f]{40}")) {
            plugin.getLogger().warning("Visual covers are enabled, but profile.cover-images.sha1 is not a SHA-1 hash");
            return;
        }
        boolean required = plugin.getConfig().getBoolean("profile.cover-images.required", false);
        String prompt = plugin.getConfig().getString("profile.cover-images.prompt",
                "Download the mProfile cover art pack?");
        try {
            player.setResourcePack(PACK_ID, url, sha1.toLowerCase(Locale.ROOT),
                    Component.text(prompt == null ? "Download the mProfile cover art pack?" : prompt), required);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Could not send the mProfile resource pack: " + exception.getMessage());
        }
    }
}
