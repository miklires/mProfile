package io.github.miklires.mprofile.util;

import io.github.miklires.mprofile.MProfilePlugin;
import org.bukkit.entity.Player;

public final class PluginScheduler {
    private final MProfilePlugin plugin;

    public PluginScheduler(MProfilePlugin plugin) { this.plugin = plugin; }
    public void global(Runnable task) { plugin.getServer().getGlobalRegionScheduler().execute(plugin, task); }
    public boolean player(Player player, Runnable task) { return player.getScheduler().execute(plugin, task, null, 1L); }
    public void async(Runnable task) { plugin.getServer().getAsyncScheduler().runNow(plugin, ignored -> task.run()); }
}
