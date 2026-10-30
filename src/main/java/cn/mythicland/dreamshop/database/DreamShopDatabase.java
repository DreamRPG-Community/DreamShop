package cn.mythicland.dreamshop.database;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamlib.bootstrap.DreamLibPluginLifecycle;
import cn.mythicland.dreamlib.bootstrap.annotation.LifecycleComponent;
import cn.mythicland.dreamlib.database.DatabaseDrivers;
import cn.mythicland.dreamlib.database.MigrationRunner;
import cn.mythicland.dreamlib.database.MigrationSpec;
import cn.mythicland.dreamlib.database.SqliteDatabase;
import cn.mythicland.dreamlib.library.LibraryLoadResult;
import cn.mythicland.dreamlib.library.LibraryService;
import cn.mythicland.dreamshop.config.DreamShopConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * Owns the independent DreamShop SQLite driver and database lifecycle.
 */
@LifecycleComponent
public final class DreamShopDatabase implements DreamLibPluginLifecycle {

    private final JavaPlugin plugin;
    private final DreamLibApi lib;
    private final DreamShopConfiguration configuration;
    private LibraryLoadResult libraries;
    private SqliteDatabase database;

    /**
     * Creates the database context without touching the filesystem.
     *
     * @param plugin        owning plugin
     * @param lib           shared Lib facade
     * @param configuration bound configuration
     */
    public DreamShopDatabase(
            JavaPlugin plugin,
            DreamLibApi lib,
            DreamShopConfiguration configuration
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lib = Objects.requireNonNull(lib, "lib");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    /**
     * Loads the verified driver, opens the plugin-owned database, and applies migrations.
     */
    @Override
    public void enable() {
        libraries = lib.libraryService().load(
                plugin,
                List.of(DatabaseDrivers.SQLITE),
                LibraryService.DEFAULT_REPOSITORY
        );
        try {
            Path databaseFile = plugin.getDataFolder().toPath()
                    .toAbsolutePath()
                    .normalize()
                    .resolve(configuration.snapshot().databaseFile())
                    .normalize();
            Path root = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
            if (!databaseFile.startsWith(root) || databaseFile.equals(root)) {
                throw new IllegalStateException("DreamShop database path escapes its data directory");
            }
            database = lib.databaseService().openSqlite(
                    plugin,
                    databaseFile,
                    DatabaseDrivers.SQLITE_CLASS,
                    libraries.classLoader()
            );
            new MigrationRunner().migrate(
                    database,
                    plugin,
                    List.of(
                            new MigrationSpec(1, "db/sqlite/V1__create_shops.sql"),
                            new MigrationSpec(2, "db/sqlite/V2__add_managed_stock.sql"),
                            new MigrationSpec(3, "db/sqlite/V3__add_managed_stock_payload.sql"),
                            new MigrationSpec(4, "db/sqlite/V4__add_shop_position_index.sql"),
                            new MigrationSpec(5, "db/sqlite/V5__create_transactions.sql"),
                            new MigrationSpec(6, "db/sqlite/V6__add_transaction_index.sql")
                    )
            );
        } catch (IOException | SQLException | RuntimeException exception) {
            closeResources(exception);
            throw new IllegalStateException("Failed to initialize DreamShop database", exception);
        }
    }

    /**
     * Keeps the database open during a normal configuration reload.
     */
    @Override
    public void reload() {
        if (database == null) throw new IllegalStateException("DreamShop database is not open");
    }

    /**
     * Returns the open database to dependent storage components.
     *
     * @return open SQLite database
     */
    public SqliteDatabase database() {
        if (database == null) throw new IllegalStateException("DreamShop database is not open");
        return database;
    }

    /**
     * Closes the database before releasing the driver class loader.
     */
    @Override
    public void disable() {
        closeResources(null);
    }

    private void closeResources(Throwable failure) {
        if (database != null) {
            try {
                database.close();
            } catch (SQLException exception) {
                if (failure != null) failure.addSuppressed(exception);
                else plugin.getLogger().warning("Failed to close DreamShop database: " + exception.getMessage());
            }
            database = null;
        }
        if (libraries != null) {
            try {
                libraries.close();
            } catch (IOException exception) {
                if (failure != null) failure.addSuppressed(exception);
                else plugin.getLogger().warning("Failed to close DreamShop libraries: " + exception.getMessage());
            }
            libraries = null;
        }
    }
}
