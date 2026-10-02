package net.tfminecraft.tlibs.objects.api.subapi;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.NBTType;
import de.tr7zw.nbtapi.iface.ReadWriteItemNBT;
import de.tr7zw.nbtapi.iface.ReadableNBT;
import io.lumine.mythic.lib.api.item.ItemTag;
import io.lumine.mythic.lib.api.item.NBTItem;
import net.tfminecraft.tlibs.armour.MmoItemTagPreserver;
import net.tfminecraft.tlibs.armour.MmoItemTagPreserver.SavedTag;
import net.tfminecraft.tlibs.config.RebuildConfig;
import net.tfminecraft.tlibs.util.LegacyModelData;
import net.tfminecraft.tlibs.utils.ItemNbtDebug;
import net.tfminecraft.tlibs.utils.PersistentDataCopier;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

class ItemPreservationTest {
    @BeforeAll static void startServer() { MockBukkit.mock(); }
    @AfterAll static void stopServer() { MockBukkit.unmock(); }

    @Test
    void copiesEveryPersistentDataTypeIncludingListsAndContainerArrays() {
        ItemMeta from = new org.mockbukkit.mockbukkit.inventory.meta.ItemMetaMock();
        ItemMeta to = new org.mockbukkit.mockbukkit.inventory.meta.ItemMetaMock();
        PersistentDataContainer data = from.getPersistentDataContainer();
        data.set(key("string"), PersistentDataType.STRING, "value");
        data.set(key("integer"), PersistentDataType.INTEGER, 42);
        data.set(key("long"), PersistentDataType.LONG, 43L);
        data.set(key("double"), PersistentDataType.DOUBLE, 1.5);
        data.set(key("float"), PersistentDataType.FLOAT, 2.5f);
        data.set(key("byte"), PersistentDataType.BYTE, (byte) 3);
        data.set(key("short"), PersistentDataType.SHORT, (short) 4);
        data.set(key("boolean"), PersistentDataType.BOOLEAN, true);
        data.set(key("bytes"), PersistentDataType.BYTE_ARRAY, new byte[]{1, 2});
        data.set(key("integers"), PersistentDataType.INTEGER_ARRAY, new int[]{3, 4});
        data.set(key("longs"), PersistentDataType.LONG_ARRAY, new long[]{5, 6});
        PersistentDataContainer nested = data.getAdapterContext().newPersistentDataContainer();
        nested.set(key("child"), PersistentDataType.STRING, "nested");
        data.set(key("nested"), PersistentDataType.TAG_CONTAINER, nested);
        data.set(key("array"), PersistentDataType.TAG_CONTAINER_ARRAY, new PersistentDataContainer[]{nested});
        data.set(key("list"), PersistentDataType.LIST.strings(), List.of("one", "two"));
        to.getPersistentDataContainer().set(key("target_only"), PersistentDataType.INTEGER, 9);
        to.getPersistentDataContainer().set(key("integer"), PersistentDataType.INTEGER, 99);
        PersistentDataCopier.copy(from, to);
        PersistentDataContainer copied = to.getPersistentDataContainer();
        assertAll(
                () -> assertEquals("value", copied.get(key("string"), PersistentDataType.STRING)),
                () -> assertEquals(42, copied.get(key("integer"), PersistentDataType.INTEGER)),
                () -> assertEquals(43L, copied.get(key("long"), PersistentDataType.LONG)),
                () -> assertEquals(1.5, copied.get(key("double"), PersistentDataType.DOUBLE)),
                () -> assertEquals(2.5f, copied.get(key("float"), PersistentDataType.FLOAT)),
                () -> assertEquals((byte) 3, copied.get(key("byte"), PersistentDataType.BYTE)),
                () -> assertEquals((short) 4, copied.get(key("short"), PersistentDataType.SHORT)),
                () -> assertTrue(copied.get(key("boolean"), PersistentDataType.BOOLEAN)),
                () -> assertArrayEquals(new byte[]{1, 2}, copied.get(key("bytes"), PersistentDataType.BYTE_ARRAY)),
                () -> assertArrayEquals(new int[]{3, 4}, copied.get(key("integers"), PersistentDataType.INTEGER_ARRAY)),
                () -> assertArrayEquals(new long[]{5, 6}, copied.get(key("longs"), PersistentDataType.LONG_ARRAY)),
                () -> assertEquals("nested", copied.get(key("nested"), PersistentDataType.TAG_CONTAINER).get(key("child"), PersistentDataType.STRING)),
                () -> assertNotNull(copied.get(key("array"), PersistentDataType.TAG_CONTAINER_ARRAY)),
                () -> assertEquals(List.of("one", "two"), copied.get(key("list"), PersistentDataType.LIST.strings())),
                () -> assertEquals(9, copied.get(key("target_only"), PersistentDataType.INTEGER)));
    }

