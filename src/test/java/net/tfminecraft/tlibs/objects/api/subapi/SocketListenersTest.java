package net.tfminecraft.tlibs.objects.api.subapi;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static net.tfminecraft.tlibs.objects.api.subapi.MMOItemRebuildBridgeTest.*;

import java.util.*;
import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.version.ServerVersion;
import io.lumine.mythic.lib.version.wrapper.VersionWrapper;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.event.item.ApplyGemStoneEvent;
import net.Indyuce.mmoitems.api.interaction.GemStone;
import net.Indyuce.mmoitems.api.interaction.GemStone.ResultType;
import net.Indyuce.mmoitems.api.interaction.UseItem;
import net.Indyuce.mmoitems.api.item.build.ItemStackBuilder;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.VolatileMMOItem;
import net.Indyuce.mmoitems.api.player.PlayerData;
import net.Indyuce.mmoitems.stat.data.*;
import net.tfminecraft.tlibs.config.SocketTierConfig;
import net.tfminecraft.tlibs.event.MMOItemRebuildEvent;
import net.tfminecraft.tlibs.event.MMOItemRebuildEvent.RebuildReason;
import net.tfminecraft.tlibs.socket.*;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class SocketListenersTest {
    @BeforeAll static void start() { MockBukkit.mock(); }
    @AfterAll static void stop() { MockBukkit.unmock(); }
    Harness h;
    MMOItems previousMmo; MythicLib previousMythic;
    Map<Object,Object> previousColors;
    Player player; PlayerData playerData;
    Type gemType, hostType;
    GemStone useGem;
    GemStone.ApplyResult applyResult;
    final List<NBTItem> appliedGems = new ArrayList<>();
    final IdentityHashMap<ItemStack,GemSocketsData> sockets = new IdentityHashMap<>();
    MockedConstruction<LiveMMOItem> liveItems;
    MockedConstruction<GemStone> gemApplications;
    MockedStatic<Type> types;
    MockedStatic<PlayerData> players;
    TieredSocketApplyListener apply;
    TieredSocketRebuildListener rebuild;

    @BeforeEach void setup() throws Exception {
        h = new Harness(); previousColors = takeMap(SocketTierRegistry.class,"COLORS");
        previousMmo = MMOItems.plugin; previousMythic = MythicLib.plugin;
        MMOItems mmo = mock(MMOItems.class); when(mmo.namespace()).thenReturn("mmoitems");
        YamlConfiguration config = new YamlConfiguration(); when(mmo.getConfig()).thenReturn(config); MMOItems.plugin = mmo;
        net.Indyuce.mmoitems.server.ServerAdapter serverAdapter = mock(net.Indyuce.mmoitems.server.ServerAdapter.class);
        net.Indyuce.mmoitems.stat.type.DoubleStat consumeSeconds = mock(net.Indyuce.mmoitems.stat.type.DoubleStat.class);
        when(serverAdapter.consumableConsumeSeconds()).thenReturn(consumeSeconds); when(mmo.getServerAdapter()).thenReturn(serverAdapter);
        MythicLib mythic = mock(MythicLib.class); when(mythic.namespace()).thenReturn("mythiclib");
        ServerVersion version = mock(ServerVersion.class); VersionWrapper wrapper = mock(VersionWrapper.class);
        when(mythic.getVersion()).thenReturn(version); when(version.getWrapper()).thenReturn(wrapper); MythicLib.plugin = mythic;
        when(wrapper.getNBTItem(any(ItemStack.class))).thenAnswer(c -> h.adapter(c.getArgument(0)));
        player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID()); when(player.getName()).thenReturn("Tester");
        playerData = mock(PlayerData.class); gemType = mock(Type.class); hostType = mock(Type.class); useGem = mock(GemStone.class);
        when(useGem.checkItemRequirements()).thenReturn(true);
        when(gemType.toUseItem(eq(playerData), any(NBTItem.class))).thenReturn(useGem);
        types = mockStatic(Type.class); types.when(() -> Type.get(any(NBTItem.class))).thenReturn(gemType);
        types.when(() -> Type.get("SWORD")).thenReturn(hostType);
        players = mockStatic(PlayerData.class); players.when(() -> PlayerData.get(player)).thenReturn(playerData);
        liveItems = mockConstruction(LiveMMOItem.class, (mmoItem, context) -> {
            ItemStack item = ((NBTItem) context.arguments().getFirst()).getItem();
            when(mmoItem.hasData(ItemStats.GEM_SOCKETS)).thenAnswer(c -> sockets.containsKey(item));
            when(mmoItem.getData(ItemStats.GEM_SOCKETS)).thenAnswer(c -> sockets.get(item));
            doAnswer(c -> { sockets.put(item,c.getArgument(1)); return null; }).when(mmoItem).setData(eq(ItemStats.GEM_SOCKETS),any());
            ItemStackBuilder builder = mock(ItemStackBuilder.class);
            when(builder.build()).thenAnswer(c -> { ItemStack result = item.clone(); sockets.put(result,sockets.get(item)); return result; });
            when(mmoItem.newBuilder()).thenReturn(builder);
        });
        applyResult = new GemStone.ApplyResult(ResultType.NONE);
        gemApplications = mockConstruction(GemStone.class, (gem, context) -> {
            appliedGems.add((NBTItem) context.arguments().get(1));
            when(gem.applyOntoItem(any(NBTItem.class),any(Type.class))).thenAnswer(c -> applyResult);
        });
        SocketTierRegistry.put("blue","Blue1",1); SocketTierRegistry.put("blue","Blue2",2); SocketTierRegistry.put("blue","Blue3",3);
        apply = new TieredSocketApplyListener(); rebuild = new TieredSocketRebuildListener();
    }
    @AfterEach void cleanup() throws Exception {
        PendingTieredSocketApply.clear(player); gemApplications.close(); liveItems.close(); players.close(); types.close();
        MMOItems.plugin = previousMmo; MythicLib.plugin = previousMythic; restoreMap(SocketTierRegistry.class,"COLORS",previousColors); h.close();
    }

    @Test void clickGuardsLeaveInventoryAndCursorUntouched() throws Exception {
        ItemStack gem = gem("Blue1"), host = host("Blue2"); InventoryClickEvent event = click(gem,host);
        field(SocketTierConfig.class,"enabled").set(h.sockets,false); apply.onInventoryClick(event);
        field(SocketTierConfig.class,"enabled").set(h.sockets,true);
        when(event.getAction()).thenReturn(InventoryAction.NOTHING); apply.onInventoryClick(event);
        when(event.getAction()).thenReturn(InventoryAction.SWAP_WITH_CURSOR);
        when(event.getWhoClicked()).thenReturn(mock(HumanEntity.class)); apply.onInventoryClick(event);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getCursor()).thenReturn(null); apply.onInventoryClick(event);
        when(event.getCursor()).thenReturn(gem); when(event.getCurrentItem()).thenReturn(null); apply.onInventoryClick(event);
        ItemStack air = new ItemStack(Material.AIR);
        when(event.getCurrentItem()).thenReturn(host); when(event.getCursor()).thenReturn(air); apply.onInventoryClick(event);
        when(event.getCursor()).thenReturn(gem); when(event.getCurrentItem()).thenReturn(air); apply.onInventoryClick(event);
        verify(event,never()).setCancelled(true); assertEquals(2,gem.getAmount()); assertTrue(gemApplications.constructed().isEmpty());
    }

    @Test void ignoresUnknownNonGemRequirementsNonMmoAndUnavailableSockets() {
        ItemStack gem = gem("Blue1"), host = host("Blue2"); InventoryClickEvent event = click(gem,host);
        types.when(() -> Type.get(any(NBTItem.class))).thenReturn(null); apply.onInventoryClick(event);
        types.when(() -> Type.get(any(NBTItem.class))).thenReturn(gemType);
        UseItem ordinary = mock(UseItem.class); when(gemType.toUseItem(eq(playerData),any(NBTItem.class))).thenReturn(ordinary); apply.onInventoryClick(event);
        when(gemType.toUseItem(eq(playerData),any(NBTItem.class))).thenReturn(useGem);
        when(useGem.checkItemRequirements()).thenReturn(false); apply.onInventoryClick(event); when(useGem.checkItemRequirements()).thenReturn(true);
        ItemStack stone = new ItemStack(Material.STONE); when(event.getCurrentItem()).thenReturn(stone); apply.onInventoryClick(event);
        when(event.getCurrentItem()).thenReturn(host); sockets.remove(host); apply.onInventoryClick(event);
        sockets.put(host,slots("Blue1")); apply.onInventoryClick(event); // MMOItems handles exact matches itself.
        sockets.put(host,slots("Red1")); apply.onInventoryClick(event);
        verify(event,never()).setCancelled(true); assertEquals(2,gem.getAmount()); assertTrue(gemApplications.constructed().isEmpty());
    }

    @Test void noneDoesNotConsumeGemAndFailureConsumesExactlyOneWithoutStashing() {
        ItemStack gem = gem("Blue1"), host = host("Blue3","Blue2"); InventoryClickEvent event = click(gem,host);
        PendingTieredSocketApply.stash(player,gem,"stale",false);
        apply.onInventoryClick(event); assertNull(PendingTieredSocketApply.poll(player)); assertEquals(2,gem.getAmount());
        verify(event,never()).setCancelled(true);
        applyResult = new GemStone.ApplyResult(ResultType.FAILURE); apply.onInventoryClick(event);
        assertEquals(1,gem.getAmount()); verify(event).setCancelled(true); verify(event,never()).setCurrentItem(any());
        assertNull(PendingTieredSocketApply.poll(player));
    }

    @Test void successfulTierUpgradeSpoofsOnlyCloneAndUsesLowestCompatibleSocket() {
        ItemStack gem = gem("Blue1"), host = host("Blue3","Blue2"), result = host();
        applyResult = new GemStone.ApplyResult(result); InventoryClickEvent event = click(gem,host); apply.onInventoryClick(event);
        assertEquals(1,gem.getAmount()); assertEquals("Blue1",data(gem,ItemStats.GEM_COLOR.getNBTPath()));
        verify(event).setCancelled(true); verify(event).setCurrentItem(result);
        PendingTieredSocketApply.Entry pending = PendingTieredSocketApply.poll(player);
        assertNotNull(pending); assertTrue(pending.wasSpoofed()); assertEquals("Blue2",pending.getAppliedSocketColor());
        assertEquals("Blue1",data(pending.getCursorSnapshot(),ItemStats.GEM_COLOR.getNBTPath()));
        assertEquals("Blue2",appliedGems.getFirst().getString(ItemStats.GEM_COLOR.getNBTPath()));
        assertEquals(2,appliedGems.getFirst().getItem().getAmount());
        GemStone constructed = gemApplications.constructed().getFirst();
        verify(constructed).applyOntoItem(h.adapter(host),hostType);
    }

    @Test void consumingLastGemKeepsTheAppliedCursorSnapshot() {
        ItemStack gem = gem("Blue1"), host = host("Blue2"), result = host(); gem.setAmount(1);
        applyResult = new GemStone.ApplyResult(result); apply.onInventoryClick(click(gem,host));
        assertEquals(0,gem.getAmount());
        PendingTieredSocketApply.Entry entry = PendingTieredSocketApply.poll(player);
        assertNotNull(entry);
        assertEquals(1,entry.getCursorSnapshot().getAmount(), "The rebuild context must preserve the consumed gem, before cursor mutation");
        assertEquals("Blue1",data(entry.getCursorSnapshot(),ItemStats.GEM_COLOR.getNBTPath()));
    }

    @Test void applyTierGateRejectsUnknownColorsAndKeepsRegisteredColors() throws Exception {
        ApplyGemStoneEvent event = applyEvent(ResultType.SUCCESS); VolatileMMOItem gem = mock(VolatileMMOItem.class);
        when(event.getGemStone()).thenReturn(gem);
        field(SocketTierConfig.class,"enabled").set(h.sockets,false); apply.onApplyGemTierGate(event);
        field(SocketTierConfig.class,"enabled").set(h.sockets,true); when(event.isCancelled()).thenReturn(true); apply.onApplyGemTierGate(event);
        when(event.isCancelled()).thenReturn(false); when(event.getResult()).thenReturn(ResultType.NONE); apply.onApplyGemTierGate(event);
        when(event.getResult()).thenReturn(ResultType.SUCCESS); apply.onApplyGemTierGate(event);
        when(gem.hasData(ItemStats.GEM_COLOR)).thenReturn(true); when(gem.getData(ItemStats.GEM_COLOR)).thenReturn(new StringData("Blue1")); apply.onApplyGemTierGate(event);
        verify(event,never()).setCancelled(true);
        when(gem.getData(ItemStats.GEM_COLOR)).thenReturn(new StringData("Unknown")); apply.onApplyGemTierGate(event);
        verify(event).setCancelled(true); verify(event).setResult(ResultType.NONE);
    }

    @Test void stashCursorOnlyForSuccessfulEnabledGemApplication() throws Exception {
        ApplyGemStoneEvent event = applyEvent(ResultType.SUCCESS); ItemStack gem = gem("Blue1"); when(player.getItemOnCursor()).thenReturn(gem);
        field(SocketTierConfig.class,"enabled").set(h.sockets,false); apply.onApplyGemStashCursor(event);
        field(SocketTierConfig.class,"enabled").set(h.sockets,true); when(event.getResult()).thenReturn(ResultType.FAILURE); apply.onApplyGemStashCursor(event);
        when(event.getResult()).thenReturn(ResultType.SUCCESS); when(player.getItemOnCursor()).thenReturn(null); apply.onApplyGemStashCursor(event);
        ItemStack air = new ItemStack(Material.AIR); when(player.getItemOnCursor()).thenReturn(air); apply.onApplyGemStashCursor(event);
        assertNull(PendingTieredSocketApply.poll(player));
        when(player.getItemOnCursor()).thenReturn(gem); apply.onApplyGemStashCursor(event);
        PendingTieredSocketApply.Entry entry = PendingTieredSocketApply.poll(player);
        assertEquals("Blue1",entry.getAppliedSocketColor()); assertFalse(entry.wasSpoofed()); assertNotSame(gem,entry.getCursorSnapshot());
    }

    @Test void socketEditorReadsDefensivelyAndReplacesOnlyOneMatchingSlot() {
        ItemStack plain = new ItemStack(Material.STONE), air = new ItemStack(Material.AIR);
        assertNull(GemSocketsNbtEditor.getSockets(null)); assertNull(GemSocketsNbtEditor.getSockets(air)); assertNull(GemSocketsNbtEditor.getSockets(plain));
        assertEquals(Set.of(),GemSocketsNbtEditor.getGemstoneUuids(plain)); assertEquals(List.of(),GemSocketsNbtEditor.getEmptySlots(plain));
        UUID id = UUID.randomUUID(); ItemStack item = host("Blue1","Blue1","Red"); sockets.get(item).add(gemData(id));
        assertEquals(Set.of(id),GemSocketsNbtEditor.getGemstoneUuids(item));
        List<String> read = GemSocketsNbtEditor.getEmptySlots(item); read.clear(); assertEquals(3,sockets.get(item).getEmptySlots().size());
        assertNull(GemSocketsNbtEditor.replaceEmptySlotColor(null,"a","b"));
        assertSame(item,GemSocketsNbtEditor.replaceEmptySlotColor(item,null,"b")); assertSame(item,GemSocketsNbtEditor.replaceEmptySlotColor(item,"a",null));
        assertSame(item,GemSocketsNbtEditor.replaceEmptySlotColor(item,"a","a")); assertSame(plain,GemSocketsNbtEditor.replaceEmptySlotColor(plain,"a","b"));
        assertSame(item,GemSocketsNbtEditor.replaceEmptySlotColor(item,"absent","b"));
        ItemStack replaced = GemSocketsNbtEditor.replaceEmptySlotColor(item,"Blue1","Blue2");
        assertNotSame(item,replaced); assertEquals(List.of("Blue2","Blue1","Red"),GemSocketsNbtEditor.getEmptySlots(replaced));
        assertEquals(List.of("Blue1","Green"),GemSocketsNbtEditor.addedEmptySlots(List.of("Red","Blue1"),List.of("Blue1","Red","Blue1","Green")));
    }

    @Test void rebuildAppliesOverridesOnlyToTheNewGemAndMergesExistingOnes() {
        ItemStack old = host(), fresh = host(); UUID kept = UUID.randomUUID(), added = UUID.randomUUID();
        sockets.get(old).add(gemData(kept)); sockets.get(fresh).add(gemData(kept)); sockets.get(fresh).add(gemData(added));
        SocketOverrideStore.put(old,kept,"Blue2");
        MMOItemRebuildEvent event = event(old,fresh,RebuildReason.GEM_APPLY);
        rebuild.onRebuild(new MMOItemRebuildEvent(player,null,fresh,RebuildReason.GEM_APPLY));
        rebuild.onRebuild(new MMOItemRebuildEvent(player,old,null,RebuildReason.GEM_APPLY));
        rebuild.onRebuild(event); assertEquals("Blue2",SocketOverrideStore.get(fresh,kept)); assertNull(SocketOverrideStore.get(fresh,added));
        event.setSpoofed(true); rebuild.onRebuild(event); assertNull(SocketOverrideStore.get(fresh,added));
        event.setAppliedSocketColor("Blue3"); rebuild.onRebuild(event); assertEquals("Blue3",SocketOverrideStore.get(fresh,added));
        MMOItemRebuildEvent unchanged = event(old,old,RebuildReason.GEM_APPLY); unchanged.setSpoofed(true); unchanged.setAppliedSocketColor("Blue3");
        rebuild.onRebuild(unchanged); assertEquals(Map.of(kept.toString(),"Blue2"),SocketOverrideStore.read(old));
    }

    @Test void unsocketRestoresOriginalTierAndRemovesOnlyConsumedOverride() {
        UUID removed = UUID.randomUUID(), kept = UUID.randomUUID(); ItemStack old = host("Red"), fresh = host("Red","Blue1");
        sockets.get(old).add(gemData(removed)); sockets.get(old).add(gemData(kept)); sockets.get(fresh).add(gemData(kept));
        SocketOverrideStore.put(old,removed,"Blue3"); SocketOverrideStore.put(old,kept,"Blue2");
        MMOItemRebuildEvent event = event(old,fresh,RebuildReason.GEM_UNSOCKET); rebuild.onRebuild(event);
        assertEquals(List.of("Red","Blue3"),GemSocketsNbtEditor.getEmptySlots(event.getNewItem()));
        assertEquals(Map.of(kept.toString(),"Blue2"),SocketOverrideStore.read(event.getNewItem()));
        assertEquals("Blue3",SocketOverrideStore.get(old,removed));
    }

    @Test void unsocketHandlesNoRemovalMissingOverridesAndAbsentOrCorrectSlots() {
        for (String mode : List.of("no-removal","no-override","no-slot","correct-slot")) {
            UUID id = UUID.randomUUID(); ItemStack old = host(), fresh = mode.equals("correct-slot") ? host("Blue2") : host();
            sockets.get(old).add(gemData(id)); if (mode.equals("no-removal")) sockets.get(fresh).add(gemData(id));
            if (!mode.equals("no-override")) SocketOverrideStore.put(old,id,"Blue2");
            MMOItemRebuildEvent event = event(old,fresh,RebuildReason.GEM_UNSOCKET); rebuild.onRebuild(event);
            assertSame(fresh,event.getNewItem());
            if (mode.equals("no-removal")) assertEquals("Blue2",SocketOverrideStore.get(fresh,id)); else assertNull(SocketOverrideStore.get(fresh,id));
        }
    }

    ItemStack gem(String color) { ItemStack item = h.item(Material.EMERALD,"GEM_STONE","RUBY"); item.setAmount(2); putData(item,ItemStats.GEM_COLOR.getNBTPath(),color); return item; }
    ItemStack host(String... colors) { ItemStack item = h.item(Material.DIAMOND_SWORD,"SWORD","RUBY"); sockets.put(item,slots(colors)); return item; }
    static GemSocketsData slots(String... colors) { return new GemSocketsData(new ArrayList<>(Arrays.asList(colors))); }
    static GemstoneData gemData(UUID id) { return new GemstoneData("GEM_STONE","RUBY","Blue1","Ruby",id); }
    InventoryClickEvent click(ItemStack gem, ItemStack host) {
        InventoryClickEvent event = mock(InventoryClickEvent.class); when(event.getWhoClicked()).thenReturn(player);
        when(event.getAction()).thenReturn(InventoryAction.SWAP_WITH_CURSOR); when(event.getCursor()).thenReturn(gem); when(event.getCurrentItem()).thenReturn(host); return event;
    }
    ApplyGemStoneEvent applyEvent(ResultType result) { ApplyGemStoneEvent event = mock(ApplyGemStoneEvent.class); when(event.getPlayer()).thenReturn(player); when(event.getResult()).thenReturn(result); return event; }
    MMOItemRebuildEvent event(ItemStack old, ItemStack fresh, RebuildReason reason) { return new MMOItemRebuildEvent(player,old,fresh,reason); }
}
