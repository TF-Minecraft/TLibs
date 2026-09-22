package net.tfminecraft.tlibs.mmoitem;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.tfminecraft.tlibs.event.MMOItemRebuildEvent.RebuildReason;

public final class GemApplyPrimer {
	private static final Map<UUID, Entry> primers = new ConcurrentHashMap<>();
	private static Plugin plugin;

	private GemApplyPrimer() {
	}

	public static void init(Plugin host) {
		plugin = host;
	}

	public static void put(UUID playerId, Inventory inventory, int slot, ItemStack oldItemSnapshot, String targetType,
			String targetId, RebuildReason reason) {
		put(playerId, inventory, slot, oldItemSnapshot, targetType, targetId, reason, 40L);
	}

	public static void put(UUID playerId, Inventory inventory, int slot, ItemStack oldItemSnapshot, String targetType,
			String targetId, RebuildReason reason, long expireTicks) {
		primers.put(playerId, new Entry(inventory, slot, oldItemSnapshot, targetType, targetId, reason));
		if (plugin != null) {
			Bukkit.getScheduler().runTaskLater(plugin, () -> primers.remove(playerId), expireTicks);
		}
	}

	public static Entry get(UUID playerId) {
		return primers.get(playerId);
	}

	public static void clear(UUID playerId) {
		primers.remove(playerId);
	}

	public static boolean matches(Entry entry, String targetType, String targetId) {
		if (entry == null) {
			return false;
		}
		return entry.targetType.equalsIgnoreCase(targetType) && entry.targetId.equalsIgnoreCase(targetId);
	}

	public static final class Entry {
		private final Inventory inventory;
		private final int slot;
		private final ItemStack oldItemSnapshot;
		private final String targetType;
		private final String targetId;
		private final RebuildReason reason;

		private Entry(Inventory inventory, int slot, ItemStack oldItemSnapshot, String targetType, String targetId,
				RebuildReason reason) {
			this.inventory = inventory;
			this.slot = slot;
			this.oldItemSnapshot = oldItemSnapshot;
			this.targetType = targetType;
			this.targetId = targetId;
			this.reason = reason;
		}

		public Inventory getInventory() {
			return inventory;
		}

		public int getSlot() {
			return slot;
		}

		public ItemStack getOldItemSnapshot() {
			return oldItemSnapshot;
		}

		public String getTargetType() {
			return targetType;
		}

		public String getTargetId() {
			return targetId;
		}

		public RebuildReason getReason() {
			return reason;
		}
	}
}
