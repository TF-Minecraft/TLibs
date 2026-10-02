package net.tfminecraft.tlibs.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

public final class PersistentDataCopier {
	private static final String PUBLIC_BUKKIT_VALUES = "PublicBukkitValues";

	private PersistentDataCopier() {
	}

	public static void copy(ItemMeta fromMeta, ItemMeta toMeta) {
		if (fromMeta == null || toMeta == null) {
			return;
		}
		copy(fromMeta.getPersistentDataContainer(), toMeta.getPersistentDataContainer());
	}

	public static void copy(PersistentDataContainer from, PersistentDataContainer to) {
		if (from == null || to == null) {
			return;
		}
		from.copyTo(to, true);
	}

	public static PersistentDataContainer snapshot(ItemStack item) {
		if (item == null || !item.hasItemMeta()) {
			return null;
		}
		ItemMeta meta = item.getItemMeta();
		PersistentDataContainer from = meta.getPersistentDataContainer();
		if (from.getKeys().isEmpty()) {
			return null;
		}
		PersistentDataContainer snapshot = from.getAdapterContext().newPersistentDataContainer();
		copy(from, snapshot);
		return snapshot;
	}

	public static void applySnapshot(ItemStack item, PersistentDataContainer snapshot) {
		if (item == null || snapshot == null || snapshot.getKeys().isEmpty()) {
			return;
		}
		ItemMeta meta = item.getItemMeta();
		if (meta == null) {
			return;
		}
		copy(snapshot, meta.getPersistentDataContainer());
		item.setItemMeta(meta);
	}

	public static String publicBukkitValuesTag() {
		return PUBLIC_BUKKIT_VALUES;
	}

}
