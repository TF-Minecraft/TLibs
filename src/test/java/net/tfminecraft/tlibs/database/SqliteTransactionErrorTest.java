package net.tfminecraft.tlibs.database;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteTransactionErrorTest {
    @TempDir Path root;
    @BeforeEach void initializeDriver() { SqliteProvider.ensureDriverLoaded(); }

    int count(SqliteDatabase database) throws SQLException {
        try (var statement = database.getConnection().createStatement(); var rows = statement.executeQuery("SELECT count(*) FROM entries")) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    @Test void bodyErrorRollsBackRealSQLiteRowsAcrossCloseAndReopen() throws Exception {
        Path file = root.resolve("transaction.db");
        NoSuchMethodError original = new NoSuchMethodError("simulated provider linkage failure");
        SqliteDatabase database = new SqliteDatabase(file.toFile());
        try {
            database.execute("CREATE TABLE entries (value TEXT)");
            assertSame(original, assertThrows(NoSuchMethodError.class, () -> database.runTransaction(connection -> {
                database.executeUpdate("INSERT INTO entries VALUES (?)", "must roll back");
                throw original;
            })));
            assertTrue(database.getConnection().getAutoCommit());
        } finally { database.close(); }
        SqliteDatabase reopened = new SqliteDatabase(file.toFile());
        try {
            assertEquals(0, count(reopened), "Error must not commit the partial INSERT when auto-commit is restored");
            reopened.runTransaction(connection -> reopened.executeUpdate("INSERT INTO entries VALUES (?)", "committed"));
            assertTrue(reopened.getConnection().getAutoCommit());
            assertEquals(1, count(reopened));
        } finally { reopened.close(); }
        SqliteDatabase finalReopen = new SqliteDatabase(file.toFile());
        try { assertEquals(1, count(finalReopen)); }
        finally { finalReopen.close(); }
    }

    @Test void successfulRollbackRestoresAutoCommitAndSameRealConnectionCanCommitAgain() throws Exception {
        SqliteDatabase database = new SqliteDatabase(root.resolve("same-connection.db").toFile());
        try {
            database.execute("CREATE TABLE entries (value TEXT)");
            Connection originalConnection = database.getConnection();
            AssertionError original = new AssertionError("guard failed after INSERT");
            assertSame(original, assertThrows(AssertionError.class, () -> database.runTransaction(connection -> {
                database.executeUpdate("INSERT INTO entries VALUES (?)", "must roll back"); throw original;
            })));
            assertSame(originalConnection, database.getConnection());
            assertTrue(originalConnection.getAutoCommit());
            assertEquals(0, count(database));
            database.runTransaction(connection -> database.executeUpdate("INSERT INTO entries VALUES (?)", "committed"));
            assertSame(originalConnection, database.getConnection());
            assertTrue(originalConnection.getAutoCommit());
            assertEquals(1, count(database));
        } finally { database.close(); }
    }

    @Test void failedRollbackDiscardsConnectionWithoutRestoringAutoCommitAndRetainsRuntimeFailure() throws Exception {
        Connection connection = mock(Connection.class);
        SQLException rollback = new SQLException("rollback failed");
        IllegalStateException original = new IllegalStateException("body failed");
        doThrow(rollback).when(connection).rollback();
        try (var driver = mockStatic(DriverManager.class)) {
            driver.when(() -> DriverManager.getConnection(anyString())).thenReturn(connection);
            SqliteDatabase database = new SqliteDatabase(root.resolve("mock.db").toFile());
            SqliteDatabaseException failure = assertThrows(SqliteDatabaseException.class, () -> database.runTransaction(c -> { throw original; }));
            assertSame(rollback, failure.getCause());
            verify(connection, never()).setAutoCommit(true);
            verify(connection).close();
            assertThrows(SqliteDatabaseException.class, database::getConnection);
            assertArrayEquals(new Throwable[] {original}, failure.getSuppressed());
            database.close();
            verify(connection, times(1)).close();
        }
    }

    @Test void failedRollbackAfterErrorKeepsOriginalErrorAndNeverRestoresAutoCommit() throws Exception {
        Connection connection = mock(Connection.class);
        SQLException rollback = new SQLException("rollback failed");
        StackOverflowError original = new StackOverflowError("body failed");
        doThrow(rollback).when(connection).rollback();
        try (var driver = mockStatic(DriverManager.class)) {
            driver.when(() -> DriverManager.getConnection(anyString())).thenReturn(connection);
            SqliteDatabase database = new SqliteDatabase(root.resolve("mock-error.db").toFile());
            assertSame(original, assertThrows(StackOverflowError.class, () -> database.runTransaction(c -> { throw original; })));
            verify(connection).rollback();
            verify(connection, never()).setAutoCommit(true);
            verify(connection).close();
            assertThrows(SqliteDatabaseException.class, database::getConnection);
            assertArrayEquals(new Throwable[] {rollback}, original.getSuppressed());
        }
    }

    @Test void failedRollbackAndCloseRetainOriginalErrorAndAllSecondaryFailures() throws Exception {
        Connection connection = mock(Connection.class);
        SQLException rollback = new SQLException("rollback failed"), close = new SQLException("close failed");
        AssertionError original = new AssertionError("body failed");
        doThrow(rollback).when(connection).rollback(); doThrow(close).when(connection).close();
        try (var driver = mockStatic(DriverManager.class)) {
            driver.when(() -> DriverManager.getConnection(anyString())).thenReturn(connection);
            SqliteDatabase database = new SqliteDatabase(root.resolve("close-failure.db").toFile());
            assertSame(original, assertThrows(AssertionError.class, () -> database.runTransaction(c -> { throw original; })));
            assertArrayEquals(new Throwable[] {rollback}, original.getSuppressed());
            assertArrayEquals(new Throwable[] {close}, rollback.getSuppressed());
            verify(connection, never()).setAutoCommit(true);
            assertThrows(SqliteDatabaseException.class, database::getConnection);
        }
    }

    @Test void uncheckedRollbackAndCloseFailuresAlsoDiscardConnection() throws Exception {
        for (Throwable rollback : List.of(new IllegalStateException("rollback failed"), new LinkageError("rollback failed"))) {
            Connection connection = mock(Connection.class);
            Throwable close = new AssertionError("close failed");
            IllegalStateException original = new IllegalStateException("body failed");
            doThrow(rollback).when(connection).rollback(); doThrow(close).when(connection).close();
            try (var driver = mockStatic(DriverManager.class)) {
                driver.when(() -> DriverManager.getConnection(anyString())).thenReturn(connection);
                SqliteDatabase database = new SqliteDatabase(root.resolve("unchecked.db").toFile());
                SqliteDatabaseException failure = assertThrows(SqliteDatabaseException.class, () -> database.runTransaction(c -> { throw original; }));
                assertSame(rollback, failure.getCause());
                assertArrayEquals(new Throwable[] {original}, failure.getSuppressed());
                assertArrayEquals(new Throwable[] {close}, rollback.getSuppressed());
                verify(connection, never()).setAutoCommit(true);
                assertThrows(SqliteDatabaseException.class, database::getConnection);
            }
        }
    }

    @Test void repeatedSameErrorCannotSelfSuppressOrRestoreAnUncertainTransaction() throws Exception {
        Connection connection = mock(Connection.class);
        AssertionError original = new AssertionError("same failure from body and driver");
        doThrow(original).when(connection).rollback(); doThrow(original).when(connection).close();
        try (var driver = mockStatic(DriverManager.class)) {
            driver.when(() -> DriverManager.getConnection(anyString())).thenReturn(connection);
            SqliteDatabase database = new SqliteDatabase(root.resolve("same-error.db").toFile());
            assertSame(original, assertThrows(AssertionError.class, () -> database.runTransaction(c -> { throw original; })));
            assertEquals(0, original.getSuppressed().length);
            verify(connection, never()).setAutoCommit(true);
            assertThrows(SqliteDatabaseException.class, database::getConnection);
        }
    }

    @Test void restorationFailureAfterRollbackCannotMaskOriginalBodyError() throws Exception {
        for (Throwable restore : List.of(new SQLException("restore failed"), new IllegalStateException("restore failed"), new LinkageError("restore failed"))) {
            Connection connection = mock(Connection.class);
            AssertionError original = new AssertionError("body failed");
            doThrow(restore).when(connection).setAutoCommit(true);
            try (var driver = mockStatic(DriverManager.class)) {
                driver.when(() -> DriverManager.getConnection(anyString())).thenReturn(connection);
                SqliteDatabase database = new SqliteDatabase(root.resolve("restoration-failure.db").toFile());
                assertSame(original, assertThrows(AssertionError.class, () -> database.runTransaction(c -> { throw original; })));
                verify(connection).rollback();
                assertArrayEquals(new Throwable[] {restore}, original.getSuppressed());
                database.close();
            }
        }
    }

    @Test void restorationFailureAfterCommitPreservesExistingSqlWrapperAndUncheckedIdentity() throws Exception {
        for (Throwable restore : List.of(new SQLException("restore failed"), new IllegalStateException("restore failed"), new LinkageError("restore failed"))) {
            Connection connection = mock(Connection.class);
            doThrow(restore).when(connection).setAutoCommit(true);
            try (var driver = mockStatic(DriverManager.class)) {
                driver.when(() -> DriverManager.getConnection(anyString())).thenReturn(connection);
                SqliteDatabase database = new SqliteDatabase(root.resolve("committed-restoration-failure.db").toFile());
                Throwable failure = assertThrows(Throwable.class, () -> database.runTransaction(c -> {}));
                if (restore instanceof SQLException) {
                    assertInstanceOf(SqliteDatabaseException.class, failure); assertSame(restore, failure.getCause());
                } else assertSame(restore, failure);
                verify(connection).commit(); verify(connection, never()).rollback();
                database.close();
            }
        }
    }
}
