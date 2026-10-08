package net.tfminecraft.tlibs.armour.equipment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;

import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import dev.lone.itemsadder.api.Events.ItemsAdderPackCompressedEvent;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;

class EquipmentAssetsTest {
    private static final int BRONZE_RGB = 0xb38e5d;
    private static final Key BRONZE = Key.key("tfmc_equipment", "tfmc_armor/bronze");
    @TempDir Path plugins;
    private final Logger logger = mock(Logger.class);

    @BeforeAll static void startServer() { MockBukkit.mock(); }
    @AfterAll static void stopServer() { MockBukkit.unmock(); }

    private void bronzeSources() throws IOException {
        Path pack = plugins.resolve("ItemsAdder/contents/tfmc_armor");
        Files.createDirectories(pack.resolve("configs"));
        Files.writeString(pack.resolve("configs/bronze.yml"), """
            info:
              namespace: tfmc_armor
            armors_rendering:
              bronze:
                color: '#b38e5d'
                layer_1: layers/bronze_1
                layer_2: layers/bronze_2
            """);
        Path textures = pack.resolve("resourcepack/assets/tfmc_armor/textures/layers");
        Files.createDirectories(textures);
        Files.writeString(textures.resolve("bronze_1.png"), "one");
        Files.writeString(textures.resolve("bronze_2.png"), "two");
    }

