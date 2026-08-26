package io.github.miklires.mprofile.message;

import io.github.miklires.mprofile.MProfilePlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;

public final class MessageService {
    private final MProfilePlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private YamlConfiguration messages;

    public MessageService(MProfilePlugin plugin) { this.plugin = plugin; reload(); }

    public void reload() {
        String language = plugin.getConfig().getString("language.default", "en_US");
        File file = new File(plugin.getDataFolder(), "lang/" + language + ".yml");
        if (!file.isFile()) file = new File(plugin.getDataFolder(), "lang/en_US.yml");
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public void send(CommandSender sender, String key) { send(sender, key, Map.of()); }

    public void send(CommandSender sender, String key, Map<String, String> replacements) {
        String value = messages.getString(key, "<red>Missing message: " + key);
        String prefix = messages.getString("prefix", "");
        for (var entry : replacements.entrySet()) value = value.replace("{" + entry.getKey() + "}", escape(entry.getValue()));
        sender.sendMessage(miniMessage.deserialize(prefix + value));
    }

    public String raw(String key, String fallback) { return messages.getString(key, fallback); }

    private String escape(String value) { return value.replace("<", "\\<"); }
}
