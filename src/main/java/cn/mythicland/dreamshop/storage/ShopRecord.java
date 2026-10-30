package cn.mythicland.dreamshop.storage;

import cn.mythicland.dreamshop.api.DisplayMode;
import cn.mythicland.dreamshop.api.ShopId;
import cn.mythicland.dreamshop.api.ShopProduct;
import cn.mythicland.dreamshop.api.ShopStatus;
import cn.mythicland.dreamshop.api.ShopType;
import cn.mythicland.dreamshop.api.StockMode;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Main-thread mutable persistence model for one shop.
 */
public final class ShopRecord {

    private final ShopId id;
    private final UUID owner;
    private final boolean adminShop;
    private ItemStack product;
    private String mythicItemId;
    private String mythicRevision;
    private BigDecimal price;
    private ShopType type;
    private StockMode stockMode;
    private DisplayMode displayMode;
    private ShopStatus status;
    private String invalidReason;
    /**
     * Plugin-managed stock contents. A null value means the legacy physical chest has not been
     * migrated yet; once initialized the array length is the virtual chest size.
     */
    private ItemStack[] stockContents;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * Creates a mutable record from detached values.
     */
    public ShopRecord(
            ShopId id,
            UUID owner,
            boolean adminShop,
            ItemStack product,
            String mythicItemId,
            String mythicRevision,
            BigDecimal price,
            ShopType type,
            StockMode stockMode,
            DisplayMode displayMode,
            ShopStatus status,
            String invalidReason,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                id,
                owner,
                adminShop,
                product,
                mythicItemId,
                mythicRevision,
                price,
                type,
                stockMode,
                displayMode,
                status,
                invalidReason,
                null,
                createdAt,
                updatedAt
        );
    }

    /**
     * Creates a record with plugin-managed virtual stock contents.
     */
    public ShopRecord(
            ShopId id,
            UUID owner,
            boolean adminShop,
            ItemStack product,
            String mythicItemId,
            String mythicRevision,
            BigDecimal price,
            ShopType type,
            StockMode stockMode,
            DisplayMode displayMode,
            ShopStatus status,
            String invalidReason,
            ItemStack[] stockContents,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.owner = owner;
        this.adminShop = adminShop;
        this.product = product.clone();
        this.mythicItemId = normalizeText(mythicItemId);
        this.mythicRevision = normalizeText(mythicRevision);
        this.price = price;
        this.type = type;
        this.stockMode = stockMode;
        this.displayMode = displayMode;
        this.status = status;
        this.invalidReason = invalidReason;
        this.stockContents = copyContents(stockContents);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public ShopId id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public boolean adminShop() {
        return adminShop;
    }

    public ItemStack product() {
        return product.clone();
    }

    public void setProduct(ItemStack value) {
        product = value.clone();
    }

    public String mythicItemId() {
        return mythicItemId;
    }

    public void setMythicItemId(String value) {
        mythicItemId = normalizeText(value);
    }

    public String mythicRevision() {
        return mythicRevision;
    }

    public void setMythicRevision(String value) {
        mythicRevision = normalizeText(value);
    }

    public BigDecimal price() {
        return price;
    }

    public void setPrice(BigDecimal value) {
        price = value;
    }

    public ShopType type() {
        return type;
    }

    public void setType(ShopType value) {
        type = value;
    }

    public StockMode stockMode() {
        return stockMode;
    }

    public void setStockMode(StockMode value) {
        stockMode = value;
    }

    public DisplayMode displayMode() {
        return displayMode;
    }

    public void setDisplayMode(DisplayMode value) {
        displayMode = value;
    }

    public ShopStatus status() {
        return status;
    }

    public void setStatus(ShopStatus value) {
        status = value;
    }

    public String invalidReason() {
        return invalidReason;
    }

    public void setInvalidReason(String value) {
        invalidReason = value;
    }

    /**
     * Returns whether this record has completed the one-time migration from its physical chest.
     */
    public boolean hasManagedStock() {
        return stockContents != null;
    }

    /**
     * Returns a detached copy of the virtual stock contents.
     *
     * @return stock contents, or an empty standard chest for an unmigrated record
     */
    public ItemStack[] stockContents() {
        return stockContents == null ? new ItemStack[27] : copyContents(stockContents);
    }

    /**
     * Returns the virtual stock size.
     */
    public int stockSize() {
        return stockContents == null ? 27 : stockContents.length;
    }

    /**
     * Replaces the virtual stock contents with detached item stacks.
     */
    public void setStockContents(ItemStack[] value) {
        if (value == null) throw new IllegalArgumentException("Stock contents cannot be null");
        if (value.length < 9 || value.length > 54 || value.length % 9 != 0) {
            throw new IllegalArgumentException("Stock contents must contain 9 to 54 slots in rows of nine");
        }
        stockContents = copyContents(value);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public void touch() {
        updatedAt = Instant.now();
    }

    /**
     * Builds an immutable public product snapshot.
     *
     * @return detached product
     */
    public ShopProduct productSnapshot() {
        return new ShopProduct(product(), mythicItemId, mythicRevision);
    }

    private static String normalizeText(String value) {
        if (value == null) return null;
        String text = value.trim();
        return text.isBlank() ? null : text;
    }

    private static ItemStack[] copyContents(ItemStack[] contents) {
        if (contents == null) return null;
        ItemStack[] copy = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            copy[index] = contents[index] == null ? null : contents[index].clone();
        }
        return copy;
    }
}