    @Test
    void snapshotsDetachPersistentDataAndNullArgumentsAreHarmless() {
        try (NbtFixture nbt = new NbtFixture()) {
            ItemStack item = nbt.item(Material.STONE);
            assertNull(PersistentDataCopier.snapshot(null));
            assertNull(PersistentDataCopier.snapshot(item));
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(key("value"), PersistentDataType.STRING, "before");
            item.setItemMeta(meta);
            PersistentDataContainer snapshot = PersistentDataCopier.snapshot(item);
            meta.getPersistentDataContainer().set(key("value"), PersistentDataType.STRING, "after");
            item.setItemMeta(meta);
            assertEquals("before", snapshot.get(key("value"), PersistentDataType.STRING));
            ItemStack target = nbt.item(Material.STONE);
            PersistentDataCopier.applySnapshot(target, snapshot);
            assertEquals("before", target.getItemMeta().getPersistentDataContainer().get(key("value"), PersistentDataType.STRING));
            PersistentDataCopier.copy((ItemMeta) null, meta);
            PersistentDataCopier.copy(meta, (ItemMeta) null);
            PersistentDataCopier.copy((PersistentDataContainer) null, snapshot);
            PersistentDataCopier.copy(snapshot, (PersistentDataContainer) null);
            PersistentDataCopier.applySnapshot(null, snapshot);
            PersistentDataCopier.applySnapshot(target, null);
            PersistentDataCopier.applySnapshot(target, nbt.item(Material.STONE).getItemMeta().getPersistentDataContainer());
            PersistentDataCopier.applySnapshot(nbt.item(Material.AIR), snapshot);
            assertEquals("PublicBukkitValues", PersistentDataCopier.publicBukkitValuesTag());
        }
    }

    @Test
    void legacyModelAccessReplacesComponentAndSupportsClearing() {
        ItemMeta meta = new ModelMeta();
        assertFalse(LegacyModelData.has(meta));
        assertThrows(IllegalStateException.class, () -> LegacyModelData.get(meta));
        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        component.setFloats(List.of(1f, 2f)); component.setFlags(List.of(true));
        component.setStrings(List.of("old")); component.setColors(List.of(Color.RED));
        meta.setCustomModelDataComponent(component);
        LegacyModelData.set(meta, 27);
        assertTrue(LegacyModelData.has(meta));
        assertEquals(27, LegacyModelData.get(meta));
        assertEquals(List.of(27f), meta.getCustomModelDataComponent().getFloats());
        assertEquals(List.of(), meta.getCustomModelDataComponent().getFlags());
        assertEquals(List.of(), meta.getCustomModelDataComponent().getStrings());
        assertEquals(List.of(), meta.getCustomModelDataComponent().getColors());
        LegacyModelData.set(meta, null);
        assertFalse(LegacyModelData.has(meta));
    }

