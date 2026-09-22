package net.tfminecraft.tlibs.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteDatabaseTest {
    @Test
    void openInsertQueryClose(@TempDir Path tempDir) throws Exception {
        File dbFile = tempDir.resolve("test.db").toFile();

        SqliteProvider.ensureDriverLoaded();
        assertTrue(SqliteProvider.isAvailable());

        SqliteDatabase database = new SqliteDatabase(dbFile);
        database.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, v TEXT)");
        database.executeUpdate("INSERT INTO t (v) VALUES (?)", "ok");

        try (PreparedStatement statement = database.getConnection().prepareStatement("SELECT v FROM t WHERE v = ?")) {
            statement.setString(1, "ok");
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals("ok", result.getString("v"));
            }
        }

        database.close();
    }
}
