package cn.mythicland.dreamshop.service;

import cn.mythicland.dreamshop.integration.MmItemBridge;
import cn.mythicland.dreamshop.storage.ShopRecord;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Performs strict product matching while giving DreamRPG item service identities priority.
 */
final class ItemMatcher {

    private ItemMatcher() {
    }

    static boolean matches(ItemStack candidate, ShopRecord record, MmItemBridge mm) {
        if (isEmpty(candidate)) return false;
        Optional<MmItemBridge.Identity> candidateIdentity = mm.identify(candidate);
        if (record.mythicItemId() != null) {
            if (candidateIdentity.isEmpty()) return false;
            MmItemBridge.Identity identity = candidateIdentity.orElseThrow();
            return record.mythicItemId().equalsIgnoreCase(identity.internalName());
        }
        if (candidateIdentity.isPresent()) return false;
        return candidate.isSimilar(record.product());
    }

    static int count(Inventory inventory, ShopRecord record, MmItemBridge mm) {
        int total = 0;
        for (ItemStack item : inventory.getContents()) {
            if (!matches(item, record, mm)) continue;
            total = Math.addExact(total, item.getAmount());
        }
        return total;
    }

    static int space(Inventory inventory, ShopRecord record, MmItemBridge mm) {
        int total = 0;
        for (ItemStack item : inventory.getContents()) {
            if (isEmpty(item)) {
                total = Math.addExact(total, record.product().getMaxStackSize());
            } else if (matches(item, record, mm)) {
                total = Math.addExact(total, Math.max(0, item.getMaxStackSize() - item.getAmount()));
            }
        }
        return total;
    }

    static int remove(Inventory inventory, ShopRecord record, MmItemBridge mm, int amount) {
        if (amount < 1) return 0;
        int remaining = amount;
        for (int slot = 0; slot < inventory.getSize() && remaining > 0; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!matches(item, record, mm)) continue;
            int taken = Math.min(remaining, item.getAmount());
            remaining -= taken;
            int left = item.getAmount() - taken;
            if (left < 1) {
                inventory.setItem(slot, null);
            } else {
                ItemStack remainder = item.clone();
                remainder.setAmount(left);
                inventory.setItem(slot, remainder);
            }
        }
        return amount - remaining;
    }

    static int add(Inventory inventory, ShopRecord record, MmItemBridge mm, int amount) {
        if (amount < 1) return 0;
        ItemStack product = record.product();
        product.setAmount(Math.min(amount, product.getMaxStackSize()));
        int remaining = amount;
        while (remaining > 0) {
            int chunk = Math.min(remaining, product.getMaxStackSize());
            ItemStack stack = product.clone();
            stack.setAmount(chunk);
            java.util.Map<Integer, ItemStack> leftovers = inventory.addItem(stack);
            int leftoverAmount = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            remaining -= chunk - leftoverAmount;
            if (leftoverAmount > 0) break;
        }
        return amount - remaining;
    }

    static boolean canAdd(Inventory inventory, ShopRecord record, MmItemBridge mm, int amount) {
        if (amount < 1) return true;
        ItemStack[] contents = inventory.getContents();
        InventorySimulation simulation = new InventorySimulation(contents);
        int remaining = amount;
        for (int slot = 0; slot < simulation.size() && remaining > 0; slot++) {
            ItemStack item = simulation.item(slot);
            if (isEmpty(item)) {
                int moved = Math.min(remaining, record.product().getMaxStackSize());
                simulation.set(slot, record.product(), moved);
                remaining -= moved;
            } else if (matches(item, record, mm)) {
                int capacity = Math.max(0, item.getMaxStackSize() - item.getAmount());
                int moved = Math.min(remaining, capacity);
                item.setAmount(item.getAmount() + moved);
                remaining -= moved;
            }
        }
        return remaining == 0;
    }

    static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == org.bukkit.Material.AIR || item.getAmount() < 1;
    }

    private static final class InventorySimulation {

        private final ItemStack[] contents;

        private InventorySimulation(ItemStack[] source) {
            contents = new ItemStack[source.length];
            for (int index = 0; index < source.length; index++) {
                contents[index] = source[index] == null ? null : source[index].clone();
            }
        }

        private int size() {
            return contents.length;
        }

        private ItemStack item(int slot) {
            return contents[slot];
        }

        private void set(int slot, ItemStack source, int amount) {
            ItemStack copy = source.clone();
            copy.setAmount(amount);
            contents[slot] = copy;
        }
    }
}