    @Test
    void mmoTagsSnapshotAndRestoreOnlyMissingOwnedTags() {
        try (NbtFixture nbt = new NbtFixture()) {
            ItemStack item = nbt.item(Material.DIAMOND_SWORD);
            Map<String, Object> original = nbt.data(item);
            original.putAll(Map.of("other", "ignore", "MMOITEMS_ITEM_TYPE", "SWORD", "MMOITEMS_DURABILITY", 17,
                    "MMOITEMS_MAX_DURABILITY", 40, "MMOITEMS_DAMAGE", 3.5, "MMOITEMS_FLAG", (byte) 1,
                    "MMOITEMS_GEM_STONES", new LinkedHashMap<>(Map.of("gem", "ruby"))));
            assertTrue(MmoItemTagPreserver.hasItemType(item));
            assertTrue(MmoItemTagPreserver.hasCustomDurability(item));
            assertTrue(MmoItemTagPreserver.hasDurabilityValue(item));
            assertEquals(17, MmoItemTagPreserver.customDurability(item));
            assertEquals(40, MmoItemTagPreserver.customMaxDurability(item));
            List<SavedTag> saved = MmoItemTagPreserver.snapshot(item);
            assertEquals(6, saved.size());
            assertFalse(saved.stream().anyMatch(tag -> tag.key().equals("other")));
            ItemStack target = nbt.item(Material.IRON_SWORD);
            nbt.data(target).put("MMOITEMS_DURABILITY", 25);
            List<SavedTag> nullable = new ArrayList<>(saved);
            nullable.add(null);
            nullable.add(new SavedTag(null, NBTType.NBTTagString, "invalid", 0, 0, (byte) 0, null));
            MmoItemTagPreserver.restoreMissing(target, nullable);
            assertEquals(25, MmoItemTagPreserver.customDurability(target));
            assertEquals("SWORD", nbt.data(target).get("MMOITEMS_ITEM_TYPE"));
            assertEquals(3.5, nbt.data(target).get("MMOITEMS_DAMAGE"));
            assertEquals((byte) 1, nbt.data(target).get("MMOITEMS_FLAG"));
            assertEquals(Map.of("gem", "ruby"), nbt.data(target).get("MMOITEMS_GEM_STONES"));
            assertFalse(nbt.data(target).containsKey("other"));
            assertEquals(List.of(), MmoItemTagPreserver.snapshot(null));
            assertEquals(List.of(), MmoItemTagPreserver.snapshot(nbt.item(Material.AIR)));
            MmoItemTagPreserver.restoreMissing(null, saved);
            MmoItemTagPreserver.restoreMissing(nbt.item(Material.AIR), saved);
            MmoItemTagPreserver.restoreMissing(target, null);
            MmoItemTagPreserver.restoreMissing(target, List.of());
            assertFalse(MmoItemTagPreserver.hasItemType(null));
            assertFalse(MmoItemTagPreserver.hasCustomDurability(nbt.item(Material.AIR)));
            assertEquals(0, MmoItemTagPreserver.customMaxDurability(nbt.item(Material.STONE)));
        }
    }

    @Test
    void detectsLegacyAndCompoundSkinMarkersAndIgnoresIncompleteCompounds() {
        try (NbtFixture nbt = new NbtFixture()) {
            ItemStack item = nbt.item(Material.STONE);
            assertFalse(ItemSkinPreserver.hasSkinData(null));
            assertFalse(ItemSkinPreserver.hasSkinData(nbt.item(Material.AIR)));
            assertFalse(ItemSkinPreserver.hasSkinData(item));
            nbt.data(item).put("itemsadder", "wrong type");
            assertFalse(ItemSkinPreserver.hasSkinData(item));
            nbt.data(item).put("itemsadder", new LinkedHashMap<>(Map.of("namespace", "tfmc")));
            assertFalse(ItemSkinPreserver.hasSkinData(item));
            nbt.data(item).put("itemsadder", new LinkedHashMap<>(Map.of("id", "skin")));
            assertFalse(ItemSkinPreserver.hasSkinData(item));
            nbt.data(item).put("itemsadder", new LinkedHashMap<>(Map.of("namespace", "tfmc", "id", "skin")));
            assertTrue(ItemSkinPreserver.hasSkinData(item));
            ItemSkinPreserver.writeIaTag(item, "tfmc", "skin");
            assertEquals("tfmc.skin", nbt.data(item).get("ia"));
            ItemSkinPreserver.writeAmodel(item, 9);
            assertEquals("9", nbt.data(item).get("amodel"));
            assertEquals(9, LegacyModelData.get(item.getItemMeta()));
            ItemSkinPreserver.writeAmodel(nbt.item(Material.AIR), 3);
        }
    }

