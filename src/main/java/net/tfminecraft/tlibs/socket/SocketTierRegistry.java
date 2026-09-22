package net.tfminecraft.tlibs.socket;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.Indyuce.mmoitems.stat.data.GemSocketsData;

public final class SocketTierRegistry {
	private static final Map<String, ColorEntry> COLORS = new HashMap<>();

	private SocketTierRegistry() {
	}

	public static void clear() {
		COLORS.clear();
	}

	public static void put(String groupId, String color, int tier) {
		if (groupId == null || groupId.isBlank() || color == null || color.isBlank()) {
			return;
		}
		COLORS.put(color, new ColorEntry(groupId, tier));
	}

	public static String getGroup(String color) {
		ColorEntry entry = resolve(color);
		return entry == null ? null : entry.groupId;
	}

	public static int getTier(String color) {
		ColorEntry entry = resolve(color);
		return entry == null ? Integer.MAX_VALUE : entry.tier;
	}

	public static boolean sameGroup(String itemColor, String socketColor) {
		String itemGroup = getGroup(itemColor);
		String socketGroup = getGroup(socketColor);
		return itemGroup != null && itemGroup.equals(socketGroup);
	}

	public static boolean canFit(String itemColor, String socketColor) {
		if (itemColor == null || socketColor == null) {
			return false;
		}
		if (!sameGroup(itemColor, socketColor)) {
			return false;
		}
		return getTier(itemColor) <= getTier(socketColor);
	}

	public static String pickLowestCompatible(GemSocketsData sockets, String itemColor) {
		if (sockets == null || itemColor == null) {
			return null;
		}
		List<String> candidates = sockets.getEmptySlots().stream()
				.filter(slot -> canFit(itemColor, slot))
				.sorted(Comparator.comparingInt(SocketTierRegistry::getTier))
				.collect(Collectors.toList());
		if (candidates.isEmpty()) {
			return null;
		}
		return candidates.get(0);
	}

	private static ColorEntry resolve(String color) {
		if (color == null || color.isBlank()) {
			return null;
		}
		ColorEntry entry = COLORS.get(color);
		if (entry != null) {
			return entry;
		}
		if (color.equals(GemSocketsData.getUncoloredGemSlot())) {
			return COLORS.get("Uncolored");
		}
		return null;
	}

	private static final class ColorEntry {
		private final String groupId;
		private final int tier;

		private ColorEntry(String groupId, int tier) {
			this.groupId = groupId;
			this.tier = tier;
		}
	}
}
