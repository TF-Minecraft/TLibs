package net.tfminecraft.tlibs.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

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
		for (NamespacedKey key : from.getKeys()) {
			copyKey(from, to, key);
		}
	}

	public static PersistentDataContainer snapshot(ItemStack item) {
		if (item == null || !item.hasItemMeta()) {
			return null;
		}
		ItemMeta meta = item.getItemMeta();
		if (meta == null) {
			return null;
		}
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

	private static void copyKey(PersistentDataContainer from, PersistentDataContainer to, NamespacedKey key) {
		if (from.has(key, PersistentDataType.STRING)) {
			to.set(key, PersistentDataType.STRING, from.get(key, PersistentDataType.STRING));
		} else if (from.has(key, PersistentDataType.INTEGER)) {
			to.set(key, PersistentDataType.INTEGER, from.get(key, PersistentDataType.INTEGER));
		} else if (from.has(key, PersistentDataType.LONG)) {
			to.set(key, PersistentDataType.LONG, from.get(key, PersistentDataType.LONG));
		} else if (from.has(key, PersistentDataType.DOUBLE)) {
			to.set(key, PersistentDataType.DOUBLE, from.get(key, PersistentDataType.DOUBLE));
		} else if (from.has(key, PersistentDataType.FLOAT)) {
			to.set(key, PersistentDataType.FLOAT, from.get(key, PersistentDataType.FLOAT));
		} else if (from.has(key, PersistentDataType.BYTE)) {
			to.set(key, PersistentDataType.BYTE, from.get(key, PersistentDataType.BYTE));
		} else if (from.has(key, PersistentDataType.SHORT)) {
			to.set(key, PersistentDataType.SHORT, from.get(key, PersistentDataType.SHORT));
		} else if (from.has(key, PersistentDataType.BOOLEAN)) {
			to.set(key, PersistentDataType.BOOLEAN, from.get(key, PersistentDataType.BOOLEAN));
		} else if (from.has(key, PersistentDataType.BYTE_ARRAY)) {
			to.set(key, PersistentDataType.BYTE_ARRAY, from.get(key, PersistentDataType.BYTE_ARRAY));
		} else if (from.has(key, PersistentDataType.INTEGER_ARRAY)) {
			to.set(key, PersistentDataType.INTEGER_ARRAY, from.get(key, PersistentDataType.INTEGER_ARRAY));
		} else if (from.has(key, PersistentDataType.LONG_ARRAY)) {
			to.set(key, PersistentDataType.LONG_ARRAY, from.get(key, PersistentDataType.LONG_ARRAY));
		} else if (from.has(key, PersistentDataType.TAG_CONTAINER)) {
			PersistentDataContainer nested = from.get(key, PersistentDataType.TAG_CONTAINER);
			if (nested != null) {
				PersistentDataContainer target = to.getAdapterContext().newPersistentDataContainer();
				copy(nested, target);
				to.set(key, PersistentDataType.TAG_CONTAINER, target);
			}
		}
	}
}
