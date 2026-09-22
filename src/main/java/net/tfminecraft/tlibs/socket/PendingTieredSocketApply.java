package net.tfminecraft.tlibs.socket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class PendingTieredSocketApply {
	private static final Map<UUID, Entry> PENDING = new ConcurrentHashMap<>();

	private PendingTieredSocketApply() {
	}

	public static void stash(Player player, ItemStack cursorSnapshot, String appliedSocketColor, boolean spoofed) {
		if (player == null || cursorSnapshot == null) {
			return;
		}
		PENDING.put(player.getUniqueId(), new Entry(cursorSnapshot.clone(), appliedSocketColor, spoofed));
	}

	public static Entry poll(Player player) {
		if (player == null) {
			return null;
		}
		return PENDING.remove(player.getUniqueId());
	}

	public static void clear(Player player) {
		if (player == null) {
			return;
		}
		PENDING.remove(player.getUniqueId());
	}

	public static final class Entry {
		private final ItemStack cursorSnapshot;
		private final String appliedSocketColor;
		private final boolean spoofed;

		private Entry(ItemStack cursorSnapshot, String appliedSocketColor, boolean spoofed) {
			this.cursorSnapshot = cursorSnapshot;
			this.appliedSocketColor = appliedSocketColor;
			this.spoofed = spoofed;
		}

		public ItemStack getCursorSnapshot() {
			return cursorSnapshot;
		}

		public String getAppliedSocketColor() {
			return appliedSocketColor;
		}

		public boolean wasSpoofed() {
			return spoofed;
		}
	}
}
