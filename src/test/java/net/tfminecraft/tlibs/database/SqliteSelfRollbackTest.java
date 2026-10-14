package net.tfminecraft.tlibs.database;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteSelfRollbackTest {
    @TempDir Path root;
    @BeforeEach void initializeDriver() { SqliteProvider.ensureDriverLoaded(); }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next()); return rows.getString(1);
        }
    }

    private static List<String> rows(Connection connection) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement statement = connection.createStatement(); var rows = statement.executeQuery("SELECT value FROM entries ORDER BY rowid")) {
            while (rows.next()) values.add(rows.getString(1));
        }
        return values;
    }

    private static void reopenRows(Path file, String... expected) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file)) {
            assertEquals(List.of(expected), rows(connection));
        }
    }

    private static void setup(SqliteDatabase database) {
        database.execute("CREATE TABLE entries (value TEXT UNIQUE)");
        database.executeUpdate("INSERT INTO entries VALUES (?)", "seed");
    }

    @Test void fullDatabaseRecoversOnSameConnectionWithWalForeignKeysAndTimeoutPreserved() throws Exception {
        Path file = root.resolve("full.db");
        SqliteDatabase database = new SqliteDatabase(file.toFile());
        try {
            database.execute("PRAGMA journal_mode=WAL");
            database.execute("PRAGMA foreign_keys=ON");
            database.execute("PRAGMA busy_timeout=5000");
            setup(database);
            Connection original = database.getConnection();
            database.execute("PRAGMA max_page_count=" + scalar(original, "PRAGMA page_count"));
            AtomicReference<SqliteDatabaseException> bodyFailure = new AtomicReference<>();
            SqliteDatabaseException failure = assertThrows(SqliteDatabaseException.class, () -> database.runTransaction(connection -> {
                database.executeUpdate("INSERT INTO entries VALUES (?)", "partial");
                try {
                    for (int i = 0; i < 1000; i++) database.executeUpdate("INSERT INTO entries VALUES (?)", "x".repeat(2000) + i);
                } catch (SqliteDatabaseException error) { bodyFailure.set(error); throw error; }
            }));
            assertSame(bodyFailure.get(), failure, "SQL helper's runtime wrapper must remain primary");
            assertEquals(13, assertInstanceOf(SQLException.class, failure.getCause()).getErrorCode(), "SQLITE_FULL");
            assertEquals(1, failure.getSuppressed().length);
            assertSame(original, database.getConnection());
            assertTrue(original.getAutoCommit());
            assertEquals(List.of("seed"), rows(original));
            assertEquals("wal", scalar(original, "PRAGMA journal_mode"));
            assertEquals("1", scalar(original, "PRAGMA foreign_keys"));
            assertEquals("5000", scalar(original, "PRAGMA busy_timeout"));
            database.execute("PRAGMA max_page_count=1000000");
            database.runTransaction(connection -> database.executeUpdate("INSERT INTO entries VALUES (?)", "after-space-freed"));
            assertSame(original, database.getConnection());
            assertTrue(original.getAutoCommit());
        } finally { database.close(); }
        reopenRows(file, "seed", "after-space-freed");
    }

    @Test void rollbackConflictAndTriggerRecoverWhileAbortRemainsStatementLevel() throws Exception {
        for (String mode : List.of("OR ROLLBACK", "ROLLBACK", "ABORT")) {
            Path file = root.resolve(mode.replace(' ', '-') + ".db");
            SqliteDatabase database = new SqliteDatabase(file.toFile());
            try {
                setup(database);
                if (!mode.equals("OR ROLLBACK")) database.execute("CREATE TRIGGER reject_bad BEFORE INSERT ON entries WHEN NEW.value='bad' BEGIN SELECT RAISE(" + mode + ", 'bad row'); END");
                Connection original = database.getConnection();
                AtomicReference<SqliteDatabaseException> bodyFailure = new AtomicReference<>();
                SqliteDatabaseException failure = assertThrows(SqliteDatabaseException.class, () -> database.runTransaction(connection -> {
                    database.executeUpdate("INSERT INTO entries VALUES (?)", "partial");
                    try {
                        database.executeUpdate(mode.equals("OR ROLLBACK") ? "INSERT OR ROLLBACK INTO entries VALUES (?)" : "INSERT INTO entries VALUES (?)", mode.equals("OR ROLLBACK") ? "seed" : "bad");
                    } catch (SqliteDatabaseException error) { bodyFailure.set(error); throw error; }
                }));
                assertSame(bodyFailure.get(), failure);
                assertInstanceOf(SQLException.class, failure.getCause());
                assertEquals(mode.equals("ABORT") ? 0 : 1, failure.getSuppressed().length);
                assertSame(original, database.getConnection());
                assertTrue(original.getAutoCommit());
                assertEquals(List.of("seed"), rows(original));
                database.runTransaction(connection -> database.executeUpdate("INSERT INTO entries VALUES (?)", "after"));
            } finally { database.close(); }
            reopenRows(file, "seed", "after");
        }
    }

    @Test void errorAndRuntimeAfterSqliteRollbackKeepExactOriginalAndUsableConnection() throws Exception {
        for (Throwable original : List.of(new AssertionError("guard after rollback"), new IllegalStateException("guard after rollback"))) {
            Path file = root.resolve(original.getClass().getSimpleName() + ".db");
            SqliteDatabase database = new SqliteDatabase(file.toFile());
            try {
                setup(database);
                Connection connection = database.getConnection();
                Throwable failure = assertThrows(Throwable.class, () -> database.runTransaction(c -> {
                    database.executeUpdate("INSERT INTO entries VALUES (?)", "partial");
                    assertThrows(SqliteDatabaseException.class, () -> database.executeUpdate("INSERT OR ROLLBACK INTO entries VALUES (?)", "seed"));
                    SqliteSelfRollbackTest.<RuntimeException>sneaky(original);
                }));
                assertSame(original, failure);
                assertEquals(1, failure.getSuppressed().length);
                assertInstanceOf(SQLException.class, failure.getSuppressed()[0]);
                assertSame(connection, database.getConnection());
                assertTrue(connection.getAutoCommit());
                assertEquals(List.of("seed"), rows(connection));
                database.runTransaction(c -> database.executeUpdate("INSERT INTO entries VALUES (?)", "after"));
            } finally { database.close(); }
            reopenRows(file, "seed", "after");
        }
    }

    @Test void sneakyCheckedBodyFailureRollsBackBeforeStandaloneAndLaterTransactionWrites() throws Exception {
        Path file = root.resolve("checked.db");
        SqliteDatabase database = new SqliteDatabase(file.toFile());
        IOException original = new IOException("undeclared body failure");
        try {
            setup(database);
            Connection connection = database.getConnection();
            SqliteDatabaseException failure = assertThrows(SqliteDatabaseException.class, () -> database.runTransaction(c -> {
                database.executeUpdate("INSERT INTO entries VALUES (?)", "partial");
                SqliteSelfRollbackTest.<RuntimeException>sneaky(original);
            }));
            assertEquals("Transaction failed", failure.getMessage());
            assertSame(original, failure.getCause());
            assertSame(connection, database.getConnection());
            assertTrue(connection.getAutoCommit());
            assertEquals(List.of("seed"), rows(connection));
            database.executeUpdate("INSERT INTO entries VALUES (?)", "standalone-after");
            reopenRows(file, "seed", "standalone-after");
            database.runTransaction(c -> database.executeUpdate("INSERT INTO entries VALUES (?)", "transaction-after"));
        } finally { database.close(); }
        reopenRows(file, "seed", "standalone-after", "transaction-after");
    }

    @Test void busyWriteAndBusyCommitStillRollbackThenResumeAfterLockRelease() throws Exception {
        for (boolean commitBusy : List.of(false, true)) {
            Path file = root.resolve("busy-" + commitBusy + ".db");
            SqliteDatabase database = new SqliteDatabase(file.toFile());
            try {
                database.execute("PRAGMA journal_mode=DELETE");
                database.execute("PRAGMA busy_timeout=20");
                setup(database);
                Connection original = database.getConnection();
                try (Connection blocker = DriverManager.getConnection("jdbc:sqlite:" + file); Statement statement = blocker.createStatement()) {
                    if (commitBusy) { blocker.setAutoCommit(false); scalar(blocker, "SELECT count(*) FROM entries"); }
                    else statement.execute("BEGIN IMMEDIATE");
                    SqliteDatabaseException failure = assertThrows(SqliteDatabaseException.class, () -> database.runTransaction(c -> database.executeUpdate("INSERT INTO entries VALUES (?)", "partial")));
                    assertEquals(5, assertInstanceOf(SQLException.class, failure.getCause()).getErrorCode(), "SQLITE_BUSY");
                    assertEquals(0, failure.getSuppressed().length);
                    assertSame(original, database.getConnection());
                    assertTrue(original.getAutoCommit());
                    if (commitBusy) blocker.rollback(); else statement.execute("ROLLBACK");
                }
                assertEquals(List.of("seed"), rows(original));
                database.runTransaction(c -> database.executeUpdate("INSERT INTO entries VALUES (?)", "after"));
                assertEquals("20", scalar(original, "PRAGMA busy_timeout"));
            } finally { database.close(); }
            reopenRows(file, "seed", "after");
        }
    }

    @Test void nestedAutoCommitRemainsDiscardedRatherThanMisidentifiedAsAutoRollback() throws Exception {
        SqliteDatabase database = new SqliteDatabase(root.resolve("nested.db").toFile());
        try {
            setup(database);
            Connection original = database.getConnection();
            assertThrows(SqliteDatabaseException.class, () -> database.runTransaction(c ->
                    database.runTransaction(inner -> database.executeUpdate("INSERT INTO entries VALUES (?)", "inner"))));
            assertTrue(original.isClosed());
            assertThrows(SqliteDatabaseException.class, database::getConnection);
        } finally { database.close(); }
    }

    @Test void sqlCommitFailureAfterProvenRollbackKeepsWrapperCauseAndSuppressedRollback() throws Exception {
        Connection connection = mock(Connection.class);
        Statement probe = mock(Statement.class);
        SQLException original = new SQLException("commit failed"), rollback = new SQLException("already rolled back");
        doThrow(original).when(connection).commit(); doThrow(rollback).when(connection).rollback();
        when(connection.createStatement()).thenReturn(probe);
        try (var driver = mockStatic(DriverManager.class)) {
            driver.when(() -> DriverManager.getConnection(anyString())).thenReturn(connection);
            SqliteDatabase database = new SqliteDatabase(root.resolve("commit.db").toFile());
            try {
                SqliteDatabaseException failure = assertThrows(SqliteDatabaseException.class, () -> database.runTransaction(c -> {}));
                assertEquals("Transaction failed", failure.getMessage());
                assertSame(original, failure.getCause());
                assertArrayEquals(new Throwable[] {rollback}, failure.getSuppressed());
                verify(probe).execute("BEGIN"); verify(probe).close();
                verify(connection).setAutoCommit(true); verify(connection, never()).close();
                assertSame(connection, database.getConnection());
            } finally { database.close(); }
        }
    }

    @Test void failedRollbackWithRealStillOpenTransactionDiscardsAndNeverCommitsPartialRows() throws Exception {
        Path file = root.resolve("unknown.db");
        Connection real = DriverManager.getConnection("jdbc:sqlite:" + file);
        Connection connection = mock(Connection.class, delegatesTo(real));
        IOException rollback = new IOException("undeclared rollback failure");
        IllegalStateException original = new IllegalStateException("body failed");
        doAnswer(invocation -> { SqliteSelfRollbackTest.<SQLException>sneaky(rollback); return null; }).when(connection).rollback();
        try (var driver = mockStatic(DriverManager.class)) {
            driver.when(() -> DriverManager.getConnection(anyString())).thenReturn(connection);
            SqliteDatabase database = new SqliteDatabase(file.toFile());
            try {
                setup(database);
                SqliteDatabaseException failure = assertThrows(SqliteDatabaseException.class, () -> database.runTransaction(c -> {
                    database.executeUpdate("INSERT INTO entries VALUES (?)", "partial"); throw original;
                }));
                assertSame(rollback, failure.getCause());
                assertArrayEquals(new Throwable[] {original}, failure.getSuppressed());
                assertThrows(SqliteDatabaseException.class, database::getConnection);
                verify(connection, never()).setAutoCommit(true);
                assertTrue(real.isClosed());
            } finally { database.close(); real.close(); }
        }
        reopenRows(file, "seed");
    }

    @Test void uncertainProbeAndCheckedCloseFailuresCannotRestoreOrRetainConnection() throws Exception {
        for (String step : List.of("flag", "statement", "execute", "close")) {
            Connection connection = mock(Connection.class);
            Statement probe = mock(Statement.class);
            IOException unknown = new IOException("undeclared probe " + step), close = new IOException("undeclared connection close");
            SQLException rollback = new SQLException("rollback failed");
            AssertionError original = new AssertionError("body failed");
            doThrow(rollback).when(connection).rollback();
            when(connection.createStatement()).thenReturn(probe);
            if (step.equals("flag")) doAnswer(i -> { SqliteSelfRollbackTest.<SQLException>sneaky(unknown); return null; }).when(connection).getAutoCommit();
            if (step.equals("statement")) doAnswer(i -> { SqliteSelfRollbackTest.<SQLException>sneaky(unknown); return null; }).when(connection).createStatement();
            if (step.equals("execute")) doAnswer(i -> { SqliteSelfRollbackTest.<SQLException>sneaky(unknown); return null; }).when(probe).execute("BEGIN");
            if (step.equals("close")) doAnswer(i -> { SqliteSelfRollbackTest.<SQLException>sneaky(unknown); return null; }).when(probe).close();
            doAnswer(i -> { SqliteSelfRollbackTest.<SQLException>sneaky(close); return null; }).when(connection).close();
            try (var driver = mockStatic(DriverManager.class)) {
                driver.when(() -> DriverManager.getConnection(anyString())).thenReturn(connection);
                SqliteDatabase database = new SqliteDatabase(root.resolve(step + ".db").toFile());
                assertSame(original, assertThrows(AssertionError.class, () -> database.runTransaction(c -> { throw original; })));
                assertArrayEquals(new Throwable[] {rollback}, original.getSuppressed());
                assertArrayEquals(new Throwable[] {close}, rollback.getSuppressed());
                verify(connection, never()).setAutoCommit(true);
                assertThrows(SqliteDatabaseException.class, database::getConnection);
                database.close(); verify(connection, times(1)).close();
            }
        }
    }

    @Test void checkedRestorationFailurePreservesPrimaryAndUsesExistingRestoreWrapperAfterCommit() throws Exception {
        for (boolean bodyFails : List.of(false, true)) {
            Connection connection = mock(Connection.class);
            IOException restore = new IOException("undeclared restore failure");
            AssertionError original = new AssertionError("body failed");
            doAnswer(i -> { SqliteSelfRollbackTest.<SQLException>sneaky(restore); return null; }).when(connection).setAutoCommit(true);
            try (var driver = mockStatic(DriverManager.class)) {
                driver.when(() -> DriverManager.getConnection(anyString())).thenReturn(connection);
                SqliteDatabase database = new SqliteDatabase(root.resolve("restore-" + bodyFails + ".db").toFile());
                try {
                    Throwable failure = assertThrows(Throwable.class, () -> database.runTransaction(c -> { if (bodyFails) throw original; }));
                    if (bodyFails) { assertSame(original, failure); assertArrayEquals(new Throwable[] {restore}, failure.getSuppressed()); }
                    else { assertInstanceOf(SqliteDatabaseException.class, failure); assertEquals("Failed to restore auto-commit", failure.getMessage()); assertSame(restore, failure.getCause()); }
                } finally { database.close(); }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneaky(Throwable failure) throws T { throw (T) failure; }
}
