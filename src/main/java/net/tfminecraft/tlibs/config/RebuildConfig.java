package net.tfminecraft.tlibs.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import org.bukkit.configuration.file.FileConfiguration;

import net.tfminecraft.tlibs.TLibs;

public final class RebuildConfig {
	private static final String SECTION = "mmo-item-rebuild";

	private boolean enabled = true;
	private boolean copyPersistentData = true;
	private boolean copyAppearance = true;
	private boolean debugNbt = false;
	private List<String> preserveNbtTags = new ArrayList<>();

	public void reload(FileConfiguration config) {
		enabled = config.getBoolean(SECTION + ".enabled", true);
		copyPersistentData = config.getBoolean(SECTION + ".copy-persistent-data", true);
		copyAppearance = config.getBoolean(SECTION + ".copy-appearance", true);
		debugNbt = config.getBoolean(SECTION + ".debug-nbt", false);

		List<String> raw = config.getStringList(SECTION + ".preserve-nbt");
		List<String> filtered = new ArrayList<>();
		Logger logger = TLibs.getInstance().getLogger();
		for (String tag : raw) {
			if (tag == null || tag.isBlank()) {
				continue;
			}
			String normalized = tag.trim();
			if (isBlockedTag(normalized)) {
				logger.warning("[TLibs] Ignoring blocked preserve-nbt tag: " + normalized);
				continue;
			}
			filtered.add(normalized);
		}
		preserveNbtTags = Collections.unmodifiableList(filtered);
		logger.info("[TLibs] mmo-item-rebuild enabled=" + enabled + " debug-nbt=" + debugNbt
				+ " copy-pdc=" + copyPersistentData);
	}

	public boolean isEnabled() {
		return enabled;
	}

	public boolean copyPersistentData() {
		return copyPersistentData;
	}

	public boolean copyAppearance() {
		return copyAppearance;
	}

	public List<String> getPreserveNbtTags() {
		return preserveNbtTags;
	}

	public boolean debugNbt() {
		return debugNbt;
	}

	public static boolean isBlockedTag(String tag) {
		String upper = tag.toUpperCase(Locale.ROOT);
		return upper.startsWith("MMOITEMS_") || upper.startsWith("MMOITEM_");
	}
}
