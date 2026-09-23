package net.tfminecraft.tlibs.objects.api.subapi;

import net.tfminecraft.tlibs.util.LegacyModelData;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.lone.itemsadder.api.CustomStack;
import io.lumine.mythic.lib.api.item.NBTItem;
import net.tfminecraft.tlibs.objects.TLibAPI;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.cooking.item.FoodItem;

public class ItemChecker extends TLibAPI{
	private final ItemAPI itemApi;

	public ItemChecker(ItemAPI api) {
		this.itemApi = api;
		this.initialize(api.getServer());
	}
	
	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public String getAsStringPath(ItemStack i) {
		String path = "v."+i.getType().toString().toLowerCase();
		if(this.getPluginChecker().checkPlugin("MMOItems") && this.getPluginChecker().checkPlugin("MythicLib")) {
			NBTItem nbt = NBTItem.get(i);
			if(nbt.hasType()) {
				path = "m."+nbt.getType().toLowerCase()+"."+nbt.getString("MMOITEMS_ITEM_ID").toLowerCase();
			}
		}
		if(this.getPluginChecker().checkPlugin("ItemsAdder")) {
			CustomStack stack = CustomStack.byItemStack(i);
			if(stack != null) {
				path = "ia."+stack.getNamespacedID();
			}
		}
		if(this.getPluginChecker().checkPlugin("Cooking")) {
			FoodItem food = FoodItem.fromItem(i);
			if(food != null) {
				String origin = food.getOrigin() == null ? "" : food.getOrigin();
				String category = food.getCategory() == null ? "" : food.getCategory();
				return "c." + category + "(type=" + food.getId() + ";origin=" + origin + ")";
			}
		}
		if(path.split("\\.")[0].equalsIgnoreCase("v") && i.hasItemMeta()) {
			ItemMeta meta = i.getItemMeta();
			boolean hasName = meta.hasDisplayName();
			boolean hasModel = LegacyModelData.has(meta);

			if (hasName || hasModel) {
				StringBuilder sb = new StringBuilder("modeled.(");
				sb.append("type=").append(i.getType().toString().toLowerCase());
				if (hasName) {
					sb.append(";name=").append(meta.getDisplayName());
				}
				if (hasModel) {
					sb.append(";model=").append(LegacyModelData.get(meta));
				}
				sb.append(")");
				return sb.toString();
			}
		}
		return path;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public boolean checkItemWithPath(ItemStack item, String s) {
		if (item == null || item.getType().isAir() || s == null || s.isBlank()) {
			return false;
		}
		String type = s.split("\\.")[0]; //v.emerald
		ItemPathHandler handler = itemApi != null ? itemApi.getPathHandler(type) : null;
		if (handler != null) {
			return handler.matches(item, s);
		}
		if (s.equalsIgnoreCase("item")) {
			Material material = item.getType();
			return material.isItem() && !material.isBlock();
		}
		if(type.equalsIgnoreCase("v")) {
			if (isCustomIdentifiedItem(item)) {
				return false;
			}
			String[] parts = s.split("\\.");
			if (parts.length < 2) {
				return false;
			}
			try {
				return item.getType().equals(Material.valueOf(parts[1].toUpperCase()));
			} catch (IllegalArgumentException e) {
				return false;
			}
		} else if(type.equalsIgnoreCase("m")) {
			if(!(this.getPluginChecker().checkPlugin("MMOItems") && this.getPluginChecker().checkPlugin("MythicLib"))) {
				Bukkit.getLogger().info("[TLibs] ERROR! This operation requires MMOItems and MythicLib!");
				return false;
			}
			String[] parts = s.split("\\.");
			if (parts.length < 2) {
				return false;
			}
			NBTItem nbt = NBTItem.get(item);
			if(!nbt.hasType()) return false;
			if(!nbt.getType().equalsIgnoreCase(parts[1])) return false;
			if (parts.length == 2) {
				return true;
			}
			return nbt.getString("MMOITEMS_ITEM_ID").equalsIgnoreCase(parts[2]);
		} else if(type.equalsIgnoreCase("ia")) {
			if(!(this.getPluginChecker().checkPlugin("ItemsAdder"))) {
				Bukkit.getLogger().info("[TLibs] ERROR! This operation requires ItemsAdder and LoneLibs!");
				return false;
			}
			String itemPath = s.split("\\.")[1]; //ia.tfmc:abyssalite
			CustomStack stack = CustomStack.byItemStack(item);
			if(stack != null) {
				if(itemPath.equalsIgnoreCase(stack.getNamespacedID())) return true;
			}
		} else if (type.equalsIgnoreCase("modeled")) {
			if (!item.hasItemMeta()) return false;

			String raw = s.substring(s.indexOf('(') + 1, s.lastIndexOf(')')); // type=emerald;name=§eYellowshard;model=3
			String[] parts = raw.split(";");
			Map<String, String> attributes = new HashMap<>();

			for (String part : parts) {
				String[] keyValue = part.split("=", 2);
				if (keyValue.length == 2) {
					attributes.put(keyValue[0].toLowerCase(), keyValue[1]);
				}
			}

			// Check type
			if (attributes.containsKey("type")) {
				Material material;
				try {
					material = Material.valueOf(attributes.get("type").toUpperCase());
				} catch (IllegalArgumentException e) {
					return false;
				}
				if (item.getType() != material) return false;
			}

			// Check name
			if (attributes.containsKey("name")) {
				String expectedName = attributes.get("name");
				if (!item.getItemMeta().hasDisplayName()) return false;
				String actualName = item.getItemMeta().getDisplayName();
				if (!actualName.equals(expectedName)) return false;
			}

			// Check model
			if (attributes.containsKey("model")) {
				if (!LegacyModelData.has(item.getItemMeta())) return false;
				int expectedModel;
				try {
					expectedModel = Integer.parseInt(attributes.get("model"));
				} catch (NumberFormatException e) {
					return false;
				}
				int actualModel = LegacyModelData.get(item.getItemMeta());
				if (actualModel != expectedModel) return false;
			}

			return true;
		} else if (type.equalsIgnoreCase("c")) {

			FoodItem fi = FoodItem.fromItem(item);
			if (fi == null) return false;

			// Remove the leading "c."
			String raw = s.substring(2);

			String category;
			String typeFilter = null;

			// Check if parentheses exist
			if (raw.contains("(") && raw.endsWith(")")) {

				// category before "("
				category = raw.substring(0, raw.indexOf("("));

				// inside the parentheses: "type=something"
				String inside = raw.substring(raw.indexOf("(") + 1, raw.length() - 1);

				// expected: type=<value>
				if (inside.startsWith("type=")) {
					typeFilter = inside.substring(5);
				}

			} else {
				// Simple c.category
				category = raw;
			}

			// First check category
			if (!fi.getCategory().equalsIgnoreCase(category)) return false;

			// If no type filter, category match is enough
			if (typeFilter == null) return true;

			// Type must also match
			return fi.getId().equalsIgnoreCase(typeFilter);
		}

		return false;
	}

	private boolean isCustomIdentifiedItem(ItemStack item) {
		if (this.getPluginChecker().checkPlugin("MMOItems") && this.getPluginChecker().checkPlugin("MythicLib")) {
			NBTItem nbt = NBTItem.get(item);
			if (nbt.hasType()) {
				return true;
			}
		}
		if (this.getPluginChecker().checkPlugin("ItemsAdder")) {
			CustomStack stack = CustomStack.byItemStack(item);
			if (stack != null) {
				return true;
			}
		}
		return false;
	}

	public String getMMOItemsType(ItemStack i) {
		NBTItem nbt = NBTItem.get(i);
		if(!nbt.hasType()) return "none";
		return nbt.getType();
	}
}
