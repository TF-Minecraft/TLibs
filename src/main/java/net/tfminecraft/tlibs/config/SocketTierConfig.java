package net.tfminecraft.tlibs.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import net.tfminecraft.tlibs.socket.SocketTierRegistry;

public final class SocketTierConfig {
	private static final String ENABLED_SECTION = "tiered-sockets";
	private static final String GROUPS_SECTION = "socket-tier-groups";

	private boolean enabled = true;

	public void reload(FileConfiguration config) {
		enabled = config.getBoolean(ENABLED_SECTION + ".enabled", true);
		SocketTierRegistry.clear();
		ConfigurationSection groups = config.getConfigurationSection(GROUPS_SECTION);
		if (groups == null) {
			return;
		}
		for (String groupId : groups.getKeys(false)) {
			ConfigurationSection colors = groups.getConfigurationSection(groupId);
			if (colors == null) {
				continue;
			}
			for (String color : colors.getKeys(false)) {
				SocketTierRegistry.put(groupId, color, colors.getInt(color));
			}
		}
	}

	public boolean isEnabled() {
		return enabled;
	}
}
