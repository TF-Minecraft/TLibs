package net.tfminecraft.tlibs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.tlibs.armour.equipment.EquipmentAssetListener;
import net.tfminecraft.tlibs.armour.equipment.EquipmentAssets;
import net.tfminecraft.tlibs.armour.equipment.ItemsAdderEquipmentListener;
import net.tfminecraft.tlibs.command.TLibsCommand;

class EquipmentWiringTest {
    @Test void equipmentCommandReportsAndRefreshes() {
        TLibsCommand commands = new TLibsCommand();
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("tlibs.admin")).thenReturn(true);
        Command command = mock(Command.class);
        TLibs plugin = mock(TLibs.class);
        EquipmentAssets assets = mock(EquipmentAssets.class);
        EquipmentAssetListener listener = mock(EquipmentAssetListener.class);
        when(plugin.getEquipmentAssets()).thenReturn(assets);
        when(plugin.getEquipmentListener()).thenReturn(listener);
        when(assets.isEnabled()).thenReturn(true, false);
        when(assets.sourceCount()).thenReturn(137);
        when(assets.publishedCount()).thenReturn(120);
        try (MockedStatic<TLibs> tlibs = mockStatic(TLibs.class)) {
            tlibs.when(TLibs::getInstance).thenReturn(plugin);
            assertTrue(commands.onCommand(sender, command, "tlibs", new String[] { "equipment" }));
            verify(sender).sendMessage("§a[TLibs] Equipment assets enabled: 137 armour sets found, 120 in the served pack.");
            verify(assets, never()).refreshSources();
            assertTrue(commands.onCommand(sender, command, "tlibs", new String[] { "EQUIPMENT", "other" }));
            verify(sender).sendMessage("§a[TLibs] Equipment assets disabled: 137 armour sets found, 120 in the served pack.");
            assertTrue(commands.onCommand(sender, command, "tlibs", new String[] { "equipment", "Refresh" }));
            verify(assets).refreshSources();
            verify(assets).refreshPublished();
            verify(listener).resyncAll();
        }
        assertEquals(List.of("reload", "equipment"), commands.onTabComplete(sender, command, "", new String[] { "" }));
        assertEquals(List.of("equipment"), commands.onTabComplete(sender, command, "", new String[] { "EQ" }));
        assertEquals(List.of("refresh"), commands.onTabComplete(sender, command, "", new String[] { "equipment", "RE" }));
        assertEquals(List.of(), commands.onTabComplete(sender, command, "", new String[] { "equipment", "x" }));
        assertEquals(List.of(), commands.onTabComplete(sender, command, "", new String[] { "reload", "" }));
        assertEquals(List.of(), commands.onTabComplete(sender, command, "", new String[] { "equipment", "", "" }));
    }

    @Test void itemsAdderHooksAreOnlyRegisteredWhenItemsAdderIsPresent() throws Exception {
        TLibs plugin = mock(TLibs.class);
        Field assets = TLibs.class.getDeclaredField("equipmentAssets");
        assets.setAccessible(true);
        assets.set(plugin, mock(EquipmentAssets.class));
        Method register = TLibs.class.getDeclaredMethod("registerEquipmentAssets");
        register.setAccessible(true);
        when(plugin.getEquipmentAssets()).thenCallRealMethod();
        when(plugin.getEquipmentListener()).thenCallRealMethod();
        PluginManager manager = mock(PluginManager.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            register.invoke(plugin);
            verify(manager).registerEvents(isA(EquipmentAssetListener.class), eq(plugin));
            verify(manager, never()).registerEvents(isA(ItemsAdderEquipmentListener.class), any());
            when(manager.getPlugin("ItemsAdder")).thenReturn(mock(Plugin.class));
            register.invoke(plugin);
            verify(manager).registerEvents(isA(ItemsAdderEquipmentListener.class), eq(plugin));
        }
        assertNotNull(plugin.getEquipmentAssets());
        assertNotNull(plugin.getEquipmentListener());
    }
}
