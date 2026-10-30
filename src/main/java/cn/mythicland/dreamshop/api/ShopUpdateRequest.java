package cn.mythicland.dreamshop.api;

import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable update request for one existing shop.
 */
public final class ShopUpdateRequest {

    private final ShopId id;
    private final ItemStack product;
    private final BigDecimal price;
    private final ShopType type;
    private final StockMode stockMode;
    private final DisplayMode displayMode;

    private ShopUpdateRequest(Builder builder) {
        id = Objects.requireNonNull(builder.id, "id");
        product = Objects.requireNonNull(builder.product, "product").clone();
        price = Objects.requireNonNull(builder.price, "price").setScale(2, java.math.RoundingMode.UNNECESSARY);
        if (price.signum() <= 0) throw new IllegalArgumentException("Price must be positive");
        type = Objects.requireNonNull(builder.type, "type");
        stockMode = Objects.requireNonNull(builder.stockMode, "stockMode");
        displayMode = Objects.requireNonNull(builder.displayMode, "displayMode");
    }

    public ShopId id() {
        return id;
    }

    public ItemStack product() {
        return product.clone();
    }

    public BigDecimal price() {
        return price;
    }

    public ShopType type() {
        return type;
    }

    public StockMode stockMode() {
        return stockMode;
    }

    public DisplayMode displayMode() {
        return displayMode;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Named builder for a shop update.
     */
    public static final class Builder {

        private ShopId id;
        private ItemStack product;
        private BigDecimal price;
        private ShopType type;
        private StockMode stockMode;
        private DisplayMode displayMode;

        public Builder id(ShopId value) {
            id = value;
            return this;
        }

        public Builder product(ItemStack value) {
            product = value;
            return this;
        }

        public Builder price(BigDecimal value) {
            price = value;
            return this;
        }

        public Builder type(ShopType value) {
            type = value;
            return this;
        }

        public Builder stockMode(StockMode value) {
            stockMode = value;
            return this;
        }

        public Builder displayMode(DisplayMode value) {
            displayMode = value;
            return this;
        }

        public ShopUpdateRequest build() {
            return new ShopUpdateRequest(this);
        }
    }
}
