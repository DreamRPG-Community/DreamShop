package cn.mythicland.dreamshop.service;

import cn.mythicland.dreamshop.api.ShopId;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifies a private, plugin-owned stock inventory without backing it with a world container.
 */
final class ShopStockInventoryHolder implements InventoryHolder {

    private final ShopId shopId;
    private final UUID viewer;
    private Inventory inventory;

    ShopStockInventoryHolder(ShopId shopId, UUID viewer) {
        this.shopId = Objects.requireNonNull(shopId, "shopId");
        this.viewer = Objects.requireNonNull(viewer, "viewer");
    }

    ShopId shopId() {
        return shopId;
    }

    UUID viewer() {
        return viewer;
    }

    void attach(Inventory value) {
        inventory = Objects.requireNonNull(value, "value");
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