    @Test
    void skinApplicationClonesReplacementAndCarriesModelColorAndIaIdentity() {
        try (NbtFixture nbt = new NbtFixture()) {
            ItemStack old = nbt.item(Material.LEATHER_CHESTPLATE);
            ItemMeta appearance = old.getItemMeta();
            LegacyModelData.set(appearance, 25);
            ((LeatherArmorMeta) appearance).setColor(Color.BLUE);
            old.setItemMeta(appearance);
            nbt.data(old).putAll(Map.of("ia", "tfmc.blue_skin", "amodel", "12"));
            ItemStack replacement = nbt.item(Material.LEATHER_CHESTPLATE);
            ((org.bukkit.inventory.meta.Damageable) nbt.meta(replacement)).setDamage(6);
            ((org.bukkit.inventory.meta.Damageable) nbt.meta(replacement)).setMaxDamage(50);
            nbt.data(replacement).put("MMOITEMS_ITEM_TYPE", "ARMOR");
            ItemStack applied = ItemSkinPreserver.apply(old, replacement);
            assertNotSame(replacement, applied);
            assertEquals(25, LegacyModelData.get(applied.getItemMeta()));
            assertEquals(Color.BLUE, ((LeatherArmorMeta) applied.getItemMeta()).getColor());
            assertEquals("tfmc.blue_skin", nbt.data(applied).get("ia"));
            assertEquals(Map.of("namespace", "tfmc", "id", "blue_skin"), nbt.data(applied).get("itemsadder"));
            assertFalse(LegacyModelData.has(replacement.getItemMeta()));
            assertSame(replacement, ItemSkinPreserver.apply(null, replacement));
            assertSame(replacement, ItemSkinPreserver.apply(nbt.item(Material.AIR), replacement));
            assertSame(replacement, ItemSkinPreserver.apply(nbt.item(Material.STONE), replacement));
            assertNull(ItemSkinPreserver.apply(old, null));
        }
    }

    @Test
    void skinApplicationUsesLegacyModelFallbackAndCompoundOnlyIdentity() {
        try (NbtFixture nbt = new NbtFixture()) {
            ItemStack old = nbt.item(Material.EMERALD);
            ItemStack result = nbt.item(Material.STONE);
            nbt.data(old).put("amodel", "11");
            assertSame(result, ItemSkinPreserver.applyAppearanceFromSkin(old, result));
            assertEquals(11, LegacyModelData.get(result.getItemMeta()));
            assertEquals(Material.EMERALD, result.getType());
            nbt.data(old).put("amodel", "bad");
            nbt.data(old).put("ia", "malformed");
            nbt.data(old).put("itemsadder", new LinkedHashMap<>(Map.of("namespace", "tfmc", "id", "gem")));
            ItemStack another = nbt.item(Material.DIAMOND);
            ItemSkinPreserver.applyAppearanceFromSkin(old, another);
            assertFalse(LegacyModelData.has(another.getItemMeta()));
            assertEquals(Map.of("namespace", "tfmc", "id", "gem"), nbt.data(another).get("itemsadder"));
            ItemMeta nonDamageable = mock(ItemMeta.class);
            when(nonDamageable.clone()).thenReturn(nonDamageable);
            ItemStack bareMeta = nbt.item(Material.STONE, nonDamageable);
            ItemSkinPreserver.applyAppearance(bareMeta, Material.DIRT, null, null);
            assertEquals(Material.DIRT, bareMeta.getType());
            ItemStack absent = nbt.item(Material.AIR);
            ItemSkinPreserver.applyAppearance(absent, Material.AIR, null, null);
        }
    }

    @Test
    void explicitAppearanceMutatesTheExistingItemAndPreservesOwnedTags() {
        try (NbtFixture nbt = new NbtFixture()) {
            ItemStack item = nbt.item(Material.LEATHER_CHESTPLATE);
            nbt.data(item).put("MMOITEMS_ITEM_TYPE", "ARMOR");
            ItemSkinPreserver.applyAppearance(item, Material.LEATHER_CHESTPLATE, 9, Color.RED);
            assertEquals(9, LegacyModelData.get(item.getItemMeta()));
            assertEquals(Color.RED, ((LeatherArmorMeta) item.getItemMeta()).getColor());
            assertEquals("ARMOR", nbt.data(item).get("MMOITEMS_ITEM_TYPE"));
        }
    }

