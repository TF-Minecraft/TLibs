package net.tfminecraft.tlibs.socket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import net.Indyuce.mmoitems.stat.data.GemSocketsData;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.config.RebuildConfig;
import net.tfminecraft.tlibs.config.SocketTierConfig;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

class SocketStorageConfigTest {
    private static final NamespacedKey CURRENT = new NamespacedKey("tlibs", "socket_overrides");
    private static final NamespacedKey LEGACY = new NamespacedKey("geminfusion", "socket_overrides");
    private MockedStatic<TLibs> pluginStatic;
    private MockedStatic<GemSocketsData> gemStatic;
    private Map<Object, Object> savedRegistry;
    private Map<Object, Object> savedPending;

    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void isolateState() throws Exception {
        savedRegistry = new HashMap<>(state(SocketTierRegistry.class, "COLORS"));
        savedPending = new HashMap<>(state(PendingTieredSocketApply.class, "PENDING"));
        SocketTierRegistry.clear();
        state(PendingTieredSocketApply.class, "PENDING").clear();
        TLibs plugin = mock(TLibs.class);
        when(plugin.getName()).thenReturn("TLibs");
        when(plugin.namespace()).thenReturn("tlibs");
        when(plugin.getLogger()).thenReturn(Logger.getLogger("SocketStorageConfigTest"));
        pluginStatic = mockStatic(TLibs.class);
        pluginStatic.when(TLibs::getInstance).thenReturn(plugin);
        gemStatic = mockStatic(GemSocketsData.class);
        gemStatic.when(GemSocketsData::getUncoloredGemSlot).thenReturn("Localized Uncolored");
    }

    @AfterEach
    void restoreState() throws Exception {
        if (gemStatic != null) gemStatic.close();
        if (pluginStatic != null) pluginStatic.close();
        state(SocketTierRegistry.class, "COLORS").clear();
        state(SocketTierRegistry.class, "COLORS").putAll(savedRegistry);
        state(PendingTieredSocketApply.class, "PENDING").clear();
        state(PendingTieredSocketApply.class, "PENDING").putAll(savedPending);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> state(Class<?> owner, String field) throws Exception {
        Field value = owner.getDeclaredField(field);
        value.setAccessible(true);
        return (Map<Object, Object>) value.get(null);
    }

    @Test
    void socketOverridesRoundTripMergeAndRemoveWithoutTouchingOtherData() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ItemStack source = new ItemStack(Material.DIAMOND_SWORD);
        ItemStack target = new ItemStack(Material.IRON_SWORD);
        NamespacedKey unrelated = new NamespacedKey("other", "state");
        ItemMeta meta = target.getItemMeta();
        meta.getPersistentDataContainer().set(unrelated, PersistentDataType.INTEGER, 7);
        target.setItemMeta(meta);
        SocketOverrideStore.put(source, first, "Blue");
        SocketOverrideStore.write(target, Map.of(first.toString(), "Red", second.toString(), "Green"));
        SocketOverrideStore.mergeOnto(target, source);
        assertEquals(Map.of(first.toString(), "Blue", second.toString(), "Green"), SocketOverrideStore.read(target));
        assertEquals(7, target.getItemMeta().getPersistentDataContainer().get(unrelated, PersistentDataType.INTEGER));
        assertEquals("Blue", SocketOverrideStore.get(target, first));
        SocketOverrideStore.remove(target, first);
        SocketOverrideStore.remove(target, first);
        assertNull(SocketOverrideStore.get(target, first));
        assertEquals("Green", SocketOverrideStore.get(target, second));
        SocketOverrideStore.write(target, null);
        assertTrue(SocketOverrideStore.read(target).isEmpty());
        SocketOverrideStore.write(target, Map.of());
        assertFalse(target.getItemMeta().getPersistentDataContainer().has(CURRENT));
    }

    @Test
    void removedLegacySocketDoesNotResurrectOnTheNextRead() {
        UUID id = UUID.randomUUID();
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        setRaw(item, LEGACY, "{\"" + id + "\":\"Blue\"}");
        assertEquals("Blue", SocketOverrideStore.get(item, id));
        SocketOverrideStore.remove(item, id);
        assertNull(SocketOverrideStore.get(item, id));
        assertFalse(item.getItemMeta().getPersistentDataContainer().has(LEGACY));
    }

