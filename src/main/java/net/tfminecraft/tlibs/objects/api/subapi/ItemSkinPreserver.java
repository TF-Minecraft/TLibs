package net.tfminecraft.tlibs.objects.api.subapi;

import net.tfminecraft.tlibs.armour.CosmeticHelmetFix;
import net.tfminecraft.tlibs.armour.MmoItemTagPreserver;
import net.tfminecraft.tlibs.armour.MmoItemTagPreserver.SavedTag;
import net.tfminecraft.tlibs.util.LegacyModelData;

import java.util.List;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadableNBT;
import io.lumine.mythic.lib.api.item.ItemTag;
import io.lumine.mythic.lib.api.item.NBTItem;

public final class ItemSkinPreserver {
	private ItemSkinPreserver() {
	}

	public static ItemStack apply(ItemStack oldItem, ItemStack newItem) {
		if (oldItem == null || newItem == null || oldItem.getType().isAir() || !hasSkinData(oldItem)) {
			return newItem;
		}
		return applyAppearanceFromSkin(oldItem, newItem.clone());
	}

	public static boolean hasSkinData(ItemStack item) {
		if (item == null || item.getType().isAir()) {
			return false;
		}
		NBTItem nbt = NBTItem.get(item);
		return nbt.hasTag("ia") || nbt.hasTag("amodel") || hasItemsAdderCompound(item);
	}

	// This path mutates the existing ItemStack; replacing it would change aliases held by callers.
	@SuppressWarnings("deprecation")
	public static ItemStack applyAppearanceFromSkin(ItemStack oldItem, ItemStack result) {
		NBTItem oldNbt = NBTItem.get(oldItem);
		NBTItem resultNbt = NBTItem.get(result);

		if (oldNbt.hasTag("ia")) {
			resultNbt.addTag(new ItemTag("ia", oldNbt.getString("ia")));
		}
		if (oldNbt.hasTag("amodel")) {
			resultNbt.addTag(new ItemTag("amodel", oldNbt.getString("amodel")));
		}
		result = resultNbt.toItem();

		Material previousType = result.getType();
		int previousDamage = currentDamage(result);
		Integer previousMaxDamage = currentMaxDamage(result);
		List<SavedTag> tags = MmoItemTagPreserver.snapshot(result);

		Material skinMaterial = oldItem.getType();
		result.setType(skinMaterial);

		ItemMeta oldMeta = oldItem.getItemMeta();
		ItemMeta resultMeta = result.getItemMeta();
		if (oldMeta != null && resultMeta != null) {
			if (LegacyModelData.has(oldMeta)) {
				LegacyModelData.set(resultMeta, LegacyModelData.get(oldMeta));
			} else if (oldNbt.hasTag("amodel")) {
				try {
					LegacyModelData.set(resultMeta, Integer.parseInt(oldNbt.getString("amodel")));
				} catch (NumberFormatException ignored) {
				}
			}
			if (oldMeta instanceof LeatherArmorMeta oldLeather && resultMeta instanceof LeatherArmorMeta newLeather) {
				newLeather.setColor(oldLeather.getColor());
				result.setItemMeta(newLeather);
			} else {
				result.setItemMeta(resultMeta);
			}
		}

		CosmeticHelmetFix.afterAppearanceChange(result, previousType, previousDamage, previousMaxDamage, tags);

		String[] iaParts = parseIaTag(oldNbt);
		if (iaParts != null) {
			writeItemsAdderCompound(result, iaParts[0], iaParts[1]);
		} else {
			String[] compoundParts = readItemsAdderCompound(oldItem);
			if (compoundParts != null) {
				writeItemsAdderCompound(result, compoundParts[0], compoundParts[1]);
			}
		}

		return result;
	}

	public static ItemStack writeIaTag(ItemStack item, String namespace, String id) {
		NBTItem nbt = NBTItem.get(item);
		nbt.addTag(new ItemTag("ia", namespace + "." + id));
		return nbt.toItem();
	}

	public static ItemStack writeAmodel(ItemStack item, int customModelData) {
		NBTItem nbt = NBTItem.get(item);
		nbt.addTag(new ItemTag("amodel", String.valueOf(customModelData)));
		ItemStack updated = nbt.toItem();
		ItemMeta meta = updated.getItemMeta();
		if (meta != null) {
			LegacyModelData.set(meta, customModelData);
			updated.setItemMeta(meta);
		}
		return updated;
	}

	public static void writeItemsAdderCompound(ItemStack item, String namespace, String id) {
		NBT.modify(item, nbt -> {
			nbt.getOrCreateCompound("itemsadder");
			nbt.getCompound("itemsadder").setString("namespace", namespace);
			nbt.getCompound("itemsadder").setString("id", id);
		});
	}

	// This path mutates the existing ItemStack; replacing it would change aliases held by callers.
	@SuppressWarnings("deprecation")
	public static void applyAppearance(ItemStack item, Material type, Integer customModelData, Color leatherColor) {
		Material previousType = item.getType();
		int previousDamage = currentDamage(item);
		Integer previousMaxDamage = currentMaxDamage(item);
		List<SavedTag> tags = MmoItemTagPreserver.snapshot(item);
		item.setType(type);
		ItemMeta meta = item.getItemMeta();
		if (meta == null) {
			MmoItemTagPreserver.restoreMissing(item, tags);
			return;
		}
		if (customModelData != null) {
			LegacyModelData.set(meta, customModelData);
		}
		if (leatherColor != null && meta instanceof LeatherArmorMeta leather) {
			leather.setColor(leatherColor);
			item.setItemMeta(leather);
		} else {
			item.setItemMeta(meta);
		}
		CosmeticHelmetFix.afterAppearanceChange(item, previousType, previousDamage, previousMaxDamage, tags);
	}

	private static int currentDamage(ItemStack item) {
		ItemMeta meta = item.getItemMeta();
		if (meta instanceof Damageable damageable) {
			return damageable.getDamage();
		}
		return 0;
	}

	private static Integer currentMaxDamage(ItemStack item) {
		ItemMeta meta = item.getItemMeta();
		if (meta instanceof Damageable damageable && damageable.hasMaxDamage()) {
			return damageable.getMaxDamage();
		}
		return null;
	}

	private static boolean hasItemsAdderCompound(ItemStack item) {
		return readItemsAdderCompound(item) != null;
	}

	private static String[] readItemsAdderCompound(ItemStack item) {
		String[] parts = new String[2];
		boolean[] found = { false };
		NBT.get(item, nbt -> {
			if (!nbt.hasTag("itemsadder")) {
				return;
			}
			ReadableNBT compound = nbt.getCompound("itemsadder");
			if (compound == null
				|| !compound.hasTag("namespace")
				|| !compound.hasTag("id")) {
				return;
			}
			parts[0] = compound.getString("namespace");
			parts[1] = compound.getString("id");
			found[0] = true;
		});
		return found[0] ? parts : null;
	}

	private static String[] parseIaTag(NBTItem nbt) {
		if (!nbt.hasTag("ia")) {
			return null;
		}
		String ia = nbt.getString("ia");
		int dot = ia.indexOf('.');
		if (dot < 0) {
			return null;
		}
		return new String[] { ia.substring(0, dot), ia.substring(dot + 1) };
	}
}
