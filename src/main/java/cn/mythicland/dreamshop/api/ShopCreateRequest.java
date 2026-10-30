package cn.mythicland.dreamshop.api;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable request to create a shop.
 */
public final class ShopCreateRequest {

    private final Location location;
    private final UUID owner;
    private final ItemStack product;
    private final BigDecimal price;
    private final ShopType type;
    private final StockMode stockMode;
    private final DisplayMode displayMode;
    private final boolean adminShop;

    private ShopCreateRequest(Builder builder) {
        location = Objects.requireNonNull(builder.location, "location").clone();
        owner = builder.owner;
        product = Objects.requireNonNull(builder.product, "product").clone();
        price = normalizePrice(builder.price);
        type = Objects.requireNonNull(builder.type, "type");
        stockMode = Objects.requireNonNull(builder.stockMode, "stockMode");
        displayMode = Objects.requireNonNull(builder.displayMode, "displayMode");
        adminShop = builder.adminShop;
        if (!adminShop && owner == null) throw new IllegalArgumentException("Player shops need an owner");
        if (product.getAmount() < 1) throw new IllegalArgumentException("Product amount must be positive");
    }

    private static BigDecimal normalizePrice(BigDecimal value) {
        BigDecimal price = Objects.requireNonNull(value, "price").setScale(2, java.math.RoundingMode.UNNECESSARY);
        if (price.signum() <= 0) throw new IllegalArgumentException("Price must be positive");
        return price;
    }

    public Location location() {
        return location.clone();
    }

    public UUID owner() {
        return owner;
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

    public boolean adminShop() {
        return adminShop;
    }

    /**
     * Creates a request builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Named builder for the multi-field creation request.
     */
    public static final class Builder {

        private Location location;
        private UUID owner;
        private ItemStack product;
        private BigDecimal price;
        private ShopType type = ShopType.SELLING;
        private StockMode stockMode = StockMode.NORMAL;
        private DisplayMode displayMode = DisplayMode.SIGN_AND_ITEM;
        private boolean adminShop;

        public Builder location(Location value) {
            location = value;
            return this;
        }

        public Builder owner(UUID value) {
            owner = value;
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

        public Builder adminShop(boolean value) {
            adminShop = value;
            return this;
        }

        public ShopCreateRequest build() {
            return new ShopCreateRequest(this);
        }
    }
}
