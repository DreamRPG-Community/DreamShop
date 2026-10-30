package cn.mythicland.dreamshop.integration;

import cn.mythicland.dreamlib.bootstrap.annotation.InjectComponent;
import cn.mythicland.dreamlib.plugin.PluginLookup;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Optional ServicesManager bridge to WorldProtect's temporary {@code /edit} mode.
 */
@InjectComponent
public final class WorldProtectBridge {

    private static final String API_NAME = "cn.mythicland.dreamworldprotect.api.DreamWorldProtectionApi";

    private final JavaPlugin owner;
    private final Object api;
    private final Method editModeMethod;
    private boolean failureLogged;

    /**
     * Resolves WorldProtect when it is installed and enabled.
     *
     * @param owner DreamShop plugin
     */
    public WorldProtectBridge(JavaPlugin owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
        Object resolvedApi = null;
        Method resolvedEditMode = null;
        Optional<Plugin> worldProtect = new PluginLookup(owner).find("DreamWorld");
        if (worldProtect.isPresent()) {
            try {
                ClassLoader classLoader = worldProtect.orElseThrow().getClass().getClassLoader();
                Class<?> apiType = Class.forName(API_NAME, false, classLoader);
                RegisteredServiceProvider<?> registration = owner.getServer().getServicesManager()
                        .getRegistration(apiType);
                if (registration != null && registration.getProvider() != null) {
                    resolvedApi = registration.getProvider();
                    resolvedEditMode = apiType.getMethod("isEditMode", UUID.class);
                }
            } catch (ClassNotFoundException | NoSuchMethodException exception) {
                owner.getLogger().log(
                        Level.WARNING,
                        "DreamWorld protection is enabled but its edit-mode API is unavailable.",
                        exception
                );
            }
        }
        api = resolvedApi;
        editModeMethod = resolvedEditMode;
    }

    /**
     * Returns whether WorldProtect's optional API was resolved.
     *
     * @return true when the bridge is available
     */
    public boolean available() {
        return api != null && editModeMethod != null;
    }

    /**
     * Returns whether a player is currently in the WorldProtect {@code /edit} mode.
     *
     * @param playerId player UUID
     * @return true when edit mode is enabled; false when WorldProtect is unavailable
     */
    public boolean isEditMode(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!available()) return false;
        try {
            Object result = editModeMethod.invoke(api, playerId);
            if (!(result instanceof Boolean value)) {
                throw new IllegalStateException("DreamWorld protection returned a non-boolean edit-mode value");
            }
            return value;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            if (!failureLogged) {
                failureLogged = true;
                owner.getLogger().log(Level.WARNING, "DreamWorld protection edit-mode lookup failed.", exception);
            }
            return false;
        }
    }
}