    @Test
    void baselineMergePreservesConfiguredScalarsCompoundsAndPersistentData() {
        try (NbtFixture nbt = new NbtFixture()) {
            ItemStack old = nbt.item(Material.STONE);
            ItemStack replacement = nbt.item(Material.DIAMOND);
            ItemMeta meta = old.getItemMeta();
            meta.getPersistentDataContainer().set(key("source"), PersistentDataType.STRING, "old");
            old.setItemMeta(meta);
            nbt.data(old).putAll(Map.of("scalar", "keep", "compound", new LinkedHashMap<>(Map.of("value", 7)),
                    "PublicBukkitValues", new LinkedHashMap<>(Map.of("raw:value", "keep"))));
            RebuildConfig config = mock(RebuildConfig.class);
            when(config.getPreserveNbtTags()).thenReturn(List.of("scalar", "compound", "absent"));
            when(config.copyPersistentData()).thenReturn(true);
            ItemStack result = ItemRebuildMerger.applyBaseline(old, replacement, config);
            assertNotSame(replacement, result);
            assertEquals("keep", nbt.data(result).get("scalar"));
            assertEquals(Map.of("value", 7), nbt.data(result).get("compound"));
            assertEquals(Map.of("raw:value", "keep"), nbt.data(result).get("PublicBukkitValues"));
            assertEquals("old", result.getItemMeta().getPersistentDataContainer().get(key("source"), PersistentDataType.STRING));
            assertTrue(nbt.data(replacement).isEmpty());
            assertNull(ItemRebuildMerger.applyBaseline(old, null, config));
            assertSame(replacement, ItemRebuildMerger.applyBaseline(null, replacement, config));
            assertSame(replacement, ItemRebuildMerger.applyBaseline(nbt.item(Material.AIR), replacement, config));
        }
    }

    @Test
    void baselineUsesExplicitSnapshotAndAppliesOptionalAppearance() {
        try (NbtFixture nbt = new NbtFixture()) {
            ItemStack old = nbt.item(Material.EMERALD);
            nbt.data(old).put("amodel", "8");
            ItemStack replacement = nbt.item(Material.DIAMOND);
            RebuildConfig config = mock(RebuildConfig.class);
            when(config.getPreserveNbtTags()).thenReturn(List.of());
            when(config.copyAppearance()).thenReturn(true);
            when(config.copyPersistentData()).thenReturn(true);
            PersistentDataContainer saved = old.getItemMeta().getPersistentDataContainer();
            saved.set(key("earlier"), PersistentDataType.INTEGER, 4);
            ItemStack result = ItemRebuildMerger.applyBaseline(old, replacement, config, saved);
            assertEquals(Material.EMERALD, result.getType());
            assertEquals(8, LegacyModelData.get(result.getItemMeta()));
            assertEquals(4, result.getItemMeta().getPersistentDataContainer().get(key("earlier"), PersistentDataType.INTEGER));
            assertEquals(Material.DIAMOND, ItemRebuildMerger.applyBaseline(nbt.item(Material.STONE), replacement, config).getType());
            when(config.copyPersistentData()).thenReturn(false);
            assertEquals(Material.EMERALD, ItemRebuildMerger.applyBaseline(old, replacement, config).getType());
        }
    }

    @Test
    void debugOutputSummarizesDataAndContainsNbtReadErrors() {
        try (NbtFixture nbt = new NbtFixture()) {
            ItemStack item = nbt.item(Material.STONE);
            assertEquals("{id:\"minecraft:air\",Count:0b}", ItemNbtDebug.toSnbt(null));
            assertEquals("{id:\"minecraft:air\",Count:0b}", ItemNbtDebug.toSnbt(nbt.item(Material.AIR)));
            nbt.data(item).put("test", "value");
            assertEquals("{test=value}", ItemNbtDebug.toSnbt(item));
            assertEquals("pdc=<none>", ItemNbtDebug.pdcSummary((ItemStack) null));
            assertEquals("pdc=<none>", ItemNbtDebug.pdcSummary(nbt.item(Material.AIR)));
            assertEquals("pdc=<empty>", ItemNbtDebug.pdcSummary(item));
            assertEquals("pdcSnapshot=<empty>", ItemNbtDebug.pdcSummary((PersistentDataContainer) null));
            ItemMeta meta = item.getItemMeta();
            assertEquals("pdcSnapshot=<empty>", ItemNbtDebug.pdcSummary(meta.getPersistentDataContainer()));
            meta.getPersistentDataContainer().set(key("one"), PersistentDataType.INTEGER, 1);
            item.setItemMeta(meta);
            assertEquals("pdc=test:one", ItemNbtDebug.pdcSummary(item));
            assertEquals("pdcSnapshot=test:one", ItemNbtDebug.pdcSummary(meta.getPersistentDataContainer()));
            nbt.failReads = true;
            assertEquals("{error:\"NBT unavailable\"}", ItemNbtDebug.toSnbt(item));
        }
    }

