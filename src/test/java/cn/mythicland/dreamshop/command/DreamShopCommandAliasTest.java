package cn.mythicland.dreamshop.command;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DreamShopCommandAliasTest {
    @Test
    void pluginDescriptorRegistersTheShortAlias() {
        var stream = Objects.requireNonNull(getClass().getResourceAsStream("/plugin.yml"));
        var descriptor = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        assertTrue(descriptor.getStringList("commands.dreamshop.aliases").contains("dshop"));
    }
}
