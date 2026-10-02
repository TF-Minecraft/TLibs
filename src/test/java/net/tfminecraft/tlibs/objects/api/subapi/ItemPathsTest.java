package net.tfminecraft.tlibs.objects.api.subapi;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import dev.lone.itemsadder.api.CustomStack;
import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.build.ItemStackBuilder;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.manager.ItemManager;
import net.Indyuce.mmoitems.manager.TypeManager;
import net.tfminecraft.cooking.item.FoodItem;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.util.LegacyModelData;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;
import org.mockito.MockedConstruction;

class ItemPathsTest {
    private ItemAPI api;
    private ItemChecker checker;
    private ItemCreator creator;
    private PluginManager plugins;
    private MockedConstruction<ItemStack> itemStacks;

    @BeforeAll
    static void startServer() { MockBukkit.mock(); }
    @AfterAll
    static void stopServer() { MockBukkit.unmock(); }

    @BeforeEach
    void initializeApi() {
        // MockBukkit lacks the modern model-data component. Keep ItemStack as the external
        // boundary while exercising real item metadata, components, and TLibs path logic.
        itemStacks = mockConstruction(ItemStack.class, (item, context) -> {
            Material[] material = {(Material) context.arguments().get(0)};
            int[] amount = {context.arguments().size() > 1 ? (Integer) context.arguments().get(1) : 1};
            ItemMeta[] meta = {material[0].isAir() ? null : new ModelMeta()};
            when(item.getType()).thenAnswer(call -> material[0]);
            doAnswer(call -> { material[0] = call.getArgument(0); return null; }).when(item).setType(any(Material.class));
            when(item.getAmount()).thenAnswer(call -> amount[0]);
            doAnswer(call -> { amount[0] = call.getArgument(0); return null; }).when(item).setAmount(anyInt());
            when(item.getItemMeta()).thenAnswer(call -> meta[0] == null ? null : meta[0].clone());
            when(item.setItemMeta(any())).thenAnswer(call -> { ItemMeta value = call.getArgument(0); meta[0] = value == null ? null : value.clone(); return true; });
            when(item.hasItemMeta()).thenAnswer(call -> meta[0] != null && (meta[0].hasDisplayName()
                    || meta[0].hasLore() || meta[0].hasEnchants() || LegacyModelData.has(meta[0])));
        });
        Server server = mock(Server.class);
        plugins = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(plugins);
        api = new ItemAPI();
        api.setup(server);
        checker = api.getChecker();
        creator = api.getCreator();
        assertSame(server, api.getServer());
        assertNotNull(api.getArmorMerger());
    }

    @AfterEach
    void restoreItems() { if (itemStacks != null) itemStacks.close(); }

    private void enable(String name) { when(plugins.getPlugin(name)).thenReturn(mock(Plugin.class)); }

    @Test
    void registeredPrefixesOverrideBuiltinsAndAreCaseInsensitive() {
        ItemStack result = new ItemStack(Material.DIAMOND);
        ItemPathHandler handler = new ItemPathHandler() {
            @Override public ItemStack create(String path) { assertEquals("V.special", path); return result; }
            @Override public boolean matches(ItemStack stack, String path) { return stack == result && path.equals("V.special"); }
        };
        api.registerPathHandler(null, handler);
        api.registerPathHandler(" ", handler);
        api.registerPathHandler("x", null);
        assertNull(api.getPathHandler(null));
        assertNull(api.getPathHandler(" "));
        assertNull(api.getPathHandler("x"));
        api.registerPathHandler("v", handler);
        assertSame(handler, api.getPathHandler("V"));
        assertSame(result, creator.getItemFromPath("V.special"));
        assertTrue(checker.checkItemWithPath(result, "V.special"));
        api.unregisterPathHandler(null);
        api.unregisterPathHandler(" ");
        api.unregisterPathHandler("V");
        assertNull(api.getPathHandler("v"));
        ItemPathHandler createOnly = path -> result;
        assertFalse(createOnly.matches(result, "x.value"));
    }

