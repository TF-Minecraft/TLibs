package net.tfminecraft.tlibs.armour.equipment;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;

/** Keeps worn custom armour on its equipment asset. Nothing changes until the asset is in the served pack. */
public final class EquipmentAssetListener implements Listener {
	private static final List<EquipmentSlot> ARMOUR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
			EquipmentSlot.LEGS, EquipmentSlot.FEET);

	private final Plugin plugin;
	private final EquipmentAssets assets;

	public EquipmentAssetListener(Plugin plugin, EquipmentAssets assets) {
		this.plugin = plugin;
		this.assets = assets;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		Bukkit.getScheduler().runTask(plugin, () -> syncInventory(player));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onArmourChange(PlayerArmorChangeEvent event) {
		Player player = event.getPlayer();
		Bukkit.getScheduler().runTask(plugin, () -> syncWorn(player));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onArmourStand(PlayerArmorStandManipulateEvent event) {
		LivingEntity stand = event.getRightClicked();
		Bukkit.getScheduler().runTask(plugin, () -> syncWorn(stand));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onEntitiesLoad(EntitiesLoadEvent event) {
		EquipmentAssetSync sync = assets.sync();
		for (Entity entity : event.getEntities()) {
			if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
				syncWorn(living, sync);
			}
		}
	}

	/** Re-checks every online player's inventory and every loaded mob or armour stand. */
	public void resyncAll() {
		EquipmentAssetSync sync = assets.sync();
		for (Player player : Bukkit.getOnlinePlayers()) {
			syncInventory(player, sync);
		}
		for (World world : Bukkit.getWorlds()) {
			for (LivingEntity living : world.getLivingEntities()) {
				if (!(living instanceof Player)) {
					syncWorn(living, sync);
				}
			}
		}
	}

	void syncInventory(Player player) {
		if (player.isOnline()) {
			syncInventory(player, assets.sync());
		}
	}

	void syncWorn(LivingEntity entity) {
		if (entity.isValid()) {
			syncWorn(entity, assets.sync());
		}
	}

	private static void syncInventory(Player player, EquipmentAssetSync sync) {
		PlayerInventory inventory = player.getInventory();
		ItemStack[] contents = inventory.getContents();
		for (int slot = 0; slot < contents.length; slot++) {
			ItemStack copy = copy(contents[slot]);
			if (copy != null && sync.sync(copy)) {
				inventory.setItem(slot, copy);
			}
		}
	}

	private static void syncWorn(LivingEntity entity, EquipmentAssetSync sync) {
		EntityEquipment equipment = entity.getEquipment();
		if (equipment == null) {
			return;
		}
		for (EquipmentSlot slot : ARMOUR) {
			ItemStack copy = copy(equipment.getItem(slot));
			if (copy != null && sync.sync(copy)) {
				equipment.setItem(slot, copy);
			}
		}
	}

	private static ItemStack copy(ItemStack item) {
		return item == null || item.getType().isAir() ? null : item.clone();
	}
}
