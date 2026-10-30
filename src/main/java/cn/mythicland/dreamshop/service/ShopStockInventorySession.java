package cn.mythicland.dreamshop.service;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;

/**
 * Tracks one administrator's private stock editor and its last synchronized baseline.
 */
final class ShopStockInventorySession {

    private final ShopStockInventoryHolder holder;
    private final Inventory inventory;
    private ItemStack[] baseline;

    ShopStockInventorySession(ShopStockInventoryHolder holder, Inventory inventory) {
        this.holder = Objects.requireNonNull(holder, "holder");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.baseline = copy(inventory.getContents());
    }

    ShopStockInventoryHolder holder() {
        return holder;
    }

    Inventory inventory() {
        return inventory;
    }

    /**
     * Updates slots that the administrator has not changed since the editor was opened.
     */
    void mergeRefresh(ItemStack[] replacement) {
        if (replacement.length != inventory.getSize()) return;
        ItemStack[] current = inventory.getContents();
        for (int slot = 0; slot < replacement.length; slot++) {
            if (same(current[slot], baseline[slot])) {
                inventory.setItem(slot, cloneOrNull(replacement[slot]));
            }
        }
        baseline = copy(inventory.getContents());
    }

    private static boolean same(ItemStack left, ItemStack right) {
        if (left == null || right == null) return left == right;
        return left.getAmount() == right.getAmount() && left.isSimilar(right);
    }

    private static ItemStack[] copy(ItemStack[] source) {
        ItemStack[] result = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) result[index] = cloneOrNull(source[index]);
        return result;
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return item == null ? null : item.clone();
    }
}
