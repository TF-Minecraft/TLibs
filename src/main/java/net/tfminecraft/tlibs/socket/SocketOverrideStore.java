package net.tfminecraft.tlibs.socket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.lumine.mythic.lib.gson.JsonObject;
import io.lumine.mythic.lib.gson.JsonParser;
import net.tfminecraft.tlibs.TLibs;

public final class SocketOverrideStore {
	private static final NamespacedKey LEGACY_KEY = new NamespacedKey("geminfusion", "socket_overrides");

	private SocketOverrideStore() {
	}

	private static NamespacedKey key() {
		return new NamespacedKey(TLibs.getInstance(), "socket_overrides");
	}

	public static Map<String, String> read(ItemStack item) {
		if (item == null || !item.hasItemMeta()) {
			return new HashMap<>();
		}
		ItemMeta meta = item.getItemMeta();
		Map<String, String> map = parseJson(meta.getPersistentDataContainer().get(key(), PersistentDataType.STRING));
		Map<String, String> legacy = parseJson(meta.getPersistentDataContainer().get(LEGACY_KEY, PersistentDataType.STRING));
		map.putAll(legacy);
		return map;
	}

	public static void write(ItemStack item, Map<String, String> overrides) {
		if (item == null) {
			return;
		}
		ItemMeta meta = item.getItemMeta();
		if (meta == null) {
			return;
		}
		if (overrides == null || overrides.isEmpty()) {
			meta.getPersistentDataContainer().remove(key());
		} else {
			JsonObject json = new JsonObject();
			for (Map.Entry<String, String> entry : overrides.entrySet()) {
				json.addProperty(entry.getKey(), entry.getValue());
			}
			meta.getPersistentDataContainer().set(key(), PersistentDataType.STRING, json.toString());
		}
		item.setItemMeta(meta);
	}

	public static void mergeOnto(ItemStack target, ItemStack source) {
		if (target == null || source == null) {
			return;
		}
		Map<String, String> merged = read(target);
		merged.putAll(read(source));
		write(target, merged);
	}

	public static void put(ItemStack item, UUID historicId, String socketColor) {
		if (item == null || historicId == null || socketColor == null) {
			return;
		}
		Map<String, String> overrides = read(item);
		overrides.put(historicId.toString(), socketColor);
		write(item, overrides);
	}

	public static void remove(ItemStack item, UUID historicId) {
		if (item == null || historicId == null) {
			return;
		}
		Map<String, String> overrides = read(item);
		if (overrides.remove(historicId.toString()) != null) {
			write(item, overrides);
		}
	}

	public static String get(ItemStack item, UUID historicId) {
		if (item == null || historicId == null) {
			return null;
		}
		return read(item).get(historicId.toString());
	}

	private static Map<String, String> parseJson(String json) {
		Map<String, String> map = new HashMap<>();
		if (json == null || json.isBlank()) {
			return map;
		}
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		for (String key : parsed.keySet()) {
			map.put(key, parsed.get(key).getAsString());
		}
		return map;
	}
}
