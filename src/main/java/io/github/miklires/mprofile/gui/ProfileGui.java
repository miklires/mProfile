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
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ProfileGui implements Listener {
    private final MProfilePlugin plugin;
    private final ProfileService profiles;
    private final MessageService messages;
    private final IntegrationService integrations;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, Long> lastOpen = new ConcurrentHashMap<>();

    public ProfileGui(MProfilePlugin plugin, ProfileService profiles, MessageService messages) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.messages = messages;
        integrations = new IntegrationService(plugin);
    }

    public CompletableFuture<Boolean> open(Player viewer, UUID playerId) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (!plugin.scheduler().player(viewer, () -> beginOpen(viewer, playerId, result))) result.complete(false);
        return result;
    }

    private void beginOpen(Player viewer, UUID playerId, CompletableFuture<Boolean> result) {
        if (!reserveOpen(viewer)) {
            messages.send(viewer, "cooldown");
            result.complete(false);
            return;
        }
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            plugin.scheduler().player(online, () -> profiles.capture(online)
                    .thenCompose(ignored -> profiles.find(playerId)).whenComplete((profile, error) ->
                            finishOpen(viewer, profile, error, result)));
        } else {
            profiles.find(playerId).whenComplete((profile, error) -> finishOpen(viewer, profile, error, result));
        }
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
        String configuredTitle = plugin.getConfig().getString("gui.title", "");
        Component title = configuredTitle == null || configuredTitle.isBlank()
                || configuredTitle.equals("<dark_gray>Profile: <white>{player}")
                ? messages.component("gui-title", "<dark_gray>Profile: <white>{player}",
                        Map.of("player", profile.lastName()))
                : miniMessage.deserialize(configuredTitle.replace("{player}", miniMessage.escapeTags(profile.lastName())));
        ProfileHolder holder = new ProfileHolder(profile.playerId());
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.inventory(inventory);

        boolean detailed = VisibilityPolicy.canViewStatistics(profile.visibility(), profile.playerId(),
                viewer.getUniqueId(), bypass);
        IntegrationSummary summary = integrations.read(profile.playerId());
        Set<Integer> occupied = new HashSet<>();
        String accent = themeColor(profile.theme());
        place(inventory, occupied, "identity", 14, head(profile,
                miniMessage.deserialize(accent + miniMessage.escapeTags(profile.lastName())), List.of(
                status(profile),
                messages.component("gui-identity-visibility", "<gray>Visibility: <white>{visibility}",
                        Map.of("visibility", profile.visibility().name())),
                messages.component("gui-identity-theme", "<gray>Theme: <white>{theme}",
                        Map.of("theme", profile.theme()))
        )));
        place(inventory, occupied, "biography", 21, item(material("biography", Material.WRITABLE_BOOK),
                messages.component("gui-biography-name", "<yellow>Biography"),
                List.of(Component.text(profile.biography().isBlank()
                        ? messages.raw("gui-biography-empty", "No biography set") : profile.biography(), NamedTextColor.GRAY))));
        place(inventory, occupied, "statistics", 23, statistics(profile, detailed));
        place(inventory, occupied, "integrations", 25,
                item(material("integrations", Material.NAME_TAG), messages.component("gui-integrations-name", "<light_purple>mPlugins"), List.of(
                messages.component("gui-integrations-reputation", "<gray>Reputation: <white>{reputation}", Map.of("reputation", summary.reputation())),
                messages.component("gui-integrations-badges", "<gray>Badges: <white>{badges}", Map.of("badges", summary.badges())),
                messages.component("gui-integrations-color", "<gray>Color: <white>{color}", Map.of("color", summary.color()))
        )));
        place(inventory, occupied, "activity", 41,
                item(material("activity", Material.CLOCK), messages.component("gui-activity-name", "<green>Activity"), List.of(
                messages.component("gui-activity-first", "<gray>First seen: <white>{time}", Map.of("time", localizedAge(profile.firstSeen()))),
                messages.component("gui-activity-last", "<gray>Last seen: <white>{time}", Map.of("time", localizedAge(profile.lastSeen())))
        )));
        addActions(inventory, holder, occupied, profile, viewer);
        viewer.openInventory(inventory);
    }

    private ItemStack statistics(ProfileData profile, boolean detailed) {
        List<Component> lore = new ArrayList<>();
        if (detailed) {
            Duration played = Duration.ofSeconds(profile.playtimeTicks() / 20);
            lore.add(messages.component("gui-statistics-playtime", "<gray>Playtime: <white>{hours}h",
                    Map.of("hours", Long.toString(played.toHours()))));
            lore.add(messages.component("gui-statistics-kills", "<gray>Player kills: <white>{kills}",
                    Map.of("kills", Integer.toString(profile.playerKills()))));
            lore.add(messages.component("gui-statistics-deaths", "<gray>Deaths: <white>{deaths}",
                    Map.of("deaths", Integer.toString(profile.deaths()))));
        } else {
            lore.add(miniMessage.deserialize(messages.raw("gui-limited", "<yellow>Detailed statistics are hidden.")));
        }
        return item(material("statistics", Material.DIAMOND_SWORD),
                messages.component("gui-statistics-name", "<red>Statistics"), lore);
    }

    private Component status(ProfileData profile) {
        if (Bukkit.getPlayer(profile.playerId()) != null) {
            return miniMessage.deserialize(messages.raw("gui-status-online", "<green>Online"));
        }
        String ago = localizedAge(profile.lastSeen());
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

    private String localizedAge(Instant instant) {
        long seconds = Math.max(0, Duration.between(instant, Instant.now()).getSeconds());
        if (seconds < 5) return messages.raw("time-now", "now");
        String key;
        long value;
        if (seconds < 60) { key = "time-seconds-ago"; value = seconds; }
        else if (seconds < 3600) { key = "time-minutes-ago"; value = seconds / 60; }
        else if (seconds < 86400) { key = "time-hours-ago"; value = seconds / 3600; }
        else { key = "time-days-ago"; value = seconds / 86400; }
        return messages.raw(key, "{value}").replace("{value}", Long.toString(value));
    }

    private boolean place(Inventory inventory, Set<Integer> occupied, String key, int fallback, ItemStack item) {
        int slot = plugin.getConfig().getInt("gui.slots." + key, fallback) - 1;
        if (slot < 0 || slot >= inventory.getSize() || !occupied.add(slot)) return false;
        inventory.setItem(slot, item);
        return true;
    }

    private Material material(String key, Material fallback) {
        String configured = plugin.getConfig().getString("gui.materials." + key, fallback.name());
        Material found = configured == null ? null : Material.matchMaterial(configured);
        return found == null || !found.isItem() ? fallback : found;
    }

    private ItemStack head(ProfileData profile, Component name, List<Component> lore) {
        ItemStack item = item(Material.PLAYER_HEAD, name, lore);
        if (item.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(profile.playerId()));
            item.setItemMeta(skull);
        }
        return item;
    }

    private String themeColor(String theme) {
        String color = plugin.getConfig().getString("profile.theme-colors." + theme.toUpperCase(java.util.Locale.ROOT), "<aqua>");
        return color == null || color.length() > 32 ? "<aqua>" : color;
    }

    private boolean reserveOpen(Player viewer) {
        if (viewer.hasPermission("mprofile.cooldown.bypass")) return true;
        long cooldown = Math.clamp(plugin.getConfig().getLong("access.view-cooldown-millis", 750), 0, 60_000);
        if (cooldown == 0) return true;
        long now = System.nanoTime();
        Long previous = lastOpen.put(viewer.getUniqueId(), now);
        if (previous == null || now - previous >= cooldown * 1_000_000L) return true;
        lastOpen.put(viewer.getUniqueId(), previous);
        return false;
    }

    private void addActions(Inventory inventory, ProfileHolder holder, Set<Integer> occupied,
                            ProfileData profile, Player viewer) {
        if (profile.playerId().equals(viewer.getUniqueId()) || !plugin.getConfig().getBoolean("interactions.enabled", true)) return;
        for (String key : List.of("message", "report", "ignore")) {
            String path = "interactions.actions." + key;
            if (!plugin.getConfig().getBoolean(path + ".enabled", false)) continue;
            String required = plugin.getConfig().getString(path + ".required-plugin", "");
            if (required != null && !required.isBlank() && !plugin.getServer().getPluginManager().isPluginEnabled(required)) continue;
            String command = plugin.getConfig().getString(path + ".command", "");
            if (command == null || command.isBlank() || command.length() > 128) continue;
            command = command.stripLeading().replace("{player}", profile.lastName());
            if (command.chars().anyMatch(Character::isISOControl)) continue;
            while (command.startsWith("/")) command = command.substring(1);
            boolean suggest = plugin.getConfig().getString(path + ".mode", "SUGGEST").equalsIgnoreCase("SUGGEST");
            int fallback = switch (key) { case "message" -> 47; case "report" -> 49; default -> 51; };
            Material fallbackMaterial = switch (key) { case "message" -> Material.PAPER; case "report" -> Material.REDSTONE_TORCH; default -> Material.BARRIER; };
            String nameKey = "gui-action-" + key;
            ItemStack button = item(actionMaterial(path, fallbackMaterial), messages.component(nameKey, "<yellow>" + key),
                    List.of(messages.component("gui-action-hint", "<gray>Click to use")));
            int slot = plugin.getConfig().getInt(path + ".slot", fallback) - 1;
            if (slot >= 0 && slot < inventory.getSize() && occupied.add(slot)) {
                inventory.setItem(slot, button);
                holder.action(slot, new ProfileAction(command, suggest));
            }
        }
    }

    private Material actionMaterial(String path, Material fallback) {
        String configured = plugin.getConfig().getString(path + ".material", fallback.name());
        Material found = configured == null ? null : Material.matchMaterial(configured);
        return found == null || !found.isItem() ? fallback : found;
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
        if (!(event.getView().getTopInventory().getHolder(false) instanceof ProfileHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        ProfileAction action = holder.action(event.getRawSlot());
        if (action == null) return;
        player.closeInventory();
        if (action.suggest()) {
            player.sendMessage(messages.component("interaction-prompt", "<yellow>Click here to prepare the command.")
                    .clickEvent(ClickEvent.suggestCommand("/" + action.command())));
        } else {
            player.performCommand(action.command());
        }
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof ProfileHolder) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target)) return;
        if (!plugin.getConfig().getBoolean("access.right-click.enabled", true)) return;
        if (plugin.getConfig().getBoolean("access.right-click.require-sneaking", true) && !event.getPlayer().isSneaking()) return;
        if (!event.getPlayer().hasPermission("mprofile.use")) return;
        if (plugin.getConfig().getBoolean("access.right-click.cancel-interaction", false)) event.setCancelled(true);
        open(event.getPlayer(), target.getUniqueId());
    }
}
