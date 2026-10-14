package net.tfminecraft.tlibs.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
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

    /**
     * Attempts rollback for every failure; confirmed rollback preserves runtime/error identity.
     * A confirmed SQLite auto-rollback keeps this connection and its settings usable.
     * An uncertain rollback discards the connection; the owning store must be recreated.
     */
    public void runTransaction(Consumer<Connection> work) {
        synchronized (this) {
            Connection conn = getConnection();
            boolean completed = false;
            Throwable failure = null;
            try {
                conn.setAutoCommit(false);
                work.accept(conn);
                conn.commit();
                completed = true;
            } catch (Throwable e) {
                failure = e;
                Throwable rollbackFailure = null;
                try {
                    conn.rollback();
                    completed = true;
                } catch (Throwable rollbackError) {
                    if (!alreadyRolledBack(conn)) {
                        // Restoring auto-commit can commit partial work after a failed rollback.
                        connection = null;
                        try {
                            conn.close();
                        } catch (Throwable closeError) {
                            if (closeError != rollbackError) rollbackError.addSuppressed(closeError);
                        }
                        if (e instanceof Error original) {
                            if (rollbackError != original) original.addSuppressed(rollbackError);
                            throw original;
                        }
                        SqliteDatabaseException wrapped = new SqliteDatabaseException("Transaction failed and rollback failed", rollbackError);
                        wrapped.addSuppressed(e);
                        failure = wrapped;
                        throw wrapped;
                    }
                    // The successful BEGIN opened an empty transaction after SQLite's rollback.
                    rollbackFailure = rollbackError;
                    completed = true;
                }
                if (!(e instanceof RuntimeException || e instanceof Error)) {
                    SqliteDatabaseException wrapped = new SqliteDatabaseException("Transaction failed", e);
                    if (rollbackFailure != null) wrapped.addSuppressed(rollbackFailure);
                    failure = wrapped;
                    throw wrapped;
                }
                if (rollbackFailure != null && rollbackFailure != e) e.addSuppressed(rollbackFailure);
                if (e instanceof RuntimeException original) throw original;
                throw (Error) e;
            } finally {
                if (completed) {
                    try {
                        conn.setAutoCommit(true);
                    } catch (Throwable restoreError) {
                        if (failure != null) {
                            if (restoreError != failure) failure.addSuppressed(restoreError);
                        } else {
                            if (!(restoreError instanceof RuntimeException || restoreError instanceof Error))
                                throw new SqliteDatabaseException("Failed to restore auto-commit", restoreError);
                            if (restoreError instanceof RuntimeException original) throw original;
                            throw (Error) restoreError;
                        }
                    }
                }
            }
        }
    }

    /** BEGIN cannot succeed inside a live transaction; success proves no uncommitted work remains. */
    private static boolean alreadyRolledBack(Connection conn) {
        try {
            if (conn.getAutoCommit()) return false;
            try (Statement statement = conn.createStatement()) {
                statement.execute("BEGIN");
            }
            return true;
        } catch (Throwable probeError) {
            return false;
        }
    }

    private static void bindParams(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }
}
