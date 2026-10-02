package net.tfminecraft.tlibs.database;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.File;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SqliteFailureTest {
    @TempDir Path root;
    @BeforeEach void initializeRealDriver(){SqliteProvider.ensureDriverLoaded();}
    @Test void transactionsCommitAndRollbackRealRows()throws Exception{
        SqliteDatabase db=new SqliteDatabase(root.resolve("real.db").toFile());
        try{
            db.execute("create table entries (v text)");db.runTransaction(c->db.executeUpdate("insert into entries values (?)","committed"));
            IllegalStateException error=new IllegalStateException("abort");assertSame(error,assertThrows(IllegalStateException.class,()->db.runTransaction(c->{db.executeUpdate("insert into entries values (?)","rolled back");throw error;})));
            try(var s=db.getConnection().createStatement();var r=s.executeQuery("select count(*) from entries")){assertTrue(r.next());assertEquals(1,r.getInt(1));}
            assertTrue(db.getConnection().getAutoCommit());
        }finally{db.close();}
        db.close();assertThrows(SqliteDatabaseException.class,db::getConnection);assertThrows(IllegalArgumentException.class,()->new SqliteDatabase(null));
    }
    @Test void jdbcFailuresAreWrappedAndCloseAlwaysClearsConnection()throws Exception{
        Connection c=mock(Connection.class);SQLException failure=new SQLException("driver failed");
        try(var driver=mockStatic(DriverManager.class)){
            driver.when(()->DriverManager.getConnection(org.mockito.ArgumentMatchers.anyString())).thenThrow(failure);
            assertSame(failure,assertThrows(SqliteDatabaseException.class,()->new SqliteDatabase(root.resolve("fail.db").toFile())).getCause());
            driver.when(()->DriverManager.getConnection(org.mockito.ArgumentMatchers.anyString())).thenReturn(c);
            SqliteDatabase db=new SqliteDatabase(new File("relative-"+UUID.randomUUID()+".db"));
            when(c.prepareStatement("bad")).thenThrow(failure);assertSame(failure,assertThrows(SqliteDatabaseException.class,()->db.execute("bad")).getCause());assertSame(failure,assertThrows(SqliteDatabaseException.class,()->db.executeUpdate("bad",1)).getCause());
            doThrow(failure).when(c).close();assertSame(failure,assertThrows(SqliteDatabaseException.class,db::close).getCause());assertThrows(SqliteDatabaseException.class,db::getConnection);db.close();
        }
    }
    @Test void transactionFailuresAttemptRollbackAndRestoreAutoCommit()throws Exception{
        Connection c=mock(Connection.class);SQLException failure=new SQLException("commit failed");
        try(var driver=mockStatic(DriverManager.class)){
            driver.when(()->DriverManager.getConnection(org.mockito.ArgumentMatchers.anyString())).thenReturn(c);
            SqliteDatabase db=new SqliteDatabase(root.resolve("fake.db").toFile());
            doThrow(failure).when(c).commit();assertSame(failure,assertThrows(SqliteDatabaseException.class,()->db.runTransaction(conn->{})).getCause());verify(c).rollback();verify(c).setAutoCommit(true);
            SQLException rollback=new SQLException("rollback failed");doThrow(rollback).when(c).rollback();assertSame(rollback,assertThrows(SqliteDatabaseException.class,()->db.runTransaction(conn->{})).getCause());
            IllegalStateException application=new IllegalStateException("application");assertSame(rollback,assertThrows(SqliteDatabaseException.class,()->db.runTransaction(conn->{throw application;})).getCause());
            reset(c);doThrow(failure).when(c).setAutoCommit(true);assertSame(failure,assertThrows(SqliteDatabaseException.class,()->db.runTransaction(conn->{})).getCause());db.close();
        }
    }
    @Test void availabilityCanInitializeTheDriverAndWaitingInitializersRecheckState() throws Exception {
        Field loaded=SqliteProvider.class.getDeclaredField("driverLoaded"); loaded.setAccessible(true);
        Field available=SqliteProvider.class.getDeclaredField("driverAvailable"); available.setAccessible(true);
        boolean wasLoaded=loaded.getBoolean(null), wasAvailable=available.getBoolean(null);
        java.util.concurrent.atomic.AtomicReference<Throwable> error=new java.util.concurrent.atomic.AtomicReference<>();
        Thread waiting=new Thread(()->{try{SqliteProvider.ensureDriverLoaded();}catch(Throwable e){error.set(e);}}, "sqlite-driver-initializer");
        try {
            loaded.setBoolean(null,false); assertTrue(SqliteProvider.isAvailable());
            synchronized(SqliteProvider.class) {
                loaded.setBoolean(null,false); waiting.start();
                long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
                while(waiting.getState()!=Thread.State.BLOCKED && System.nanoTime()<deadline) java.util.concurrent.locks.LockSupport.parkNanos(100_000);
                assertEquals(Thread.State.BLOCKED,waiting.getState());
                SqliteProvider.ensureDriverLoaded();
            }
            waiting.join(3000); assertFalse(waiting.isAlive()); assertNull(error.get()); assertTrue(SqliteProvider.isAvailable());
        } finally {
            waiting.join(3000); loaded.setBoolean(null,wasLoaded);available.setBoolean(null,wasAvailable);
        }
    }
    @Test void providerReportsMissingDriverInIsolatedPluginClassLoader()throws Exception{
        ClassLoader parent=SqliteProvider.class.getClassLoader();
        ClassLoader loader=new ClassLoader(parent){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(name.equals("org.sqlite.JDBC"))throw new ClassNotFoundException(name);
                if(name.equals(SqliteProvider.class.getName())){
                    synchronized(getClassLoadingLock(name)){
                        Class<?> loaded=findLoadedClass(name);if(loaded!=null)return loaded;
                        try(var in=parent.getResourceAsStream(name.replace('.','/')+".class")){byte[] bytes=in.readAllBytes();Class<?> c=defineClass(name,bytes,0,bytes.length,SqliteProvider.class.getProtectionDomain());if(resolve)resolveClass(c);return c;}catch(java.io.IOException ex){throw new ClassNotFoundException(name,ex);}
                    }
                }
                return super.loadClass(name,resolve);
            }
        };
        Class<?> isolated=loader.loadClass(SqliteProvider.class.getName());assertEquals(false,isolated.getMethod("isAvailable").invoke(null));assertEquals(false,isolated.getMethod("isAvailable").invoke(null));
    }
}
