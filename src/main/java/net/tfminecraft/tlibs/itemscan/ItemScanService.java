package net.tfminecraft.tlibs.itemscan;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class ItemScanService implements Listener {

    private static ItemScanService instance;

    private final List<ItemScanHandler> handlers = new CopyOnWriteArrayList<>();
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
        if (instance == this) {
            instance = null;
        }
    }

    public void subscribe(ItemScanHandler handler) {
        if (handler != null && !handlers.contains(handler)) {
            handlers.add(handler);
        }
    }

    public void unsubscribe(ItemScanHandler handler) {
        handlers.remove(handler);
    }

    private void pulse() {
        if (handlers.isEmpty()) {
            return;
        }
        List<? extends Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            roundRobin = 0;
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
        applyHandlers(player, null, -1, stack);
        entity.setItemStack(stack);
    }

    private void scanPlayerView(Player player) {
        InventoryView view = player.getOpenInventory();
        InventoryType topType = view.getTopInventory().getType();
        if (topType == InventoryType.CRAFTING || topType == InventoryType.CREATIVE) {
            scanInventory(player, player.getInventory());
            return;
        }
        scanOpenView(player, view);
    }

    private void scanOpenView(Player player, InventoryView view) {
        scanInventory(player, view.getTopInventory());
        scanInventory(player, player.getInventory());
    }

    private void scanInventory(Player player, Inventory inventory) {
        if (inventory == null || !isScannable(inventory)) {
            return;
        }
        int size = inventory.getSize();
        for (int slot = 0; slot < size; slot++) {
            scanSlot(player, inventory, slot);
        }
    }

    private static boolean isScannable(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        if (holder == null) {
            return true;
        }
        return holder instanceof Player || holder instanceof BlockState
                || holder instanceof Entity || holder instanceof DoubleChest;
    }

    private void scanSlot(Player player, Inventory inventory, int slot) {
        ItemStack stack = inventory.getItem(slot);
        applyHandlers(player, inventory, slot, stack);
    }

    private void applyHandlers(Player player, Inventory inventory, int slot, ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || handlers.isEmpty()) {
            return;
        }
        for (ItemScanHandler handler : handlers) {
            if (handler.matches(stack)) {
                handler.update(player, inventory, slot, stack);
            }
        }
    }
}
