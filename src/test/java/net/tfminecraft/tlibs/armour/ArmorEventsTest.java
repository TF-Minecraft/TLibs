package net.tfminecraft.tlibs.armour;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

class ArmorEventsTest {
    private MockedStatic<Bukkit> bukkit;
    private PluginManager plugins;
    private ArmorListener listener;
    private Player player;
    private PlayerInventory inventory;
    private final ItemStack[] worn = new ItemStack[4];
    private final List<ArmorEquipEvent> emitted = new ArrayList<>();
    private boolean cancel;

    @BeforeAll static void startServer() { MockBukkit.mock(); }
    @AfterAll static void stopServer() { MockBukkit.unmock(); }
    @BeforeEach
    void setup() {
        Server server = mock(Server.class);
        plugins = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(plugins);
        bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        doAnswer(call -> { ArmorEquipEvent event = call.getArgument(0); emitted.add(event); event.setCancelled(cancel); return null; })
                .when(plugins).callEvent(any(ArmorEquipEvent.class));
        listener = new ArmorListener(List.of("CHEST", "invalid"));
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getType()).thenReturn(InventoryType.PLAYER);
        when(inventory.getHelmet()).thenAnswer(call -> worn[3]);
        when(inventory.getChestplate()).thenAnswer(call -> worn[2]);
        when(inventory.getLeggings()).thenAnswer(call -> worn[1]);
        when(inventory.getBoots()).thenAnswer(call -> worn[0]);
        when(inventory.getArmorContents()).thenReturn(worn);
    }
    @AfterEach void cleanup() { bukkit.close(); }

    @Test
    void armorClassificationAndEventPropertiesFollowThePublicContract() {
        assertNull(ArmorType.matchType(null));
        assertNull(ArmorType.matchType(new ItemStack(Material.AIR)));
        ItemStack empty = new ItemStack(Material.DIAMOND_HELMET);
        empty.setAmount(0);
        assertNull(ArmorType.matchType(empty));
        assertNull(ArmorType.matchType(new ItemStack(Material.STONE)));
        for (Material material : List.of(Material.DIAMOND_HELMET, Material.PLAYER_HEAD, Material.SKELETON_SKULL, Material.CARVED_PUMPKIN))
            assertEquals(ArmorType.HELMET, ArmorType.matchType(new ItemStack(material)));
        assertEquals(ArmorType.CHESTPLATE, ArmorType.matchType(new ItemStack(Material.ELYTRA)));
        assertEquals(ArmorType.CHESTPLATE, ArmorType.matchType(new ItemStack(Material.DIAMOND_CHESTPLATE)));
        assertEquals(ArmorType.LEGGINGS, ArmorType.matchType(new ItemStack(Material.DIAMOND_LEGGINGS)));
        assertEquals(ArmorType.BOOTS, ArmorType.matchType(new ItemStack(Material.DIAMOND_BOOTS)));
        assertEquals(5, ArmorType.HELMET.getSlot());
        assertEquals(8, ArmorType.BOOTS.getSlot());
        ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);
        ArmorEquipEvent event = new ArmorEquipEvent(player, ArmorEquipEvent.EquipMethod.PICK_DROP, ArmorType.HELMET, null, helmet);
        assertEquals(ArmorType.HELMET, event.getType());
        assertEquals(ArmorEquipEvent.EquipMethod.PICK_DROP, event.getMethod());
        assertSame(ArmorEquipEvent.getHandlerList(), event.getHandlers());
        assertFalse(event.isCancelled());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
        assertNull(event.getOldArmorPiece());
        assertSame(helmet, event.getNewArmorPiece());
        event.setOldArmorPiece(helmet);
        event.setNewArmorPiece(empty);
        assertSame(helmet, event.getOldArmorPiece());
        assertNull(event.getNewArmorPiece());
        assertEquals(8, ArmorEquipEvent.EquipMethod.values().length);
    }

    @Test
    void registersBothListenersAndHandlesResourceReadAndCloseFailures() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getResource("armorequipevent-blocked.txt")).thenReturn(new ByteArrayInputStream("CHEST\nFURNACE\n".getBytes(StandardCharsets.UTF_8)));
        ArmorEquipEvent.registerListener(plugin);
        verify(plugins).registerEvents(any(ArmorListener.class), eq(plugin));
        verify(plugins).registerEvents(any(DispenserArmorListener.class), eq(plugin));
        when(plugin.getResource("armorequipevent-blocked.txt")).thenReturn(new InputStream() {
            @Override public int read() throws IOException { throw new IOException("read failed"); }
        });
        assertDoesNotThrow(() -> ArmorEquipEvent.registerListener(plugin));
        when(plugin.getResource("armorequipevent-blocked.txt")).thenReturn(new ByteArrayInputStream(new byte[0]) {
            @Override public void close() throws IOException { throw new IOException("close failed"); }
        });
        doThrow(new IllegalStateException("dispenser listener unavailable"))
                .when(plugins).registerEvents(any(DispenserArmorListener.class), eq(plugin));
        assertDoesNotThrow(() -> ArmorEquipEvent.registerListener(plugin));
    }

    @Test
    void missingOptionalBlockedResourceDoesNotPreventRegistration() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        assertDoesNotThrow(() -> ArmorEquipEvent.registerListener(plugin));
        verify(plugins).registerEvents(any(ArmorListener.class), eq(plugin));
    }

    @Test
    void clickGuardsIgnoreUnrelatedInventoryOperations() {
        InventoryClickEvent event = click(ClickType.LEFT, null, null, 5);
        when(event.getAction()).thenReturn(InventoryAction.NOTHING);
        listener.onClick(event);
        when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
        when(event.getSlotType()).thenReturn(InventoryType.SlotType.OUTSIDE);
        listener.onClick(event);
        when(event.getSlotType()).thenReturn(InventoryType.SlotType.ARMOR);
        Inventory chest = mock(Inventory.class);
        when(chest.getType()).thenReturn(InventoryType.CHEST);
        when(event.getClickedInventory()).thenReturn(chest);
        listener.onClick(event);
        when(event.getClickedInventory()).thenReturn(inventory);
        when(event.getInventory()).thenReturn(chest);
        listener.onClick(event);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getWhoClicked()).thenReturn(mock(org.bukkit.entity.HumanEntity.class));
        listener.onClick(event);
        listener.onClick(click(ClickType.LEFT, null, new ItemStack(Material.DIAMOND_HELMET), 6));
        assertTrue(emitted.isEmpty());
    }

    @Test
    void shiftClicksEquipAndUnequipEveryArmorSlotAndPropagateCancellation() {
        Material[] pieces = {Material.DIAMOND_BOOTS, Material.DIAMOND_LEGGINGS, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_HELMET};
        for (int i = 0; i < pieces.length; i++) {
            ItemStack piece = new ItemStack(pieces[i]);
            ArmorType type = ArmorType.matchType(piece);
            InventoryClickEvent equip = click(ClickType.SHIFT_LEFT, piece, null, 15);
            listener.onClick(equip);
            ArmorEquipEvent emittedEquip = emitted.getLast();
            assertEquals(ArmorEquipEvent.EquipMethod.SHIFT_CLICK, emittedEquip.getMethod());
            assertSame(piece, emittedEquip.getNewArmorPiece());
            assertNull(emittedEquip.getOldArmorPiece());
            worn[i] = piece;
            int count = emitted.size();
            listener.onClick(equip);
            assertEquals(count, emitted.size());
            cancel = true;
            InventoryClickEvent unequip = click(ClickType.SHIFT_RIGHT, piece, null, type.getSlot());
            listener.onClick(unequip);
            verify(unequip).setCancelled(true);
            assertSame(piece, emitted.getLast().getOldArmorPiece());
            assertNull(emitted.getLast().getNewArmorPiece());
            worn[i] = null;
            cancel = false;
        }
        int count = emitted.size();
        listener.onClick(click(ClickType.SHIFT_LEFT, new ItemStack(Material.STONE), null, 10));
        assertEquals(count, emitted.size());
    }

    @Test
    void ordinaryAndHotbarSwapsEmitCorrectPieces() {
        ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);
        ItemStack old = new ItemStack(Material.IRON_HELMET);
        cancel = true;
        InventoryClickEvent equip = click(ClickType.LEFT, old, helmet, 5);
        listener.onClick(equip);
        verify(equip).setCancelled(true);
        assertSame(old, emitted.getLast().getOldArmorPiece());
        assertSame(helmet, emitted.getLast().getNewArmorPiece());
        assertEquals(ArmorEquipEvent.EquipMethod.PICK_DROP, emitted.getLast().getMethod());
        listener.onClick(click(ClickType.LEFT, helmet, null, 5));
        assertNull(emitted.getLast().getNewArmorPiece());
        InventoryClickEvent hotbar = click(ClickType.NUMBER_KEY, old, null, 5);
        when(hotbar.getHotbarButton()).thenReturn(2);
        when(hotbar.getSlot()).thenReturn(39);
        when(inventory.getItem(2)).thenReturn(helmet);
        when(inventory.getItem(39)).thenReturn(old);
        listener.onClick(hotbar);
        assertEquals(ArmorEquipEvent.EquipMethod.HOTBAR_SWAP, emitted.getLast().getMethod());
        assertSame(helmet, emitted.getLast().getNewArmorPiece());
        InventoryClickEvent offhand = click(ClickType.SWAP_OFFHAND, old, null, 5);
        when(offhand.getHotbarButton()).thenReturn(-1);
        when(inventory.getItem(EquipmentSlot.OFF_HAND)).thenReturn(helmet);
        listener.onClick(offhand);
        assertSame(helmet, emitted.getLast().getNewArmorPiece());
        when(inventory.getItem(EquipmentSlot.OFF_HAND)).thenReturn(null);
        listener.onClick(offhand);
        assertSame(old, emitted.getLast().getOldArmorPiece());
    }

    @Test
    void interactionsRespectBlockedBlocksDeniedActionsAndOccupiedSlots() {
        ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);
        PlayerInteractEvent event = interact(Action.RIGHT_CLICK_BLOCK, helmet);
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.CHEST);
        when(event.getClickedBlock()).thenReturn(block);
        listener.onInteract(event);
        assertTrue(emitted.isEmpty());
        when(player.isSneaking()).thenReturn(true);
        cancel = true;
        listener.onInteract(event);
        verify(event).setCancelled(true);
        verify(player).updateInventory();
        assertEquals(ArmorEquipEvent.EquipMethod.HOTBAR, emitted.getLast().getMethod());
        worn[3] = helmet;
        int count = emitted.size();
        listener.onInteract(event);
        assertEquals(count, emitted.size());
        when(event.useItemInHand()).thenReturn(Result.DENY);
        listener.onInteract(event);
        listener.onInteract(interact(Action.PHYSICAL, helmet));
        listener.onInteract(interact(Action.LEFT_CLICK_AIR, helmet));
        listener.onInteract(interact(Action.RIGHT_CLICK_AIR, new ItemStack(Material.CARVED_PUMPKIN)));
        listener.onInteract(interact(Action.RIGHT_CLICK_AIR, new ItemStack(Material.STONE)));
        listener.onInteract(interact(Action.RIGHT_CLICK_AIR, null));
        assertEquals(count, emitted.size());
        worn[3] = null;
        for (Material material : List.of(Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS)) {
            PlayerInteractEvent other = interact(Action.RIGHT_CLICK_AIR, new ItemStack(material));
            when(other.useInteractedBlock()).thenReturn(Result.DENY);
            listener.onInteract(other);
            assertEquals(ArmorType.matchType(other.getItem()), emitted.getLast().getType());
        }
    }

    @Test
    void dragsAndDispensersPropagateEquipCancellation() {
        ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getOldCursor()).thenReturn(helmet);
        when(drag.getWhoClicked()).thenReturn(player);
        when(drag.getRawSlots()).thenReturn(Set.of());
        listener.onDrag(drag);
        when(drag.getRawSlots()).thenReturn(Set.of(6));
        listener.onDrag(drag);
        assertTrue(emitted.isEmpty());
        when(drag.getRawSlots()).thenReturn(Set.of(5));
        cancel = true;
        listener.onDrag(drag);
        verify(drag).setCancelled(true);
        verify(drag).setResult(Result.DENY);
        assertEquals(ArmorEquipEvent.EquipMethod.DRAG, emitted.getLast().getMethod());
        BlockDispenseArmorEvent dispense = mock(BlockDispenseArmorEvent.class);
        when(dispense.getItem()).thenReturn(helmet);
        when(dispense.getTargetEntity()).thenReturn(player);
        new DispenserArmorListener().onArmorDispense(dispense);
        verify(dispense).setCancelled(true);
        assertEquals(ArmorEquipEvent.EquipMethod.DISPENSER, emitted.getLast().getMethod());
        int count = emitted.size();
        when(dispense.getTargetEntity()).thenReturn(mock(LivingEntity.class));
        new DispenserArmorListener().onArmorDispense(dispense);
        when(dispense.getItem()).thenReturn(new ItemStack(Material.STONE));
        new DispenserArmorListener().onArmorDispense(dispense);
        assertEquals(count, emitted.size());
    }

    @Test
    void cancelledBreakRestoresEachArmorSlotOneDamagePointBeforeBreaking() {
        cancel = true;
        Material[] materials = {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS};
        for (Material material : materials) {
            ItemStack broken = new ItemStack(material);
            Damageable damage = (Damageable) broken.getItemMeta();
            damage.setDamage(50);
            broken.setItemMeta(damage);
            listener.onBreak(new PlayerItemBreakEvent(player, broken));
        }
        var restored = org.mockito.ArgumentCaptor.forClass(ItemStack.class);
        verify(inventory).setHelmet(restored.capture());
        verify(inventory).setChestplate(restored.capture());
        verify(inventory).setLeggings(restored.capture());
        verify(inventory).setBoots(restored.capture());
        for (ItemStack item : restored.getAllValues()) {
            assertEquals(1, item.getAmount());
            assertEquals(49, ((Damageable) item.getItemMeta()).getDamage());
        }
        int count = emitted.size();
        listener.onBreak(new PlayerItemBreakEvent(player, new ItemStack(Material.STONE)));
        assertEquals(count, emitted.size());
    }

    @Test
    void cancelledBreakPreservesLargeCustomDurabilityWithoutShortOverflow() {
        cancel = true;
        ItemStack broken = mock(ItemStack.class);
        ItemStack clone = mock(ItemStack.class);
        Damageable damage = new org.mockbukkit.mockbukkit.inventory.meta.ItemMetaMock();
        damage.setMaxDamage(40000);
        damage.setDamage(40000);
        assertEquals(40000, damage.getDamage());
        when(broken.getType()).thenReturn(Material.DIAMOND_HELMET);
        when(broken.getAmount()).thenReturn(1);
        when(broken.clone()).thenReturn(clone);
        when(clone.getItemMeta()).thenReturn(damage);
        listener.onBreak(new PlayerItemBreakEvent(player, broken));
        verify(inventory).setHelmet(clone);
        var restoredMeta = org.mockito.ArgumentCaptor.forClass(org.bukkit.inventory.meta.ItemMeta.class);
        verify(clone).setItemMeta(restoredMeta.capture());
        assertEquals(39999, ((Damageable) restoredMeta.getValue()).getDamage());
    }

    @Test
    void deathEmitsRemovalForEveryNonemptyArmorPieceUnlessInventoryIsKept() {
        worn[0] = new ItemStack(Material.DIAMOND_BOOTS);
        worn[1] = new ItemStack(Material.AIR);
        worn[3] = new ItemStack(Material.DIAMOND_HELMET);
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getKeepInventory()).thenReturn(true);
        listener.onDeath(event);
        assertTrue(emitted.isEmpty());
        when(event.getKeepInventory()).thenReturn(false);
        listener.onDeath(event);
        assertEquals(2, emitted.size());
        assertTrue(emitted.stream().allMatch(change -> change.getMethod() == ArmorEquipEvent.EquipMethod.DEATH && change.getNewArmorPiece() == null));
    }

    private InventoryClickEvent click(ClickType click, ItemStack current, ItemStack cursor, int rawSlot) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getClick()).thenReturn(click);
        when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
        when(event.getSlotType()).thenReturn(InventoryType.SlotType.ARMOR);
        when(event.getClickedInventory()).thenReturn(inventory);
        Inventory top = mock(Inventory.class);
        when(top.getType()).thenReturn(InventoryType.CRAFTING);
        when(event.getInventory()).thenReturn(top);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(rawSlot);
        when(event.getCurrentItem()).thenReturn(current);
        when(event.getCursor()).thenReturn(cursor);
        return event;
    }
    private PlayerInteractEvent interact(Action action, ItemStack item) {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getAction()).thenReturn(action);
        when(event.getItem()).thenReturn(item);
        when(event.useItemInHand()).thenReturn(Result.DEFAULT);
        when(event.useInteractedBlock()).thenReturn(Result.DEFAULT);
        return event;
    }
}
