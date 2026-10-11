package io.github.miklires.mprofile.gui;

import io.github.miklires.mprofile.MProfilePlugin;
import io.github.miklires.mprofile.integration.IntegrationService;
import io.github.miklires.mprofile.integration.IntegrationSummary;
import io.github.miklires.mprofile.message.MessageService;
import io.github.miklires.mprofile.profile.ProfileData;
import io.github.miklires.mprofile.profile.ProfileService;
import io.github.miklires.mprofile.profile.VisibilityPolicy;
import io.github.miklires.mprofile.resourcepack.ProfileResourcePack;
import io.github.miklires.mprofile.api.ProfileVisibility;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogInstancesProvider;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.ClickCallback;
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
import java.util.Locale;

public final class ProfileGui implements Listener {
    private final MProfilePlugin plugin;
    private final ProfileService profiles;
    private final MessageService messages;
    private final IntegrationService integrations;
    private final ProfileResourcePack resourcePack;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, Long> lastOpen = new ConcurrentHashMap<>();

    public ProfileGui(MProfilePlugin plugin, ProfileService profiles, MessageService messages,
                      ProfileResourcePack resourcePack) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.messages = messages;
        this.resourcePack = resourcePack;
        integrations = new IntegrationService(plugin);
    }

    public CompletableFuture<Boolean> open(Player viewer, UUID playerId) {
        return open(viewer, playerId, false);
    }

    public CompletableFuture<Boolean> openInventory(Player viewer, UUID playerId) {
        return open(viewer, playerId, true);
    }

    private CompletableFuture<Boolean> open(Player viewer, UUID playerId, boolean forceInventory) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (!plugin.scheduler().player(viewer, () -> beginOpen(viewer, playerId, forceInventory, result))) result.complete(false);
        return result;
    }

    private void beginOpen(Player viewer, UUID playerId, boolean forceInventory, CompletableFuture<Boolean> result) {
        if (!reserveOpen(viewer)) {
            messages.send(viewer, "cooldown");
            result.complete(false);
            return;
        }
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            plugin.scheduler().player(online, () -> profiles.capture(online)
                    .thenCompose(ignored -> profiles.find(playerId)).whenComplete((profile, error) ->
                            finishOpen(viewer, profile, error, forceInventory, result)));
        } else {
            profiles.find(playerId).whenComplete((profile, error) -> finishOpen(viewer, profile, error, forceInventory, result));
        }
    }

    private void finishOpen(Player viewer, java.util.Optional<ProfileData> profile, Throwable error,
                            boolean forceInventory, CompletableFuture<Boolean> result) {
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
            if (!forceInventory && "DIALOG".equalsIgnoreCase(plugin.getConfig().getString("gui.mode", "DIALOG"))) {
                try {
                    openDialog(viewer, data, bypass);
                } catch (RuntimeException | LinkageError dialogError) {
                    plugin.getLogger().warning("Could not open profile dialog: " + dialogError.getMessage());
                    if (plugin.getConfig().getBoolean("gui.fallback-to-inventory", true)) openInventory(viewer, data, bypass);
                    else { result.complete(false); return; }
                }
            } else openInventory(viewer, data, bypass);
            result.complete(true);
        });
    }

    private void openDialog(Player viewer, ProfileData profile, boolean bypass) {
        boolean owner = profile.playerId().equals(viewer.getUniqueId());
        boolean detailed = VisibilityPolicy.canViewStatistics(profile.visibility(), profile.playerId(),
                viewer.getUniqueId(), bypass) && (owner || bypass || profile.showStatistics());
        IntegrationSummary summary = integrations.read(profile.playerId());
        List<DialogBody> bodies = new ArrayList<>();
        bodies.add(DialogBody.plainMessage(cover(profile.cover(), viewer), 400));
        Component biography = Component.text(profile.biography().isBlank()
                ? messages.raw("gui-biography-empty", "No biography set") : profile.biography(), NamedTextColor.GRAY);
        bodies.add(DialogBody.item(head(profile, Component.text(profile.lastName()), List.of(status(profile))))
                .description(DialogBody.plainMessage(biography, 300)).showDecorations(true).showTooltip(true)
                .width(64).height(64).build());

        List<Component> details = new ArrayList<>();
        details.add(status(profile));
        details.add(messages.component("gui-identity-visibility", "<gray>Visibility: <white>{visibility}",
                Map.of("visibility", profile.visibility().name())));
        details.add(messages.component("gui-identity-theme", "<gray>Theme: <white>{theme}",
                Map.of("theme", profile.theme())));
        details.add(messages.component("gui-identity-cover", "<gray>Cover: <white>{cover}",
                Map.of("cover", profile.cover())));
        if (detailed) {
            Duration played = Duration.ofSeconds(profile.playtimeTicks() / 20);
            details.add(messages.component("gui-statistics-playtime", "<gray>Playtime: <white>{hours}h",
                    Map.of("hours", Long.toString(played.toHours()))));
            details.add(messages.component("gui-statistics-kills", "<gray>Player kills: <white>{kills}",
                    Map.of("kills", Integer.toString(profile.playerKills()))));
            details.add(messages.component("gui-statistics-deaths", "<gray>Deaths: <white>{deaths}",
                    Map.of("deaths", Integer.toString(profile.deaths()))));
        } else details.add(miniMessage.deserialize(messages.raw("gui-limited", "<yellow>Detailed statistics are hidden.")));
        if (owner || bypass || profile.showActivity()) {
            details.add(messages.component("gui-activity-first", "<gray>First seen: <white>{time}",
                    Map.of("time", localizedAge(profile.firstSeen()))));
            details.add(messages.component("gui-activity-last", "<gray>Last seen: <white>{time}",
                    Map.of("time", localizedAge(profile.lastSeen()))));
        }
        if (owner || bypass || profile.showIntegrations()) {
            details.add(messages.component("gui-integrations-reputation", "<gray>Reputation: <white>{reputation}",
                    Map.of("reputation", summary.reputation())));
            details.add(messages.component("gui-integrations-badges", "<gray>Badges: <white>{badges}",
                    Map.of("badges", summary.badges())));
            details.add(messages.component("gui-integrations-color", "<gray>Color: <white>{color}",
                    Map.of("color", summary.color())));
        }
        bodies.add(DialogBody.plainMessage(Component.join(JoinConfiguration.separator(Component.newline()), details), 400));

        List<ActionButton> actions = new ArrayList<>();
        if (owner) {
            actions.add(dialogButton(messages.component("gui-action-settings", "<gold>Profile settings"),
                    ClickEvent.runCommand("/profile settings")));
        } else {
            for (String key : List.of("message", "report", "ignore")) {
                if (key.equals("message") && !profile.allowMessages()) continue;
                ActionButton button = configuredDialogAction(key, profile);
                if (button != null) actions.add(button);
            }
            if (canVote(viewer)) {
                actions.add(dialogButton(messages.component("gui-action-positive-vote", "<green>+ Reputation"),
                        ClickEvent.runCommand("/profile vote " + profile.lastName() + " positive")));
                if (integrations.negativeReputationVotingAvailable()) {
                    actions.add(dialogButton(messages.component("gui-action-negative-vote", "<red>- Reputation"),
                            ClickEvent.runCommand("/profile vote " + profile.lastName() + " negative")));
                }
            }
        }
        ActionButton close = ActionButton.builder(messages.component("dialog-settings-cancel", "<gray>Close"))
                .width(150).action(DialogAction.customClick((response, audience) -> { },
                        ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(15)).build())).build();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.component("gui-title", "<dark_gray>Profile: <white>{player}",
                                Map.of("player", profile.lastName())))
                        .externalTitle(Component.text(profile.lastName())).canCloseWithEscape(true).pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE).body(bodies).build())
                .type(actions.isEmpty() ? DialogInstancesProvider.instance().notice(close)
                        : DialogInstancesProvider.instance().multiAction(actions).columns(2).exitAction(close).build()));
        viewer.showDialog(dialog);
    }

    private ActionButton configuredDialogAction(String key, ProfileData profile) {
        String path = "interactions.actions." + key;
        if (!plugin.getConfig().getBoolean("interactions.enabled", true)
                || !plugin.getConfig().getBoolean(path + ".enabled", false)) return null;
        String required = plugin.getConfig().getString(path + ".required-plugin", "");
        if (required != null && !required.isBlank()
                && !plugin.getServer().getPluginManager().isPluginEnabled(required)) return null;
        String command = plugin.getConfig().getString(path + ".command", "");
        if (command == null || command.isBlank() || command.length() > 128
                || command.chars().anyMatch(Character::isISOControl)) return null;
        command = command.stripLeading().replace("{player}", profile.lastName());
        while (command.startsWith("/")) command = command.substring(1);
        boolean suggest = plugin.getConfig().getString(path + ".mode", "SUGGEST").equalsIgnoreCase("SUGGEST");
        return dialogButton(messages.component("gui-action-" + key, "<yellow>" + key),
                suggest ? ClickEvent.suggestCommand("/" + command) : ClickEvent.runCommand("/" + command));
    }

    private ActionButton dialogButton(Component label, ClickEvent click) {
        return ActionButton.builder(label).width(150).action(DialogAction.staticAction(click)).build();
    }

    public void openSettings(Player player) {
        profiles.find(player.getUniqueId()).whenComplete((found, error) -> plugin.scheduler().player(player, () -> {
            if (error != null || found.isEmpty()) {
                messages.send(player, error == null ? "not-found" : "storage-error", Map.of("player", player.getName()));
                return;
            }
            showSettingsDialog(player, found.get());
        }));
    }

    public void openVoteDialog(Player voter, ProfileData target, int direction) {
        if (!voter.hasPermission("mprofile.vote") || !voter.hasPermission("mreputation.vote")) {
            messages.send(voter, "no-permission");
            return;
        }
        if (!integrations.reputationVotingAvailable()) { messages.send(voter, "vote-unavailable"); return; }
        if (direction < 0 && !integrations.negativeReputationVotingAvailable()) {
            messages.send(voter, "vote-negative-disabled");
            return;
        }
        if (voter.getUniqueId().equals(target.playerId())) { messages.send(voter, "vote-self"); return; }
        int normalizedDirection = Integer.signum(direction);
        if (normalizedDirection == 0) { messages.send(voter, "vote-rejected"); return; }
        DialogInstancesProvider provider = DialogInstancesProvider.instance();
        List<DialogInput> inputs = List.of(provider.textBuilder("evidence",
                        messages.component("dialog-vote-evidence", "Reason or evidence"))
                .maxLength(160).width(400).build());
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();
        ActionButton submit = ActionButton.builder(messages.component("dialog-vote-submit", "<green>Submit vote"))
                .width(150).action(DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player player)) return;
                    String evidence = response.getText("evidence");
                    plugin.scheduler().player(player, () -> submitVote(player, target, normalizedDirection,
                            evidence == null ? "" : evidence));
                }, once)).build();
        ActionButton cancel = ActionButton.builder(messages.component("dialog-settings-cancel", "<gray>Cancel"))
                .width(150).action(DialogAction.customClick((response, audience) -> { }, once)).build();
        String sign = normalizedDirection > 0 ? "+" : "−";
        Dialog dialog = Dialog.create(factory -> factory.empty().base(DialogBase.builder(messages.component(
                        "dialog-vote-title", "<gold>Rate {player}", Map.of("player", target.lastName())))
                .canCloseWithEscape(true).pause(false).afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(Component.text(sign + "1 · " + target.lastName()), 400)))
                .inputs(inputs).build()).type(provider.confirmation(submit, cancel)));
        voter.showDialog(dialog);
    }

    private void submitVote(Player voter, ProfileData target, int direction, String evidence) {
        String result = integrations.vote(voter, target, direction, evidence);
        String key = switch (result) {
            case "SUCCESS" -> "vote-success";
            case "DISABLED" -> "vote-disabled";
            case "NEGATIVE_DISABLED" -> "vote-negative-disabled";
            case "SELF_VOTE" -> "vote-self";
            case "NOT_ENOUGH_PLAYTIME" -> "vote-playtime";
            case "ACCOUNT_TOO_NEW" -> "vote-account-age";
            case "EVIDENCE_REQUIRED" -> "vote-evidence";
            case "RECIPROCAL_VOTE" -> "vote-reciprocal";
            case "COOLDOWN" -> "vote-cooldown";
            case "DAILY_LIMIT" -> "vote-limit";
            case "ALREADY_VOTED" -> "vote-duplicate";
            case "UNAVAILABLE" -> "vote-unavailable";
            default -> "vote-rejected";
        };
        messages.send(voter, key, Map.of("player", target.lastName()));
    }

    private void showSettingsDialog(Player player, ProfileData profile) {
        DialogInstancesProvider provider = DialogInstancesProvider.instance();
        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(provider.textBuilder("biography", messages.component("dialog-settings-bio", "Biography"))
                .initial(profile.biography()).maxLength(Math.clamp(
                        plugin.getConfig().getInt("profile.maximum-biography-length", 120), 1, 120)).width(400).build());
        inputs.add(provider.singleOptionBuilder("visibility", messages.component("dialog-settings-visibility", "Visibility"),
                options(provider, List.of("PUBLIC", "LIMITED", "PRIVATE"), profile.visibility().name())).width(400).build());
        inputs.add(provider.singleOptionBuilder("theme", messages.component("dialog-settings-theme", "Theme"),
                options(provider, configuredValues("profile.themes", List.of("BLUE", "GREEN", "RED", "PURPLE", "GOLD")),
                        profile.theme())).width(400).build());
        inputs.add(provider.singleOptionBuilder("cover", messages.component("dialog-settings-cover", "Cover"),
                options(provider, configuredValues("profile.covers", List.of("NONE")), profile.cover())).width(400).build());
        inputs.add(provider.booleanBuilder("show_statistics", messages.component("dialog-settings-statistics", "Show statistics"))
                .initial(profile.showStatistics()).build());
        inputs.add(provider.booleanBuilder("show_activity", messages.component("dialog-settings-activity", "Show activity"))
                .initial(profile.showActivity()).build());
        inputs.add(provider.booleanBuilder("show_integrations", messages.component("dialog-settings-integrations", "Show integrations"))
                .initial(profile.showIntegrations()).build());
        inputs.add(provider.booleanBuilder("allow_messages", messages.component("dialog-settings-messages", "Allow messages"))
                .initial(profile.allowMessages()).build());

        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(15)).build();
        ActionButton save = ActionButton.builder(messages.component("dialog-settings-save", "<green>Save"))
                .width(150).action(DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player responder) plugin.scheduler().player(responder,
                            () -> saveSettings(responder, response));
                }, once)).build();
        ActionButton cancel = ActionButton.builder(messages.component("dialog-settings-cancel", "<gray>Cancel"))
                .width(150).action(DialogAction.customClick((response, audience) -> { }, once)).build();
        Dialog dialog = Dialog.create(factory -> factory.empty().base(DialogBase.builder(
                        messages.component("dialog-settings-title", "<gold>Profile settings"))
                .canCloseWithEscape(true).pause(false).afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(cover(profile.cover(), player), 400))).inputs(inputs).build())
                .type(provider.confirmation(save, cancel)));
        player.showDialog(dialog);
    }

    private List<SingleOptionDialogInput.OptionEntry> options(DialogInstancesProvider provider, List<String> values,
                                                               String selected) {
        return values.stream().map(value -> provider.singleOptionEntry(value, Component.text(value),
                value.equalsIgnoreCase(selected))).toList();
    }

    private void saveSettings(Player player, DialogResponseView response) {
        List<String> themes = configuredValues("profile.themes", List.of("BLUE", "GREEN", "RED", "PURPLE", "GOLD"));
        List<String> covers = configuredValues("profile.covers", List.of("NONE"));
        String theme = selected(response.getText("theme"), themes, themes.getFirst());
        String cover = selected(response.getText("cover"), covers, covers.getFirst());
        ProfileVisibility visibility;
        try { visibility = ProfileVisibility.valueOf(String.valueOf(response.getText("visibility")).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { visibility = ProfileVisibility.PUBLIC; }
        int limit = Math.clamp(plugin.getConfig().getInt("profile.maximum-biography-length", 120), 0, 120);
        String rawBiography = response.getText("biography");
        String biography = io.github.miklires.mprofile.profile.ProfileText.biography(
                rawBiography == null ? "" : rawBiography, limit);
        ProfileVisibility finalVisibility = visibility;
        profiles.update(player, current -> current.preferences(biography, finalVisibility, theme, cover,
                Boolean.TRUE.equals(response.getBoolean("show_statistics")),
                Boolean.TRUE.equals(response.getBoolean("show_activity")),
                Boolean.TRUE.equals(response.getBoolean("show_integrations")),
                Boolean.TRUE.equals(response.getBoolean("allow_messages")))).thenAccept(success ->
                plugin.scheduler().player(player, () -> {
                    messages.send(player, success ? "settings-updated" : "storage-error");
                    if (success) open(player, player.getUniqueId());
                }));
    }

    private String selected(String input, List<String> allowed, String fallback) {
        if (input == null) return fallback;
        String normalized = input.toUpperCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : fallback;
    }

    private List<String> configuredValues(String path, List<String> fallback) {
        List<String> values = plugin.getConfig().getStringList(path).stream().map(value -> value.toUpperCase(Locale.ROOT))
                .filter(value -> value.matches("[A-Z0-9_-]{1,32}")).distinct().toList();
        return values.isEmpty() ? fallback : values;
    }

    private Component cover(String name) {
        String normalized = name == null ? "NONE" : name.toUpperCase(Locale.ROOT);
        String style = plugin.getConfig().getString("profile.cover-styles." + normalized,
                "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        return miniMessage.deserialize(style == null || style.length() > 512
                ? "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━" : style);
    }

    private Component cover(String name, Player viewer) {
        if (!resourcePack.visualCoversAvailable(viewer)) return cover(name);
        String normalized = name == null ? "NONE" : name.toUpperCase(Locale.ROOT);
        String glyph = plugin.getConfig().getString("profile.cover-glyphs." + normalized, "");
        if (glyph == null || glyph.codePointCount(0, glyph.length()) != 1) return cover(name);
        return Component.text(glyph).font(Key.key("mprofile", "covers")).color(NamedTextColor.WHITE);
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
                viewer.getUniqueId(), bypass) && (profile.playerId().equals(viewer.getUniqueId())
                || bypass || profile.showStatistics());
        IntegrationSummary summary = integrations.read(profile.playerId());
        Set<Integer> occupied = new HashSet<>();
        String accent = themeColor(profile.theme());
        place(inventory, occupied, "identity", 14, head(profile,
                miniMessage.deserialize(accent + miniMessage.escapeTags(profile.lastName())), List.of(
                status(profile),
                messages.component("gui-identity-visibility", "<gray>Visibility: <white>{visibility}",
                        Map.of("visibility", profile.visibility().name())),
                messages.component("gui-identity-theme", "<gray>Theme: <white>{theme}",
                        Map.of("theme", profile.theme())),
                messages.component("gui-identity-cover", "<gray>Cover: <white>{cover}",
                        Map.of("cover", profile.cover()))
        )));
        place(inventory, occupied, "cover", 5, item(material("cover", Material.WHITE_BANNER),
                Component.text(profile.cover(), NamedTextColor.GOLD), List.of(cover(profile.cover()))));
        place(inventory, occupied, "biography", 21, item(material("biography", Material.WRITABLE_BOOK),
                messages.component("gui-biography-name", "<yellow>Biography"),
                List.of(Component.text(profile.biography().isBlank()
                        ? messages.raw("gui-biography-empty", "No biography set") : profile.biography(), NamedTextColor.GRAY))));
        place(inventory, occupied, "statistics", 23, statistics(profile, detailed));
        boolean owner = profile.playerId().equals(viewer.getUniqueId());
        if (owner || bypass || profile.showIntegrations()) place(inventory, occupied, "integrations", 25,
                item(material("integrations", Material.NAME_TAG), messages.component("gui-integrations-name", "<light_purple>mPlugins"), List.of(
                        messages.component("gui-integrations-reputation", "<gray>Reputation: <white>{reputation}", Map.of("reputation", summary.reputation())),
                        messages.component("gui-integrations-badges", "<gray>Badges: <white>{badges}", Map.of("badges", summary.badges())),
                        messages.component("gui-integrations-color", "<gray>Color: <white>{color}", Map.of("color", summary.color()))
                )));
        if (owner || bypass || profile.showActivity()) place(inventory, occupied, "activity", 41,
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
        if (profile.playerId().equals(viewer.getUniqueId())) {
            addCommandAction(inventory, holder, occupied, "settings", 53, Material.COMPARATOR,
                    messages.component("gui-action-settings", "<gold>Profile settings"), "profile settings", false);
            return;
        }
        if (!plugin.getConfig().getBoolean("interactions.enabled", true)) return;
        for (String key : List.of("message", "report", "ignore")) {
            if (key.equals("message") && !profile.allowMessages()) continue;
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
        if (canVote(viewer)) {
            addCommandAction(inventory, holder, occupied, "positive-vote", 46, Material.LIME_DYE,
                    messages.component("gui-action-positive-vote", "<green>+ Reputation"),
                    "profile vote " + profile.lastName() + " positive", false);
            if (integrations.negativeReputationVotingAvailable()) {
                addCommandAction(inventory, holder, occupied, "negative-vote", 54, Material.RED_DYE,
                        messages.component("gui-action-negative-vote", "<red>- Reputation"),
                        "profile vote " + profile.lastName() + " negative", false);
            }
        }
    }

    private void addCommandAction(Inventory inventory, ProfileHolder holder, Set<Integer> occupied, String key,
                                  int fallback, Material material, Component name, String command, boolean suggest) {
        int slot = plugin.getConfig().getInt("gui.slots." + key, fallback) - 1;
        if (slot < 0 || slot >= inventory.getSize() || !occupied.add(slot)) return;
        inventory.setItem(slot, item(material, name, List.of(messages.component("gui-action-hint", "<gray>Click to use"))));
        holder.action(slot, new ProfileAction(command, suggest));
    }

    private boolean canVote(Player player) {
        return player.hasPermission("mprofile.vote") && player.hasPermission("mreputation.vote")
                && integrations.reputationVotingAvailable();
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
