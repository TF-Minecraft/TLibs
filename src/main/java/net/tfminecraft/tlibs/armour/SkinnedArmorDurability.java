package net.tfminecraft.tlibs.armour;

import org.bukkit.Material;

/**
 * Helmet skins such as eyepatches are carved pumpkins. That material has no
 * vanilla durability, so MMOItems treats the piece as an undamageable custom
 * item and spends durability on every hit. Once the item carries its own max
 * damage, vanilla armor damage is the loss that should stick.
 */
public final class SkinnedArmorDurability {
	private SkinnedArmorDurability() {
	}

	public static boolean isCosmeticHelmetMaterial(Material material) {
		return material != null && isCosmeticHelmetName(material.name());
	}

	static boolean isCosmeticHelmetName(String name) {
		if (name == null) {
			return false;
		}
		return "CARVED_PUMPKIN".equals(name) || name.endsWith("_HEAD") || name.endsWith("_SKULL");
	}

	public static int resolveMaxDamage(int componentMax, int materialMax, int customMax) {
		if (customMax > 0) {
			return customMax;
		}
		if (componentMax > 0) {
			return componentMax;
		}
		return Math.max(materialMax, 0);
	}

	public static boolean revertUndamageableDrain(boolean materialHasDurability, boolean hasOwnMaxDamage) {
		return !materialHasDurability && hasOwnMaxDamage;
	}

	/**
	 * Same bar mapping MMOItems uses: a full custom bar is zero vanilla damage,
	 * and any loss shows at least one point on the bar.
	 */
	public static int vanillaDamage(int current, int customMax, int vanillaMax) {
		if (customMax <= 0 || vanillaMax <= 0 || current >= customMax) {
			return 0;
		}
		int scaled = (int) ((1.0d - ((double) current / (double) customMax)) * vanillaMax);
		return Math.max(1, scaled);
	}
}
