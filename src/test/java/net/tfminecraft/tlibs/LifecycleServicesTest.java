package net.tfminecraft.tlibs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import net.tfminecraft.tlibs.command.TLibsCommand;
import net.tfminecraft.tlibs.config.*;
import net.tfminecraft.tlibs.enums.APIType;
import net.tfminecraft.tlibs.itemscan.ItemScanService;
import net.tfminecraft.tlibs.listener.FurnitureRepairListener;
import net.tfminecraft.tlibs.mmoitem.*;
import net.tfminecraft.tlibs.socket.*;
import net.tfminecraft.tlibs.utils.RebuildDebug;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

@SuppressWarnings("deprecation")
class LifecycleServicesTest {
    record SavedField(Field field,Object owner,Object value) {void restore()throws Exception{field.set(owner,value);}}
    List<SavedField> saved=new ArrayList<>();
    void snapshot(Object object)throws Exception{
        for(Class<?> type=object.getClass();type!=Object.class;type=type.getSuperclass())for(Field f:type.getDeclaredFields()){
            if(!Modifier.isStatic(f.getModifiers())&&!Modifier.isFinal(f.getModifiers())){f.setAccessible(true);saved.add(new SavedField(f,object,f.get(object)));}
        }
    }
    void snapshotStatic(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);saved.add(new SavedField(f,null,f.get(null)));}
    @BeforeEach void setup()throws Exception{snapshotStatic(TLibs.class,"instance");snapshotStatic(GemApplyPrimer.class,"plugin");snapshot(TLibs.getItemAPI());snapshot(TLibs.getBlockAPI());}
    @AfterEach void cleanup()throws Exception{
        if(MockBukkit.isMocked())MockBukkit.unmock();
        for(SavedField f:saved)f.restore();
    }
    @Test void lifecycleInitializesApisCommandsServicesAndReloadsDiskConfig()throws Exception{
        MockBukkit.mock();TLibs plugin=MockBukkit.load(TLibs.class);
        assertSame(plugin,TLibs.getInstance());assertNotNull(ItemScanService.get());assertNotNull(TLibs.getRebuildConfig());assertNotNull(TLibs.getSocketTierConfig());
        assertSame(TLibs.getItemAPI(),TLibs.getApiInstance(APIType.ITEM_API));assertSame(TLibs.getBlockAPI(),TLibs.getApiInstance(APIType.BLOCK_API));
        assertNotNull(TLibs.getItemAPI().getCreator());assertNotNull(TLibs.getBlockAPI().getChecker());
        assertNotNull(plugin.getCommand("tlibs").getExecutor());assertNotNull(plugin.getCommand("tlibs").getTabCompleter());
        Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"),"mmo-item-rebuild:\n  enabled: false\n  debug-nbt: true\ntiered-sockets:\n  enabled: false\n");
        plugin.reloadPluginConfig();assertFalse(TLibs.getRebuildConfig().isEnabled());assertTrue(RebuildDebug.enabled());RebuildDebug.log("enabled");
        plugin.onDisable();assertNull(ItemScanService.get());plugin.onDisable();
        TLibs spy=spy(plugin);doReturn(null).when(spy).getCommand("tlibs");spy.onEnable();spy.onDisable();
    }
    @Test void commandsCheckPermissionReportUsageReloadAndComplete(){
        TLibsCommand commands=new TLibsCommand();CommandSender sender=mock(CommandSender.class);Command command=mock(Command.class);TLibs plugin=mock(TLibs.class);
        assertTrue(commands.onCommand(sender,command,"tlibs",new String[0]));verify(sender).sendMessage("§cYou do not have permission.");assertEquals(List.of(),commands.onTabComplete(sender,command,"",new String[]{"r"}));
        when(sender.hasPermission("tlibs.admin")).thenReturn(true);
        assertTrue(commands.onCommand(sender,command,"",new String[0]));assertTrue(commands.onCommand(sender,command,"",new String[]{"unknown"}));
        try(var tlibs=mockStatic(TLibs.class)){tlibs.when(TLibs::getInstance).thenReturn(plugin);assertTrue(commands.onCommand(sender,command,"",new String[]{"RELOAD"}));verify(plugin).reloadPluginConfig();}
        assertEquals(List.of("reload"),commands.onTabComplete(sender,command,"",new String[]{"RE"}));assertEquals(List.of(),commands.onTabComplete(sender,command,"",new String[]{"x"}));assertEquals(List.of(),commands.onTabComplete(sender,command,"",new String[0]));
    }
    @Test void registrarWaitsForDependenciesAndRegistersEachListenerOnce(){
        TLibs plugin=mock(TLibs.class);when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());PluginManager manager=mock(PluginManager.class);
        RebuildConfig config=mock(RebuildConfig.class);SocketTierConfig sockets=mock(SocketTierConfig.class);
        try(var tlibs=mockStatic(TLibs.class);var bukkit=mockStatic(Bukkit.class)){
            tlibs.when(TLibs::getInstance).thenReturn(plugin);tlibs.when(TLibs::getRebuildConfig).thenReturn(config);tlibs.when(TLibs::getSocketTierConfig).thenReturn(sockets);bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            MMOItemRebuildRegistrar registrar=new MMOItemRebuildRegistrar();registrar.tryRegister();assertFalse(registrar.isRegistered());
            when(config.isEnabled()).thenReturn(true);registrar.tryRegister();assertFalse(registrar.isRegistered());
            Plugin other=mock(Plugin.class);when(other.getName()).thenReturn("Other");registrar.onPluginEnable(new PluginEnableEvent(other));
            Plugin mmo=mock(Plugin.class);when(mmo.getName()).thenReturn("MMOItems");when(manager.isPluginEnabled("MMOItems")).thenReturn(true);
            registrar.onPluginEnable(new PluginEnableEvent(mmo));assertTrue(registrar.isRegistered());
            verify(manager).registerEvents(isA(MMOItemRebuildBridge.class),eq(plugin));verify(manager).registerEvents(isA(MMOItemRebuildBaselineListener.class),eq(plugin));
            when(sockets.isEnabled()).thenReturn(true);registrar.tryRegister();registrar.tryRegister();
            verify(manager).registerEvents(isA(TieredSocketApplyListener.class),eq(plugin));verify(manager).registerEvents(isA(TieredSocketRebuildListener.class),eq(plugin));
        }
    }
    @Test void debugLoggingSupportsStartupAndDisabledConfiguration(){
        try(var tlibs=mockStatic(TLibs.class)){
            assertTrue(RebuildDebug.enabled());RebuildDebug.log("before startup");
            TLibs plugin=mock(TLibs.class);Logger logger=mock(Logger.class);when(plugin.getLogger()).thenReturn(logger);RebuildConfig config=mock(RebuildConfig.class);
            tlibs.when(TLibs::getInstance).thenReturn(plugin);tlibs.when(TLibs::getRebuildConfig).thenReturn(config);
            assertFalse(RebuildDebug.enabled());RebuildDebug.log("hidden");verifyNoInteractions(logger);RebuildDebug.logAlways("visible");verify(logger).info("[MMORebuild] visible");
        }
    }
    @Test void furnitureRepairOnlyHidesVisibleItemsAdderLabels(){
        boolean previous=FurnitureRepairListener.HIDE_ITEMSADDER_FURNITURE_STANDS;
        try{
            FurnitureRepairListener listener=new FurnitureRepairListener();Chunk chunk=mock(Chunk.class);ChunkLoadEvent event=new ChunkLoadEvent(chunk,false);clearInvocations(chunk);
            FurnitureRepairListener.HIDE_ITEMSADDER_FURNITURE_STANDS=false;listener.onChunkLoad(event);verifyNoInteractions(chunk);
            Entity other=mock(Entity.class);ArmorStand invisible=mock(ArmorStand.class),wrong=mock(ArmorStand.class),furniture=mock(ArmorStand.class);
            when(wrong.isCustomNameVisible()).thenReturn(true);when(wrong.getCustomName()).thenReturn("other");when(furniture.isCustomNameVisible()).thenReturn(true);when(furniture.getCustomName()).thenReturn("ItemsAdder_furniture");
            when(chunk.getEntities()).thenReturn(new Entity[]{other,invisible,wrong,furniture});FurnitureRepairListener.HIDE_ITEMSADDER_FURNITURE_STANDS=true;listener.onChunkLoad(event);
            verify(furniture).setCustomNameVisible(false);verify(wrong,never()).setCustomNameVisible(anyBoolean());verify(invisible,never()).getCustomName();
        }finally{FurnitureRepairListener.HIDE_ITEMSADDER_FURNITURE_STANDS=previous;}
    }
}
