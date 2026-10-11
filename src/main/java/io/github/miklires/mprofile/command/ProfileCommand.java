package io.github.miklires.mprofile.command;

import io.github.miklires.mprofile.MProfilePlugin;
import io.github.miklires.mprofile.api.ProfileVisibility;
import io.github.miklires.mprofile.gui.ProfileGui;
import io.github.miklires.mprofile.message.MessageService;
import io.github.miklires.mprofile.profile.ProfileService;
import io.github.miklires.mprofile.profile.ProfileText;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ProfileCommand implements BasicCommand {
    private final MProfilePlugin plugin;
    private final ProfileService profiles;
    private final ProfileGui gui;
    private final MessageService messages;

    public ProfileCommand(MProfilePlugin plugin, ProfileService profiles, ProfileGui gui, MessageService messages) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.gui = gui;
        this.messages = messages;
    }

    @Override public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        CommandSender sender = source.getSender();
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) { reload(sender); return; }
        if (!(sender instanceof Player player)) { messages.send(sender, "player-only"); return; }
        if (!player.hasPermission("mprofile.use")) { messages.send(player, "no-permission"); return; }
        if (!plugin.ready()) { messages.send(player, "loading"); return; }
        if (args.length == 0) { gui.open(player, player.getUniqueId()); return; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "bio" -> biography(player, args);
            case "visibility" -> visibility(player, args);
            case "theme" -> theme(player, args);
            case "settings" -> settings(player);
            case "inventory" -> inventory(player, args);
            case "vote" -> vote(player, args);
            default -> openNamed(player, args[0]);
        }
    }

    private void settings(Player player) {
        if (!player.hasPermission("mprofile.edit")) { messages.send(player, "no-permission"); return; }
        gui.openSettings(player);
    }

    private void inventory(Player player, String[] args) {
        if (args.length < 2) { gui.openInventory(player, player.getUniqueId()); return; }
        openNamed(player, args[1], true);
    }

    private void vote(Player player, String[] args) {
        if (!player.hasPermission("mprofile.vote")) { messages.send(player, "no-permission"); return; }
        if (args.length < 3) { messages.send(player, "usage"); return; }
        int direction = switch (args[2].toLowerCase(Locale.ROOT)) {
            case "positive", "plus", "+" -> 1;
            case "negative", "minus", "-" -> -1;
            default -> 0;
        };
        if (direction == 0) { messages.send(player, "usage"); return; }
        Player online = Bukkit.getPlayerExact(args[1]);
        if (online != null) {
            boolean scheduled = plugin.scheduler().player(online, () -> profiles.capture(online)
                    .thenCompose(ignored -> profiles.find(online.getUniqueId()))
                    .whenComplete((found, error) -> plugin.scheduler().player(player, () -> {
                            if (error != null) messages.send(player, "storage-error");
                            else if (found.isEmpty()) messages.send(player, "not-found", Map.of("player", args[1]));
                            else gui.openVoteDialog(player, found.get(), direction);
                        })));
            if (!scheduled) messages.send(player, "not-found", Map.of("player", args[1]));
            return;
        }
        profiles.findByName(args[1]).whenComplete((found, error) -> plugin.scheduler().player(player, () -> {
            if (error != null) messages.send(player, "storage-error");
            else if (found.isEmpty()) messages.send(player, "not-found", Map.of("player", args[1]));
            else gui.openVoteDialog(player, found.get(), direction);
        }));
    }

    private void biography(Player player, String[] args) {
        if (!player.hasPermission("mprofile.edit")) { messages.send(player, "no-permission"); return; }
        int limit = Math.clamp(plugin.getConfig().getInt("profile.maximum-biography-length", 120), 0, 120);
        String input = args.length < 2 ? "" : String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (input.length() > limit) { messages.send(player, "bio-too-long", Map.of("limit", Integer.toString(limit))); return; }
        String biography = ProfileText.biography(input, limit);
        save(player, data -> data.preferences(biography, data.visibility(), data.theme()), "bio-updated");
    }

    private void visibility(Player player, String[] args) {
        if (!player.hasPermission("mprofile.edit")) { messages.send(player, "no-permission"); return; }
        if (args.length < 2) { messages.send(player, "invalid-visibility"); return; }
        try {
            ProfileVisibility visibility = ProfileVisibility.valueOf(args[1].toUpperCase(Locale.ROOT));
            save(player, data -> data.preferences(data.biography(), visibility, data.theme()), "visibility-updated",
                    Map.of("visibility", visibility.name()));
        } catch (IllegalArgumentException exception) { messages.send(player, "invalid-visibility"); }
    }

    private void theme(Player player, String[] args) {
        if (!player.hasPermission("mprofile.edit")) { messages.send(player, "no-permission"); return; }
        List<String> themes = configuredThemes();
        String theme = args.length < 2 ? "" : args[1].toUpperCase(Locale.ROOT);
        if (!themes.contains(theme)) { messages.send(player, "invalid-theme", Map.of("themes", String.join(", ", themes))); return; }
        save(player, data -> data.preferences(data.biography(), data.visibility(), theme), "theme-updated",
                Map.of("theme", theme));
    }

    private void openNamed(Player player, String name) {
        openNamed(player, name, false);
    }

    private void openNamed(Player player, String name, boolean forceInventory) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            if (forceInventory) gui.openInventory(player, online.getUniqueId()); else gui.open(player, online.getUniqueId());
            return;
        }
        profiles.findByName(name).whenComplete((found, error) -> plugin.scheduler().player(player, () -> {
            if (error != null) messages.send(player, "storage-error");
            else if (found.isEmpty()) messages.send(player, "not-found", Map.of("player", name));
            else if (forceInventory) gui.openInventory(player, found.get().playerId());
            else gui.open(player, found.get().playerId());
        }));
    }

    private void save(Player player, java.util.function.UnaryOperator<io.github.miklires.mprofile.profile.ProfileData> update,
                      String message) { save(player, update, message, Map.of()); }

    private void save(Player player, java.util.function.UnaryOperator<io.github.miklires.mprofile.profile.ProfileData> update,
                      String message, Map<String, String> replacements) {
        profiles.update(player, update).thenAccept(success -> plugin.scheduler().player(player,
                () -> messages.send(player, success ? message : "storage-error", replacements)));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("mprofile.reload")) { messages.send(sender, "no-permission"); return; }
        plugin.reloadRuntime();
        messages.send(sender, "reloaded");
    }

    @Override public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
        CommandSender sender = source.getSender();
        if (args.length <= 1) {
            var values = new java.util.ArrayList<>(List.of("bio", "visibility", "theme", "settings", "inventory", "vote"));
            if (sender.hasPermission("mprofile.reload")) values.add("reload");
            Bukkit.getOnlinePlayers().forEach(player -> values.add(player.getName()));
            return filter(values, args.length == 0 ? "" : args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("visibility"))
            return filter(List.of("PUBLIC", "LIMITED", "PRIVATE"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("theme"))
            return filter(configuredThemes(), args[1]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("inventory") || args[0].equalsIgnoreCase("vote")))
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("vote"))
            return filter(List.of("positive", "negative"), args[2]);
        return List.of();
    }

    private Collection<String> filter(Collection<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).distinct().toList();
    }

    private List<String> configuredThemes() {
        List<String> themes = plugin.getConfig().getStringList("profile.themes").stream()
                .map(value -> value.toUpperCase(Locale.ROOT))
                .filter(value -> value.matches("[A-Z0-9_-]{1,32}"))
                .distinct().toList();
        return themes.isEmpty() ? List.of("BLUE", "GREEN", "RED", "PURPLE", "GOLD") : themes;
    }
}
