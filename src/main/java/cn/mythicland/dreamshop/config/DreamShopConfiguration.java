package cn.mythicland.dreamshop.config;

import cn.mythicland.dreamshop.api.DisplayMode;
import cn.mythicland.framework.bukkit.config.ConfigView;
import cn.mythicland.framework.bukkit.config.ConfigurableComponent;
import cn.mythicland.framework.bukkit.config.annotation.ConfigComponent;
import cn.mythicland.framework.bukkit.config.annotation.ConfigProperty;

import java.util.List;
import java.util.Objects;

/**
 * Loads the small reloadable configuration surface of DreamShop.
 */
@ConfigComponent
public final class DreamShopConfiguration implements ConfigurableComponent {

    private volatile Snapshot snapshot;

    /**
     * Binds and validates the current configuration.
     *
     * @param configuration Lib configuration view
     */
    @Override
    public synchronized void reload(ConfigView configuration) {
        RawSettings raw = Objects.requireNonNull(configuration, "configuration").bind(RawSettings.class);
        if (raw.maximumQuantity() < 1) {
            throw new IllegalStateException("trade.maximum-quantity must be positive");
        }
        List<String> lines = raw.signLines().stream()
                .map(Objects::requireNonNull)
                .toList();
        if (lines.size() != 4) throw new IllegalStateException("display.sign-lines must contain four lines");
        Snapshot next = new Snapshot(
                raw.quantityTimeoutSeconds(),
                raw.previewTimeoutSeconds(),
                raw.maximumQuantity(),
                raw.defaultDisplayMode(),
                lines
        );
        snapshot = next;
    }

    /**
     * Returns the current immutable configuration snapshot.
     *
     * @return configuration snapshot
     */
    public Snapshot snapshot() {
        Snapshot value = snapshot;
        if (value == null) throw new IllegalStateException("DreamShop configuration is not loaded");
        return value;
    }

    private record RawSettings(
            @ConfigProperty(
                    path = "trade.quantity-timeout-seconds",
                    defaultValue = "30",
                    positive = true
            )
            int quantityTimeoutSeconds,
            @ConfigProperty(
                    path = "trade.preview-timeout-seconds",
                    defaultValue = "30",
                    positive = true
            )
            int previewTimeoutSeconds,
            @ConfigProperty(
                    path = "trade.maximum-quantity",
                    defaultValue = "2304",
                    positive = true
            )
            int maximumQuantity,
            @ConfigProperty(
                    path = "display.default-mode",
                    defaultValue = "SIGN_AND_ITEM"
            )
            DisplayMode defaultDisplayMode,
            @ConfigProperty(
                    path = "display.sign-lines",
                    defaultValue = "&c{owner},&5类型:{type-colored} {stock},{item},每件价格: &1&l{price}",
                    trim = false
            )
            List<String> signLines
    ) {
    }

    /**
     * Immutable settings used by services and menus.
     */
    public record Snapshot(
            int quantityTimeoutSeconds,
            int previewTimeoutSeconds,
            int maximumQuantity,
            DisplayMode defaultDisplayMode,
            List<String> signLines
    ) {

        public Snapshot {
            if (quantityTimeoutSeconds < 1 || previewTimeoutSeconds < 1 || maximumQuantity < 1) {
                throw new IllegalArgumentException("DreamShop numeric settings must be positive");
            }
            defaultDisplayMode = Objects.requireNonNull(defaultDisplayMode, "defaultDisplayMode");
            signLines = List.copyOf(Objects.requireNonNull(signLines, "signLines"));
        }
    }
}
