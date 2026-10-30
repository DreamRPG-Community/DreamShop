package cn.mythicland.dreamshop.api;

import java.util.Objects;

/**
 * Immutable result of a shop deletion request.
 */
public record ShopDeleteResult(
        Status status,
        ShopSnapshot snapshot,
        String message
) {

    public ShopDeleteResult {
        status = Objects.requireNonNull(status, "status");
        message = Objects.requireNonNull(message, "message");
    }

    /**
     * Deletion result status.
     */
    public enum Status {
        DELETED,
        REJECTED,
        FAILED
    }
}
