package net.tfminecraft.tlibs.objects.api.subapi;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteItemNBT;
import io.lumine.mythic.lib.api.item.ItemTag;
import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.event.item.ApplyGemStoneEvent;
import net.Indyuce.mmoitems.api.event.item.UnsocketGemStoneEvent;
import net.Indyuce.mmoitems.api.interaction.GemStone.ResultType;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.config.RebuildConfig;
import net.tfminecraft.tlibs.config.SocketTierConfig;
import net.tfminecraft.tlibs.event.MMOItemRebuildEvent;
import net.tfminecraft.tlibs.event.MMOItemRebuildEvent.RebuildReason;
import net.tfminecraft.tlibs.mmoitem.*;
import net.tfminecraft.tlibs.socket.PendingTieredSocketApply;
import org.bukkit.*;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

class MMOItemRebuildBridgeTest {
    @BeforeAll static void start() { MockBukkit.mock(); }
    @AfterAll static void stop() { MockBukkit.unmock(); }
    Harness h;
    MMOItemRebuildBridge bridge;
    Player player;
    Inventory top, bottom;
    PlayerInventory bag;
    InventoryView view;
    List<Runnable> tasks, delayed;
    List<MMOItemRebuildEvent> events;
    Consumer<MMOItemRebuildEvent> listener;
    Map<Object, Object> oldPending, oldPrimers;
    Object oldPrimerPlugin;
    MockedStatic<Bukkit> bukkit;

