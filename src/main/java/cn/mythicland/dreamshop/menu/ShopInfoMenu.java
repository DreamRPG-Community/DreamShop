package cn.mythicland.dreamshop.menu;

import cn.mythicland.dreamlib.menu.AnnotatedMenuView;
import cn.mythicland.dreamlib.menu.MenuButton;
import cn.mythicland.dreamlib.text.LegacyText;
import cn.mythicland.dreamshop.api.ShopId;
import cn.mythicland.dreamshop.api.ShopSnapshot;
import cn.mythicland.dreamshop.service.ShopService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Read-only product preview menu for ordinary players.
 */
public final class ShopInfoMenu extends AnnotatedMenuView {

    private final ShopService service;
    private final ShopId id;

    public ShopInfoMenu(ShopService service, ShopId id) {
        this.service = Objects.requireNonNull(service, "service");
        this.id = Objects.requireNonNull(id, "id");
    }

    @Override
    public String title(Player player) {
        return LegacyText.colorize("&8物品预览");
    }

    @Override
    public int size(Player player) {
        return 27;
    }

    @MenuButton(slot = 13)
    private ItemStack product() {
        ShopSnapshot shop = service.menuSnapshot(id);
        return shop.product().template();
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
