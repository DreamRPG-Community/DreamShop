package cn.mythicland.dreamshop.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable result of a confirmed trade.
 */
public record TradeResult(
        Status status,
        UUID transactionId,
        ShopId shopId,
        UUID player,
        int quantity,
        BigDecimal totalPrice,
        String message,
        Instant completedAt
) {

    public TradeResult {
        status = Objects.requireNonNull(status, "status");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(shopId, "shopId");
        Objects.requireNonNull(player, "player");
        if (quantity < 1) throw new IllegalArgumentException("Quantity must be positive");
        Objects.requireNonNull(totalPrice, "totalPrice");
        message = Objects.requireNonNull(message, "message");
        completedAt = Objects.requireNonNull(completedAt, "completedAt");
    }

    /**
     * Trade result status.
     */
    public enum Status {
        SUCCESS,
        EXPIRED,
        REJECTED,
        FAILED
    }
}