    @Test
    void currentSocketOverridesTakePrecedenceAndWritesMigrateLegacyEntries() {
        UUID id = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        setRaw(item, CURRENT, "{\"" + id + "\":\"Gold\"}");
        setRaw(item, LEGACY, "{\"" + id + "\":\"Blue\",\"" + second + "\":\"Green\"}");
        assertEquals(Map.of(id.toString(), "Gold", second.toString(), "Green"), SocketOverrideStore.read(item));
        SocketOverrideStore.put(item, id, "Red");
        assertEquals(Map.of(id.toString(), "Red", second.toString(), "Green"), SocketOverrideStore.read(item));
        assertFalse(item.getItemMeta().getPersistentDataContainer().has(LEGACY));
    }

    @Test
    void absentSocketDataAndInvalidOperationArgumentsAreHarmless() {
        ItemStack item = new ItemStack(Material.STONE);
        UUID id = UUID.randomUUID();
        assertTrue(SocketOverrideStore.read(null).isEmpty());
        assertTrue(SocketOverrideStore.read(item).isEmpty());
        setRaw(item, CURRENT, " ");
        assertTrue(SocketOverrideStore.read(item).isEmpty());
        SocketOverrideStore.write(null, Map.of("x", "y"));
        SocketOverrideStore.write(mock(ItemStack.class), Map.of("x", "y"));
        SocketOverrideStore.mergeOnto(null, item);
        SocketOverrideStore.mergeOnto(item, null);
        SocketOverrideStore.put(null, id, "Blue");
        SocketOverrideStore.put(item, null, "Blue");
        SocketOverrideStore.put(item, id, null);
        SocketOverrideStore.remove(null, id);
        SocketOverrideStore.remove(item, null);
        assertNull(SocketOverrideStore.get(null, id));
        assertNull(SocketOverrideStore.get(item, null));
        assertTrue(SocketOverrideStore.read(item).isEmpty());
    }

