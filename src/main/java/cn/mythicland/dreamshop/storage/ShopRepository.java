package cn.mythicland.dreamshop.storage;

import cn.mythicland.dreamshop.api.DisplayMode;
import cn.mythicland.dreamshop.api.ShopId;
import cn.mythicland.dreamshop.api.ShopStatus;
import cn.mythicland.dreamshop.api.ShopType;
import cn.mythicland.dreamshop.api.StockMode;
import cn.mythicland.dreamshop.api.TransactionRecord;
import cn.mythicland.dreamshop.database.DreamShopDatabase;
import cn.mythicland.framework.bukkit.item.ItemStackArrayCodec;
import cn.mythicland.framework.component.InjectComponent;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;

/**
 * SQLite persistence boundary for DreamShop. It never opens or reads legacy QuickShop files.
 */
@InjectComponent
public final class ShopRepository {

    private final DreamShopDatabase database;

    /**
     * Creates a repository around Lib's already-open SQLite database.
     *
     * @param database database lifecycle owner
     */
    public ShopRepository(DreamShopDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /**
     * Loads all persisted shop records.
     *
     * @return detached records in coordinate order
     */
    public List<ShopRecord> loadAll() {
        try {
            return database.database().query(connection -> {
                List<ShopRecord> records = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, world, x, y, z, owner, admin_shop, product, mythic_item_id, "
                                + "mythic_revision, price, shop_type, stock_mode, display_mode, status, "
                                + "invalid_reason, created_at, updated_at, stock_size, stock "
                                + "FROM shops ORDER BY world, x, y, z")) {
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) records.add(readShop(result));
                    }
                }
                return List.copyOf(records);
            });
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load DreamShop shops", exception);
        }
    }

    /**
     * Inserts a new shop record.
     *
     * @param record record to insert
     */
    public void insert(ShopRecord record) {
        executeUpdate(record, "INSERT INTO shops (id, world, x, y, z, owner, admin_shop, product, "
                + "stock_size, stock, mythic_item_id, mythic_revision, price, shop_type, stock_mode, "
                + "display_mode, status, invalid_reason, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        );
    }

    /**
     * Updates all mutable fields of one shop.
     *
     * @param record record to update
     */
    public void update(ShopRecord record) {
        try {
            database.database().transaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE shops SET owner=?, admin_shop=?, product=?, stock_size=?, stock=?, "
                                + "mythic_item_id=?, mythic_revision=?, price=?, shop_type=?, stock_mode=?, "
                                + "display_mode=?, status=?, invalid_reason=?, created_at=?, updated_at=? "
                                + "WHERE id=?")) {
                    bindMutable(statement, record);
                    statement.setString(16, record.id().encoded());
                    if (statement.executeUpdate() != 1) throw new IllegalStateException(
                            "DreamShop shop disappeared: " + record.id().encoded());
                }
                return null;
            });
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update DreamShop shop: " + record.id().encoded(), exception);
        }
    }

    /**
     * Retains a deleted shop record for audit and recovery inspection.
     *
     * @param record record to mark deleted
     */
    public void markDeleted(ShopRecord record) {
        record.setStatus(ShopStatus.DELETED);
        record.setInvalidReason("已删除");
        record.touch();
        update(record);
    }

    /**
     * Permanently removes a stale active record whose physical shop anchor no longer exists.
     * Explicit administrator deletions continue to use {@link #markDeleted(ShopRecord)} so their
     * audit records are retained.
     *
     * @param id stale shop identifier
     */
    public void delete(ShopId id) {
        Objects.requireNonNull(id, "id");
        try {
            database.database().transaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM shops WHERE id=?")) {
                    statement.setString(1, id.encoded());
                    statement.executeUpdate();
                }
                return null;
            });
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to remove stale DreamShop shop: " + id.encoded(), exception);
        }
    }

    /**
     * Writes one structured transaction record.
     *
     * @param record transaction record
     */
    public void insertTransaction(TransactionRecord record) {
        try {
            database.database().transaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO transactions (id, shop_id, player, owner, shop_type, quantity, "
                                + "unit_price, total_price, status, failure_reason, created_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET "
                                + "shop_id = excluded.shop_id, player = excluded.player, owner = excluded.owner, "
                                + "shop_type = excluded.shop_type, quantity = excluded.quantity, "
                                + "unit_price = excluded.unit_price, total_price = excluded.total_price, "
                                + "status = excluded.status, failure_reason = excluded.failure_reason, "
                                + "created_at = excluded.created_at")) {
                    statement.setString(1, record.id().toString());
                    statement.setString(2, record.shopId().encoded());
                    statement.setString(3, record.player().toString());
                    setNullableUuid(statement, 4, record.owner());
                    statement.setString(5, record.type().name());
                    statement.setInt(6, record.quantity());
                    statement.setString(7, record.unitPrice().toPlainString());
                    statement.setString(8, record.totalPrice().toPlainString());
                    statement.setString(9, record.status().name());
                    statement.setString(10, record.failureReason());
                    statement.setLong(11, record.createdAt().toEpochMilli());
                    statement.executeUpdate();
                }
                return null;
            });
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to record DreamShop transaction", exception);
        }
    }

    private void executeUpdate(ShopRecord record, String sql) {
        try {
            database.database().transaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, record.id().encoded());
                    statement.setString(2, record.id().world());
                    statement.setInt(3, record.id().x());
                    statement.setInt(4, record.id().y());
                    statement.setInt(5, record.id().z());
                    setNullableUuid(statement, 6, record.owner());
                    statement.setBoolean(7, record.adminShop());
                    statement.setString(8, serializeItem(record.product()));
                    bindStock(statement, 9, record);
                    setNullableString(statement, 11, record.mythicItemId());
                    setNullableString(statement, 12, record.mythicRevision());
                    statement.setString(13, record.price().toPlainString());
                    statement.setString(14, record.type().name());
                    statement.setString(15, record.stockMode().name());
                    statement.setString(16, record.displayMode().name());
                    statement.setString(17, record.status().name());
                    statement.setString(18, record.invalidReason());
                    statement.setLong(19, record.createdAt().toEpochMilli());
                    statement.setLong(20, record.updatedAt().toEpochMilli());
                    statement.executeUpdate();
                }
                return null;
            });
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to create DreamShop shop: " + record.id().encoded(), exception);
        }
    }

    private static void bindMutable(PreparedStatement statement, ShopRecord record) throws SQLException {
        setNullableUuid(statement, 1, record.owner());
        statement.setBoolean(2, record.adminShop());
        statement.setString(3, serializeItem(record.product()));
        bindStock(statement, 4, record);
        setNullableString(statement, 6, record.mythicItemId());
        setNullableString(statement, 7, record.mythicRevision());
        statement.setString(8, record.price().toPlainString());
        statement.setString(9, record.type().name());
        statement.setString(10, record.stockMode().name());
        statement.setString(11, record.displayMode().name());
        statement.setString(12, record.status().name());
        statement.setString(13, record.invalidReason());
        statement.setLong(14, record.createdAt().toEpochMilli());
        statement.setLong(15, record.updatedAt().toEpochMilli());
    }

    private static ShopRecord readShop(ResultSet result) throws SQLException {
        try {
            return new ShopRecord(
                    new ShopId(result.getString("world"), result.getInt("x"), result.getInt("y"), result.getInt("z")),
                    nullableUuid(result.getString("owner")),
                    result.getBoolean("admin_shop"),
                    deserializeItem(result.getString("product")),
                    result.getString("mythic_item_id"),
                    result.getString("mythic_revision"),
                    new BigDecimal(result.getString("price")),
                    ShopType.valueOf(result.getString("shop_type")),
                    StockMode.valueOf(result.getString("stock_mode")),
                    DisplayMode.valueOf(result.getString("display_mode")),
                    ShopStatus.valueOf(result.getString("status")),
                    result.getString("invalid_reason"),
                    deserializeStock(result),
                    Instant.ofEpochMilli(result.getLong("created_at")),
                    Instant.ofEpochMilli(result.getLong("updated_at"))
            );
        } catch (RuntimeException exception) {
            throw new SQLException("Invalid DreamShop shop record", exception);
        }
    }

    private static String serializeItem(ItemStack item) {
        return ItemStackArrayCodec.serialize(new ItemStack[]{item});
    }

    private static ItemStack deserializeItem(String encoded) {
        ItemStack[] items = ItemStackArrayCodec.deserialize(encoded, 1);
        if (items[0] == null) throw new IllegalArgumentException("Serialized product is empty");
        return items[0];
    }

    private static ItemStack[] deserializeStock(ResultSet result) throws SQLException {
        String encoded = result.getString("stock");
        int expectedLength = result.getInt("stock_size");
        if (result.wasNull() || encoded == null || encoded.isBlank()) return null;
        return ItemStackArrayCodec.deserialize(encoded, expectedLength);
    }

    private static void bindStock(PreparedStatement statement, int index, ShopRecord record) throws SQLException {
        if (!record.hasManagedStock()) {
            statement.setNull(index, Types.INTEGER);
            statement.setNull(index + 1, Types.VARCHAR);
            return;
        }
        statement.setInt(index, record.stockSize());
        statement.setString(index + 1, ItemStackArrayCodec.serialize(record.stockContents()));
    }

    private static UUID nullableUuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private static void setNullableUuid(PreparedStatement statement, int index, UUID value) throws SQLException {
        if (value == null) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, value.toString());
    }

    private static void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, value);
    }
}
