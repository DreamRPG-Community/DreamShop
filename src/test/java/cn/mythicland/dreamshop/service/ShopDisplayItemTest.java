package cn.mythicland.dreamshop.service;

import cn.mythicland.dreamshop.api.*;
import cn.mythicland.dreamshop.config.DreamShopConfiguration;
import cn.mythicland.dreamshop.database.DreamShopDatabase;
import cn.mythicland.dreamshop.integration.*;
import cn.mythicland.dreamshop.storage.ShopRecord;
import cn.mythicland.dreamshop.storage.ShopRepository;
import cn.mythicland.framework.bukkit.admin.AdminPanelService;
import cn.mythicland.framework.bukkit.menu.MenuManager;
import cn.mythicland.framework.bukkit.task.PluginTaskScope;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShopDisplayItemTest {
    @Test
    void togglingItemDisplayRemovesAndRestoresTheDisplayAndPersistsTheChoice() throws Exception {
        ShopRepository repository = mock(ShopRepository.class);
        ShopService service = new ShopService(
                mock(JavaPlugin.class), mock(PluginTaskScope.class), mock(MenuManager.class),
                mock(DreamShopConfiguration.class), mock(DreamShopDatabase.class), repository,
                mock(CoinLedger.class), mock(MmItemBridge.class), mock(WorldProtectBridge.class),
                mock(AdminPanelService.class), Logger.getAnonymousLogger()
        );
        ShopId id = new ShopId("world", 1, 64, 2);
        ShopRecord record = new ShopRecord(id, UUID.randomUUID(), false, new ItemStack(Material.DIAMOND), null, null,
                BigDecimal.ONE, ShopType.SELLING, StockMode.NORMAL, DisplayMode.SIGN_AND_ITEM,
                ShopStatus.ACTIVE, "", Instant.now(), Instant.now()
        );
        Field field = ShopService.class.getDeclaredField("records");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") Map<ShopId, ShopRecord> records = (Map<ShopId, ShopRecord>) field.get(service);
        records.put(id, record);
        Player player = mock(Player.class);
        when(player.hasPermission(ShopService.ADMIN_PERMISSION)).thenReturn(true);
        World world = mock(World.class);
        Block chest = mock(Block.class);
        Block front = mock(Block.class);
        when(world.getBlockAt(1, 64, 2)).thenReturn(chest);
        when(chest.getRelative(any(org.bukkit.block.BlockFace.class))).thenReturn(front);
        when(front.getType()).thenReturn(Material.STONE);
        Item item = mock(Item.class);
        UUID itemId = UUID.randomUUID();
        when(item.getUniqueId()).thenReturn(itemId);
        when(world.dropItem(any(), any())).thenReturn(item);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(() -> Bukkit.getEntity(itemId)).thenReturn(item);
            service.toggleDisplayItem(player, id);
            assertEquals(DisplayMode.SIGN, record.displayMode());
            verify(world, never()).dropItem(any(), any());
            service.toggleDisplayItem(player, id);
            assertEquals(DisplayMode.SIGN_AND_ITEM, record.displayMode());
            verify(world).dropItem(any(), any());
            service.toggleDisplayItem(player, id);
            assertEquals(DisplayMode.SIGN, record.displayMode());
            verify(item).remove();
            verify(repository, times(3)).update(record);
        }
    }
}
