package net.tfminecraft.tlibs.armour.equipment;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import org.bukkit.configuration.file.FileConfiguration;

import net.kyori.adventure.key.Key;

/**
 * Custom armour as vanilla equipment assets, so it renders with shader packs.
 *
 * ItemsAdder draws custom armour with a core shader keyed on the leather colour,
 * and shader packs replace that shader. TLibs reads the same
 * {@code armors_rendering} entries, adds a matching equipment asset to the
 * ItemsAdder pack when it is built, and gives worn leather of that colour the
 * asset. Items only get an asset that the served pack contains. ItemsAdder item
 * definitions are never touched, so ItemsAdder never rebuilds anyone's items.
 */
public final class EquipmentAssets {
	private static final String SECTION = "equipment-assets";

	private final Logger logger;
	private boolean enabled;
	private String namespace = "tfmc_equipment";
	private Path contents;
	private Path pack;
	private long resyncDelayTicks = 100;
	private volatile EquipmentPack sources = EquipmentPack.EMPTY;
	private volatile Map<Integer, Key> published = Map.of();

	public EquipmentAssets(Logger logger) {
		this.logger = logger;
	}

	public void reload(FileConfiguration config, Path pluginsFolder) {
		enabled = config.getBoolean(SECTION + ".enabled", true);
		String configured = config.getString(SECTION + ".namespace", "tfmc_equipment");
		if (!Key.parseableNamespace(configured)) {
			logger.warning("[Equipment] Invalid namespace '" + configured + "', using tfmc_equipment");
			configured = "tfmc_equipment";
		}
		namespace = configured;
		contents = pluginsFolder.resolve(config.getString(SECTION + ".itemsadder-contents", "ItemsAdder/contents"));
		pack = pluginsFolder.resolve(config.getString(SECTION + ".itemsadder-pack", "ItemsAdder/output/generated.zip"));
		resyncDelayTicks = Math.max(1, config.getLong(SECTION + ".resync-delay-ticks", 100));
		refreshSources();
		refreshPublished();
	}

	/** Re-reads the armour definitions and textures; the served pack only changes on the next build. */
	public void refreshSources() {
		if (!enabled) {
			sources = EquipmentPack.EMPTY;
			return;
		}
		sources = EquipmentPack.build(contents, namespace, ArmourRenderingScanner.scan(contents, logger), logger);
		logger.info("[Equipment] " + sources.assets().size() + " custom armour sets ready for the pack");
	}

	/** Uses the assets the current pack file already contains, e.g. after a restart. */
	public void refreshPublished() {
		if (!enabled) {
			published = Map.of();
			return;
		}
		try {
			published = sources.publishedIn(PackListing.read(pack));
		} catch (IOException e) {
			published = Map.of();
			logger.warning("[Equipment] Could not read " + pack + ": " + e.getMessage());
		}
		logger.info("[Equipment] " + published.size() + " custom armour sets in the served pack");
	}

	/** Adds the asset files to a pack being built; they count as served from now on. */
	public void addTo(BiConsumer<String, byte[]> pack) {
		EquipmentPack snapshot = sources;
		snapshot.entries().forEach(pack);
		published = snapshot.assets();
		logger.info("[Equipment] Added " + published.size() + " custom armour sets to the pack");
	}

	public EquipmentAssetSync sync() {
		return new EquipmentAssetSync(namespace, published);
	}

	public int sourceCount() {
		return sources.assets().size();
	}

	public int publishedCount() {
		return published.size();
	}

	public boolean isEnabled() {
		return enabled;
	}

	public long resyncDelayTicks() {
		return resyncDelayTicks;
	}
}
