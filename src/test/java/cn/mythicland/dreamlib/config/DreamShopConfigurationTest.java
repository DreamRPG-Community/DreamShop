package cn.mythicland.dreamlib.config;

import cn.mythicland.dreamshop.config.DreamShopConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the sign is rendered from the reloadable configuration template.
 */
class DreamShopConfigurationTest {

    @Test
    void keepsCustomSignLinesAndPlaceholderText() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("database.file", "custom.db");
        configuration.set("display.sign-lines", List.of(
                "&6{item}",
                "&7{owner}",
                "&b{type} {stock}",
                "&e价格 {price}"
        ));
        List<String> warnings = new ArrayList<>();

        DreamShopConfiguration settings = new DreamShopConfiguration();
        settings.reload(new ConfigView(configuration, warnings::add));

        assertEquals(List.of(
                "&6{item}",
                "&7{owner}",
                "&b{type} {stock}",
                "&e价格 {price}"
        ), settings.snapshot().signLines());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void rejectsAConfigurationWithoutExactlyFourSignLines() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("display.sign-lines", List.of("only one line"));

        DreamShopConfiguration settings = new DreamShopConfiguration();

        assertThrows(IllegalStateException.class,
                () -> settings.reload(new ConfigView(configuration, ignored -> {
                })));
    }
}