    @Test
    void pendingApplicationsSnapshotCursorAndAreConsumedExactlyOnce() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        ItemStack cursor = new ItemStack(Material.EMERALD, 3);
        PendingTieredSocketApply.stash(null, cursor, "Blue", true);
        PendingTieredSocketApply.stash(player, null, "Blue", true);
        assertNull(PendingTieredSocketApply.poll(player));
        assertNull(PendingTieredSocketApply.poll(null));
        PendingTieredSocketApply.clear(null);
        PendingTieredSocketApply.stash(player, cursor, "Blue", true);
        cursor.setAmount(1);
        PendingTieredSocketApply.Entry entry = PendingTieredSocketApply.poll(player);
        assertEquals(3, entry.getCursorSnapshot().getAmount());
        assertNotSame(cursor, entry.getCursorSnapshot());
        assertEquals("Blue", entry.getAppliedSocketColor());
        assertTrue(entry.wasSpoofed());
        assertNull(PendingTieredSocketApply.poll(player));
        PendingTieredSocketApply.stash(player, cursor, null, false);
        PendingTieredSocketApply.clear(player);
        assertNull(PendingTieredSocketApply.poll(player));
    }

    @Test
    void tiersChooseTheLowestCompatibleSocketAndResolveConfiguredUncoloredAlias() {
        SocketTierRegistry.put("gem", "Blue", 1);
        SocketTierRegistry.put("gem", "Gold", 3);
        SocketTierRegistry.put("gem", "Silver", 2);
        SocketTierRegistry.put("gem", "Uncolored", 4);
        SocketTierRegistry.put("rune", "Red", 1);
        SocketTierRegistry.put(null, "bad", 1);
        SocketTierRegistry.put(" ", "bad", 1);
        SocketTierRegistry.put("gem", null, 1);
        SocketTierRegistry.put("gem", " ", 1);
        assertNull(SocketTierRegistry.getGroup(null));
        assertNull(SocketTierRegistry.getGroup(" "));
        assertNull(SocketTierRegistry.getGroup("Unknown"));
        assertEquals(Integer.MAX_VALUE, SocketTierRegistry.getTier("Unknown"));
        assertEquals("gem", SocketTierRegistry.getGroup("Localized Uncolored"));
        assertEquals(4, SocketTierRegistry.getTier("Localized Uncolored"));
        assertTrue(SocketTierRegistry.sameGroup("Blue", "Gold"));
        assertFalse(SocketTierRegistry.sameGroup("Blue", "Red"));
        assertTrue(SocketTierRegistry.canFit("Blue", "Blue"));
        assertTrue(SocketTierRegistry.canFit("Blue", "Gold"));
        assertFalse(SocketTierRegistry.canFit("Gold", "Blue"));
        assertFalse(SocketTierRegistry.canFit("Blue", "Red"));
        assertFalse(SocketTierRegistry.canFit(null, "Gold"));
        assertFalse(SocketTierRegistry.canFit("Blue", null));
        GemSocketsData sockets = new GemSocketsData(List.of("Red", "Gold", "Silver"));
        assertEquals("Silver", SocketTierRegistry.pickLowestCompatible(sockets, "Blue"));
        assertNull(SocketTierRegistry.pickLowestCompatible(sockets, "Unknown"));
        assertNull(SocketTierRegistry.pickLowestCompatible(null, "Blue"));
        assertNull(SocketTierRegistry.pickLowestCompatible(sockets, null));
        SocketTierRegistry.clear();
        assertNull(SocketTierRegistry.getGroup("Blue"));
    }

    @Test
    void socketConfigReloadReplacesRegistryAndSkipsNonSectionValues() {
        SocketTierConfig config = new SocketTierConfig();
        assertTrue(config.isEnabled());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tiered-sockets.enabled", false);
        yaml.set("socket-tier-groups.gem.Blue", 1);
        yaml.set("socket-tier-groups.gem.Gold", 3);
        yaml.set("socket-tier-groups.rune.Red", 2);
        yaml.set("socket-tier-groups.invalid", "scalar");
        config.reload(yaml);
        assertFalse(config.isEnabled());
        assertEquals("gem", SocketTierRegistry.getGroup("Blue"));
        assertEquals(3, SocketTierRegistry.getTier("Gold"));
        assertEquals("rune", SocketTierRegistry.getGroup("Red"));
        config.reload(new YamlConfiguration());
        assertTrue(config.isEnabled());
        assertNull(SocketTierRegistry.getGroup("Blue"));
    }

    @Test
    void rebuildConfigurationFiltersOwnedTagsAndExposesImmutableCopies() {
        RebuildConfig config = new RebuildConfig();
        assertTrue(config.isEnabled());
        assertTrue(config.copyPersistentData());
        assertTrue(config.copyAppearance());
        assertFalse(config.debugNbt());
        assertEquals(List.of(), config.getPreserveNbtTags());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("mmo-item-rebuild.enabled", false);
        yaml.set("mmo-item-rebuild.copy-persistent-data", false);
        yaml.set("mmo-item-rebuild.copy-appearance", false);
        yaml.set("mmo-item-rebuild.debug-nbt", true);
        yaml.set("mmo-item-rebuild.preserve-nbt",
                Arrays.asList(null, " ", " MMOITEMS_TYPE ", "mmoitem_level", " other:tag ", "safe"));
        config.reload(yaml);
        assertFalse(config.isEnabled());
        assertFalse(config.copyPersistentData());
        assertFalse(config.copyAppearance());
        assertTrue(config.debugNbt());
        assertEquals(List.of("other:tag", "safe"), config.getPreserveNbtTags());
        assertThrows(UnsupportedOperationException.class, () -> config.getPreserveNbtTags().add("bad"));
        assertTrue(RebuildConfig.isBlockedTag("mmoitems_item_id"));
        assertTrue(RebuildConfig.isBlockedTag("MMOITEM_TYPE"));
        assertFalse(RebuildConfig.isBlockedTag("other:tag"));
        List<String> old = config.getPreserveNbtTags();
        config.reload(new YamlConfiguration());
        assertTrue(config.isEnabled());
        assertEquals(List.of(), config.getPreserveNbtTags());
        assertEquals(List.of("other:tag", "safe"), old);
    }

    private static void setRaw(ItemStack item, NamespacedKey key, String json) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, json);
        item.setItemMeta(meta);
    }
}
