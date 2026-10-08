package net.tfminecraft.tlibs.armour.equipment;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import dev.lone.itemsadder.api.Events.ItemsAdderPackCompressedEvent;

/** Follows ItemsAdder reloads and adds the equipment assets to every pack it builds. */
public final class ItemsAdderEquipmentListener implements Listener {
	private final Plugin plugin;
	private final EquipmentAssets assets;
	private final EquipmentAssetListener items;

	public ItemsAdderEquipmentListener(Plugin plugin, EquipmentAssets assets, EquipmentAssetListener items) {
		this.plugin = plugin;
		this.assets = assets;
		this.items = items;
	}

	@EventHandler
	public void onLoad(ItemsAdderLoadDataEvent event) {
		assets.refreshSources();
	}

	@EventHandler
	public void onPack(ItemsAdderPackCompressedEvent event) {
		assets.addTo(event::setEntry);
		// Give clients time to download the new pack before items point at its assets.
		Bukkit.getScheduler().runTaskLater(plugin, items::resyncAll, assets.resyncDelayTicks());
	}
}
