package io.github.miklires.mprofile.message;

import io.github.miklires.mprofile.MProfilePlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

public final class MessageService {
    private static final Pattern SAFE_LANGUAGE = Pattern.compile("[A-Za-z0-9_-]{2,16}");
    private final MProfilePlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private YamlConfiguration messages;

    public MessageService(MProfilePlugin plugin) { this.plugin = plugin; reload(); }

    public void reload() {
        String language = plugin.getConfig().getString("language.default", "en_US");
        if (language == null || !SAFE_LANGUAGE.matcher(language).matches()) {
            plugin.getLogger().warning("Invalid language name; falling back to en_US");
            language = "en_US";
        }
        File file = new File(plugin.getDataFolder(), "lang/" + language + ".yml");
        if (!file.isFile()) file = new File(plugin.getDataFolder(), "lang/en_US.yml");
        messages = YamlConfiguration.loadConfiguration(file);
        try (var stream = plugin.getResource("lang/en_US.yml")) {
            if (stream != null) {
                var defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                messages.setDefaults(defaults);
            }
        } catch (java.io.IOException exception) {
            plugin.getLogger().warning("Could not load built-in English message defaults: " + exception.getMessage());
        }
    }

    public void send(CommandSender sender, String key) { send(sender, key, Map.of()); }

    public void send(CommandSender sender, String key, Map<String, String> replacements) {
        String value = messages.getString(key, "<red>Missing message: " + key);
        String prefix = messages.getString("prefix", "");
        for (var entry : replacements.entrySet()) value = value.replace("{" + entry.getKey() + "}", escape(entry.getValue()));
        sender.sendMessage(miniMessage.deserialize(prefix + value));
    }

    public String raw(String key, String fallback) { return messages.getString(key, fallback); }

    public Component component(String key, String fallback) { return component(key, fallback, Map.of()); }

    public Component component(String key, String fallback, Map<String, String> replacements) {
        String value = raw(key, fallback);
        for (var entry : replacements.entrySet())
            value = value.replace("{" + entry.getKey() + "}", escape(entry.getValue()));
        return miniMessage.deserialize(value);
    }

    private String escape(String value) { return miniMessage.escapeTags(value); }
}
