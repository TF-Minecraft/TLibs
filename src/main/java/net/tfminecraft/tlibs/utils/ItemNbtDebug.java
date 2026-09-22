package net.tfminecraft.tlibs.utils;

import java.util.stream.Collectors;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.NBTItem;
import de.tr7zw.nbtapi.iface.ReadableItemNBT;

public final class ItemNbtDebug {
	private ItemNbtDebug() {
	}

	public static String toSnbt(ItemStack item) {
		if (item == null || item.getType().isAir()) {
			return "{id:\"minecraft:air\",Count:0b}";
		}
		try {
			NBTItem nbtItem = new NBTItem(item.clone());
			if (nbtItem.hasNBTData()) {
				return nbtItem.getCompound().toString();
			}
			final String[] fallback = { null };
			NBT.get(item, (ReadableItemNBT nbt) -> fallback[0] = nbt.toString());
			if (fallback[0] != null) {
				return fallback[0];
			}
		} catch (Exception ex) {
			return "{error:\"" + ex.getMessage() + "\"}";
		}
		return "{}";
	}

	public static String pdcSummary(ItemStack item) {
		if (item == null || !item.hasItemMeta()) {
			return "pdc=<none>";
		}
		ItemMeta meta = item.getItemMeta();
		if (meta == null) {
			return "pdc=<none>";
		}
		PersistentDataContainer pdc = meta.getPersistentDataContainer();
		if (pdc.getKeys().isEmpty()) {
			return "pdc=<empty>";
		}
		return "pdc=" + pdc.getKeys().stream().map(key -> key.toString()).collect(Collectors.joining(", "));
	}

	public static String pdcSummary(PersistentDataContainer pdc) {
		if (pdc == null || pdc.getKeys().isEmpty()) {
			return "pdcSnapshot=<empty>";
		}
		return "pdcSnapshot=" + pdc.getKeys().stream().map(key -> key.toString()).collect(Collectors.joining(", "));
	}
}