    private void servedPack(List<String> names) throws IOException {
        Path zip = plugins.resolve("ItemsAdder/output/generated.zip");
        Files.createDirectories(zip.getParent());
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (String name : names) {
                out.putNextEntry(new ZipEntry(name));
                out.closeEntry();
            }
        }
    }

    private EquipmentAssets loaded(String yaml) {
        EquipmentAssets assets = new EquipmentAssets(logger);
        assets.reload(YamlConfiguration.loadConfiguration(new java.io.StringReader(yaml)), plugins);
        return assets;
    }

    @Test void onlyAssetsInTheServedPackAreUsed() throws IOException {
        bronzeSources();
        EquipmentAssets assets = loaded("");
        assertTrue(assets.isEnabled());
        assertEquals(1, assets.sourceCount());
        assertEquals(0, assets.publishedCount());
        assertEquals(100, assets.resyncDelayTicks());
        verify(logger).warning(contains("Could not read"));

        Map<String, byte[]> built = new HashMap<>();
        assets.addTo(built::put);
        assertEquals(3, built.size());
        assertEquals(1, assets.publishedCount());

        servedPack(new ArrayList<>(built.keySet()));
        EquipmentAssets restarted = loaded("equipment-assets:\n  resync-delay-ticks: 0\n");
        assertEquals(1, restarted.publishedCount());
        assertEquals(1, restarted.resyncDelayTicks());
    }

    @Test void customPathsAndNamespaceAreRespected() throws IOException {
        bronzeSources();
        Files.move(plugins.resolve("ItemsAdder/contents"), plugins.resolve("content"));
        EquipmentAssets assets = loaded("equipment-assets:\n  namespace: custom_ns\n  itemsadder-contents: content\n  itemsadder-pack: none.zip\n");
        Map<String, byte[]> built = new HashMap<>();
        assets.addTo(built::put);
        assertTrue(built.containsKey("assets/custom_ns/equipment/tfmc_armor/bronze.json"));

        EquipmentAssets invalid = loaded("equipment-assets:\n  namespace: 'Not Valid'\n  itemsadder-contents: content\n");
        verify(logger).warning(contains("Invalid namespace 'Not Valid'"));
        built.clear();
        invalid.addTo(built::put);
        assertTrue(built.containsKey("assets/tfmc_equipment/equipment/tfmc_armor/bronze.json"));
    }

    @Test void disabledMeansNoAssetsAndNoPackFiles() throws IOException {
        bronzeSources();
        EquipmentAssets assets = loaded("equipment-assets:\n  enabled: false\n");
        assertFalse(assets.isEnabled());
        assertEquals(0, assets.sourceCount());
        assertEquals(0, assets.publishedCount());
        Map<String, byte[]> built = new HashMap<>();
        assets.addTo(built::put);
        assertTrue(built.isEmpty());
        assertNotNull(assets.sync());
    }

    private static ItemStack bronzeLeather() {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.LEATHER_CHESTPLATE);
        Equippable leather = mock(Equippable.class);
        when(leather.assetId()).thenReturn(Key.key("minecraft", "leather"));
        Equippable.Builder builder = mock(Equippable.Builder.class);
        when(leather.toBuilder()).thenReturn(builder);
        when(builder.assetId(any())).thenReturn(builder);
        when(builder.build()).thenReturn(mock(Equippable.class));
        when(item.getData(DataComponentTypes.EQUIPPABLE)).thenReturn(leather);
        DyedItemColor dyed = mock(DyedItemColor.class);
        when(dyed.color()).thenReturn(Color.fromRGB(BRONZE_RGB));
        when(item.getData(DataComponentTypes.DYED_COLOR)).thenReturn(dyed);
        ItemStack plain = mock(ItemStack.class);
        when(plain.getData(DataComponentTypes.EQUIPPABLE)).thenReturn(leather);
        when(item.clone()).thenReturn(plain);
        return item;
    }

    /** An item whose clone is the given synced copy. */
    private static ItemStack holding(ItemStack copy) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.LEATHER_CHESTPLATE);
        when(item.clone()).thenReturn(copy);
        return item;
    }

    private static ItemStack plainItem() {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.STONE);
        when(item.clone()).thenReturn(item);
        return item;
    }

    private EquipmentAssets publishedBronze() throws IOException {
        bronzeSources();
        EquipmentAssets assets = loaded("");
        assets.addTo((name, bytes) -> { });
        return assets;
    }

    @Test void listenerSyncsJoinsArmourChangesStandsAndLoadedMobs() throws IOException {
        EquipmentAssets assets = publishedBronze();
        Plugin plugin = mock(Plugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        List<Runnable> tasks = new ArrayList<>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> { tasks.add(call.getArgument(1)); return null; });
        EquipmentAssetListener listener = new EquipmentAssetListener(plugin, assets);

        ItemStack copy = bronzeLeather();
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack air = mock(ItemStack.class);
        when(air.getType()).thenReturn(Material.AIR);
        ItemStack[] contents = { null, air, plainItem(), holding(copy) };
        when(inventory.getContents()).thenReturn(contents);
        Player player = mock(Player.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.isOnline()).thenReturn(true, false);

        EntityEquipment equipment = mock(EntityEquipment.class);
        ItemStack wornCopy = bronzeLeather();
        ItemStack worn = holding(wornCopy);
        when(equipment.getItem(EquipmentSlot.CHEST)).thenReturn(worn);
        when(player.getEquipment()).thenReturn(equipment);
        when(player.isValid()).thenReturn(true, false);

        ArmorStand stand = mock(ArmorStand.class);
        EntityEquipment standEquipment = mock(EntityEquipment.class);
        ItemStack standCopy = bronzeLeather();
        ItemStack standHead = holding(standCopy);
        when(standEquipment.getItem(EquipmentSlot.HEAD)).thenReturn(standHead);
        when(stand.getEquipment()).thenReturn(standEquipment);
        when(stand.isValid()).thenReturn(true);
        LivingEntity bare = mock(LivingEntity.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            listener.onJoin(new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null));
            listener.onArmourChange(new PlayerArmorChangeEvent(player, PlayerArmorChangeEvent.SlotType.CHEST, null, null));
            PlayerArmorStandManipulateEvent manipulate = mock(PlayerArmorStandManipulateEvent.class);
            when(manipulate.getRightClicked()).thenReturn(stand);
            listener.onArmourStand(manipulate);
            tasks.forEach(Runnable::run);
            // The player has left and the entity is gone by the time these run again.
            tasks.forEach(Runnable::run);
        }
        verify(inventory).setItem(3, copy);
        verify(inventory, times(1)).setItem(anyInt(), any());
        verify(equipment).setItem(EquipmentSlot.CHEST, wornCopy);
        verify(standEquipment, times(2)).setItem(EquipmentSlot.HEAD, standCopy);

        EntitiesLoadEvent load = mock(EntitiesLoadEvent.class);
        when(load.getEntities()).thenReturn(List.<Entity>of(player, bare, stand, mock(Entity.class)));
        listener.onEntitiesLoad(load);
        verify(standEquipment, times(3)).setItem(EquipmentSlot.HEAD, standCopy);
        verify(equipment, times(1)).setItem(any(EquipmentSlot.class), any());
    }

    @Test void resyncCoversOnlinePlayersAndLoadedEntities() throws IOException {
        EquipmentAssets assets = publishedBronze();
        EquipmentAssetListener listener = new EquipmentAssetListener(mock(Plugin.class), assets);
        ItemStack copy = bronzeLeather();
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack[] contents = { holding(copy) };
        when(inventory.getContents()).thenReturn(contents);
        Player player = mock(Player.class);
        when(player.getInventory()).thenReturn(inventory);
        ArmorStand stand = mock(ArmorStand.class);
        EntityEquipment standEquipment = mock(EntityEquipment.class);
        ItemStack standCopy = bronzeLeather();
        ItemStack standLegs = holding(standCopy);
        when(standEquipment.getItem(EquipmentSlot.LEGS)).thenReturn(standLegs);
        when(stand.getEquipment()).thenReturn(standEquipment);
        World world = mock(World.class);
        when(world.getLivingEntities()).thenReturn(List.of(player, stand));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
            listener.resyncAll();
        }
        verify(inventory).setItem(0, copy);
        verify(standEquipment).setItem(EquipmentSlot.LEGS, standCopy);
        verify(player, never()).getEquipment();
    }

    @Test void itemsAdderReloadsRescanAndPackBuildsGetTheAssets() throws IOException {
        EquipmentAssets assets = publishedBronze();
        Plugin plugin = mock(Plugin.class);
        EquipmentAssetListener items = mock(EquipmentAssetListener.class);
        ItemsAdderEquipmentListener listener = new ItemsAdderEquipmentListener(plugin, assets, items);

        Files.writeString(plugins.resolve("ItemsAdder/contents/tfmc_armor/configs/bronze.yml"), "info:\n  namespace: tfmc_armor\n");
        listener.onLoad(new ItemsAdderLoadDataEvent(true));
        assertEquals(0, assets.sourceCount());
        assertEquals(1, assets.publishedCount());

        bronzeSources();
        listener.onLoad(new ItemsAdderLoadDataEvent(true));
        ItemsAdderPackCompressedEvent pack = mock(ItemsAdderPackCompressedEvent.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            listener.onPack(pack);
        }
        verify(pack, times(3)).setEntry(anyString(), any(byte[].class));
        verify(pack).setEntry(eq("assets/tfmc_equipment/equipment/tfmc_armor/bronze.json"), any(byte[].class));
        var task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(plugin), task.capture(), eq(100L));
        task.getValue().run();
        verify(items).resyncAll();
    }
}
