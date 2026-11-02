package cn.mythicland.dreamshop.database;

import cn.mythicland.framework.database.MigrationRunner;
import cn.mythicland.framework.database.MigrationSpec;
import cn.mythicland.framework.database.SqlDatabase;
import cn.mythicland.framework.database.SqlWork;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SharedMigrationTest {
    @Test
    void pluginsCanApplyTheSameVersionIndependentlyAndRerun() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            SqlDatabase database = new SqlDatabase() {
                @Override
                public <T> T query(SqlWork<T> work) throws SQLException {
                    return work.execute(connection);
                }

                @Override
                public <T> T transaction(SqlWork<T> work) throws SQLException {
                    return work.execute(connection);
                }

                @Override
                public void close() {
                }
            };
            JavaPlugin plugin = mock(JavaPlugin.class);
            when(plugin.getResource("a.sql")).thenAnswer(ignored -> stream("CREATE TABLE a (id INTEGER)"));
            when(plugin.getResource("b.sql")).thenAnswer(ignored -> stream("CREATE TABLE b (id INTEGER)"));
            MigrationRunner first = new MigrationRunner("first_history");
            MigrationRunner second = new MigrationRunner("second_history");
            first.migrate(database, plugin::getResource, List.of(new MigrationSpec(1, "a.sql")));
            second.migrate(database, plugin::getResource, List.of(new MigrationSpec(1, "b.sql")));
            first.migrate(database, plugin::getResource, List.of(new MigrationSpec(1, "a.sql")));
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name IN ('a','b','first_history','second_history')")) {
                assertTrue(rows.next());
                assertEquals(4, rows.getInt(1));
            }
            assertThrows(IllegalArgumentException.class, () -> new MigrationRunner("unsafe;DROP TABLE a"));
        }
    }

    private static ByteArrayInputStream stream(String sql) {
        return new ByteArrayInputStream(sql.getBytes(StandardCharsets.UTF_8));
    }
}
