package cn.mythicland.dreamshop.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable public snapshot of a shop.
 *
 * @param id             block identifier
 * @param owner          owner UUID, or null for an administrator system shop
 * @param adminShop      whether the shop is owned by the system
 * @param product        product identity and template
 * @param price          fixed unit price
 * @param type           sell or buy
 * @param stockMode      managed or unlimited inventory
 * @param displayMode    visual display setting
 * @param status         current health state
 * @param invalidReason  reason when the shop is invalid
 * @param stock          currently available matching items
 * @param createdAt      creation time
 * @param updatedAt      last persistence time
 */
public record ShopSnapshot(
        ShopId id,
        UUID owner,
        boolean adminShop,
        ShopProduct product,
        BigDecimal price,
        ShopType type,
        StockMode stockMode,
        DisplayMode displayMode,
        ShopStatus status,
        String invalidReason,
        int stock,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Validates and detaches the snapshot.
     */
    public ShopSnapshot {
        id = Objects.requireNonNull(id, "id");
        product = Objects.requireNonNull(product, "product");
        price = Objects.requireNonNull(price, "price");
        type = Objects.requireNonNull(type, "type");
        stockMode = Objects.requireNonNull(stockMode, "stockMode");
        displayMode = Objects.requireNonNull(displayMode, "displayMode");
        status = Objects.requireNonNull(status, "status");
        invalidReason = Objects.requireNonNull(invalidReason, "invalidReason");
        if (price.signum() <= 0) throw new IllegalArgumentException("Shop price must be positive");
        if (stock < 0) throw new IllegalArgumentException("Shop stock cannot be negative");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