    static NamespacedKey key(String value) { return new NamespacedKey("test", value); }

    static final class NbtFixture implements AutoCloseable {
        private final IdentityHashMap<ItemStack, Store> items = new IdentityHashMap<>();
        private final IdentityHashMap<ReadableNBT, Map<String, Object>> compounds = new IdentityHashMap<>();
        private final Map<String, Map<String, Object>> serialized = new HashMap<>();
        private final MockedStatic<NBT> nbt;
        private final MockedStatic<NBTItem> mythic;
        boolean failReads;
        @SuppressWarnings({"unchecked", "rawtypes"})
        NbtFixture() {
            nbt = mockStatic(NBT.class, call -> {
                String method = call.getMethod().getName();
                if (method.equals("parseNBT")) return compound(deepCopy(serialized.get(call.getArgument(0))));
                if (method.equals("get") || method.equals("modify")) {
                    if (failReads) throw new IllegalStateException("NBT unavailable");
                    Object callback = call.getArgument(1);
                    ReadWriteItemNBT value = compound(data(call.getArgument(0)));
                    if (callback instanceof Function function) return function.apply(value);
                    ((Consumer) callback).accept(value); return null;
                }
                return org.mockito.Answers.RETURNS_DEFAULTS.answer(call);
            });
            mythic = mockStatic(NBTItem.class, call -> {
                if (call.getMethod().getName().equals("get")) return mythicItem(call.getArgument(0));
                return org.mockito.Answers.RETURNS_DEFAULTS.answer(call);
            });
        }
        ItemStack item(Material material) { return item(material, material.isAir() ? null : material.name().startsWith("LEATHER_") ? new ModelLeatherMeta() : new ModelMeta()); }
        ItemStack item(Material material, ItemMeta meta) {
            ItemStack item = mock(ItemStack.class);
            attach(item, material, meta);
            return item;
        }
        void attach(ItemStack item, Material material, ItemMeta meta) {
            Store state = new Store(); state.type = material; state.meta = meta;
            items.put(item, state);
            when(item.getType()).thenAnswer(call -> state.type);
            doAnswer(call -> { state.type = call.getArgument(0); return null; }).when(item).setType(any(Material.class));
            when(item.hasItemMeta()).thenAnswer(call -> state.meta != null);
            when(item.getItemMeta()).thenAnswer(call -> state.meta == null ? null : state.meta.clone());
            when(item.setItemMeta(any())).thenAnswer(call -> { ItemMeta value = call.getArgument(0); state.meta = value == null ? null : value.clone(); return true; });
            when(item.clone()).thenAnswer(call -> { ItemStack copy = item(state.type, state.meta == null ? null : state.meta.clone()); data(copy).putAll(deepCopy(state.data)); return copy; });
        }
        ItemMeta meta(ItemStack item) { return items.get(item).meta; }
        Map<String, Object> data(ItemStack item) { return items.get(item).data; }
        private NBTItem mythicItem(ItemStack item) {
            return mock(NBTItem.class, call -> {
                Map<String, Object> data = data(item);
                return switch (call.getMethod().getName()) {
                    case "hasTag" -> data.containsKey(call.getArgument(0));
                    case "getString" -> Objects.toString(data.get(call.getArgument(0)), "");
                    case "toItem" -> item;
                    case "addTag" -> {
                        Object argument = call.getRawArguments()[0];
                        Iterable<ItemTag> tags = argument instanceof ItemTag[] array ? Arrays.asList(array) : (List<ItemTag>) argument;
                        for (ItemTag tag : tags) data.put(tag.getPath(), tag.getValue());
                        yield call.getMock();
                    }
                    default -> org.mockito.Answers.RETURNS_DEFAULTS.answer(call);
                };
            });
        }
        @SuppressWarnings("unchecked")
        private ReadWriteItemNBT compound(Map<String, Object> data) {
            ReadWriteItemNBT value = mock(ReadWriteItemNBT.class, call -> {
                String method = call.getMethod().getName();
                Object[] args = call.getArguments();
                String key = args.length > 0 && args[0] instanceof String text ? text : null;
                if (method.equals("toString")) { String text = data.toString(); serialized.put(text, deepCopy(data)); return text; }
                if (method.equals("getKeys")) return data.keySet();
                if (method.equals("hasTag")) return data.containsKey(key);
                if (method.equals("getType")) return type(data.get(key));
                if (method.equals("getCompound")) return data.get(key) instanceof Map<?, ?> nested ? compound((Map<String, Object>) nested) : null;
                if (method.equals("getOrCreateCompound")) return compound((Map<String, Object>) data.computeIfAbsent(key, unused -> new LinkedHashMap<>()));
                if (method.equals("mergeCompound")) { data.putAll(deepCopy(compounds.get(args[0]))); return null; }
                if (method.startsWith("set") && args.length == 2) { data.put(key, args[1]); return null; }
                if (method.equals("getString")) return Objects.toString(data.get(key), "");
                if (method.equals("getInteger")) return data.getOrDefault(key, 0);
                if (method.equals("getDouble")) return data.getOrDefault(key, 0.0);
                if (method.equals("getByte")) return data.getOrDefault(key, (byte) 0);
                return org.mockito.Answers.RETURNS_DEFAULTS.answer(call);
            });
            compounds.put(value, data); return value;
        }
        static NBTType type(Object value) {
            if (value instanceof String) return NBTType.NBTTagString;
            if (value instanceof Integer) return NBTType.NBTTagInt;
            if (value instanceof Double) return NBTType.NBTTagDouble;
            if (value instanceof Byte) return NBTType.NBTTagByte;
            if (value instanceof Map<?, ?>) return NBTType.NBTTagCompound;
            return NBTType.NBTTagEnd;
        }
        @SuppressWarnings("unchecked")
        static Map<String, Object> deepCopy(Map<String, Object> original) {
            Map<String, Object> result = new LinkedHashMap<>();
            original.forEach((key, value) -> result.put(key, value instanceof Map<?, ?> map ? deepCopy((Map<String, Object>) map) : value));
            return result;
        }
        @Override public void close() { mythic.close(); nbt.close(); }
        private static final class Store { Material type; ItemMeta meta; Map<String, Object> data = new LinkedHashMap<>(); }
    }

