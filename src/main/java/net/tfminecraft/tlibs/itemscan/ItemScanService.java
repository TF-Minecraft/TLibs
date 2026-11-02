package net.tfminecraft.tlibs.itemscan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Bukkit;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class ItemScanService implements Listener {

    private static ItemScanService instance;

    private final List<ItemScanHandler> handlers = new CopyOnWriteArrayList<>();
    // What each online player's scans last saw, so a slot whose item hasn't changed can skip handlers that said so.
    private final Map<UUID, PlayerMemory> memories = new HashMap<>();
    private int roundRobin;
    private BukkitTask task;

    public static ItemScanService get() {
        return instance;
    }

    public static void start(JavaPlugin plugin) {
        if (instance != null) {
            instance.stop();
        }
        instance = new ItemScanService();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        instance.task = Bukkit.getScheduler().runTaskTimer(plugin, instance::pulse, 0L, 2L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        HandlerList.unregisterAll(this);
        handlers.clear();
        memories.clear();
        if (instance == this) {
            instance = null;
        }
    }

    public void subscribe(ItemScanHandler handler) {
        if (handler != null && !handlers.contains(handler)) {
            handlers.add(handler);
            memories.clear();
        }
    }

    public void unsubscribe(ItemScanHandler handler) {
        if (handlers.remove(handler)) {
            memories.clear();
        }
    }

    /** Forgets every skipped slot, so each handler sees every item again on its next scan (after a config reload). */
    public void rescanAll() {
        memories.clear();
    }

    private void pulse() {
        if (handlers.isEmpty()) {
            return;
        }
        List<? extends Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            roundRobin = 0;
            memories.clear();
            return;
        }
        if (roundRobin >= players.size()) {
            roundRobin = 0;
        }
        Player player = players.get(roundRobin);
        roundRobin++;
        scanPlayerView(player);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        scanOpenView(player, event.getView());
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Item entity = event.getItem();
        ItemStack stack = entity.getItemStack();
        if (stack != null && !stack.isEmpty()) {
            for (ItemScanHandler handler : handlers) {
                handler.scan(player, null, -1, stack);
            }
        }
        entity.setItemStack(stack);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        memories.remove(event.getPlayer().getUniqueId());
    }

    private void scanPlayerView(Player player) {
        InventoryView view = player.getOpenInventory();
        InventoryType topType = view.getTopInventory().getType();
        if (topType == InventoryType.CRAFTING || topType == InventoryType.CREATIVE) {
            memoryOf(player).container = null;
            scanInventory(player, player.getInventory(), false);
            return;
        }
        scanOpenView(player, view);
    }

    private void scanOpenView(Player player, InventoryView view) {
        scanInventory(player, view.getTopInventory(), true);
        scanInventory(player, player.getInventory(), false);
    }

    private void scanInventory(Player player, Inventory inventory, boolean container) {
        ItemScanHandler[] current = handlers.toArray(new ItemScanHandler[0]);
        if (current.length == 0 || inventory == null || !isScannable(inventory)) {
            return;
        }
        int size = inventory.getSize();
        SlotMemory memory = slotMemory(player, inventory, size, container);
        long now = System.currentTimeMillis();
        for (int slot = 0; slot < size; slot++) {
            scanSlot(player, inventory, slot, current, memory, now);
        }
    }

    private static boolean isScannable(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder(false);
        if (holder == null) {
            return true;
        }
        return holder instanceof Player || holder instanceof BlockState
                || holder instanceof Entity || holder instanceof DoubleChest;
    }

    private PlayerMemory memoryOf(Player player) {
        return memories.computeIfAbsent(player.getUniqueId(), id -> new PlayerMemory());
    }

    private SlotMemory slotMemory(Player player, Inventory inventory, int size, boolean container) {
        PlayerMemory memory = memoryOf(player);
        SlotMemory slots = container ? memory.container : memory.own;
        if (slots == null || slots.items.length != size || !slots.inventory.equals(inventory)) {
            slots = new SlotMemory(inventory, size);
            if (container) {
                memory.container = slots;
            } else {
                memory.own = slots;
            }
        }
        return slots;
    }

    private static void scanSlot(Player player, Inventory inventory, int slot, ItemScanHandler[] current,
            SlotMemory memory, long now) {
        ItemStack stack = inventory.getItem(slot);
        if (stack == null || stack.isEmpty()) {
            memory.forget(slot);
            return;
        }
        ItemStack seen = memory.items[slot];
        long[] due = memory.due[slot];
        boolean unchanged = seen != null && due.length == current.length && stack.equals(seen);
        if (!unchanged) {
            due = new long[current.length];
        }
        // A copy from before any handler ran, to tell whether one changed the item (in place or not).
        ItemStack before = unchanged ? seen : null;
        boolean ran = false;
        for (int i = 0; i < current.length; i++) {
            if (unchanged && due[i] > now) {
                continue;
            }
            if (before == null) {
                before = stack.clone();
            }
            ran = true;
            due[i] = current[i].scan(player, inventory, slot, stack);
        }
        if (!ran) {
            return;
        }
        ItemStack after = inventory.getItem(slot);
        if (after != null && !after.isEmpty() && after.equals(before)) {
            memory.items[slot] = before;
            memory.due[slot] = due;
        } else {
            // A handler changed the item: next scan, every handler sees the new one, as before.
            memory.forget(slot);
        }
    }

    private static final class PlayerMemory {
        private SlotMemory own;
        private SlotMemory container;
    }

    /** One inventory's slots as last scanned: a copy of each item and when each handler needs it again. */
    private static final class SlotMemory {
        private final Inventory inventory;
        private final ItemStack[] items;
        private final long[][] due;

        private SlotMemory(Inventory inventory, int size) {
            this.inventory = inventory;
            this.items = new ItemStack[size];
            this.due = new long[size][];
        }

        private void forget(int slot) {
            items[slot] = null;
            due[slot] = null;
        }
    }
}
