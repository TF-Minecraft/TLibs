package net.tfminecraft.tlibs.armour;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;

import net.tfminecraft.tlibs.armour.MmoItemTagPreserver.SavedTag;

/**
 * Carved-pumpkin helmet skins keep the original durability scale and the
 * MMOItems tags that describe gem sockets.
 */
public final class CosmeticHelmetFix {
	private CosmeticHelmetFix() {
	}

	public static void afterAppearanceChange(ItemStack item, Material previousType, int previousDamage,
			Integer previousMaxDamage, List<SavedTag> tags) {
		MmoItemTagPreserver.restoreMissing(item, tags);
		if (!shouldMaintain(item)) {
			return;
		}
		int componentMax = previousMaxDamage == null ? 0 : previousMaxDamage;
		int materialMax = previousType == null ? 0 : previousType.getMaxDurability();
		applyProfile(item, componentMax, materialMax, previousDamage, tags);
	}

	public static boolean repairIfNeeded(ItemStack item) {
		if (!shouldMaintain(item)) {
			return false;
		}
		List<SavedTag> tags = MmoItemTagPreserver.snapshot(item);
		int componentMax = 0;
		int currentDamage = 0;
		ItemMeta meta = item.getItemMeta();
		if (meta instanceof Damageable damageable) {
			currentDamage = damageable.getDamage();
			if (damageable.hasMaxDamage()) {
				componentMax = damageable.getMaxDamage();
			}
		}
		return applyProfile(item, componentMax, 0, currentDamage, tags);
	}

	public static boolean shouldRevertUndamageableDrain(ItemStack item) {
		if (!shouldMaintain(item)) {
			return false;
		}
		ItemMeta meta = item.getItemMeta();
		boolean ownMax = meta instanceof Damageable damageable && damageable.hasMaxDamage()
				&& damageable.getMaxDamage() > 0;
		boolean materialHasDurability = item.getType().getMaxDurability() > 0;
		return MmoItemTagPreserver.hasCustomDurability(item)
				&& SkinnedArmorDurability.revertUndamageableDrain(materialHasDurability, ownMax);
	}

	public static boolean isProtectedHelmet(ItemStack item) {
		return shouldMaintain(item);
	}

	public static void syncVanillaDamage(ItemStack item) {
		if (!shouldMaintain(item)) {
			return;
		}
		List<SavedTag> tags = MmoItemTagPreserver.snapshot(item);
		ItemMeta meta = item.getItemMeta();
		if (!(meta instanceof Damageable damageable) || !damageable.hasMaxDamage() || damageable.getMaxDamage() <= 0) {
			return;
		}
		int expected = expectedDamage(item, damageable.getMaxDamage(), damageable.getDamage());
		if (Math.abs(damageable.getDamage() - expected) <= 1) {
			return;
		}
		damageable.setDamage(expected);
		item.setItemMeta(damageable);
		MmoItemTagPreserver.restoreMissing(item, tags);
	}

	private static boolean applyProfile(ItemStack item, int componentMax, int materialMax, int fallbackDamage,
			List<SavedTag> tags) {
		int customMax = MmoItemTagPreserver.customMaxDurability(item);
		int max = SkinnedArmorDurability.resolveMaxDamage(componentMax, materialMax, customMax);
		if (max <= 0) {
			return false;
		}
		ItemMeta meta = item.getItemMeta();
		if (!(meta instanceof Damageable damageable)) {
			return false;
		}
		int expectedDamage = expectedDamage(item, max, fallbackDamage);
		boolean durabilityReady = damageable.hasMaxDamage() && damageable.getMaxDamage() == max
				&& Math.abs(damageable.getDamage() - expectedDamage) <= 1;
		boolean stackReady = meta.hasMaxStackSize() && meta.getMaxStackSize() == 1;
		boolean equipReady = equippableReady(meta, item.getType());
		if (durabilityReady && stackReady && equipReady) {
			return false;
		}
		meta.setMaxStackSize(1);
		damageable.setMaxDamage(max);
		damageable.setDamage(Math.min(expectedDamage, Math.max(0, max - 1)));
		EquippableComponent equippable = meta.getEquippable();
		equippable.setSlot(EquipmentSlot.HEAD);
		equippable.setDamageOnHurt(true);
		equippable.setSwappable(true);
		if (item.getType() == Material.CARVED_PUMPKIN) {
			equippable.setCameraOverlay(null);
		}
		meta.setEquippable(equippable);
		item.setItemMeta(meta);
		MmoItemTagPreserver.restoreMissing(item, tags);
		return true;
	}

	private static int expectedDamage(ItemStack item, int vanillaMax, int fallbackDamage) {
		int customMax = MmoItemTagPreserver.customMaxDurability(item);
		if (customMax <= 0) {
			return Math.max(0, fallbackDamage);
		}
		int current = MmoItemTagPreserver.hasDurabilityValue(item)
				? MmoItemTagPreserver.customDurability(item)
				: customMax;
		return SkinnedArmorDurability.vanillaDamage(current, customMax, vanillaMax);
	}

	private static boolean equippableReady(ItemMeta meta, Material type) {
		if (!meta.hasEquippable()) {
			return false;
		}
		EquippableComponent equippable = meta.getEquippable();
		if (equippable.getSlot() != EquipmentSlot.HEAD || !equippable.isDamageOnHurt() || !equippable.isSwappable()) {
			return false;
		}
		return type != Material.CARVED_PUMPKIN || equippable.getCameraOverlay() == null;
	}

	private static boolean shouldMaintain(ItemStack item) {
		if (item == null || item.getType().isAir()) {
			return false;
		}
		if (!SkinnedArmorDurability.isCosmeticHelmetMaterial(item.getType())) {
			return false;
		}
		return MmoItemTagPreserver.hasItemType(item) || MmoItemTagPreserver.hasCustomDurability(item);
	}
}
