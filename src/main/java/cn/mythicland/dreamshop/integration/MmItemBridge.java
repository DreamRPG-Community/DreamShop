package cn.mythicland.dreamshop.integration;

import cn.mythicland.framework.validation.TextValidation;
import cn.mythicland.framework.component.InjectComponent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;

/**
 * Optional ServicesManager bridge to DreamRPG item service without linking the plugin at class load time.
 */
@InjectComponent
public final class MmItemBridge {

    private static final String API_NAME = "cn.mythicland.dreamrpgitems.api.DreamRPGItemApi";
    private static final String MODE_NAME = "cn.mythicland.dreamrpgitems.api.MythicItemRefreshMode";

    private final Object api;
    private final Method identifyMethod;
    private final Method refreshMethod;
    private final Object existingInstanceMode;
    private final ClassLoader apiClassLoader;

    /**
     * Resolves DreamRPG item service when it is installed, otherwise remains unavailable.
     *
     * @param plugin DreamShop plugin
     */
    public MmItemBridge(JavaPlugin plugin) {
        Object resolvedApi = null;
        Method resolvedIdentify = null;
        Method resolvedRefresh = null;
        Object resolvedMode = null;
        try {
            Plugin addon = plugin.getServer().getPluginManager().getPlugin("DreamRPG");
            if (addon == null || !addon.isEnabled()) throw new IllegalStateException("DreamRPG items is unavailable");
            ClassLoader addonClassLoader = addon.getClass().getClassLoader();
            Class<?> apiType = Class.forName(API_NAME, false, addonClassLoader);
            RegisteredServiceProvider<?> registration = plugin.getServer().getServicesManager()
                    .getRegistration(apiType);
            if (registration != null && registration.getProvider() != null) {
                Class<?> modeType = Class.forName(MODE_NAME, false, addonClassLoader);
                resolvedApi = registration.getProvider();
                resolvedIdentify = apiType.getMethod("identify", ItemStack.class);
                resolvedRefresh = apiType.getMethod("refresh", ItemStack.class, modeType);
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object mode = Enum.valueOf((Class) modeType, "EXISTING_INSTANCE");
                resolvedMode = mode;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            resolvedApi = null;
            resolvedIdentify = null;
            resolvedRefresh = null;
            resolvedMode = null;
        }
        api = resolvedApi;
        identifyMethod = resolvedIdentify;
        refreshMethod = resolvedRefresh;
        existingInstanceMode = resolvedMode;
        apiClassLoader = api == null ? null : api.getClass().getClassLoader();
    }

    /**
     * Returns whether the optional MM API is available.
     *
     * @return true when resolved
     */
    public boolean available() {
        return api != null;
    }

    /**
     * Returns the optional addon's class loader for reflective event lookup.
     *
     * @return add-on loader, or null when unavailable
     */
    public ClassLoader classLoader() {
        return apiClassLoader;
    }

    /**
     * Identifies an MM-marked item through the public API.
     *
     * @param item item to inspect
     * @return identity when marked
     */
    public Optional<Identity> identify(ItemStack item) {
        if (!available() || item == null) return Optional.empty();
        try {
            Object result = invoke(identifyMethod, item);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) return Optional.empty();
            Object identity = optional.get();
            Method internalName = identity.getClass().getMethod("internalName");
            Method revision = identity.getClass().getMethod("revision");
            return Optional.of(new Identity(
                    String.valueOf(internalName.invoke(identity)),
                    String.valueOf(revision.invoke(identity))
            ));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Refreshes an existing MM instance, preserving its stack amount.
     *
     * @param item stored item
     * @return refresh outcome
     */
    public RefreshOutcome refresh(ItemStack item) {
        if (!available() || item == null) return new RefreshOutcome(Status.UNMANAGED, item);
        try {
            Object result = invoke(refreshMethod, item, existingInstanceMode);
            Method statusMethod = result.getClass().getMethod("status");
            Method itemMethod = result.getClass().getMethod("item");
            Status status = Status.valueOf(String.valueOf(statusMethod.invoke(result)));
            Object replacement = itemMethod.invoke(result);
            return new RefreshOutcome(status, replacement instanceof ItemStack stack ? stack.clone() : null);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return new RefreshOutcome(Status.STALE, null);
        }
    }

    private Object invoke(Method method, Object... arguments) throws ReflectiveOperationException {
        try {
            return method.invoke(api, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw exception;
        }
    }

    /**
     * Detached identity used by the shop record.
     *
     * @param internalName MM item ID
     * @param revision     source revision
     */
    public record Identity(String internalName, String revision) {

        public Identity {
            internalName = TextValidation.requireText(internalName, "internalName");
            revision = TextValidation.requireText(revision, "revision");
        }

    }

    /**
     * Minimal reflection-safe refresh status.
     */
    public enum Status {
        UNMANAGED,
        CURRENT,
        UPDATED,
        STALE
    }

    /**
     * Detached refresh result.
     *
     * @param status      refresh status
     * @param replacement replacement stack when updated
     */
    public record RefreshOutcome(Status status, ItemStack replacement) {

        public RefreshOutcome {
            status = Objects.requireNonNull(status, "status");
            replacement = replacement == null ? null : replacement.clone();
        }

        @Override
        public ItemStack replacement() {
            return replacement == null ? null : replacement.clone();
        }
    }
}
