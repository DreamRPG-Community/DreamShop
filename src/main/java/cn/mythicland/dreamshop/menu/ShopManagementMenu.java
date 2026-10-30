package cn.mythicland.dreamshop.menu;

import cn.mythicland.dreamlib.menu.*;
import cn.mythicland.dreamlib.text.LegacyText;
import cn.mythicland.dreamshop.api.*;
import cn.mythicland.dreamshop.service.ShopService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;

/**
 * DreamChest-style fixed 27-slot management menu for one shop.
 */
public final class ShopManagementMenu extends AnnotatedMenuView {

    private static final int TRADE_TYPE_SLOT = 10;
    private static final int PRICE_SLOT = 11;
    private static final int STOCK_MODE_SLOT = 12;
    private static final int STOCK_MANAGEMENT_SLOT = 13;
    private static final int DELETE_SLOT = 16;
    private static final int BACK_SLOT = 26;

    private final ShopService service;
    private final ShopId id;
    private boolean deleteArmed;

    public ShopManagementMenu(ShopService service, ShopId id) {
        this.service = Objects.requireNonNull(service, "service");
        this.id = Objects.requireNonNull(id, "id");
    }

    @Override
    public String title(Player player) {
        return LegacyText.colorize("&8DreamShop 管理");
    }

    @Override
    public int size(Player player) {
        return 27;
    }

    @MenuButton(slot = TRADE_TYPE_SLOT)
    private ItemStack tradeTypeButton() {
        ShopSnapshot shop = service.menuSnapshot(id);
        return MenuSelection.item(
                Material.EMERALD,
                "&a交易类型",
                "选择商店的交易方向",
                List.of(ShopType.SELLING.displayName(), ShopType.BUYING.displayName()),
                shop.type().ordinal(),
                List.of()
        );
    }

    @MenuButton(slot = PRICE_SLOT)
    private ItemStack priceButton() {
        ShopSnapshot shop = service.menuSnapshot(id);
        return MenuItems.button(Material.GOLD_INGOT, "&a商品价格", List.of(
                "&7每件固定价格: &f" + shop.price().toPlainString(),
                "",
                "&e点击输入新价格!"
        ));
    }

    @MenuButton(slot = STOCK_MODE_SLOT)
    private ItemStack stockModeButton() {
        ShopSnapshot shop = service.menuSnapshot(id);
        return MenuSelection.item(
                Material.CHEST,
                "&a库存模式",
                "选择商店的库存规则",
                List.of(StockMode.NORMAL.displayName(), StockMode.UNLIMITED.displayName()),
                shop.stockMode().ordinal(),
                List.of()
        );
    }

    @MenuButton(slot = STOCK_MANAGEMENT_SLOT)
    private ItemStack stockManagementButton() {
        if (service.menuSnapshot(id).stockMode() == StockMode.UNLIMITED) return new ItemStack(Material.AIR);
        return MenuItems.button(Material.CHEST, "&a库存管理", List.of(
                "&7打开库存界面进行管理",
                "",
                "&e点击打开!"
        ));
    }

    @MenuButton(slot = DELETE_SLOT)
    private ItemStack deleteButton() {
        return MenuItems.button(
                Material.REDSTONE_BLOCK,
                "&c删除商店",
                List.of(
                        "&7删除后保留交易记录",
                        "",
                        deleteArmed ? "&c再次点击以确认!" : "&e点击操作!"
                ));
    }

    @MenuButton(slot = BACK_SLOT)
    private ItemStack backButton() {
        return MenuItems.button(Material.ARROW, "&e返回总管理面板", List.of(
                "&7返回总管理面板"
        ));
    }

    @MenuAction(
            slot = TRADE_TYPE_SLOT,
            clicks = {ClickType.LEFT, ClickType.RIGHT},
            playClickSound = true
    )
    private void cycleTradeType(Player player, ClickType click) {
        service.cycleType(player, id, MenuSelection.direction(click) > 0);
    }

    @MenuAction(
            slot = PRICE_SLOT,
            clicks = {ClickType.LEFT, ClickType.RIGHT},
            playClickSound = false
    )
    private void changePrice(Player player) {
        service.beginPriceInput(player, id);
    }

    @MenuAction(
            slot = STOCK_MODE_SLOT,
            clicks = {ClickType.LEFT, ClickType.RIGHT},
            playClickSound = true
    )
    private void cycleStockMode(Player player, ClickType click) {
        service.cycleStockMode(player, id, MenuSelection.direction(click) > 0);
    }

    @MenuAction(
            slot = STOCK_MANAGEMENT_SLOT,
            clicks = {ClickType.LEFT, ClickType.RIGHT},
            playClickSound = false
    )
    private void openStock(Player player) {
        service.openStock(player, id);
    }

    @MenuAction(
            slot = DELETE_SLOT,
            clicks = {ClickType.LEFT, ClickType.RIGHT},
            playClickSound = true
    )
    private void delete(Player player, cn.mythicland.dreamlib.menu.MenuService menus) {
        if (!deleteArmed) {
            deleteArmed = true;
            menus.refresh(player);
            return;
        }
        ShopDeleteResult result = service.delete(player, id);
        player.sendMessage(LegacyText.colorize(result.status() == ShopDeleteResult.Status.DELETED
                ? "&a商店已删除。" : "&c" + result.message()));
        service.closePlayer(player);
    }

    @MenuAction(
            slot = BACK_SLOT,
            clicks = {ClickType.LEFT, ClickType.RIGHT},
            playClickSound = false
    )
    private void back(Player player) {
        service.openAdminOverview(player, id);
    }

    @Override
    public void onClose(Player player, Inventory inventory) {
        service.closeManagement(player, id);
    }

    @Override
    public void onQuit(Player player, Inventory inventory) {
        service.closeManagement(player, id);
    }
}
