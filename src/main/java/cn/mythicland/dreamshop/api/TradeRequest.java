package cn.mythicland.dreamshop.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable request to preview a quantity trade.
 */
public record TradeRequest(ShopId shopId, UUID player, int quantity) {

    public TradeRequest {
        Objects.requireNonNull(shopId, "shopId");
        Objects.requireNonNull(player, "player");
        if (quantity < 1) throw new IllegalArgumentException("Quantity must be positive");
    }
}
