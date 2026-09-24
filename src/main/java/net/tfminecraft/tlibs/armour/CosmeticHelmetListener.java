package net.tfminecraft.tlibs.armour;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.tfminecraft.tlibs.armour.MmoItemTagPreserver.SavedTag;

/**
 * Skinned helmets stay placeable carved pumpkins until this corrects them.
 * MMOItems also spends their custom durability on hits that never reach a
 * normal helmet; that extra loss is put back so only vanilla armor damage remains.
 */
public final class CosmeticHelmetListener implements Listener {
	private final Map<UUID, SavedPiece[]> pendingArmor = new ConcurrentHashMap<>();

	private record SavedPiece(ItemStack item) {
	}

	public static void repairOnlinePlayers() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			repairAll(player);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onJoin(PlayerJoinEvent event) {
		repairAll(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onClick(InventoryClickEvent event) {
		if (event.getCurrentItem() != null) {
			CosmeticHelmetFix.repairIfNeeded(event.getCurrentItem());
		}
		if (event.getCursor() != null) {
			CosmeticHelmetFix.repairIfNeeded(event.getCursor());
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPlace(BlockPlaceEvent event) {
		if (CosmeticHelmetFix.isProtectedHelmet(event.getItemInHand())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onDamageLowest(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player player)) {
			return;
		}
		ItemStack[] armor = player.getInventory().getArmorContents();
		SavedPiece[] saved = new SavedPiece[armor.length];
		boolean any = false;
		for (int slot = 0; slot < armor.length; slot++) {
			ItemStack piece = armor[slot];
			if (piece == null || piece.getType().isAir()) {
				continue;
			}
			CosmeticHelmetFix.repairIfNeeded(piece);
			if (!CosmeticHelmetFix.shouldRevertUndamageableDrain(piece)) {
				continue;
			}
			saved[slot] = new SavedPiece(piece.clone());
			any = true;
		}
		if (any) {
			pendingArmor.put(player.getUniqueId(), saved);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
	public void onDamageMonitor(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player player)) {
			return;
		}
		SavedPiece[] saved = pendingArmor.remove(player.getUniqueId());
		if (saved == null || event.isCancelled()) {
			return;
		}
		ItemStack[] current = player.getInventory().getArmorContents();
		boolean replaced = false;
		for (int slot = 0; slot < saved.length && slot < current.length; slot++) {
			SavedPiece piece = saved[slot];
			if (piece == null) {
				continue;
			}
			ItemStack live = current[slot];
			if (live == null || live.getType().isAir()) {
				current[slot] = piece.item().clone();
				replaced = true;
				continue;
			}
			List<SavedTag> tags = MmoItemTagPreserver.snapshot(piece.item());
			ItemMeta savedMeta = piece.item().getItemMeta();
			if (savedMeta != null) {
				live.setItemMeta(savedMeta);
			}
			MmoItemTagPreserver.restoreMissing(live, tags);
		}
		if (replaced) {
			player.getInventory().setArmorContents(current);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
	public void onItemDamage(PlayerItemDamageEvent event) {
		Player player = event.getPlayer();
		for (ItemStack piece : player.getInventory().getArmorContents()) {
			CosmeticHelmetFix.syncVanillaDamage(piece);
		}
	}

	private static void repairAll(Player player) {
		for (ItemStack piece : player.getInventory().getArmorContents()) {
			CosmeticHelmetFix.repairIfNeeded(piece);
		}
		for (ItemStack piece : player.getInventory().getContents()) {
			CosmeticHelmetFix.repairIfNeeded(piece);
		}
	}
}
