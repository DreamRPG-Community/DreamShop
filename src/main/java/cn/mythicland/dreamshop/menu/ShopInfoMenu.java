package cn.mythicland.dreamshop.menu;

import cn.mythicland.dreamshop.api.ShopId;
import cn.mythicland.dreamshop.api.ShopSnapshot;
import cn.mythicland.dreamshop.service.ShopService;
import cn.mythicland.framework.bukkit.menu.*;
import cn.mythicland.framework.bukkit.text.LegacyText;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Read-only product preview menu for ordinary players.
 */
public final class ShopInfoMenu extends AbstractMenu {

    private final ShopService service;
    private final ShopId id;

    public ShopInfoMenu(ShopService service, ShopId id) {
        this.service = Objects.requireNonNull(service, "service");
        this.id = Objects.requireNonNull(id, "id");
    }

    @Override
    public String buildTitle(Player player) {
        return LegacyText.colorize("&8物品预览");
    }

    @Override
    protected int resolveRowCount(Player player) {
        return 3;
    }

    @MenuSlot(slot = 13)
    private AbstractButton product(Player viewer) {
        ItemStack icon = renderProduct();
        return MenuButtons.display(icon);
    }

    private ItemStack renderProduct() {
        ShopSnapshot shop = service.menuSnapshot(id);
        return shop.product().template();
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
        service.closeInfo(player, id);
    }

    @Override
    public void onQuit(Player player, Inventory inventory) {
        service.closeInfo(player, id);
    }
}
