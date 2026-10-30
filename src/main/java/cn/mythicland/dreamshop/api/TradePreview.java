package cn.mythicland.dreamshop.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable trade preview with a one-time confirmation token.
 */
public record TradePreview(
        UUID token,
        ShopSnapshot shop,
        UUID player,
        int quantity,
        BigDecimal totalPrice,
        BigDecimal playerBalanceBefore,
        BigDecimal playerBalanceAfter,
        int stockBefore,
        int stockAfter,
        boolean canCommit,
        String message,
        Instant expiresAt
) {

    public TradePreview {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(shop, "shop");
        Objects.requireNonNull(player, "player");
        if (quantity < 1) throw new IllegalArgumentException("Quantity must be positive");
        Objects.requireNonNull(totalPrice, "totalPrice");
        Objects.requireNonNull(playerBalanceBefore, "playerBalanceBefore");
        Objects.requireNonNull(playerBalanceAfter, "playerBalanceAfter");
        if (stockBefore < 0 || stockAfter < 0) throw new IllegalArgumentException("Stock cannot be negative");
        message = Objects.requireNonNull(message, "message");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
