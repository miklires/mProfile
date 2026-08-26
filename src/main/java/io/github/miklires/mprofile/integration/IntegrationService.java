package io.github.miklires.mprofile.integration;

import io.github.miklires.mprofile.MProfilePlugin;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

public final class IntegrationService {
    private final MProfilePlugin plugin;

    public IntegrationService(MProfilePlugin plugin) { this.plugin = plugin; }

    public IntegrationSummary read(UUID playerId) {
        return new IntegrationSummary(reputation(playerId), badges(playerId), color(playerId));
    }

    private String reputation(UUID playerId) {
        Object service = service("mReputation", "io.github.miklires.mreputation.api.ReputationAPI");
        return invoke(service, "get", new Class<?>[]{UUID.class}, playerId).map(String::valueOf).orElse("—");
    }

    private String badges(UUID playerId) {
        Object service = service("mBadges", "io.github.miklires.mbadges.api.MBadgesApi");
        return invoke(service, "render", new Class<?>[]{UUID.class}, playerId).map(String::valueOf)
                .filter(value -> !value.isBlank()).orElse("—");
    }

    private String color(UUID playerId) {
        Object service = service("mColor", "io.github.miklires.mcolor.api.MColorService");
        return invoke(service, "color", new Class<?>[]{UUID.class}, playerId)
                .filter(Optional.class::isInstance).map(Optional.class::cast)
                .filter(Optional::isPresent).map(value -> String.valueOf(value.get())).orElse("—");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object service(String pluginName, String apiName) {
        Plugin provider = plugin.getServer().getPluginManager().getPlugin(pluginName);
        if (provider == null || !provider.isEnabled()) return null;
        try {
            Class api = Class.forName(apiName, false, provider.getClass().getClassLoader());
            return plugin.getServer().getServicesManager().load(api);
        } catch (ReflectiveOperationException | LinkageError exception) {
            return null;
        }
    }

    private Optional<Object> invoke(Object target, String name, Class<?>[] parameterTypes, Object... arguments) {
        if (target == null) return Optional.empty();
        try {
            Method method = target.getClass().getMethod(name, parameterTypes);
            return Optional.ofNullable(method.invoke(target, arguments));
        } catch (ReflectiveOperationException exception) {
            return Optional.empty();
        }
    }
}
