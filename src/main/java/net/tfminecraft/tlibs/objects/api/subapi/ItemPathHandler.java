package net.tfminecraft.tlibs.objects.api.subapi;

import org.bukkit.inventory.ItemStack;

/**
 * Plugin-owned item path prefix (e.g. magic.). TLibs does not depend on those plugins.
 */
public interface ItemPathHandler {

	ItemStack create(String fullPath);

	default boolean matches(ItemStack item, String fullPath) {
		return false;
	}
}
