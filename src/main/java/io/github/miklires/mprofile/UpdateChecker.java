package io.github.miklires.mprofile;

import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateChecker {
    private static final Pattern VERSION = Pattern.compile("\\\"version_number\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private final MProfilePlugin plugin;

    public UpdateChecker(MProfilePlugin plugin) { this.plugin = plugin; }

    public void start() {
        if (!plugin.getConfig().getBoolean("updates.enabled", true)) return;
        String project = plugin.getConfig().getString("updates.modrinth-project-id", "").trim();
        if (!project.matches("[A-Za-z0-9]{3,64}")) return;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.modrinth.com/v2/project/" + project
                        + "/version?loaders=%5B%22paper%22%5D&game_versions=%5B%2226.3%22%5D"))
                .timeout(Duration.ofSeconds(8)).header("User-Agent", "miklires/mProfile/"
                        + plugin.getPluginMeta().getVersion()).build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
            if (response.statusCode() != 200) return;
            Matcher matcher = VERSION.matcher(response.body());
            if (!matcher.find()) return;
            String available = matcher.group(1);
            try {
                if (VersionComparison.compare(available, plugin.getPluginMeta().getVersion()) <= 0) return;
            } catch (IllegalArgumentException ignored) { return; }
            plugin.getLogger().info("mProfile " + available + " is available on Modrinth");
            plugin.scheduler().global(() -> plugin.getServer().getOnlinePlayers().stream()
                    .filter(player -> player.hasPermission("mprofile.admin")).forEach(this::notifyPlayer));
        }).exceptionally(error -> null);
    }

    private void notifyPlayer(Player player) {
        player.sendMessage(net.kyori.adventure.text.Component.text("mProfile update is available on Modrinth"));
    }

}
