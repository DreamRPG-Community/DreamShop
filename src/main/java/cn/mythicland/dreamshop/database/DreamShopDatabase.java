package cn.mythicland.dreamshop.database;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.framework.component.LifecycleComponent;
import cn.mythicland.framework.component.ManagedLifecycle;
import cn.mythicland.framework.database.MigrationSpec;
import cn.mythicland.framework.database.SqlDatabase;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Applies shop migrations to the shared database.
 */
@LifecycleComponent
public final class DreamShopDatabase implements ManagedLifecycle {
    private final JavaPlugin plugin;
    private final DreamLibApi lib;
    private SqlDatabase database;

    public DreamShopDatabase(JavaPlugin plugin, DreamLibApi lib) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lib = Objects.requireNonNull(lib, "lib");
    }

    @Override
    public void load() {
        database = lib.sharedDatabase().database();
        try {
            lib.sharedDatabase().migrate(plugin, "dreamshop_schema_history", List.of(
                            new MigrationSpec(1, "V1__create_shops.sql"),
                            new MigrationSpec(2, "V2__add_managed_stock.sql"),
                            new MigrationSpec(3, "V3__add_managed_stock_payload.sql"),
                            new MigrationSpec(4, "V4__add_shop_position_index.sql"),
                            new MigrationSpec(5, "V5__create_transactions.sql"),
                            new MigrationSpec(6, "V6__add_transaction_index.sql")
                    )
            );
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Failed to initialize DreamShop database", e);
        }
    }

    @Override
    public void reload() {
        if (database == null) throw new IllegalStateException("DreamShop database is not open");
    }

    public SqlDatabase database() {
        if (database == null) throw new IllegalStateException("DreamShop database is not open");
        return database;
    }

    @Override
    public void unload() {
        database = null;
    }
}
