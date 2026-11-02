package cn.mythicland.dreamshop.integration;

import cn.mythicland.dreamrpg.api.CoinService;
import cn.mythicland.framework.component.InjectComponent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Narrow adapter around DreamRPG's authoritative coin service.
 */
@InjectComponent
public final class CoinLedger {

    private final CoinService service;

    /**
     * Resolves the already-enabled DreamRPG service.
     *
     * @param plugin DreamShop plugin
     */
    public CoinLedger(JavaPlugin plugin) {
        RegisteredServiceProvider<CoinService> registration = plugin.getServer()
                .getServicesManager()
                .getRegistration(CoinService.class);
        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException("DreamRPG CoinService is not registered");
        }
        service = Objects.requireNonNull(registration.getProvider(), "CoinService provider");
    }

    public BigDecimal balance(UUID player) {
        return service.balance(Objects.requireNonNull(player, "player"));
    }

    public boolean has(UUID player, BigDecimal amount) {
        return service.has(Objects.requireNonNull(player, "player"), Objects.requireNonNull(amount, "amount"));
    }

    public void deposit(UUID player, BigDecimal amount, String source) {
        service.deposit(player, amount, source);
    }

    public void withdraw(UUID player, BigDecimal amount, String source) {
        service.withdraw(player, amount, source);
    }
}
