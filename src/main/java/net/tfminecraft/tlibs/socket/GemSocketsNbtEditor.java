package net.tfminecraft.tlibs.socket;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.stat.data.GemSocketsData;
import net.Indyuce.mmoitems.stat.data.GemstoneData;

public final class GemSocketsNbtEditor {
	private GemSocketsNbtEditor() {
	}

	public static GemSocketsData getSockets(ItemStack item) {
		if (item == null || item.getType().isAir()) {
			return null;
		}
		LiveMMOItem mmo = new LiveMMOItem(NBTItem.get(item));
		if (!mmo.hasData(ItemStats.GEM_SOCKETS)) {
			return null;
		}
		return (GemSocketsData) mmo.getData(ItemStats.GEM_SOCKETS);
	}

	public static Set<UUID> getGemstoneUuids(ItemStack item) {
		Set<UUID> uuids = new HashSet<>();
		GemSocketsData sockets = getSockets(item);
		if (sockets == null) {
			return uuids;
		}
		for (GemstoneData gem : sockets.getGems()) {
			uuids.add(gem.getHistoricUUID());
		}
		return uuids;
	}

	public static List<String> getEmptySlots(ItemStack item) {
		GemSocketsData sockets = getSockets(item);
		if (sockets == null) {
			return List.of();
		}
		return new ArrayList<>(sockets.getEmptySlots());
	}

	public static ItemStack replaceEmptySlotColor(ItemStack item, String fromColor, String toColor) {
		if (item == null || fromColor == null || toColor == null || fromColor.equals(toColor)) {
			return item;
		}
		LiveMMOItem mmo = new LiveMMOItem(NBTItem.get(item));
		if (!mmo.hasData(ItemStats.GEM_SOCKETS)) {
			return item;
		}
		GemSocketsData sockets = (GemSocketsData) mmo.getData(ItemStats.GEM_SOCKETS);
		List<String> slots = sockets.getEmptySlots();
		for (int i = 0; i < slots.size(); i++) {
			if (slots.get(i).equals(fromColor)) {
				slots.set(i, toColor);
				mmo.setData(ItemStats.GEM_SOCKETS, sockets);
				return mmo.newBuilder().build();
			}
		}
		return item;
	}

	public static List<String> addedEmptySlots(List<String> before, List<String> after) {
		List<String> remaining = new ArrayList<>(after);
		for (String slot : before) {
			remaining.remove(slot);
		}
		return remaining;
	}
}
