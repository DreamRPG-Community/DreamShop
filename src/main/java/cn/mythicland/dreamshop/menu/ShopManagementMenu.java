package cn.mythicland.dreamshop.menu;

import cn.mythicland.dreamshop.api.*;
import cn.mythicland.dreamshop.service.ShopService;
import cn.mythicland.framework.bukkit.menu.*;
import cn.mythicland.framework.bukkit.menu.ClickType;
import cn.mythicland.framework.bukkit.text.LegacyText;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * DreamChest-style fixed 27-slot management menu for one shop.
 */
public final class ShopManagementMenu extends AbstractMenu {

    private static final int TRADE_TYPE_SLOT = 10;
    private static final int PRICE_SLOT = 11;
    private static final int STOCK_MODE_SLOT = 12;
    private static final int STOCK_MANAGEMENT_SLOT = 13;
    private static final int DISPLAY_ITEM_SLOT = 14;
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
    public String buildTitle(Player player) {
        return LegacyText.colorize("&8DreamShop 管理");
    }

    @Override
    protected int resolveRowCount(Player player) {
        return 3;
    }

    @MenuSlot(slot = TRADE_TYPE_SLOT)
    private AbstractButton tradeTypeButton(Player viewer) {
        ItemStack icon = renderTradeTypeButton();
        return MenuButtons.selection(
                icon,
                (player, click, menus) -> {
                    cycleTradeType(player, click);
                    return true;
                }
        );
    }

    private ItemStack renderTradeTypeButton() {
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

    @MenuSlot(slot = PRICE_SLOT)
    private AbstractButton priceButton(Player viewer) {
        ItemStack icon = renderPriceButton();
        return MenuButtons.action(
                icon,
                (player, click, menus) -> {
                    changePrice(player);
                    return true;
                }
        );
    }

    private ItemStack renderPriceButton() {
        ShopSnapshot shop = service.menuSnapshot(id);
        return MenuItems.button(Material.GOLD_INGOT, "&a商品价格", List.of(
                        "&7每件固定价格: &f" + shop.price().toPlainString(),
                        "",
                        "&e点击输入新价格!"
                )
        );
    }

    @MenuSlot(slot = STOCK_MODE_SLOT)
    private AbstractButton stockModeButton(Player viewer) {
        ItemStack icon = renderStockModeButton();
        return MenuButtons.selection(
                icon,
                (player, click, menus) -> {
                    cycleStockMode(player, click);
                    return true;
                }
        );
    }

    private ItemStack renderStockModeButton() {
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

    @MenuSlot(slot = STOCK_MANAGEMENT_SLOT)
    private AbstractButton stockManagementButton(Player viewer) {
        ItemStack icon = renderStockManagementButton();
        return MenuButtons.action(
                icon,
                (player, click, menus) -> {
                    openStock(player);
                    return true;
                }
        );
    }

    private ItemStack renderStockManagementButton() {
        if (service.menuSnapshot(id).stockMode() == StockMode.UNLIMITED) return new ItemStack(Material.AIR);
        return MenuItems.button(Material.CHEST, "&a库存管理", List.of(
                        "&7打开库存界面进行管理",
                        "",
                        "&e点击打开!"
                )
        );
    }

    @MenuSlot(slot = DISPLAY_ITEM_SLOT)
    private AbstractButton displayItemButton(Player viewer) {
        ItemStack icon = renderDisplayItemButton();
        return MenuButtons.selection(
                icon,
                (player, click, menus) -> {
                    toggleDisplayItem(player);
                    return true;
                }
        );
    }

    private ItemStack renderDisplayItemButton() {
        ShopSnapshot shop = service.menuSnapshot(id);
        return MenuSelection.item(
                Material.ITEM_FRAME,
                "&a悬浮物品",
                "选择是否展示商店商品",
                List.of("显示", "隐藏"),
                shop.displayMode() == DisplayMode.SIGN_AND_ITEM ? 0 : 1,
                List.of()
        );
    }

    @MenuSlot(slot = DELETE_SLOT)
    private AbstractButton deleteButton(Player viewer) {
        ItemStack icon = renderDeleteButton();
        return MenuButtons.selection(
                icon,
                (player, click, menus) -> {
                    delete(player, menus);
                    return true;
                }
        );
    }

    private ItemStack renderDeleteButton() {
        return MenuItems.button(
                Material.REDSTONE_BLOCK,
                "&c删除商店",
                List.of(
                        "&7删除后保留交易记录",
                        "",
                        deleteArmed ? "&c再次点击以确认!" : "&e点击操作!"
                )
        );
    }

    @MenuSlot(slot = BACK_SLOT)
    private AbstractButton backButton(Player viewer) {
        ItemStack icon = renderBackButton();
        return MenuButtons.action(
                icon,
                (player, click, menus) -> {
                    back(player);
                    return true;
                }
        );
    }

    private ItemStack renderBackButton() {
        return MenuItems.button(Material.ARROW, "&e返回总管理面板", List.of(
                        "&7返回总管理面板"
                )
        );
    }

    private void cycleTradeType(Player player, ClickType click) {
        service.cycleType(player, id, click == ClickType.LEFT);
    }

    private void changePrice(Player player) {
        service.beginPriceInput(player, id);
    }

    private void cycleStockMode(Player player, ClickType click) {
        service.cycleStockMode(player, id, click == ClickType.LEFT);
    }

    private void openStock(Player player) {
        service.openStock(player, id);
    }

    private void toggleDisplayItem(Player player) {
        service.toggleDisplayItem(player, id);
    }

    private void delete(Player player, MenuManager menus) {
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

    private void back(Player player) {
        service.openAdminOverview(player, id);
    }

    @Override
    public boolean replacesInPlace() {
        return true;
    }

    @Override
    public boolean handlesClicksAtHighestPriority() {
        return true;
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
