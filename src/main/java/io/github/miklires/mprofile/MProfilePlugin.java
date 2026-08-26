package io.github.miklires.mprofile;

import io.github.miklires.mprofile.api.MProfileApi;
import io.github.miklires.mprofile.command.ProfileCommand;
import io.github.miklires.mprofile.command.ReadyCommand;
import io.github.miklires.mprofile.gui.ProfileGui;
import io.github.miklires.mprofile.message.MessageService;
import io.github.miklires.mprofile.profile.ProfileListener;
import io.github.miklires.mprofile.profile.ProfileService;
import io.github.miklires.mprofile.storage.ProfileRepository;
import io.github.miklires.mprofile.util.PluginScheduler;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class MProfilePlugin extends JavaPlugin {
    private PluginScheduler scheduler;
    private MessageService messages;
    private ProfileRepository repository;
    private volatile BasicCommand command;
    private volatile boolean ready;

    @Override public void onEnable() {
        saveDefaultConfig();
        saveLanguage("en_US");
        saveLanguage("ru_RU");
        getConfig().options().copyDefaults(true);
        saveConfig();
        scheduler = new PluginScheduler(this);
        messages = new MessageService(this);
        registerCommandGateway();
        startMetrics();
        repository = new ProfileRepository(getDataFolder().toPath());
        repository.initialize().whenComplete((ignored, error) -> scheduler.global(() -> finishStart(error)));
        getLogger().info("mProfile " + getPluginMeta().getVersion() + " is starting");
    }

    private void finishStart(Throwable error) {
        if (error != null) {
            getLogger().severe("mProfile could not initialize storage: " + error.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        ProfileService profiles = new ProfileService(this, repository);
        ProfileGui gui = new ProfileGui(this, profiles, messages);
        command = new ProfileCommand(this, profiles, gui, messages);
        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(new ProfileListener(this, profiles), this);
        getServer().getServicesManager().register(MProfileApi.class, new ProfileApiService(profiles, gui),
                this, ServicePriority.Normal);
        getServer().getOnlinePlayers().forEach(player -> scheduler.player(player, () -> profiles.capture(player)));
        new UpdateChecker(this).start();
        ready = true;
        getLogger().info("mProfile is ready");
    }

    @Override public void onDisable() {
        ready = false;
        getServer().getServicesManager().unregisterAll(this);
        if (repository != null) repository.close();
        getLogger().info("mProfile disabled");
    }

    public boolean ready() { return ready; }
    public PluginScheduler scheduler() { return scheduler; }
    public MessageService messages() { return messages; }

    public void reloadRuntime() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        messages.reload();
    }

    private void registerCommandGateway() {
        ReadyCommand gateway = new ReadyCommand(this, () -> command);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register("profile", "Open and edit player profiles", java.util.List.of("mprofile"), gateway));
    }

    private void startMetrics() {
        int id = Math.max(0, getConfig().getInt("metrics.bstats-id", 0));
        if (getConfig().getBoolean("metrics.enabled", true) && id > 0) new Metrics(this, id);
    }

    private void saveLanguage(String language) {
        String path = "lang/" + language + ".yml";
        if (!new java.io.File(getDataFolder(), path).isFile()) saveResource(path, false);
    }
}
