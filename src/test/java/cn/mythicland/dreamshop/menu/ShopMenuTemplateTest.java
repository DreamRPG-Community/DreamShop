package cn.mythicland.dreamshop.menu;

import cn.mythicland.dreamlib.menu.MenuSelection;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the shared DreamChest-style selection lore contract used by DreamShop menus.
 */
class ShopMenuTemplateTest {

    @Test
    void selectedOptionUsesGreenTriangleAndUnselectedOptionsStayGray() {
        List<String> lore = MenuSelection.lore(
                "选择交易类型。",
                List.of("出售商店", "收购商店"),
                1,
                List.of()
        );

        assertTrue(lore.contains("&7  &7出售商店"));
        assertTrue(lore.contains("&a▶ &a收购商店"));
        assertEquals(1, MenuSelection.direction(ClickType.LEFT));
        assertEquals(-1, MenuSelection.direction(ClickType.RIGHT));
    }
}