    static class ModelMeta extends org.mockbukkit.mockbukkit.inventory.meta.ItemMetaMock {
        private ModelComponent model = new ModelComponent();
        ModelMeta() {}
        ModelMeta(ModelMeta original) { super(original); model = new ModelComponent(original.model); }
        @Override public CustomModelDataComponent getCustomModelDataComponent() { return new ModelComponent(model); }
        @Override public void setCustomModelDataComponent(CustomModelDataComponent value) { model = value == null ? new ModelComponent() : new ModelComponent(value); }
        @Override public ModelMeta clone() { return new ModelMeta(this); }
    }
    static final class ModelLeatherMeta extends org.mockbukkit.mockbukkit.inventory.meta.LeatherArmorMetaMock {
        private ModelComponent model = new ModelComponent();
        ModelLeatherMeta() {}
        ModelLeatherMeta(ModelLeatherMeta original) { super(original); model = new ModelComponent(original.model); }
        @Override public CustomModelDataComponent getCustomModelDataComponent() { return new ModelComponent(model); }
        @Override public void setCustomModelDataComponent(CustomModelDataComponent value) { model = value == null ? new ModelComponent() : new ModelComponent(value); }
        @Override public ModelLeatherMeta clone() { return new ModelLeatherMeta(this); }
    }
    static final class ModelComponent implements CustomModelDataComponent {
        private List<Float> floats = List.of(); private List<Boolean> flags = List.of();
        private List<String> strings = List.of(); private List<Color> colors = List.of();
        ModelComponent() {}
        ModelComponent(CustomModelDataComponent other) { setFloats(other.getFloats()); setFlags(other.getFlags()); setStrings(other.getStrings()); setColors(other.getColors()); }
        @Override public List<Float> getFloats() { return floats; }
        @Override public void setFloats(List<Float> value) { floats = List.copyOf(value); }
        @Override public List<Boolean> getFlags() { return flags; }
        @Override public void setFlags(List<Boolean> value) { flags = List.copyOf(value); }
        @Override public List<String> getStrings() { return strings; }
        @Override public void setStrings(List<String> value) { strings = List.copyOf(value); }
        @Override public List<Color> getColors() { return colors; }
        @Override public void setColors(List<Color> value) { colors = List.copyOf(value); }
        @Override public Map<String, Object> serialize() { return Map.of("floats", floats, "flags", flags, "strings", strings, "colors", colors); }
    }
}
