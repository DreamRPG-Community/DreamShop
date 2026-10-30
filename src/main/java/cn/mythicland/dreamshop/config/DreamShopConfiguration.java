package cn.mythicland.dreamshop.config;

import cn.mythicland.dreamlib.bootstrap.annotation.ConfigComponent;
import cn.mythicland.dreamlib.config.ConfigValue;
import cn.mythicland.dreamlib.config.ConfigView;
import cn.mythicland.dreamlib.config.ConfigurableComponent;
import cn.mythicland.dreamshop.api.DisplayMode;

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
                raw.databaseFile(),
                raw.quantityTimeoutSeconds(),
                raw.previewTimeoutSeconds(),
                raw.maximumQuantity(),
                raw.defaultDisplayMode(),
                lines
        );
        Snapshot previous = snapshot;
        if (previous != null && !previous.databaseFile().equals(next.databaseFile())) {
            throw new IllegalStateException("database.file cannot be changed during reload; restart DreamShop");
        }
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
            @ConfigValue(
                    path = "database.file",
                    defaultValue = "dreamshop.db",
                    nonBlank = true
            )
            String databaseFile,
            @ConfigValue(
                    path = "trade.quantity-timeout-seconds",
                    defaultValue = "30",
                    positive = true
            )
            int quantityTimeoutSeconds,
            @ConfigValue(
                    path = "trade.preview-timeout-seconds",
                    defaultValue = "30",
                    positive = true
            )
            int previewTimeoutSeconds,
            @ConfigValue(
                    path = "trade.maximum-quantity",
                    defaultValue = "2304",
                    positive = true
            )
            int maximumQuantity,
            @ConfigValue(
                    path = "display.default-mode",
                    defaultValue = "SIGN_AND_ITEM"
            )
            DisplayMode defaultDisplayMode,
            @ConfigValue(
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
            String databaseFile,
            int quantityTimeoutSeconds,
            int previewTimeoutSeconds,
            int maximumQuantity,
            DisplayMode defaultDisplayMode,
            List<String> signLines
    ) {

        public Snapshot {
            databaseFile = Objects.requireNonNull(databaseFile, "databaseFile");
            if (databaseFile.isBlank() || databaseFile.contains("/") || databaseFile.contains("\\")
                    || databaseFile.contains("..")) {
                throw new IllegalArgumentException("database.file must be a single file name");
            }
            if (quantityTimeoutSeconds < 1 || previewTimeoutSeconds < 1 || maximumQuantity < 1) {
                throw new IllegalArgumentException("DreamShop numeric settings must be positive");
            }
            defaultDisplayMode = Objects.requireNonNull(defaultDisplayMode, "defaultDisplayMode");
            signLines = List.copyOf(Objects.requireNonNull(signLines, "signLines"));
        }
    }
}
