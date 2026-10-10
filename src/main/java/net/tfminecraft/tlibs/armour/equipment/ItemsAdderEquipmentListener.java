package net.tfminecraft.tlibs.armour.equipment;

import java.util.HashSet;
import java.util.Set;

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

	// Default priority: TFMCCore copies overlay files at HIGHEST, after the atlas is gone.
	@EventHandler
	public void onPack(ItemsAdderPackCompressedEvent event) {
		assets.addTo(event::setEntry);
		assets.removeLegacyAtlas(new EventPack(event));
		// Give clients time to download the new pack before items point at its assets.
		Bukkit.getScheduler().runTaskLater(plugin, items::resyncAll, assets.resyncDelayTicks());
	}

	/** The entries of the pack ItemsAdder is building. */
	record EventPack(ItemsAdderPackCompressedEvent event) implements LegacyArmourAtlas.Pack {
		@Override
		public Set<String> paths() {
			Set<String> paths = new HashSet<>();
			for (Object key : event.getEntries().keySet()) {
				String path = (String) key;
				if (!event.getEntry(path).isRemoved()) {
					paths.add(path);
				}
			}
			return paths;
		}

		@Override
		public byte[] read(String path) {
			return event.getEntry(path).getBytes();
		}

		@Override
		public void remove(String path) {
			event.removeEntry(path);
		}
	}
}
