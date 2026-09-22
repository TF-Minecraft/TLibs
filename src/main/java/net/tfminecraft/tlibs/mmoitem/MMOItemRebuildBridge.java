package net.tfminecraft.tlibs.mmoitem;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.tfminecraft.tlibs.event.MMOItemRebuildEvent;
import net.tfminecraft.tlibs.event.MMOItemRebuildEvent.RebuildReason;
import net.tfminecraft.tlibs.socket.PendingTieredSocketApply;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.utils.ItemNbtDebug;
import net.tfminecraft.tlibs.utils.PersistentDataCopier;
import net.tfminecraft.tlibs.utils.RebuildDebug;
import net.Indyuce.mmoitems.api.event.item.ApplyGemStoneEvent;
import net.Indyuce.mmoitems.api.event.item.UnsocketGemStoneEvent;
import net.Indyuce.mmoitems.api.interaction.GemStone.ResultType;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;

public class MMOItemRebuildBridge implements Listener {
	private static final Map<UUID, PendingRebuild> pending = new ConcurrentHashMap<>();

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onInventoryClick(InventoryClickEvent event) {
		RebuildDebug.log("InventoryClickEvent fired clickType=" + event.getClick() + " slot=" + event.getSlot());
		if (!(event.getWhoClicked() instanceof Player player)) {
			RebuildDebug.log("InventoryClick skip: not a player");
			return;
		}
		Inventory clicked = event.getClickedInventory();
		if (clicked == null) {
			RebuildDebug.log("InventoryClick skip: clicked inventory null");
			return;
		}
		ItemStack clickedItem = event.getCurrentItem();
		if (clickedItem == null || clickedItem.getType().isAir()) {
			RebuildDebug.log("InventoryClick skip: clicked item empty");
			return;
		}

		NBTItem targetNbt = NBTItem.get(clickedItem);
		if (!targetNbt.hasType()) {
			RebuildDebug.log("InventoryClick skip: clicked item is not an MMOItem");
			return;
		}

		String targetType = targetNbt.getType();
		String targetId = targetNbt.getString("MMOITEMS_ITEM_ID");
		ItemStack oldSnapshot = clickedItem.clone();
		PersistentDataContainer pdcSnapshot = PersistentDataCopier.snapshot(oldSnapshot);
		pending.put(player.getUniqueId(), new PendingRebuild(oldSnapshot, pdcSnapshot, clicked, event.getSlot(),
				targetType, targetId));
		RebuildDebug.log("InventoryClick primed player=" + player.getName() + " target=" + targetType + "." + targetId
				+ " inv=" + clicked.getType() + " slot=" + event.getSlot() + " "
				+ ItemNbtDebug.pdcSummary(oldSnapshot) + " " + ItemNbtDebug.pdcSummary(pdcSnapshot));
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onApplyGemLowest(ApplyGemStoneEvent event) {
		Player player = event.getPlayer();
		MMOItem target = event.getTargetItem();
		RebuildDebug.log("ApplyGemStoneEvent LOWEST player=" + player.getName() + " target="
				+ target.getType().getId() + "." + target.getId());
		ensurePending(player, target, RebuildReason.GEM_APPLY);
		confirmPending(player, target, RebuildReason.GEM_APPLY);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onApplyGemMonitor(ApplyGemStoneEvent event) {
		RebuildDebug.log("ApplyGemStoneEvent MONITOR cancelled=" + event.isCancelled() + " result=" + event.getResult());
		if (event.isCancelled() || event.getResult() != ResultType.SUCCESS) {
			RebuildDebug.log("ApplyGemStoneEvent MONITOR clearing pending (failed/cancelled)");
			PendingTieredSocketApply.clear(event.getPlayer());
			clearPlayer(event.getPlayer().getUniqueId());
			return;
		}
		RebuildDebug.log("ApplyGemStoneEvent MONITOR scheduling finish for " + event.getPlayer().getName());
		scheduleFinish(event.getPlayer().getUniqueId());
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onUnsocketLowest(UnsocketGemStoneEvent event) {
		Player player = event.getPlayer();
		MMOItem target = event.getTargetItem();
		RebuildDebug.log("UnsocketGemStoneEvent LOWEST player=" + player.getName() + " target="
				+ target.getType().getId() + "." + target.getId());
		ensurePending(player, target, RebuildReason.GEM_UNSOCKET);
		confirmPending(player, target, RebuildReason.GEM_UNSOCKET);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onUnsocketMonitor(UnsocketGemStoneEvent event) {
		RebuildDebug.log("UnsocketGemStoneEvent MONITOR cancelled=" + event.isCancelled());
		if (event.isCancelled()) {
			RebuildDebug.log("UnsocketGemStoneEvent MONITOR clearing pending (cancelled)");
			clearPlayer(event.getPlayer().getUniqueId());
			return;
		}
		RebuildDebug.log("UnsocketGemStoneEvent MONITOR scheduling finish for " + event.getPlayer().getName());
		scheduleFinish(event.getPlayer().getUniqueId());
	}

	private void ensurePending(Player player, MMOItem target, RebuildReason reason) {
		String targetType = target.getType().getId();
		String targetId = target.getId();
		PendingRebuild job = pending.get(player.getUniqueId());
		if (job != null && job.targetType.equalsIgnoreCase(targetType) && job.targetId.equalsIgnoreCase(targetId)) {
			return;
		}
		RebuildDebug.log("ensurePending: no matching primer, bootstrapping " + targetType + "." + targetId);
		bootstrapPendingFromInventories(player, target, reason);
	}

	private void bootstrapPendingFromInventories(Player player, MMOItem target, RebuildReason reason) {
		String targetType = target.getType().getId();
		String targetId = target.getId();
		Inventory top = player.getOpenInventory().getTopInventory();
		Inventory bottom = player.getOpenInventory().getBottomInventory();
		if (tryBootstrapFromInventory(player, top, targetType, targetId, reason)) {
			return;
		}
		if (tryBootstrapFromInventory(player, bottom, targetType, targetId, reason)) {
			return;
		}
		tryBootstrapFromInventory(player, player.getInventory(), targetType, targetId, reason);
	}

	private boolean tryBootstrapFromInventory(Player player, Inventory inventory, String targetType, String targetId,
			RebuildReason reason) {
		if (inventory == null) {
			return false;
		}
		for (int slot = 0; slot < inventory.getSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!matchesMmoItem(stack, targetType, targetId)) {
				continue;
			}
			ItemStack oldSnapshot = stack.clone();
			PersistentDataContainer pdcSnapshot = PersistentDataCopier.snapshot(oldSnapshot);
			pending.put(player.getUniqueId(), new PendingRebuild(oldSnapshot, pdcSnapshot, inventory, slot,
					targetType, targetId));
			RebuildDebug.log("bootstrapPending from " + inventory.getType() + " slot=" + slot + " reason=" + reason
					+ " " + ItemNbtDebug.pdcSummary(oldSnapshot));
			return true;
		}
		return false;
	}

	private void confirmPending(Player player, MMOItem target, RebuildReason reason) {
		PendingRebuild job = pending.get(player.getUniqueId());
		if (job == null) {
			RebuildDebug.log("confirmPending skip: no pending job for " + player.getName());
			return;
		}
		String targetType = target.getType().getId();
		String targetId = target.getId();
		if (!job.targetType.equalsIgnoreCase(targetType) || !job.targetId.equalsIgnoreCase(targetId)) {
			RebuildDebug.log("confirmPending skip: id mismatch primed=" + job.targetType + "." + job.targetId
					+ " event=" + targetType + "." + targetId);
			return;
		}
		job.reason = reason;
		job.confirmed = true;
		RebuildDebug.log("confirmPending OK player=" + player.getName() + " target=" + targetType + "." + targetId
				+ " reason=" + reason);
	}

	private static void scheduleFinish(UUID playerId) {
		RebuildDebug.log("scheduleFinish queued for " + playerId);
		Bukkit.getScheduler().runTask(TLibs.getInstance(), () -> finishRebuild(playerId));
	}

	private static void finishRebuild(UUID playerId) {
		RebuildDebug.log("finishRebuild start playerId=" + playerId);
		PendingRebuild job = pending.remove(playerId);
		if (job == null) {
			RebuildDebug.log("finishRebuild abort: pending job was null");
			return;
		}
		if (!job.confirmed) {
			RebuildDebug.log("finishRebuild abort: job was never confirmed");
			return;
		}
		Player player = Bukkit.getPlayer(playerId);
		if (player == null || !player.isOnline()) {
			RebuildDebug.log("finishRebuild abort: player offline");
			return;
		}
		ResolvedItem resolved = resolveNewItem(player, job);
		if (resolved == null) {
			RebuildDebug.log("finishRebuild abort: could not find post-rebuild item for " + job.targetType + "."
					+ job.targetId);
			return;
		}
		ItemStack newClone = resolved.item.clone();
		logRebuildNbt(player, job, newClone);
		RebuildDebug.log("finishRebuild firing MMOItemRebuildEvent (resolved inv=" + resolved.inventory.getType()
				+ " slot=" + resolved.slot + ")");
		MMOItemRebuildEvent rebuildEvent = new MMOItemRebuildEvent(player, job.oldItem, newClone, job.reason,
				job.pdcSnapshot);
		attachApplyContext(player, rebuildEvent);
		Bukkit.getPluginManager().callEvent(rebuildEvent);
		RebuildDebug.log("finishRebuild event done " + ItemNbtDebug.pdcSummary(rebuildEvent.getNewItem()));
		RebuildDebug.log("mergedNbt=" + ItemNbtDebug.toSnbt(rebuildEvent.getNewItem()));
		resolved.inventory.setItem(resolved.slot, rebuildEvent.getNewItem());
		RebuildDebug.log("finishRebuild wrote inv=" + resolved.inventory.getType() + " slot=" + resolved.slot
				+ " for " + player.getName());
	}

	private static ResolvedItem resolveNewItem(Player player, PendingRebuild job) {
		ItemStack inOriginal = job.inventory.getItem(job.slot);
		if (matchesMmoItem(inOriginal, job.targetType, job.targetId)) {
			return new ResolvedItem(job.inventory, job.slot, inOriginal);
		}
		Inventory top = player.getOpenInventory().getTopInventory();
		ResolvedItem fromTop = findInInventory(top, job);
		if (fromTop != null) {
			return fromTop;
		}
		ResolvedItem fromPlayer = findInInventory(player.getInventory(), job);
		if (fromPlayer != null) {
			return fromPlayer;
		}
		if (inOriginal != null && !inOriginal.getType().isAir()) {
			return new ResolvedItem(job.inventory, job.slot, inOriginal);
		}
		return null;
	}

	private static ResolvedItem findInInventory(Inventory inventory, PendingRebuild job) {
		if (inventory == null) {
			return null;
		}
		for (int slot = 0; slot < inventory.getSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (matchesMmoItem(stack, job.targetType, job.targetId)) {
				return new ResolvedItem(inventory, slot, stack);
			}
		}
		return null;
	}

	private static boolean matchesMmoItem(ItemStack item, String targetType, String targetId) {
		if (item == null || item.getType().isAir()) {
			return false;
		}
		NBTItem nbt = NBTItem.get(item);
		if (!nbt.hasType()) {
			return false;
		}
		return nbt.getType().equalsIgnoreCase(targetType)
				&& nbt.getString("MMOITEMS_ITEM_ID").equalsIgnoreCase(targetId);
	}

	private static void logRebuildNbt(Player player, PendingRebuild job, ItemStack newItem) {
		RebuildDebug.log("player=" + player.getName() + " reason=" + job.reason + " slot=" + job.slot);
		RebuildDebug.log("oldItem " + ItemNbtDebug.pdcSummary(job.oldItem) + " "
				+ ItemNbtDebug.pdcSummary(job.pdcSnapshot));
		RebuildDebug.log("oldNbt=" + ItemNbtDebug.toSnbt(job.oldItem));
		RebuildDebug.log("newItem " + ItemNbtDebug.pdcSummary(newItem));
		RebuildDebug.log("newNbt=" + ItemNbtDebug.toSnbt(newItem));
	}

	private void clearPlayer(UUID playerId) {
		RebuildDebug.log("clearPlayer " + playerId);
		pending.remove(playerId);
	}

	public static void confirmAndScheduleGemApply(Player player, String targetType, String targetId) {
		PendingRebuild job = pending.get(player.getUniqueId());
		if (job == null) {
			RebuildDebug.log("confirmAndScheduleGemApply skip: no pending job for " + player.getName());
			return;
		}
		if (!job.targetType.equalsIgnoreCase(targetType) || !job.targetId.equalsIgnoreCase(targetId)) {
			RebuildDebug.log("confirmAndScheduleGemApply skip: id mismatch primed=" + job.targetType + "."
					+ job.targetId + " event=" + targetType + "." + targetId);
			return;
		}
		job.reason = RebuildReason.GEM_APPLY;
		job.confirmed = true;
		RebuildDebug.log("confirmAndScheduleGemApply OK player=" + player.getName());
		scheduleFinish(player.getUniqueId());
	}

	private static void attachApplyContext(Player player, MMOItemRebuildEvent rebuildEvent) {
		if (rebuildEvent.getReason() != RebuildReason.GEM_APPLY) {
			return;
		}
		PendingTieredSocketApply.Entry entry = PendingTieredSocketApply.poll(player);
		if (entry == null) {
			return;
		}
		rebuildEvent.setAppliedCursorSnapshot(entry.getCursorSnapshot());
		rebuildEvent.setAppliedSocketColor(entry.getAppliedSocketColor());
		rebuildEvent.setSpoofed(entry.wasSpoofed());
	}

	private static final class ResolvedItem {
		private final Inventory inventory;
		private final int slot;
		private final ItemStack item;

		private ResolvedItem(Inventory inventory, int slot, ItemStack item) {
			this.inventory = inventory;
			this.slot = slot;
			this.item = item;
		}
	}

	private static final class PendingRebuild {
		private final ItemStack oldItem;
		private final PersistentDataContainer pdcSnapshot;
		private final Inventory inventory;
		private final int slot;
		private final String targetType;
		private final String targetId;
		private RebuildReason reason;
		private boolean confirmed;

		private PendingRebuild(ItemStack oldItem, PersistentDataContainer pdcSnapshot, Inventory inventory, int slot,
				String targetType, String targetId) {
			this.oldItem = oldItem;
			this.pdcSnapshot = pdcSnapshot;
			this.inventory = inventory;
			this.slot = slot;
			this.targetType = targetType;
			this.targetId = targetId;
		}
	}
}
