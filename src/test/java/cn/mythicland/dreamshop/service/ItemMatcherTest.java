package cn.mythicland.dreamshop.service;

import cn.mythicland.dreamshop.integration.MmItemBridge;
import cn.mythicland.dreamshop.storage.ShopRecord;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ItemMatcherTest {
    @Test
    void capacityRequiresActualStackCompatibilityEvenForSameMythicId() {
        ItemStack product = new ItemStack(Material.STONE, 1, (short) 0);
        ItemStack differentMetadata = new ItemStack(Material.STONE, 32, (short) 1);
        ShopRecord record = mock(ShopRecord.class);
        when(record.product()).thenReturn(product.clone());
        when(record.mythicItemId()).thenReturn("example");
        MmItemBridge items = mock(MmItemBridge.class);
        when(items.identify(any())).thenReturn(Optional.of(new MmItemBridge.Identity("example", "revision")));
        Inventory inventory = mock(Inventory.class);
        when(inventory.getContents()).thenReturn(new ItemStack[]{differentMetadata});

        ItemFactory factory = mock(ItemFactory.class);
        when(factory.equals(null, null)).thenReturn(true);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getItemFactory).thenReturn(factory);
            assertTrue(ItemMatcher.matches(differentMetadata, record, items));
            assertFalse(ItemMatcher.canAdd(inventory, record, 1));
            when(inventory.getContents()).thenReturn(new ItemStack[]{new ItemStack(Material.STONE, 32)});
            assertTrue(ItemMatcher.canAdd(inventory, record, 1));
        }
    }
}
