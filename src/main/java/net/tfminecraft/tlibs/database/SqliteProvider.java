package net.tfminecraft.tlibs.database;

public final class SqliteProvider {
    private static final String DRIVER_CLASS = "org.sqlite.JDBC";

    private static volatile boolean driverLoaded = false;
    private static volatile boolean driverAvailable = false;

    private SqliteProvider() {}

    public static void ensureDriverLoaded() {
        if (driverLoaded) {
            return;
        }
        synchronized (SqliteProvider.class) {
            if (driverLoaded) {
                return;
            }
            try {
                Class.forName(DRIVER_CLASS);
                driverAvailable = true;
            } catch (ClassNotFoundException e) {
                driverAvailable = false;
                throw new SqliteDatabaseException("SQLite JDBC driver not available: " + DRIVER_CLASS, e);
            } finally {
                driverLoaded = true;
            }
        }
    }

    public static boolean isAvailable() {
        if (!driverLoaded) {
            try {
                ensureDriverLoaded();
            } catch (SqliteDatabaseException ignored) {
                return false;
            }
        }
        return driverAvailable;
    }
}
