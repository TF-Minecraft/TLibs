package net.tfminecraft.tlibs.armour;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.itemscan.ItemScanHandler;

/**
 * Repairs the vanilla bar of crafted pieces that a skin left reading broken while their MMOItems durability is
 * fine, such as a helmet moved from a hat look (bar of 1003) to a leather armour skin (bar of 55).
 */
public final class FalseBreakRepair implements ItemScanHandler {

	@Override
	public boolean matches(ItemStack stack) {
		return CosmeticHelmetFix.showsFalseBreak(stack);
	}

	@Override
	public void update(Player player, Inventory inventory, int slot, ItemStack stack) {
		if (CosmeticHelmetFix.repairFalseBreak(stack) && inventory != null) {
			inventory.setItem(slot, stack);
		}
	}
}
