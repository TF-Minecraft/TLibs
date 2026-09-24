package net.tfminecraft.tlibs.itemscan;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public interface ItemScanHandler {

    boolean matches(ItemStack stack);

    void update(Player player, Inventory inventory, int slot, ItemStack stack);
}
