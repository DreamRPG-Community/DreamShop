package cn.mythicland.dreamshop.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable transaction audit record.
 */
public record TransactionRecord(
        UUID id,
        ShopId shopId,
        UUID player,
        UUID owner,
        ShopType type,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        Status status,
        String failureReason,
        Instant createdAt
) {

    public TransactionRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(shopId, "shopId");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(type, "type");
        if (quantity < 1) throw new IllegalArgumentException("Quantity must be positive");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(totalPrice, "totalPrice");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(failureReason, "failureReason");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * Audit status.
     */
    public enum Status {
        PROCESSING,
        SUCCESS,
        FAILED
    }
}
