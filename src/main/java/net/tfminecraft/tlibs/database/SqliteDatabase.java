package net.tfminecraft.tlibs.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.function.Consumer;

public final class SqliteDatabase {
    private final File dbFile;
    private Connection connection;

    public SqliteDatabase(File dbFile) {
        if (dbFile == null) {
            throw new IllegalArgumentException("dbFile required");
        }
        this.dbFile = dbFile;
        SqliteProvider.ensureDriverLoaded();
        open();
    }

    private void open() {
        try {
            File parent = dbFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        } catch (SQLException e) {
            throw new SqliteDatabaseException("Failed to open SQLite database: " + dbFile.getAbsolutePath(), e);
        }
    }

    public synchronized Connection getConnection() {
        if (connection == null) {
            throw new SqliteDatabaseException("Database connection is closed: " + dbFile.getAbsolutePath());
        }
        return connection;
    }

    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            throw new SqliteDatabaseException("Failed to close SQLite database: " + dbFile.getAbsolutePath(), e);
        } finally {
            connection = null;
        }
    }

    public void execute(String sql) {
        synchronized (this) {
            try (PreparedStatement statement = getConnection().prepareStatement(sql)) {
                statement.execute();
            } catch (SQLException e) {
                throw new SqliteDatabaseException("Failed to execute SQL: " + sql, e);
            }
        }
    }

    public int executeUpdate(String sql, Object... params) {
        synchronized (this) {
            try (PreparedStatement statement = getConnection().prepareStatement(sql)) {
                bindParams(statement, params);
                return statement.executeUpdate();
            } catch (SQLException e) {
                throw new SqliteDatabaseException("Failed to execute update: " + sql, e);
            }
        }
    }

    public void runTransaction(Consumer<Connection> work) {
        synchronized (this) {
            Connection conn = getConnection();
            try {
                conn.setAutoCommit(false);
                work.accept(conn);
                conn.commit();
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    throw new SqliteDatabaseException("Transaction failed and rollback failed", rollbackError);
                }
                throw new SqliteDatabaseException("Transaction failed", e);
            } catch (RuntimeException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    throw new SqliteDatabaseException("Transaction failed and rollback failed", rollbackError);
                }
                throw e;
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    throw new SqliteDatabaseException("Failed to restore auto-commit", e);
                }
            }
        }
    }

    private static void bindParams(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }
}
