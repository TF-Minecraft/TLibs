package net.tfminecraft.tlibs.armour.equipment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Reads every {@code armors_rendering} entry from the ItemsAdder contents folder. */
public final class ArmourRenderingScanner {
	private ArmourRenderingScanner() {
	}

	public static List<ArmourRendering> scan(Path contents, Logger logger) {
		List<ArmourRendering> found = new ArrayList<>();
		if (!Files.isDirectory(contents)) {
			logger.warning("[Equipment] ItemsAdder contents folder not found: " + contents);
			return found;
		}
		List<Path> files;
		try (Stream<Path> walk = Files.walk(contents)) {
			files = walk.filter(ArmourRenderingScanner::isConfigFile).sorted(Comparator.naturalOrder()).toList();
		} catch (IOException | RuntimeException e) {
			logger.warning("[Equipment] Could not list " + contents + ": " + e.getMessage());
			return found;
		}
		for (Path file : files) {
			readFile(file, found, logger);
		}
		return found;
	}

	private static boolean isConfigFile(Path path) {
		String name = path.getFileName().toString();
		return name.endsWith(".yml") && path.toString().replace('\\', '/').contains("/configs/") && Files.isRegularFile(path);
	}

	private static void readFile(Path file, List<ArmourRendering> found, Logger logger) {
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
		ConfigurationSection rendering = yaml.getConfigurationSection("armors_rendering");
		if (rendering == null) {
			return;
		}
		String namespace = yaml.getString("info.namespace");
		if (namespace == null || namespace.isBlank()) {
			logger.warning("[Equipment] " + file.getFileName() + " has armors_rendering but no info.namespace");
			return;
		}
		for (String name : rendering.getKeys(false)) {
			ConfigurationSection entry = rendering.getConfigurationSection(name);
			Integer rgb = entry == null ? null : parseColour(entry.getString("color"));
			String layer1 = entry == null ? null : entry.getString("layer_1");
			String layer2 = entry == null ? null : entry.getString("layer_2");
			if (rgb == null || layer1 == null || layer2 == null) {
				logger.warning("[Equipment] Skipping " + namespace + ":" + name + " in " + file.getFileName()
						+ " (needs color, layer_1 and layer_2)");
				continue;
			}
			found.add(new ArmourRendering(namespace, name, rgb, layer1, layer2));
		}
	}

	static Integer parseColour(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		try {
			if (trimmed.startsWith("#") && trimmed.length() == 7) {
				return Integer.parseInt(trimmed.substring(1), 16);
			}
			int rgb = Integer.parseInt(trimmed);
			return rgb >= 0 && rgb <= 0xFFFFFF ? rgb : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
