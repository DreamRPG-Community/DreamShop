package cn.mythicland.dreamshop.api;

import java.util.Objects;

/**
 * Immutable result of a shop creation request.
 */
public record ShopCreateResult(
        Status status,
        ShopSnapshot snapshot,
        String message
) {

    public ShopCreateResult {
        status = Objects.requireNonNull(status, "status");
        message = Objects.requireNonNull(message, "message");
    }

    /**
     * Creation result status.
     */
    public enum Status {
        CREATED,
        CONFLICT,
        REJECTED,
        FAILED
    }
}
