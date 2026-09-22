package net.tfminecraft.tlibs.socket;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.event.MMOItemRebuildEvent;
import net.tfminecraft.tlibs.event.MMOItemRebuildEvent.RebuildReason;

public class TieredSocketRebuildListener implements Listener {

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onRebuild(MMOItemRebuildEvent event) {
		ItemStack oldItem = event.getOldItem();
		ItemStack newItem = event.getNewItem();
		if (oldItem == null || newItem == null) {
			return;
		}

		SocketOverrideStore.mergeOnto(newItem, oldItem);

		if (event.getReason() == RebuildReason.GEM_APPLY) {
			handleGemApply(event, oldItem, newItem);
		} else if (event.getReason() == RebuildReason.GEM_UNSOCKET) {
			handleGemUnsocket(oldItem, newItem, event);
		}
	}

	private void handleGemApply(MMOItemRebuildEvent event, ItemStack oldItem, ItemStack newItem) {
		if (!event.wasSpoofed() || event.getAppliedSocketColor() == null) {
			event.setNewItem(newItem);
			return;
		}

		UUID newGemUuid = findNewGemUuid(oldItem, newItem);
		if (newGemUuid != null) {
			Map<String, String> overrides = new HashMap<>(SocketOverrideStore.read(newItem));
			overrides.put(newGemUuid.toString(), event.getAppliedSocketColor());
			SocketOverrideStore.write(newItem, overrides);
		}

		event.setNewItem(newItem);
	}

	private void handleGemUnsocket(ItemStack oldItem, ItemStack newItem, MMOItemRebuildEvent event) {
		Set<UUID> oldGems = GemSocketsNbtEditor.getGemstoneUuids(oldItem);
		Set<UUID> newGems = GemSocketsNbtEditor.getGemstoneUuids(newItem);
		Set<UUID> removed = new HashSet<>(oldGems);
		removed.removeAll(newGems);

		if (removed.isEmpty()) {
			event.setNewItem(newItem);
			return;
		}

		List<String> oldEmpty = GemSocketsNbtEditor.getEmptySlots(oldItem);
		List<String> newEmpty = GemSocketsNbtEditor.getEmptySlots(newItem);
		List<String> addedSlots = GemSocketsNbtEditor.addedEmptySlots(oldEmpty, newEmpty);

		Map<String, String> overrides = new HashMap<>(SocketOverrideStore.read(newItem));
		ItemStack fixed = newItem;

		int addedIndex = 0;
		for (UUID removedUuid : removed) {
			String socketColor = overrides.remove(removedUuid.toString());
			if (socketColor == null) {
				continue;
			}
			if (addedIndex >= addedSlots.size()) {
				continue;
			}
			String wrongColor = addedSlots.get(addedIndex++);
			if (!socketColor.equals(wrongColor)) {
				fixed = GemSocketsNbtEditor.replaceEmptySlotColor(fixed, wrongColor, socketColor);
			}
		}

		SocketOverrideStore.write(fixed, overrides);
		event.setNewItem(fixed);
	}

	private static UUID findNewGemUuid(ItemStack oldItem, ItemStack newItem) {
		Set<UUID> oldGems = GemSocketsNbtEditor.getGemstoneUuids(oldItem);
		for (UUID uuid : GemSocketsNbtEditor.getGemstoneUuids(newItem)) {
			if (!oldGems.contains(uuid)) {
				return uuid;
			}
		}
		return null;
	}
}
