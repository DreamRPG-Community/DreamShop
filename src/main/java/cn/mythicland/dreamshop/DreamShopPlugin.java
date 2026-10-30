package cn.mythicland.dreamshop;

import cn.mythicland.dreamlib.bootstrap.DreamLibManagedPlugin;


/**
 * Minimal Bukkit entry point for the Lib-managed DreamShop component graph.
 */
public final class DreamShopPlugin extends DreamLibManagedPlugin {

    private static final String COMPONENT_PACKAGE = "cn.mythicland.dreamshop";

    public DreamShopPlugin() {
        super(COMPONENT_PACKAGE);
    }

    /**
     * Reloads DreamShop configuration through Lib's lifecycle graph.
     */
    public void reloadDreamShop() {
        bootstrap(COMPONENT_PACKAGE).reload();
    }
}