    @Test
    void ordinaryItemPathsAndMatchOnlyKeywordRetainExistingBehavior() {
        ItemStack diamond = new ItemStack(Material.DIAMOND);
        assertEquals("v.diamond", checker.getAsStringPath(diamond));
        assertEquals(Material.DIAMOND, creator.getItemFromPath("v.diamond").getType());
        assertTrue(checker.checkItemWithPath(diamond, "V.DIAMOND"));
        assertFalse(checker.checkItemWithPath(diamond, "v.dirt"));
        assertFalse(checker.checkItemWithPath(diamond, "v.invalid"));
        assertFalse(checker.checkItemWithPath(diamond, "v"));
        assertTrue(checker.checkItemWithPath(diamond, "item"));
        assertFalse(checker.checkItemWithPath(new ItemStack(Material.STONE), "item"));
        assertNull(creator.getItemFromPath("item"));
        assertNull(creator.getItemFromPath("magic.fireball"));
        assertNull(creator.getItemFromPath(null));
        assertNull(creator.getItemFromPath(" "));
        assertFalse(checker.checkItemWithPath(null, "v.diamond"));
        assertFalse(checker.checkItemWithPath(new ItemStack(Material.AIR), "v.air"));
        assertFalse(checker.checkItemWithPath(diamond, null));
        assertFalse(checker.checkItemWithPath(diamond, " "));
        assertFalse(checker.checkItemWithPath(diamond, "unknown.value"));
    }

    @Test
    void modeledPathsRoundTripNamesAndModelData() {
        ItemStack result = creator.getItemFromPath("modeled.(type=diamond;name=§eGem;model=17;ignored)");
        assertEquals(Material.DIAMOND, result.getType());
        assertEquals("§eGem", result.getItemMeta().getDisplayName());
        assertEquals(17, LegacyModelData.get(result.getItemMeta()));
        assertEquals("modeled.(type=diamond;name=§eGem;model=17)", checker.getAsStringPath(result));
        assertTrue(checker.checkItemWithPath(result, checker.getAsStringPath(result)));
        assertTrue(checker.checkItemWithPath(result, "modeled.(ignored;model=17)"));
        assertFalse(checker.checkItemWithPath(result, "modeled.(type=invalid)"));
        assertFalse(checker.checkItemWithPath(result, "modeled.(type=stone)"));
        assertFalse(checker.checkItemWithPath(result, "modeled.(name=other)"));
        assertFalse(checker.checkItemWithPath(result, "modeled.(model=bad)"));
        assertFalse(checker.checkItemWithPath(result, "modeled.(model=18)"));
        ItemStack namedOnly = creator.getItemFromPath("modeled.(name=Named)");
        assertFalse(checker.checkItemWithPath(namedOnly, "modeled.(model=17)"));
        assertEquals("modeled.(type=dirt;name=Named)", checker.getAsStringPath(namedOnly));
        ItemStack modelOnly = creator.getItemFromPath("modeled.(model=17)");
        assertFalse(checker.checkItemWithPath(modelOnly, "modeled.(name=Named)"));
        assertEquals("modeled.(type=dirt;model=17)", checker.getAsStringPath(modelOnly));
        assertFalse(checker.checkItemWithPath(new ItemStack(Material.STONE), "modeled.(type=stone)"));
        ItemStack invalid = creator.getItemFromPath("modeled.(type=invalid;model=bad)");
        assertEquals(Material.DIRT, invalid.getType());
        assertFalse(LegacyModelData.has(invalid.getItemMeta()));
        assertEquals("v.dirt", checker.getAsStringPath(invalid));
    }

    @Test
    void malformedBuiltinPathsDoNotThrow() {
        enable("ItemsAdder");
        ItemStack named = creator.getItemFromPath("modeled.(name=Named)");
        assertAll(
                () -> assertNull(creator.getItemFromPath("v")),
                () -> assertNull(creator.getItemFromPath("v.")),
                () -> assertNull(creator.getItemFromPath("v..")),
                () -> assertNull(creator.getItemFromPath("v...")),
                () -> assertNull(creator.getItemFromPath("v.invalid")),
                () -> assertNull(creator.getItemFromPath("ia")),
                () -> assertNull(creator.getItemFromPath("modeled")),
                () -> assertNull(creator.getItemFromPath("modeled.(type=stone")),
                () -> assertFalse(checker.checkItemWithPath(named, "ia")),
                () -> assertFalse(checker.checkItemWithPath(named, "modeled")),
                () -> assertFalse(checker.checkItemWithPath(named, "modeled.(type=stone")),
                () -> assertFalse(checker.checkItemWithPath(named, ".")));
    }

