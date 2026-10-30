package cn.mythicland.dreamshop.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable confirmation token returned by a previous trade preview.
 */
public record TradeConfirmation(UUID token, UUID player) {

    public TradeConfirmation {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(player, "player");
    }
}
