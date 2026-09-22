package net.tfminecraft.tlibs.objects.api.subapi;


import java.util.Optional;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.enums.APIType;
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
	@SuppressWarnings("null")
	public ItemStack merge(ItemStack item, Optional<String> name, String s) {
		ItemAPI api = (ItemAPI) TLibs.getApiInstance(APIType.ITEM_API);
		ItemStack skin = new ItemStack(Material.EMERALD, 1);
		if(s.split("\\(")[0].equalsIgnoreCase("localmodel")) {
			String info = s.split("\\(")[1].replace(")", "");
			try {
				skin = new ItemStack(Material.valueOf(info.split("\\.")[0].toUpperCase()), 1);
				ItemMeta m = skin.getItemMeta();
				m.setCustomModelData(Integer.parseInt(info.split("\\.")[1]));
				skin.setItemMeta(m);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if(s.split("\\(")[0].equalsIgnoreCase("gunskin")){
			String value = s.split("\\(")[1].replace(")", "");
			SkinData gunskin = SkinLoader.getByString(value);
			if(gunskin == null) {
				return item;
			}
			ItemMeta m = item.getItemMeta();
			if (m == null) {
				return item;
			}
			NamespacedKey skinKey = new NamespacedKey(GunsAndGadgets.getInstance(), "skin_id");
			m.getPersistentDataContainer().set(skinKey, PersistentDataType.STRING, gunskin.getId());
			item.setItemMeta(m);
			int bullets = m.getPersistentDataContainer()
				.getOrDefault(new NamespacedKey(GunsAndGadgets.getInstance(), "bullets_loaded"), PersistentDataType.INTEGER, 0);
			SkinState state = bullets > 0 ? SkinState.AIM : SkinState.CARRY;
			item = GunsAndGadgets.getInstance().getGunManager().applyModel(item, gunskin, state);
			if(name.isPresent()) {
				ItemMeta named = item.getItemMeta();
				if (named != null) {
					named.setDisplayName(name.get());
					item.setItemMeta(named);
				}
			}
			return item;
		} else {
			skin = api.getCreator().getItemFromPath(s);

			if(s.split("\\.")[0].equalsIgnoreCase("ia")) {
				String namespace = s.split("\\.")[1].split("\\:")[0];
				String id = s.split("\\.")[1].split("\\:")[1];
				item = ItemSkinPreserver.writeIaTag(item, namespace, id);
			}
		}
		if(skin.getItemMeta().hasCustomModelData()) {
			item = ItemSkinPreserver.writeAmodel(item, skin.getItemMeta().getCustomModelData());
		}
		ItemMeta skinMeta = skin.getItemMeta();
		Color leatherColor = null;
		if(skin.getType().toString().toLowerCase().contains("leather") && skinMeta instanceof LeatherArmorMeta ls) {
			leatherColor = ls.getColor();
		}
		Integer cmd = skinMeta.hasCustomModelData() ? skinMeta.getCustomModelData() : null;
		ItemSkinPreserver.applyAppearance(item, skin.getType(), cmd, leatherColor);
		if(name.isPresent()) {
			ItemMeta m = item.getItemMeta();
			if (m != null) {
				m.setDisplayName(name.get());
				item.setItemMeta(m);
			}
		}
		if(s.split("\\.")[0].equalsIgnoreCase("ia")) {
			String namespace = s.split("\\.")[1].split("\\:")[0];
			String id = s.split("\\.")[1].split("\\:")[1];
			ItemSkinPreserver.writeItemsAdderCompound(item, namespace, id);
		}
		return item;
	}
}
