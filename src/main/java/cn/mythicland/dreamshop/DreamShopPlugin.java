package cn.mythicland.dreamshop;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamlib.bootstrap.PluginBootstrap;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Minimal Bukkit entry point for the Lib-managed DreamShop component graph.
 */
public final class DreamShopPlugin extends JavaPlugin {

    private static final String COMPONENT_PACKAGE = "cn.mythicland.dreamshop";

    private PluginBootstrap bootstrap;

    /**
     * Starts DreamShop and its independent database.
     */
    @Override
    @SuppressWarnings("resource")
    public void onEnable() {
        try {
            DreamLibApi lib = DreamLibApi.require(this);
            bootstrap = lib.createPluginBootstrap(this, COMPONENT_PACKAGE);
            bootstrap.enable();
        } catch (RuntimeException exception) {
            getLogger().log(
                    Level.SEVERE,
                    "DreamShop failed to enable: " + DreamLibApi.rootCauseMessage(exception),
                    exception
            );
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /**
     * Closes menus, listeners, services, and the database.
     */
    @Override
    public void onDisable() {
        if (bootstrap != null) bootstrap.disable();
        bootstrap = null;
    }

    /**
     * Reloads DreamShop configuration through Lib's lifecycle graph.
     */
    public void reloadDreamShop() {
        if (bootstrap == null) throw new IllegalStateException("DreamShop bootstrap is unavailable");
        bootstrap.reload();
    }
}