    @Test
    void emptyVanillaPathSegmentsNeverMatchItems() {
        ItemStack item = new ItemStack(Material.STONE);
        assertFalse(checker.checkItemWithPath(item, "v.."));
        assertFalse(checker.checkItemWithPath(item, "v..."));
    }

    @Test
    void materialAndModeledAttributePathsIgnoreTheHostLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            ItemStack diamond = new ItemStack(Material.DIAMOND);
            assertAll(
                    () -> assertEquals("v.diamond", checker.getAsStringPath(diamond)),
                    () -> assertTrue(checker.checkItemWithPath(diamond, "v.diamond")),
                    () -> assertEquals(Material.DIAMOND, creator.getItemFromPath("v.diamond").getType()),
                    () -> assertEquals(Material.DIAMOND, creator.getItemFromPath("modeled.(TYPE=diamond)").getType()));
        } finally { Locale.setDefault(previous); }
    }

    @Test
    void missingIntegrationPluginsKeepFallbackBehavior() {
        ItemStack item = new ItemStack(Material.DIAMOND);
        assertEquals(Material.DIRT, creator.getItemFromPath("m.material.salt").getType());
        assertEquals(Material.DIRT, creator.getItemFromPath("ia.tfmc:gem").getType());
        assertFalse(checker.checkItemWithPath(item, "m.material.salt"));
        assertFalse(checker.checkItemWithPath(item, "ia.tfmc:gem"));
    }

    @Test
    void itemsAdderPathsUseTheCustomItemIdentity() {
        enable("ItemsAdder");
        ItemStack item = new ItemStack(Material.DIAMOND, 5);
        CustomStack custom = mock(CustomStack.class);
        when(custom.getNamespacedID()).thenReturn("tfmc:gem");
        when(custom.getItemStack()).thenReturn(item);
        try (MockedStatic<CustomStack> itemsAdder = mockStatic(CustomStack.class)) {
            itemsAdder.when(() -> CustomStack.getInstance("tfmc:gem")).thenReturn(custom);
            itemsAdder.when(() -> CustomStack.byItemStack(item)).thenReturn(custom);
            assertSame(item, creator.getItemFromPath("ia.tfmc:gem"));
            assertEquals(1, item.getAmount());
            assertSame(item, creator.getItemsAdderItem("tfmc:gem"));
            assertNull(creator.getItemsAdderItem("tfmc:missing"));
            assertEquals(Material.DIRT, creator.getItemFromPath("ia.tfmc:missing").getType());
            assertEquals("ia.tfmc:gem", checker.getAsStringPath(item));
            assertTrue(checker.checkItemWithPath(item, "ia.TFMC:GEM"));
            assertFalse(checker.checkItemWithPath(item, "ia.tfmc:other"));
            assertFalse(checker.checkItemWithPath(item, "v.diamond"));
            ItemStack plain = new ItemStack(Material.EMERALD);
            assertFalse(checker.checkItemWithPath(plain, "ia.tfmc:gem"));
            assertEquals("v.emerald", checker.getAsStringPath(plain));
            assertTrue(checker.checkItemWithPath(plain, "v.emerald"));
        }
    }

    @Test
    void mmoPathsMatchTypeOrExactIdentityWithoutMatchingVanilla() {
        enable("MMOItems");
        enable("MythicLib");
        ItemStack item = new ItemStack(Material.DIAMOND);
        NBTItem identified = mock(NBTItem.class);
        when(identified.hasType()).thenReturn(true);
        when(identified.getType()).thenReturn("MATERIAL");
        when(identified.getString("MMOITEMS_ITEM_ID")).thenReturn("SALT");
        NBTItem plain = mock(NBTItem.class);
        try (MockedStatic<NBTItem> nbt = mockStatic(NBTItem.class)) {
            nbt.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(plain);
            nbt.when(() -> NBTItem.get(item)).thenReturn(identified);
            assertEquals("m.material.salt", checker.getAsStringPath(item));
            assertEquals("MATERIAL", checker.getMMOItemsType(item));
            assertTrue(checker.checkItemWithPath(item, "m.material"));
            assertTrue(checker.checkItemWithPath(item, "m.material.salt"));
            assertFalse(checker.checkItemWithPath(item, "m.weapon"));
            assertFalse(checker.checkItemWithPath(item, "m.material.other"));
            assertFalse(checker.checkItemWithPath(item, "m"));
            assertFalse(checker.checkItemWithPath(item, "v.diamond"));
            ItemStack vanilla = new ItemStack(Material.EMERALD);
            assertEquals("none", checker.getMMOItemsType(vanilla));
            assertEquals("v.emerald", checker.getAsStringPath(vanilla));
            assertFalse(checker.checkItemWithPath(vanilla, "m.material"));
            assertTrue(checker.checkItemWithPath(vanilla, "v.emerald"));
        }
    }

    @Test
    void mmoCreationBuildsExistingDefinitionAndRejectsTypeOnlyOrMissingId() {
        enable("MMOItems");
        enable("MythicLib");
        MMOItems previous = MMOItems.plugin;
        io.lumine.mythic.lib.MythicLib previousMythic = io.lumine.mythic.lib.MythicLib.plugin;
        try {
            MMOItems plugin = mock(MMOItems.class);
            when(plugin.namespace()).thenReturn("mmoitems");
            MMOItems.plugin = plugin;
            io.lumine.mythic.lib.MythicLib mythic = mock(io.lumine.mythic.lib.MythicLib.class);
            when(mythic.namespace()).thenReturn("mythiclib");
            io.lumine.mythic.lib.version.ServerVersion version = mock(io.lumine.mythic.lib.version.ServerVersion.class);
            when(mythic.getVersion()).thenReturn(version);
            when(version.getWrapper()).thenReturn(mock(io.lumine.mythic.lib.version.wrapper.VersionWrapper.class));
            io.lumine.mythic.lib.MythicLib.plugin = mythic;
            ItemManager items = mock(ItemManager.class);
            TypeManager types = mock(TypeManager.class);
            Type material = mock(Type.class);
            MMOItem definition = mock(MMOItem.class);
            ItemStackBuilder builder = mock(ItemStackBuilder.class);
            ItemStack result = new ItemStack(Material.SUGAR);
            when(plugin.getItems()).thenReturn(items);
            when(plugin.getTypes()).thenReturn(types);
            when(types.get("MATERIAL")).thenReturn(material);
            when(items.getMMOItem(material, "SALT")).thenReturn(definition);
            when(definition.newBuilder()).thenReturn(builder);
            when(builder.build()).thenReturn(result);
            assertNull(creator.getItemFromPath("m.material"));
            assertNull(creator.getItemFromPath("m.material.missing"));
            assertSame(result, creator.getItemFromPath("m.material.salt"));
        } finally { MMOItems.plugin = previous; io.lumine.mythic.lib.MythicLib.plugin = previousMythic; }
    }

    @Test
    void cookingPathsSupportCategoriesTypesAndExportRoundTrips() {
        enable("Cooking");
        ItemStack item = new ItemStack(Material.BREAD);
        FoodItem food = mock(FoodItem.class);
        when(food.getCategory()).thenReturn("bread");
        when(food.getId()).thenReturn("rye");
        when(food.getOrigin()).thenReturn("oven");
        try (MockedStatic<FoodItem> foods = mockStatic(FoodItem.class)) {
            foods.when(() -> FoodItem.fromItem(item)).thenReturn(food);
            assertTrue(checker.checkItemWithPath(item, "c.bread"));
            assertTrue(checker.checkItemWithPath(item, "c.bread(type=rye)"));
            assertTrue(checker.checkItemWithPath(item, "c.bread(unrelated=value)"));
            assertFalse(checker.checkItemWithPath(item, "c.meat"));
            assertFalse(checker.checkItemWithPath(item, "c.bread(type=wheat)"));
            assertFalse(checker.checkItemWithPath(new ItemStack(Material.APPLE), "c.bread"));
            assertEquals("v.apple", checker.getAsStringPath(new ItemStack(Material.APPLE)));
            String path = checker.getAsStringPath(item);
            assertEquals("c.bread(type=rye;origin=oven)", path);
            assertTrue(checker.checkItemWithPath(item, path));
            assertFalse(checker.checkItemWithPath(item, "c.bread(type=rye;origin=other)"));
            when(food.getOrigin()).thenReturn(null);
            when(food.getCategory()).thenReturn(null);
            assertEquals("c.(type=rye;origin=)", checker.getAsStringPath(item));
            assertTrue(checker.checkItemWithPath(item, checker.getAsStringPath(item)));
        }
    }

    @Test
    void configurationCreatesNameLoreModelEnchantmentsAndFlags() {
        YamlConfiguration yaml = new YamlConfiguration();
        ItemStack defaults = creator.getItemFromConfig(yaml);
        assertEquals(Material.DIRT, defaults.getType());
        assertEquals("No Name", defaults.getItemMeta().getDisplayName());
        yaml.set("material", "diamond_sword");
        yaml.set("name", "&aBlade");
        yaml.set("model_data", 25);
        yaml.set("enchants", List.of("sharpness.3"));
        yaml.set("hide_enchants", true);
        yaml.set("lore", List.of("&cFirst", "Plain"));
        ItemStack result = creator.getItemFromConfig(yaml);
        assertEquals(Material.DIAMOND_SWORD, result.getType());
        ItemMeta meta = result.getItemMeta();
        assertEquals("§aBlade", meta.getDisplayName());
        assertEquals(25, LegacyModelData.get(meta));
        assertEquals(3, meta.getEnchantLevel(Enchantment.SHARPNESS));
        assertTrue(meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS));
        assertEquals(List.of("§cFirst", "Plain"), meta.getLore());
        yaml.set("hide_enchants", false);
        assertFalse(creator.getItemFromConfig(yaml).getItemMeta().hasItemFlag(ItemFlag.HIDE_ENCHANTS));
    }

    /** Supplies the modern model-data component absent from MockBukkit. */
    private static final class ModelMeta extends org.mockbukkit.mockbukkit.inventory.meta.ItemMetaMock {
        private ModelComponent model = new ModelComponent();
        ModelMeta() {}
        ModelMeta(ModelMeta original) { super(original); model = original.model.copy(); }
        @Override public org.bukkit.inventory.meta.components.CustomModelDataComponent getCustomModelDataComponent() {
            return model.copy();
        }
        @Override public void setCustomModelDataComponent(org.bukkit.inventory.meta.components.CustomModelDataComponent value) {
            model = value == null ? new ModelComponent() : new ModelComponent(value);
        }
        @Override public ModelMeta clone() {
            return new ModelMeta(this);
        }
    }

    private static final class ModelComponent implements org.bukkit.inventory.meta.components.CustomModelDataComponent {
        private List<Float> floats = List.of();
        private List<Boolean> flags = List.of();
        private List<String> strings = List.of();
        private List<org.bukkit.Color> colors = List.of();
        ModelComponent() {}
        ModelComponent(org.bukkit.inventory.meta.components.CustomModelDataComponent other) {
            setFloats(other.getFloats()); setFlags(other.getFlags()); setStrings(other.getStrings()); setColors(other.getColors());
        }
        ModelComponent copy() { return new ModelComponent(this); }
        @Override public List<Float> getFloats() { return floats; }
        @Override public void setFloats(List<Float> value) { floats = List.copyOf(value); }
        @Override public List<Boolean> getFlags() { return flags; }
        @Override public void setFlags(List<Boolean> value) { flags = List.copyOf(value); }
        @Override public List<String> getStrings() { return strings; }
        @Override public void setStrings(List<String> value) { strings = List.copyOf(value); }
        @Override public List<org.bukkit.Color> getColors() { return colors; }
        @Override public void setColors(List<org.bukkit.Color> value) { colors = List.copyOf(value); }
        @Override public Map<String, Object> serialize() { return Map.of("floats", floats, "flags", flags, "strings", strings, "colors", colors); }
    }

}
