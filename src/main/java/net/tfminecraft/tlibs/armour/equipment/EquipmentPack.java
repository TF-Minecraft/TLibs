package net.tfminecraft.tlibs.armour.equipment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

import net.kyori.adventure.key.Key;

/**
 * Resource-pack files that let every custom armour render through a vanilla
 * equipment asset instead of the ItemsAdder leather shader, plus the leather
 * colour each asset replaces.
 */
public record EquipmentPack(Map<String, byte[]> entries, Map<Integer, Key> assets) {
	public static final EquipmentPack EMPTY = new EquipmentPack(Map.of(), Map.of());

	public EquipmentPack {
		entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
		assets = Collections.unmodifiableMap(new HashMap<>(assets));
	}

	public static EquipmentPack build(Path contents, String namespace, List<ArmourRendering> renderings, Logger logger) {
		Set<Integer> duplicates = duplicateColours(renderings);
		Set<String> duplicatePaths = duplicatePaths(renderings);
		Map<String, byte[]> entries = new LinkedHashMap<>();
		Map<Integer, Key> assets = new HashMap<>();
		for (ArmourRendering rendering : renderings) {
			if (duplicates.contains(rendering.rgb())) {
				logger.warning("[Equipment] Skipping " + rendering.namespace() + ":" + rendering.name()
						+ ", another armour uses colour #" + String.format("%06x", rendering.rgb()));
				continue;
			}
			// Names that differ only in characters an asset path can't hold would overwrite each other's files.
			if (duplicatePaths.contains(rendering.assetPath())) {
				logger.warning("[Equipment] Skipping " + rendering.namespace() + ":" + rendering.name()
						+ ", another armour has the same asset path " + rendering.assetPath());
				continue;
			}
			byte[] humanoid = readTexture(contents, rendering, rendering.layer1());
			byte[] leggings = readTexture(contents, rendering, rendering.layer2());
			if (humanoid == null || leggings == null) {
				logger.warning("[Equipment] Skipping " + rendering.namespace() + ":" + rendering.name()
						+ ", layer texture not found");
				continue;
			}
			String path = rendering.assetPath();
			String base = "assets/" + namespace + "/";
			entries.put(base + "equipment/" + path + ".json", model(namespace, path));
			entries.put(base + "textures/entity/equipment/humanoid/" + path + ".png", humanoid);
			entries.put(base + "textures/entity/equipment/humanoid_leggings/" + path + ".png", leggings);
			assets.put(rendering.rgb(), Key.key(namespace, path));
		}
		return new EquipmentPack(entries, assets);
	}

	/** The colours whose model and both textures appear in the given pack listing. */
	public Map<Integer, Key> publishedIn(Set<String> packEntries) {
		Map<Integer, Key> published = new HashMap<>();
		for (Map.Entry<Integer, Key> asset : assets.entrySet()) {
			Key key = asset.getValue();
			String base = "assets/" + key.namespace() + "/";
			if (packEntries.contains(base + "equipment/" + key.value() + ".json")
					&& packEntries.contains(base + "textures/entity/equipment/humanoid/" + key.value() + ".png")
					&& packEntries.contains(base + "textures/entity/equipment/humanoid_leggings/" + key.value() + ".png")) {
				published.put(asset.getKey(), key);
			}
		}
		return published;
	}

	private static byte[] model(String namespace, String path) {
		String texture = namespace + ":" + path;
		String json = "{\"layers\":{\"humanoid\":[{\"texture\":\"" + texture + "\"}],"
				+ "\"humanoid_leggings\":[{\"texture\":\"" + texture + "\"}]}}";
		return json.getBytes(StandardCharsets.UTF_8);
	}

	private static Set<Integer> duplicateColours(List<ArmourRendering> renderings) {
		Set<Integer> seen = new HashSet<>();
		Set<Integer> duplicates = new HashSet<>();
		for (ArmourRendering rendering : renderings) {
			if (!seen.add(rendering.rgb())) {
				duplicates.add(rendering.rgb());
			}
		}
		return duplicates;
	}

	private static Set<String> duplicatePaths(List<ArmourRendering> renderings) {
		Set<String> seen = new HashSet<>();
		Set<String> duplicates = new HashSet<>();
		for (ArmourRendering rendering : renderings) {
			if (!seen.add(rendering.assetPath())) {
				duplicates.add(rendering.assetPath());
			}
		}
		return duplicates;
	}

	private static byte[] readTexture(Path contents, ArmourRendering rendering, String layer) {
		String[] texture = rendering.texture(layer);
		String relative = "resourcepack/assets/" + texture[0] + "/textures/" + texture[1] + ".png";
		try (Stream<Path> packs = Files.list(contents)) {
			for (Path pack : packs.sorted().toList()) {
				Path root = pack.resolve("resourcepack").toAbsolutePath().normalize();
				Path file = pack.resolve(relative).toAbsolutePath().normalize();
				// A layer such as "ns:../../../secret" must not read files outside the pack's resourcepack folder.
				if (!file.startsWith(root)) {
					return null;
				}
				if (Files.isRegularFile(file)) {
					return Files.readAllBytes(file);
				}
			}
		} catch (IOException | RuntimeException e) {
			return null;
		}
		return null;
	}
}
