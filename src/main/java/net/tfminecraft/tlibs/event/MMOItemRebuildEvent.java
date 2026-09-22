package net.tfminecraft.tlibs.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;

public class MMOItemRebuildEvent extends PlayerEvent {
	private static final HandlerList handlers = new HandlerList();

	public enum RebuildReason {
		GEM_APPLY,
		GEM_UNSOCKET
	}

	private final ItemStack oldItem;
	private final PersistentDataContainer pdcSnapshot;
	private final RebuildReason reason;
	private ItemStack newItem;
	private ItemStack appliedCursorSnapshot;
	private String appliedSocketColor;
	private boolean spoofed;

	public MMOItemRebuildEvent(Player player, ItemStack oldItem, ItemStack newItem, RebuildReason reason) {
		this(player, oldItem, newItem, reason, null);
	}

	public MMOItemRebuildEvent(Player player, ItemStack oldItem, ItemStack newItem, RebuildReason reason,
			PersistentDataContainer pdcSnapshot) {
		super(player);
		this.oldItem = oldItem;
		this.newItem = newItem;
		this.reason = reason;
		this.pdcSnapshot = pdcSnapshot;
	}

	public ItemStack getAppliedCursorSnapshot() {
		return appliedCursorSnapshot;
	}

	public void setAppliedCursorSnapshot(ItemStack appliedCursorSnapshot) {
		this.appliedCursorSnapshot = appliedCursorSnapshot;
	}

	public String getAppliedSocketColor() {
		return appliedSocketColor;
	}

	public void setAppliedSocketColor(String appliedSocketColor) {
		this.appliedSocketColor = appliedSocketColor;
	}

	public boolean wasSpoofed() {
		return spoofed;
	}

	public void setSpoofed(boolean spoofed) {
		this.spoofed = spoofed;
	}

	public ItemStack getOldItem() {
		return oldItem;
	}

	public PersistentDataContainer getPdcSnapshot() {
		return pdcSnapshot;
	}

	public ItemStack getNewItem() {
		return newItem;
	}

	public void setNewItem(ItemStack newItem) {
		this.newItem = newItem;
	}

	public RebuildReason getReason() {
		return reason;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
