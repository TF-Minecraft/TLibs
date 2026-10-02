package net.tfminecraft.tlibs.objects.api.subapi;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import dev.lone.itemsadder.api.CustomStack;
import net.tfminecraft.gunsandgadgets.GunsAndGadgets;
import net.tfminecraft.gunsandgadgets.guns.skins.SkinData;
import net.tfminecraft.gunsandgadgets.guns.skins.SkinState;
import net.tfminecraft.gunsandgadgets.loader.SkinLoader;
import net.tfminecraft.gunsandgadgets.manager.GunManager;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemPreservationTest.ModelMeta;
import net.tfminecraft.tlibs.objects.api.subapi.ItemPreservationTest.NbtFixture;
import net.tfminecraft.tlibs.util.LegacyModelData;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class ArmorMergerTest {
    private NbtFixture nbt;
    private ItemCreator creator;
    private ArmorMerger merger;
    private MockedStatic<TLibs> tlibs;
    private MockedConstruction<ItemStack> createdItems;

    @BeforeAll static void startServer() { MockBukkit.mock(); }
    @AfterAll static void stopServer() { MockBukkit.unmock(); }
    @BeforeEach
    void prepare() {
        nbt = new NbtFixture();
        ItemAPI api = mock(ItemAPI.class);
        creator = mock(ItemCreator.class);
        Server server = mock(Server.class); PluginManager plugins = mock(PluginManager.class); Plugin itemsAdder = mock(Plugin.class);
        when(server.getPluginManager()).thenReturn(plugins); when(plugins.getPlugin("ItemsAdder")).thenReturn(itemsAdder);
        when(api.getServer()).thenReturn(server);
        when(api.getCreator()).thenReturn(creator);
        merger = new ArmorMerger(api);
        tlibs = mockStatic(TLibs.class);
        tlibs.when(TLibs::getItemAPI).thenReturn(api);
        // Paper ItemStack creation is the external boundary; model metadata behaves normally.
        createdItems = mockConstruction(ItemStack.class, (item, context) -> {
            Material material = (Material) context.arguments().get(0);
            nbt.attach(item, material, material.isAir() ? null : new ModelMeta());
        });
    }
    @AfterEach
    void cleanup() {
        if (createdItems != null) createdItems.close();
        if (tlibs != null) tlibs.close();
        if (nbt != null) nbt.close();
    }

    @Test
    void localModelChangesAppearanceInPlaceAndPreservesMmoTags() {
        ItemStack item = nbt.item(Material.DIAMOND_SWORD);
        nbt.data(item).put("MMOITEMS_ITEM_TYPE", "SWORD");
        assertSame(item, merger.merge(item, Optional.of("Named"), "localmodel(emerald.31)"));
        assertEquals(Material.EMERALD, item.getType());
        assertEquals("Named", item.getItemMeta().getDisplayName());
        assertEquals(31, LegacyModelData.get(item.getItemMeta()));
        assertEquals("31", nbt.data(item).get("amodel"));
        assertEquals("SWORD", nbt.data(item).get("MMOITEMS_ITEM_TYPE"));
    }

    @Test
    void ordinaryAndIaSkinsCopyAppearanceAndNamespaceIdentity() {
        ItemStack plain = nbt.item(Material.EMERALD);
        when(creator.getItemFromPath("v.emerald")).thenReturn(plain);
        ItemStack item = nbt.item(Material.DIAMOND_SWORD);
        assertSame(item, merger.merge(item, Optional.empty(), "v.emerald"));
        assertEquals(Material.EMERALD, item.getType());
        assertFalse(LegacyModelData.has(item.getItemMeta()));
        ItemStack leather = nbt.item(Material.LEATHER_CHESTPLATE);
        ItemMeta appearance = leather.getItemMeta();
        ((LeatherArmorMeta) appearance).setColor(Color.RED);
        LegacyModelData.set(appearance, 8);
        leather.setItemMeta(appearance);
        when(creator.getItemsAdderItem("tfmc:red")).thenReturn(leather);
        ItemStack armor = nbt.item(Material.LEATHER_CHESTPLATE);
        assertSame(armor, merger.merge(armor, Optional.of("Red armor"), "ia.tfmc:red"));
        assertEquals("tfmc.red", nbt.data(armor).get("ia"));
        assertEquals(Map.of("namespace", "tfmc", "id", "red"), nbt.data(armor).get("itemsadder"));
        assertEquals(Color.RED, ((LeatherArmorMeta) armor.getItemMeta()).getColor());
        assertEquals(8, LegacyModelData.get(armor.getItemMeta()));
        assertEquals("Red armor", armor.getItemMeta().getDisplayName());
    }

    @Test
    void unresolvedBuiltinIaSkinNeverUsesTheCreatorsDirtFallback() {
        ItemAPI actual = realApi(true);
        try (MockedStatic<CustomStack> stacks = mockStatic(CustomStack.class)) {
            assertEquals(Material.DIRT, actual.getCreator().getItemFromPath("ia.tfmc:missing").getType());
            ItemStack original = nbt.item(Material.DIAMOND_SWORD);
            ItemMeta meta = original.getItemMeta(); meta.setDisplayName("Original"); LegacyModelData.set(meta, 17); original.setItemMeta(meta);
            nbt.data(original).put("MMOITEMS_ITEM_TYPE", "SWORD");
            assertSame(original, actual.getArmorMerger().merge(original, Optional.empty(), "ia.tfmc:missing"));
            assertAll(() -> assertEquals(Material.DIAMOND_SWORD, original.getType()),
                    () -> assertEquals(17, LegacyModelData.get(original.getItemMeta())),
                    () -> assertEquals("Original", original.getItemMeta().getDisplayName()),
                    () -> assertEquals(Map.of("MMOITEMS_ITEM_TYPE", "SWORD"), nbt.data(original)));
        }
    }

    @Test
    void builtinIaResolutionStillAppliesValidSdkSkins() {
        ItemAPI actual = realApi(true); ItemStack skin = nbt.item(Material.EMERALD);
        ItemMeta meta = skin.getItemMeta(); LegacyModelData.set(meta, 23); skin.setItemMeta(meta);
        CustomStack defined = mock(CustomStack.class); when(defined.getItemStack()).thenReturn(skin);
        try (MockedStatic<CustomStack> stacks = mockStatic(CustomStack.class)) {
            stacks.when(() -> CustomStack.getInstance("tfmc:green")).thenReturn(defined);
            ItemStack original = nbt.item(Material.DIAMOND_SWORD);
            assertSame(original, actual.getArmorMerger().merge(original, Optional.empty(), "ia.tfmc:green"));
            assertEquals(Material.EMERALD, original.getType()); assertEquals(23, LegacyModelData.get(original.getItemMeta()));
            assertEquals("tfmc.green", nbt.data(original).get("ia"));
        }
    }

    @Test
    void absentIaPluginLeavesBuiltinSkinUntouchedButRegisteredHandlerStillWorks() {
        ItemAPI actual = realApi(false); ItemStack original = nbt.item(Material.DIAMOND_SWORD);
        try (MockedStatic<CustomStack> stacks = mockStatic(CustomStack.class)) {
            assertSame(original, actual.getArmorMerger().merge(original, Optional.empty(), "ia.tfmc:custom"));
            assertEquals(Material.DIAMOND_SWORD, original.getType()); assertTrue(nbt.data(original).isEmpty());
            ItemStack custom = nbt.item(Material.EMERALD); ItemPathHandler handler = mock(ItemPathHandler.class);
            when(handler.create("ia.tfmc:custom")).thenReturn(custom); actual.registerPathHandler("IA", handler);
            assertSame(original, actual.getArmorMerger().merge(original, Optional.empty(), "ia.tfmc:custom"));
            assertEquals(Material.EMERALD, original.getType()); verify(handler).create("ia.tfmc:custom");
            stacks.verifyNoInteractions();
        }
    }

    private ItemAPI realApi(boolean itemsAdderPresent) {
        Server server = mock(Server.class); PluginManager plugins = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(plugins);
        if (itemsAdderPresent) { Plugin plugin = mock(Plugin.class); when(plugins.getPlugin("ItemsAdder")).thenReturn(plugin); }
        ItemAPI actual = new ItemAPI(); actual.setup(server); tlibs.when(TLibs::getItemAPI).thenReturn(actual); return actual;
    }

    @Test
    void malformedOrUnresolvedSkinPathsLeaveTheItemUnchanged() {
        assertAll(List.of("v.invalid", "localmodel", "localmodel()", "localmodel(stone", "localmodel(stone)", "localmodel(stone.nope)", "localmodel(invalid.9)", "localmodel(air.3)", "gunskin", "gunskin()", "ia.missing", "ia.:id", "ia.tfmc:", "ia.tfmc:id:extra").stream()
                .map(path -> () -> {
                    ItemStack original = nbt.item(Material.DIAMOND_SWORD);
                    assertAll(path,
                            () -> assertSame(original, merger.merge(original, Optional.empty(), path)),
                            () -> assertEquals(Material.DIAMOND_SWORD, original.getType()));
                }));
    }


    @Test
    void emptyArgumentsAndMissingOrAirSkinDoNotMutateTheTarget() {
        ItemStack original = nbt.item(Material.DIAMOND_SWORD);
        assertNull(merger.merge(null, Optional.empty(), "v.emerald"));
        assertSame(original, merger.merge(original, Optional.empty(), null));
        assertSame(original, merger.merge(original, Optional.empty(), " "));
        ItemStack airSkin = nbt.item(Material.AIR);
        when(creator.getItemFromPath("v.air")).thenReturn(airSkin);
        assertSame(original, merger.merge(original, Optional.empty(), "v.air"));
        assertEquals(Material.DIAMOND_SWORD, original.getType());
        assertTrue(nbt.data(original).isEmpty());
    }

    @Test
    void localModelMaterialParsingIgnoresTheHostLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            ItemStack item = nbt.item(Material.STONE);
            merger.merge(item, Optional.empty(), "localmodel(diamond.3)");
            assertEquals(Material.DIAMOND, item.getType());
        } finally { Locale.setDefault(previous); }
    }

    @Test
    void gunSkinsSelectLoadedOrCarryStateAndApplyOptionalNames() {
        GunsAndGadgets plugin = mock(GunsAndGadgets.class);
        GunManager manager = mock(GunManager.class);
        SkinData skin = mock(SkinData.class);
        when(plugin.namespace()).thenReturn("gunsandgadgets");
        when(plugin.getGunManager()).thenReturn(manager);
        when(skin.getId()).thenReturn("red");
        when(manager.applyModel(any(), eq(skin), any())).thenAnswer(call -> call.getArgument(0));
        try (MockedStatic<GunsAndGadgets> guns = mockStatic(GunsAndGadgets.class);
             MockedStatic<SkinLoader> skins = mockStatic(SkinLoader.class)) {
            guns.when(GunsAndGadgets::getInstance).thenReturn(plugin);
            skins.when(() -> SkinLoader.getByString("red")).thenReturn(skin);
            ItemStack loaded = nbt.item(Material.STICK);
            ItemMeta meta = loaded.getItemMeta();
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "bullets_loaded"), PersistentDataType.INTEGER, 2);
            loaded.setItemMeta(meta);
            assertSame(loaded, merger.merge(loaded, Optional.of("Red gun"), "gunskin(red)"));
            verify(manager).applyModel(loaded, skin, SkinState.AIM);
            assertEquals("Red gun", loaded.getItemMeta().getDisplayName());
            assertEquals("red", loaded.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "skin_id"), PersistentDataType.STRING));
            ItemStack empty = nbt.item(Material.STICK);
            assertSame(empty, merger.merge(empty, Optional.empty(), "gunskin(red)"));
            verify(manager).applyModel(empty, skin, SkinState.CARRY);
            assertSame(empty, merger.merge(empty, Optional.empty(), "gunskin(missing)"));
            ItemStack noMeta = nbt.item(Material.AIR);
            assertSame(noMeta, merger.merge(noMeta, Optional.of("ignored"), "gunskin(red)"));
            when(manager.applyModel(empty, skin, SkinState.CARRY)).thenReturn(noMeta);
            assertSame(noMeta, merger.merge(empty, Optional.of("ignored"), "gunskin(red)"));
        }
    }
}
