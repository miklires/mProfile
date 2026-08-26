package io.github.miklires.mprofile.gui;

import io.github.miklires.mprofile.MProfilePlugin;
import io.github.miklires.mprofile.integration.IntegrationService;
import io.github.miklires.mprofile.integration.IntegrationSummary;
import io.github.miklires.mprofile.message.MessageService;
import io.github.miklires.mprofile.profile.ProfileData;
import io.github.miklires.mprofile.profile.ProfileService;
import io.github.miklires.mprofile.profile.VisibilityPolicy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ProfileGui implements Listener {
    private final MProfilePlugin plugin;
    private final ProfileService profiles;
    private final MessageService messages;
    private final IntegrationService integrations;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ProfileGui(MProfilePlugin plugin, ProfileService profiles, MessageService messages) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.messages = messages;
        integrations = new IntegrationService(plugin);
    }

    public CompletableFuture<Boolean> open(Player viewer, UUID playerId) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            plugin.scheduler().player(online, () -> profiles.capture(online)
                    .thenCompose(ignored -> profiles.find(playerId)).whenComplete((profile, error) ->
                            finishOpen(viewer, profile, error, result)));
        } else {
            profiles.find(playerId).whenComplete((profile, error) -> finishOpen(viewer, profile, error, result));
        }
        return result;
    }

    private void finishOpen(Player viewer, java.util.Optional<ProfileData> profile, Throwable error,
                            CompletableFuture<Boolean> result) {
        plugin.scheduler().player(viewer, () -> {
            if (error != null || profile.isEmpty()) {
                messages.send(viewer, error == null ? "not-found" : "storage-error",
                        Map.of("player", profile.map(ProfileData::lastName).orElse("unknown")));
                result.complete(false);
                return;
            }
            ProfileData data = profile.get();
            boolean bypass = viewer.hasPermission("mprofile.view.private");
            if (!VisibilityPolicy.canOpen(data.visibility(), data.playerId(), viewer.getUniqueId(), bypass)) {
                messages.send(viewer, "private");
                result.complete(false);
                return;
            }
            openInventory(viewer, data, bypass);
            result.complete(true);
        });
    }

    private void openInventory(Player viewer, ProfileData profile, boolean bypass) {
        int configuredSize = plugin.getConfig().getInt("gui.size", 54);
        int size = Math.max(9, Math.min(54, ((configuredSize + 8) / 9) * 9));
        String title = plugin.getConfig().getString("gui.title", "<dark_gray>Profile: <white>{player}")
                .replace("{player}", profile.lastName().replace("<", "\\<"));
        ProfileHolder holder = new ProfileHolder(profile.playerId());
        Inventory inventory = Bukkit.createInventory(holder, size, miniMessage.deserialize(title));
        holder.inventory(inventory);

        boolean detailed = VisibilityPolicy.canViewStatistics(profile.visibility(), profile.playerId(),
                viewer.getUniqueId(), bypass);
        IntegrationSummary summary = integrations.read(profile.playerId());
        inventory.setItem(13, item(Material.PLAYER_HEAD, Component.text(profile.lastName(), NamedTextColor.AQUA), List.of(
                status(profile),
                Component.text("Visibility: " + profile.visibility(), NamedTextColor.GRAY),
                Component.text("Theme: " + profile.theme(), NamedTextColor.GRAY)
        )));
        inventory.setItem(20, item(Material.WRITABLE_BOOK, Component.text("Biography", NamedTextColor.YELLOW),
                List.of(Component.text(profile.biography().isBlank()
                        ? messages.raw("gui-biography-empty", "No biography set") : profile.biography(), NamedTextColor.GRAY))));
        inventory.setItem(22, statistics(profile, detailed));
        inventory.setItem(24, item(Material.NAME_TAG, Component.text("mPlugins", NamedTextColor.LIGHT_PURPLE), List.of(
                Component.text("Reputation: " + summary.reputation(), NamedTextColor.GRAY),
                Component.text("Badges: " + summary.badges(), NamedTextColor.GRAY),
                Component.text("Color: " + summary.color(), NamedTextColor.GRAY)
        )));
        inventory.setItem(40, item(Material.CLOCK, Component.text("Activity", NamedTextColor.GREEN), List.of(
                Component.text("First seen: " + profile.firstSeen(), NamedTextColor.GRAY),
                Component.text("Last seen: " + profile.lastSeen(), NamedTextColor.GRAY)
        )));
        viewer.openInventory(inventory);
    }

    private ItemStack statistics(ProfileData profile, boolean detailed) {
        List<Component> lore = new ArrayList<>();
        if (detailed) {
            Duration played = Duration.ofSeconds(profile.playtimeTicks() / 20);
            lore.add(Component.text("Playtime: " + played.toHours() + "h", NamedTextColor.GRAY));
            lore.add(Component.text("Player kills: " + profile.playerKills(), NamedTextColor.GRAY));
            lore.add(Component.text("Deaths: " + profile.deaths(), NamedTextColor.GRAY));
        } else {
            lore.add(miniMessage.deserialize(messages.raw("gui-limited", "<yellow>Detailed statistics are hidden.")));
        }
        return item(Material.DIAMOND_SWORD, Component.text("Statistics", NamedTextColor.RED), lore);
    }

    private Component status(ProfileData profile) {
        if (Bukkit.getPlayer(profile.playerId()) != null) {
            return miniMessage.deserialize(messages.raw("gui-status-online", "<green>Online"));
        }
        String ago = compactAge(profile.lastSeen());
        return miniMessage.deserialize(messages.raw("gui-status-offline", "<gray>Last seen {time}")
                .replace("{time}", ago));
    }

    static String compactAge(Instant instant) {
        long seconds = Math.max(0, Duration.between(instant, Instant.now()).getSeconds());
        if (seconds < 60) return seconds + "s ago";
        if (seconds < 3600) return seconds / 60 + "m ago";
        if (seconds < 86400) return seconds / 3600 + "h ago";
        return seconds / 86400 + "d ago";
    }

    private ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof ProfileHolder) event.setCancelled(true);
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof ProfileHolder) event.setCancelled(true);
    }
}
