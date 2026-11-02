package net.tfminecraft.tlibs.itemscan;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public interface ItemScanHandler {

    /** {@link #scan} result: look at this slot again on the next scan, changed or not. The default. */
    long EVERY_SCAN = Long.MIN_VALUE;

    /** {@link #scan} result: skip this slot until its item changes. */
    long WHEN_CHANGED = Long.MAX_VALUE;

    boolean matches(ItemStack stack);

    void update(Player player, Inventory inventory, int slot, ItemStack stack);

    /**
     * Checks one slot. The scans call this instead of {@link #matches} and {@link #update}, and skip it while the slot
     * keeps the same item (type, amount and data) and the time it returned has not come.
     *
     * <p>Return {@link #EVERY_SCAN}, {@link #WHEN_CHANGED}, or the {@link System#currentTimeMillis()} time from which
     * the unchanged item needs this handler again (food that ages, for one). Return EVERY_SCAN whenever anything but
     * the item and the clock decides what this handler does with it, such as the player, the slot or the held item.
     * A plugin whose answers depend on its config calls {@link ItemScanService#rescanAll()} after a reload.
     *
     * <p>The default calls matches, then update when it matched. A matched item is seen again on every scan, as before;
     * an item that didn't match is skipped until it changes, since {@link #matches} only sees the item. That keeps an
     * unchanged slot from running (and being copied for) a handler it can't concern. For an item being picked up,
     * inventory is null, slot is -1 and the result is ignored.
     */
    default long scan(Player player, Inventory inventory, int slot, ItemStack stack) {
        if (!matches(stack)) {
            return WHEN_CHANGED;
        }
        update(player, inventory, slot, stack);
        return EVERY_SCAN;
    }
}
