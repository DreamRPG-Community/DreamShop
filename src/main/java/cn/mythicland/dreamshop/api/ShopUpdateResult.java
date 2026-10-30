package cn.mythicland.dreamshop.api;

import java.util.Objects;

/**
 * Immutable result of a shop update request.
 */
public record ShopUpdateResult(
        Status status,
        ShopSnapshot snapshot,
        String message
) {

    public ShopUpdateResult {
        status = Objects.requireNonNull(status, "status");
        message = Objects.requireNonNull(message, "message");
    }

    /**
     * Update result status.
     */
    public enum Status {
        UPDATED,
        CONFLICT,
        REJECTED,
        FAILED
    }
}
