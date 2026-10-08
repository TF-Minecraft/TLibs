package net.tfminecraft.tlibs.armour.equipment;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;

/**
 * Points leather armour dyed with a custom-armour colour at that armour's
 * equipment asset, and points it back at leather once the colour no longer
 * matches a published asset. Items that are no longer leather lose an asset of
 * ours. Only the equippable component changes; MMOItems
 * and ItemsAdder data stay as they are.
 */
public final class EquipmentAssetSync {
	private static final Set<Material> LEATHER = EnumSet.of(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE,
			Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS);

	private final String namespace;
	private final Map<Integer, Key> assets;

	public EquipmentAssetSync(String namespace, Map<Integer, Key> assets) {
		this.namespace = namespace;
		this.assets = assets;
	}

	/** Updates the item in place and returns whether anything changed. */
	public boolean sync(ItemStack item) {
		if (item == null) {
			return false;
		}
		Equippable current = item.getData(DataComponentTypes.EQUIPPABLE);
		if (current == null) {
			return false;
		}
		Key currentAsset = current.assetId();
		boolean ours = currentAsset != null && namespace.equals(currentAsset.namespace());
		if (!LEATHER.contains(item.getType())) {
			// A skin turned this piece into something else (e.g. a carved-pumpkin helmet model): our asset would
			// still draw the old armour under it. Keep the other equip settings.
			if (!ours) {
				return false;
			}
			item.setData(DataComponentTypes.EQUIPPABLE, current.toBuilder().assetId(null).build());
			return true;
		}
		Key wanted = wanted(item);
		if (wanted == null) {
			if (!ours) {
				return false;
			}
			item.resetData(DataComponentTypes.EQUIPPABLE);
			return true;
		}
		// Some plugins copy items through Bukkit serialization, which drops the leather equip
		// sound; an asset of ours is always rebuilt on the leather defaults.
		Equippable prototype = leatherDefaults(item);
		Equippable base = ours || sameApartFromAsset(current, prototype) ? prototype : current;
		if (wanted.equals(currentAsset) && sameApartFromAsset(current, base)) {
			return false;
		}
		item.setData(DataComponentTypes.EQUIPPABLE, base.toBuilder().assetId(wanted).build());
		return true;
	}

	// Equippable#equals also compares how the sound holders were built, so compare the values instead.
	static boolean sameApartFromAsset(Equippable a, Equippable b) {
		return a.slot() == b.slot()
				&& Objects.equals(a.equipSound(), b.equipSound())
				&& Objects.equals(a.cameraOverlay(), b.cameraOverlay())
				&& Objects.equals(a.allowedEntities(), b.allowedEntities())
				&& a.dispensable() == b.dispensable()
				&& a.swappable() == b.swappable()
				&& a.damageOnHurt() == b.damageOnHurt()
				&& a.equipOnInteract() == b.equipOnInteract()
				&& a.canBeSheared() == b.canBeSheared()
				&& Objects.equals(a.shearSound(), b.shearSound());
	}

	private static Equippable leatherDefaults(ItemStack item) {
		ItemStack plain = item.clone();
		plain.resetData(DataComponentTypes.EQUIPPABLE);
		return plain.getData(DataComponentTypes.EQUIPPABLE);
	}

	private Key wanted(ItemStack item) {
		DyedItemColor dyed = item.getData(DataComponentTypes.DYED_COLOR);
		return dyed == null ? null : assets.get(dyed.color().asRGB());
	}
}
