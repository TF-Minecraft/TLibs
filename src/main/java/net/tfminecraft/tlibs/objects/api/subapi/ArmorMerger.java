package net.tfminecraft.tlibs.objects.api.subapi;

import net.tfminecraft.tlibs.util.LegacyModelData;


import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.armour.MmoItemTagPreserver;
import net.tfminecraft.tlibs.armour.MmoItemTagPreserver.SavedTag;
import net.tfminecraft.tlibs.objects.TLibAPI;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.gunsandgadgets.GunsAndGadgets;
import net.tfminecraft.gunsandgadgets.guns.skins.SkinData;
import net.tfminecraft.gunsandgadgets.guns.skins.SkinState;
import net.tfminecraft.gunsandgadgets.loader.SkinLoader;

public class ArmorMerger extends TLibAPI{
	ItemAPI api;
	public ArmorMerger(ItemAPI api) {
		this.initialize(api.getServer());
		this.api = api;
	}
	@SuppressWarnings({"deprecation", "null"})
	public ItemStack merge(ItemStack item, Optional<String> name, String s) {
		if (item == null || s == null || s.isBlank()) {
			return item;
		}
		ItemAPI api = TLibs.getItemAPI();
		ItemStack skin;
		String namespace = null;
		String id = null;
		int open = s.indexOf('(');
		String operation = open < 0 ? s : s.substring(0, open);
		if (operation.equalsIgnoreCase("localmodel") || operation.equalsIgnoreCase("gunskin")) {
			if (open < 0 || !s.endsWith(")") || open + 1 == s.length() - 1) {
				return item;
			}
			String value = s.substring(open + 1, s.length() - 1);
			if (operation.equalsIgnoreCase("localmodel")) {
				String[] parts = value.split("\\.", -1);
				if (parts.length != 2) {
					return item;
				}
				Material material;
				int model;
				try {
					material = Material.valueOf(parts[0].toUpperCase(Locale.ROOT));
					model = Integer.parseInt(parts[1]);
				} catch (IllegalArgumentException invalid) {
					return item;
				}
				skin = new ItemStack(material, 1);
				ItemMeta meta = skin.getItemMeta();
				if (meta == null) {
					return item;
				}
				LegacyModelData.set(meta, model);
				skin.setItemMeta(meta);
			} else {
				SkinData gunskin = SkinLoader.getByString(value);
				if (gunskin == null) {
					return item;
				}
				ItemMeta meta = item.getItemMeta();
				if (meta == null) {
					return item;
				}
				NamespacedKey skinKey = new NamespacedKey(GunsAndGadgets.getInstance(), "skin_id");
				meta.getPersistentDataContainer().set(skinKey, PersistentDataType.STRING, gunskin.getId());
				item.setItemMeta(meta);
				int bullets = meta.getPersistentDataContainer()
					.getOrDefault(new NamespacedKey(GunsAndGadgets.getInstance(), "bullets_loaded"), PersistentDataType.INTEGER, 0);
				SkinState state = bullets > 0 ? SkinState.AIM : SkinState.CARRY;
				item = GunsAndGadgets.getInstance().getGunManager().applyModel(item, gunskin, state);
				if (name.isPresent()) {
					ItemMeta named = item.getItemMeta();
					if (named != null) {
						named.setDisplayName(name.get());
						item.setItemMeta(named);
					}
				}
				return item;
			}
		} else {
			int dot = s.indexOf('.');
			if (dot > 0 && s.substring(0, dot).equalsIgnoreCase("ia")) {
				String path = s.substring(dot + 1);
				int colon = path.indexOf(':');
				if (colon <= 0 || colon == path.length() - 1 || path.indexOf(':', colon + 1) >= 0) {
					return item;
				}
				namespace = path.substring(0, colon);
				id = path.substring(colon + 1);
			}
			if (namespace != null && api.getPathHandler("ia") == null) {
				if (!getPluginChecker().checkPlugin("ItemsAdder")) {
					return item;
				}
				// The general creator keeps its legacy DIRT fallback; skins must resolve.
				skin = api.getCreator().getItemsAdderItem(namespace + ":" + id);
			} else {
				skin = api.getCreator().getItemFromPath(s);
			}
		}
		if (skin == null || skin.getType().isAir() || skin.getItemMeta() == null) {
			return item;
		}
		if (namespace != null) {
			item = ItemSkinPreserver.writeIaTag(item, namespace, id);
		}
		if(LegacyModelData.has(skin.getItemMeta())) {
			item = ItemSkinPreserver.writeAmodel(item, LegacyModelData.get(skin.getItemMeta()));
		}
		ItemMeta skinMeta = skin.getItemMeta();
		Color leatherColor = null;
		if(skin.getType().toString().toLowerCase(Locale.ROOT).contains("leather") && skinMeta instanceof LeatherArmorMeta ls) {
			leatherColor = ls.getColor();
		}
		Integer cmd = LegacyModelData.has(skinMeta) ? LegacyModelData.get(skinMeta) : null;
		List<SavedTag> tags = MmoItemTagPreserver.snapshot(item);
		ItemSkinPreserver.applyAppearance(item, skin.getType(), cmd, leatherColor);
		if(name.isPresent()) {
			ItemMeta m = item.getItemMeta();
			if (m != null) {
				m.setDisplayName(name.get());
				item.setItemMeta(m);
			}
		}
		MmoItemTagPreserver.restoreMissing(item, tags);
		if (namespace != null) {
			ItemSkinPreserver.writeItemsAdderCompound(item, namespace, id);
		}
		return item;
	}
}
