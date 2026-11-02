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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShopStockNavigationTest {
    @Test
    void switchingStockViewsSavesPreviousContentsWithoutClosingTheScreen() throws Exception {
        MenuManager menus = mock(MenuManager.class);
        ShopRepository repository = mock(ShopRepository.class);
        ShopService service = new ShopService(
                mock(JavaPlugin.class), mock(PluginTaskScope.class), menus,
                mock(DreamShopConfiguration.class), mock(DreamShopDatabase.class), repository,
                mock(CoinLedger.class), mock(MmItemBridge.class), mock(WorldProtectBridge.class),
                mock(AdminPanelService.class), Logger.getAnonymousLogger()
        );
        ShopId id = new ShopId("world", 1, 64, 2);
        ShopRecord record = new ShopRecord(id, UUID.randomUUID(), false, new ItemStack(Material.DIAMOND), null, null,
                BigDecimal.ONE, ShopType.SELLING, StockMode.NORMAL, DisplayMode.SIGN,
                ShopStatus.ACTIVE, "", Instant.now(), Instant.now()
        );
        record.setStockContents(new ItemStack[27]);
        Field field = ShopService.class.getDeclaredField("records");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") Map<ShopId, ShopRecord> records = (Map<ShopId, ShopRecord>) field.get(service);
        records.put(id, record);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(ShopService.ADMIN_PERMISSION)).thenReturn(true);
        World world = mock(World.class);
        when(player.getLocation()).thenReturn(new Location(world, 1, 64, 2));
        InventoryView view = mock(InventoryView.class);
        when(player.getOpenInventory()).thenReturn(view);
        Inventory first = mock(Inventory.class);
        when(first.getSize()).thenReturn(27);
        ItemStack[] edited = new ItemStack[27];
        edited[0] = new ItemStack(Material.DIAMOND, 5);
        when(first.getContents()).thenReturn(edited);
        when(view.getTopInventory()).thenReturn(first);
        Inventory second = mock(Inventory.class);
        when(second.getContents()).thenReturn(new ItemStack[27]);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), eq(27), anyString()))
                    .thenReturn(first, second);

            service.openStock(player, id);
            service.openStock(player, id);

            verify(player).openInventory(first);
            verify(player).openInventory(second);
            verify(player, never()).closeInventory();
            verify(menus, never()).close(player);
            verify(repository).update(record);
            ArgumentCaptor<ItemStack[]> contents = ArgumentCaptor.forClass(ItemStack[].class);
            verify(second).setContents(contents.capture());
            assertEquals(5, contents.getValue()[0].getAmount());
        }
    }
}