    @BeforeEach void setup() throws Exception {
        h = new Harness(); bridge = new MMOItemRebuildBridge();
        oldPending = takeMap(MMOItemRebuildBridge.class, "pending");
        oldPrimers = takeMap(GemApplyPrimer.class, "primers");
        oldPrimerPlugin = field(GemApplyPrimer.class, "plugin").get(null);
        GemApplyPrimer.init(null);
        player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Tester"); when(player.isOnline()).thenReturn(true);
        top = inventory(Inventory.class); bottom = inventory(Inventory.class); bag = inventory(PlayerInventory.class);
        view = mock(InventoryView.class); when(view.getTopInventory()).thenReturn(top); when(view.getBottomInventory()).thenReturn(bottom);
        when(player.getOpenInventory()).thenReturn(view); when(player.getInventory()).thenReturn(bag);
        tasks = new ArrayList<>(); delayed = new ArrayList<>(); events = new ArrayList<>(); listener = e -> {};
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(any(Plugin.class), any(Runnable.class))).thenAnswer(c -> { tasks.add(c.getArgument(1)); return null; });
        when(scheduler.runTaskLater(any(Plugin.class), any(Runnable.class), anyLong())).thenAnswer(c -> { delayed.add(c.getArgument(1)); return null; });
        PluginManager manager = mock(PluginManager.class);
        doAnswer(c -> { Event event = c.getArgument(0); if (event instanceof MMOItemRebuildEvent rebuild) { events.add(rebuild); listener.accept(rebuild); } return null; }).when(manager).callEvent(any());
        bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
        bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
    }
    @AfterEach void cleanup() throws Exception {
        PendingTieredSocketApply.clear(player);
        restoreMap(MMOItemRebuildBridge.class, "pending", oldPending);
        restoreMap(GemApplyPrimer.class, "primers", oldPrimers);
        field(GemApplyPrimer.class, "plugin").set(null, oldPrimerPlugin);
        bukkit.close(); h.close();
    }

    @Test void clickGuardsDoNotPrimeAndSuccessfulClickTakesDetachedSnapshot() throws Exception {
        InventoryClickEvent event = click(null);
        when(event.getWhoClicked()).thenReturn(mock(HumanEntity.class)); bridge.onInventoryClick(event);
        when(event.getWhoClicked()).thenReturn(player); when(event.getClickedInventory()).thenReturn(null); bridge.onInventoryClick(event);
        when(event.getClickedInventory()).thenReturn(top); bridge.onInventoryClick(event);
        ItemStack air = new ItemStack(Material.AIR); when(event.getCurrentItem()).thenReturn(air); bridge.onInventoryClick(event);
        ItemStack stone = new ItemStack(Material.STONE); when(event.getCurrentItem()).thenReturn(stone); bridge.onInventoryClick(event);
        assertTrue(pending().isEmpty());
        ItemStack old = target(); putData(old, "saved", "before"); top.setItem(1, old);
        bridge.onInventoryClick(click(old)); putData(old, "saved", "changed");
        MMOItemRebuildBridge.confirmAndScheduleGemApply(player, "sword", "ruby"); tasks.removeFirst().run();
        MMOItemRebuildEvent rebuilt = events.getFirst();
        assertEquals("before", data(rebuilt.getOldItem(), "saved"));
        assertEquals("before", rebuilt.getPdcSnapshot().get(key("saved"), PersistentDataType.STRING));
        assertNotSame(old, rebuilt.getNewItem()); assertSame(rebuilt.getNewItem(), top.getItem(1));
    }

    @Test void appliesCursorContextAndBaselinePersistentDataThenWritesListenerResult() {
        ItemStack old = target(); putData(old, "saved", "retained"); top.setItem(1, old); bridge.onInventoryClick(click(old));
        ItemStack fresh = target(); top.setItem(1, fresh);
        listener = new MMOItemRebuildBaselineListener()::onRebuild;
        ItemStack cursor = new ItemStack(Material.EMERALD); PendingTieredSocketApply.stash(player, cursor, "Blue2", true);
        ApplyGemStoneEvent apply = apply("SWORD", "RUBY"); bridge.onApplyGemLowest(apply); bridge.onApplyGemMonitor(apply); tasks.removeFirst().run();
        MMOItemRebuildEvent event = events.getFirst();
        assertEquals(RebuildReason.GEM_APPLY, event.getReason()); assertTrue(event.wasSpoofed());
        assertEquals("Blue2", event.getAppliedSocketColor()); assertEquals(cursor, event.getAppliedCursorSnapshot());
        assertNull(PendingTieredSocketApply.poll(player));
        assertSame(event.getNewItem(),top.getItem(1));
        assertEquals("retained", data(event.getNewItem(), "saved"));
        assertSame(MMOItemRebuildEvent.getHandlerList(), event.getHandlers());
        MMOItemRebuildEvent simple = new MMOItemRebuildEvent(player, null, fresh, RebuildReason.GEM_APPLY);
        assertNull(simple.getPdcSnapshot()); simple.setNewItem(old); assertSame(old, simple.getNewItem());
    }

    @Test void bootstrapsTopBottomAndPlayerInventoriesAndResolvesMovedItems() {
        for (Inventory source : List.of(top, bottom, bag)) {
            top.clear(); bottom.clear(); bag.clear(); events.clear();
            ItemStack old = target(); source.setItem(2, old);
            ApplyGemStoneEvent apply = apply("SWORD", "RUBY"); bridge.onApplyGemLowest(apply);
            source.setItem(2, null); ItemStack result = target();
            Inventory destination = source == top ? bag : top; destination.setItem(3, result);
            bridge.onApplyGemMonitor(apply); tasks.removeFirst().run();
            assertEquals(1, events.size()); assertSame(events.getFirst().getNewItem(), destination.getItem(3));
        }
    }

    @Test void unsocketConfirmsAndKeepsApplyContextUntouched() {
        ItemStack old = target(); top.setItem(1, old); bridge.onInventoryClick(click(old));
        UnsocketGemStoneEvent event = unsocket("SWORD", "RUBY"); bridge.onUnsocketLowest(event);
        PendingTieredSocketApply.stash(player, new ItemStack(Material.EMERALD), "Blue", false);
        bridge.onUnsocketMonitor(event); tasks.removeFirst().run();
        assertEquals(RebuildReason.GEM_UNSOCKET, events.getFirst().getReason());
        assertNotNull(PendingTieredSocketApply.poll(player));
        assertNull(events.getFirst().getAppliedCursorSnapshot());
    }

    @Test void cancelledOrFailedOperationsClearPendingWithoutScheduling() throws Exception {
        for (ResultType result : List.of(ResultType.NONE, ResultType.FAILURE, ResultType.SUCCESS)) {
            ItemStack old = target(); bridge.onInventoryClick(click(old));
            PendingTieredSocketApply.stash(player, old, "Blue", false);
            ApplyGemStoneEvent event = apply("SWORD", "RUBY"); when(event.getResult()).thenReturn(result);
            when(event.isCancelled()).thenReturn(result == ResultType.SUCCESS);
            bridge.onApplyGemMonitor(event); assertTrue(pending().isEmpty()); assertNull(PendingTieredSocketApply.poll(player));
        }
        bridge.onInventoryClick(click(target())); UnsocketGemStoneEvent unsocket = unsocket("SWORD", "RUBY");
        when(unsocket.isCancelled()).thenReturn(true); bridge.onUnsocketMonitor(unsocket);
        assertTrue(pending().isEmpty()); assertTrue(tasks.isEmpty());
    }

    @Test void missingUnconfirmedOfflineOrUnresolvableJobsDoNotFireEvents() {
        ApplyGemStoneEvent apply = apply("SWORD", "RUBY");
        bridge.onApplyGemLowest(apply); bridge.onApplyGemMonitor(apply); tasks.removeFirst().run();
        bridge.onInventoryClick(click(target())); bridge.onApplyGemMonitor(apply); tasks.removeFirst().run();
        for (Boolean online : Arrays.asList(false, null, true)) {
            ItemStack old = target(); top.setItem(1, old); bridge.onInventoryClick(click(old)); bridge.onApplyGemLowest(apply);
            if (online == null) bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(null);
            else { bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player); when(player.isOnline()).thenReturn(online); }
            if (Boolean.TRUE.equals(online)) { top.setItem(1, new ItemStack(Material.AIR)); when(view.getTopInventory()).thenReturn(null); }
            bridge.onApplyGemMonitor(apply); tasks.removeFirst().run();
        }
        assertTrue(events.isEmpty());
    }

    @Test void mismatchingPrimersCannotBeConfirmedAndValidReplacementsBootstrap() throws Exception {
        MMOItemRebuildBridge.confirmAndScheduleGemApply(player, "SWORD", "RUBY"); assertTrue(tasks.isEmpty());
        bridge.onInventoryClick(click(target()));
        MMOItemRebuildBridge.confirmAndScheduleGemApply(player, "BOW", "RUBY");
        MMOItemRebuildBridge.confirmAndScheduleGemApply(player, "SWORD", "OTHER"); assertTrue(tasks.isEmpty());
        when(view.getTopInventory()).thenReturn(null); when(view.getBottomInventory()).thenReturn(null);
        bridge.onApplyGemLowest(apply("BOW", "OTHER")); assertFalse(pending().isEmpty());
        ItemStack unrelatedType = h.item(Material.BOW, "BOW", "RUBY"); bag.setItem(0, unrelatedType);
        bag.setItem(1, h.item(Material.DIAMOND_SWORD, "SWORD", "OTHER"));
        bag.setItem(2, new ItemStack(Material.STONE));
        bridge.onApplyGemLowest(apply("SWORD", "OTHER")); bridge.onApplyGemMonitor(apply("SWORD", "OTHER")); tasks.removeFirst().run();
        assertEquals("OTHER", data(events.getFirst().getOldItem(), "MMOITEMS_ITEM_ID"));
    }

    @Test void unrelatedReplacementNeverReceivesPreviousItemsRebuildEvent() {
        ItemStack old = target(); putData(old, "saved", "private-old-value"); top.setItem(1, old); bridge.onInventoryClick(click(old));
        bridge.onApplyGemLowest(apply("SWORD", "RUBY"));
        ItemStack unrelated = new ItemStack(Material.DIAMOND); top.setItem(1, unrelated);
        bridge.onApplyGemMonitor(apply("SWORD", "RUBY")); tasks.removeFirst().run();
        assertTrue(events.isEmpty(), "An unrelated replacement must never inherit the original item's rebuild metadata");
        assertSame(unrelated, top.getItem(1));
    }

    @Test void primersExposeSnapshotMatchClearAndExpire() {
        UUID id = player.getUniqueId(); ItemStack old = target();
        assertFalse(GemApplyPrimer.matches(null, "SWORD", "RUBY"));
        GemApplyPrimer.put(id, top, 2, old, "SWORD", "RUBY", RebuildReason.GEM_APPLY);
        GemApplyPrimer.Entry entry = GemApplyPrimer.get(id);
        assertAll(() -> assertSame(top, entry.getInventory()), () -> assertEquals(2, entry.getSlot()),
                () -> assertSame(old, entry.getOldItemSnapshot()), () -> assertEquals("SWORD", entry.getTargetType()),
                () -> assertEquals("RUBY", entry.getTargetId()), () -> assertEquals(RebuildReason.GEM_APPLY, entry.getReason()));
        assertTrue(GemApplyPrimer.matches(entry, "sword", "ruby"));
        assertFalse(GemApplyPrimer.matches(entry, "BOW", "RUBY")); assertFalse(GemApplyPrimer.matches(entry, "SWORD", "OTHER"));
        GemApplyPrimer.clear(id); assertNull(GemApplyPrimer.get(id));
        GemApplyPrimer.init(h.plugin); GemApplyPrimer.put(id, top, 1, old, "SWORD", "RUBY", RebuildReason.GEM_UNSOCKET, 3L);
        assertNotNull(GemApplyPrimer.get(id)); delayed.removeFirst().run(); assertNull(GemApplyPrimer.get(id));
    }

    @Test void expirationOfAnEarlierPrimerDoesNotDeleteItsReplacement() {
        UUID id = player.getUniqueId(); GemApplyPrimer.init(h.plugin);
        GemApplyPrimer.put(id, top, 1, target(), "SWORD", "RUBY", RebuildReason.GEM_APPLY, 2L);
        GemApplyPrimer.put(id, top, 2, target(), "SWORD", "RUBY", RebuildReason.GEM_UNSOCKET, 40L);
        GemApplyPrimer.Entry latest = GemApplyPrimer.get(id); delayed.removeFirst().run();
        assertSame(latest, GemApplyPrimer.get(id), "An older timeout must not erase a newer pending operation");
        delayed.removeFirst().run(); assertNull(GemApplyPrimer.get(id));
    }

    ItemStack target() { return h.item(Material.DIAMOND_SWORD, "SWORD", "RUBY"); }
    InventoryClickEvent click(ItemStack item) {
        InventoryClickEvent e = mock(InventoryClickEvent.class); when(e.getWhoClicked()).thenReturn(player);
        when(e.getClickedInventory()).thenReturn(top); when(e.getCurrentItem()).thenReturn(item);
        when(e.getSlot()).thenReturn(1); when(e.getClick()).thenReturn(ClickType.LEFT); return e;
    }
    ApplyGemStoneEvent apply(String type, String id) {
        MMOItem target = target(type, id); ApplyGemStoneEvent e = mock(ApplyGemStoneEvent.class);
        when(e.getPlayer()).thenReturn(player); when(e.getTargetItem()).thenReturn(target); when(e.getResult()).thenReturn(ResultType.SUCCESS); return e;
    }
    UnsocketGemStoneEvent unsocket(String type, String id) {
        MMOItem target = target(type, id); UnsocketGemStoneEvent e = mock(UnsocketGemStoneEvent.class);
        when(e.getPlayer()).thenReturn(player); when(e.getTargetItem()).thenReturn(target); return e;
    }
    static MMOItem target(String type, String id) { Type t = mock(Type.class); when(t.getId()).thenReturn(type); MMOItem item = mock(MMOItem.class); when(item.getType()).thenReturn(t); when(item.getId()).thenReturn(id); return item; }
    static <T extends Inventory> T inventory(Class<T> type) {
        T inv = mock(type); ItemStack[] contents = new ItemStack[6]; when(inv.getSize()).thenReturn(contents.length);
        when(inv.getType()).thenReturn(InventoryType.CHEST); when(inv.getItem(anyInt())).thenAnswer(c -> contents[c.getArgument(0)]);
        doAnswer(c -> { contents[c.getArgument(0)] = c.getArgument(1); return null; }).when(inv).setItem(anyInt(), nullable(ItemStack.class));
        doAnswer(c -> { Arrays.fill(contents, null); return null; }).when(inv).clear(); return inv;
    }
    static Field field(Class<?> type, String name) throws Exception { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
    @SuppressWarnings("unchecked") static Map<Object,Object> map(Class<?> type, String name) throws Exception { return (Map<Object,Object>) field(type, name).get(null); }
    static Map<Object,Object> takeMap(Class<?> type, String name) throws Exception { Map<Object,Object> m = map(type,name); Map<Object,Object> saved = new HashMap<>(m); m.clear(); return saved; }
    static void restoreMap(Class<?> type, String name, Map<Object,Object> saved) throws Exception { Map<Object,Object> m = map(type,name); m.clear(); m.putAll(saved); }
    Map<Object,Object> pending() throws Exception { return map(MMOItemRebuildBridge.class, "pending"); }
    static NamespacedKey key(String value) { return new NamespacedKey("test", value.toLowerCase(Locale.ROOT)); }
    static String data(ItemStack item, String name) { return item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer().get(key(name), PersistentDataType.STRING) : null; }
    static void putData(ItemStack item, String name, String value) { ItemMeta meta = item.getItemMeta(); meta.getPersistentDataContainer().set(key(name), PersistentDataType.STRING,value); item.setItemMeta(meta); }

    static final class Harness implements AutoCloseable {
        final TLibs plugin = mock(TLibs.class);
        final RebuildConfig config = new RebuildConfig(); final SocketTierConfig sockets = new SocketTierConfig();
        final MockedStatic<TLibs> tlibs; final MockedStatic<NBTItem> mythic; final MockedStatic<NBT> nbt;
        final IdentityHashMap<ItemStack,NBTItem> adapters = new IdentityHashMap<>();
        Harness() throws Exception {
            when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("BridgeTest"));
            when(plugin.namespace()).thenReturn("tlibs");
            field(RebuildConfig.class,"copyAppearance").set(config,false);
            tlibs = mockStatic(TLibs.class); tlibs.when(TLibs::getInstance).thenReturn(plugin);
            tlibs.when(TLibs::getRebuildConfig).thenReturn(config); tlibs.when(TLibs::getSocketTierConfig).thenReturn(sockets);
            mythic = mockStatic(NBTItem.class); mythic.when(() -> NBTItem.get(any(ItemStack.class))).thenAnswer(c -> adapter(c.getArgument(0)));
            nbt = mockStatic(NBT.class, c -> {
                if (c.getMethod().getName().equals("get")) {
                    ReadWriteItemNBT value = mock(ReadWriteItemNBT.class); when(value.toString()).thenReturn("{}");
                    Object callback = c.getArgument(1); if (callback instanceof Function<?,?> f) return ((Function<ReadWriteItemNBT,?>) f).apply(value);
                    ((Consumer<ReadWriteItemNBT>) callback).accept(value); return null;
                }
                return RETURNS_DEFAULTS.answer(c);
            });
        }
        ItemStack item(Material material, String type, String id) { ItemStack item = new ItemStack(material); putData(item,"MMOITEMS_ITEM_TYPE",type); putData(item,"MMOITEMS_ITEM_ID",id); return item; }
        NBTItem adapter(ItemStack item) { return adapters.computeIfAbsent(item, key -> mock(NBTItem.class, c -> switch (c.getMethod().getName()) {
            case "hasType" -> data(item,"MMOITEMS_ITEM_TYPE") != null;
            case "getType" -> Objects.toString(data(item,"MMOITEMS_ITEM_TYPE"), "");
            case "getString" -> Objects.toString(data(item,c.getArgument(0)), "");
            case "getItem", "toItem" -> item;
            case "addTag" -> { Object arg = c.getRawArguments()[0]; Iterable<ItemTag> tags = arg instanceof ItemTag[] a ? Arrays.asList(a) : (List<ItemTag>) arg; for (ItemTag tag: tags) putData(item,tag.getPath(),tag.getValue().toString()); yield c.getMock(); }
            default -> RETURNS_DEFAULTS.answer(c);
        })); }
        @Override public void close() { nbt.close(); mythic.close(); tlibs.close(); }
    }
}
