package net.tfminecraft.tlibs.armour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkinnedArmorDurabilityTest {
	@Test
	void cosmeticHelmetMaterials_arePumpkinsAndHeads() {
		assertTrue(SkinnedArmorDurability.isCosmeticHelmetName("CARVED_PUMPKIN"));
		assertTrue(SkinnedArmorDurability.isCosmeticHelmetName("PLAYER_HEAD"));
		assertTrue(SkinnedArmorDurability.isCosmeticHelmetName("SKELETON_SKULL"));
		assertFalse(SkinnedArmorDurability.isCosmeticHelmetName("DIAMOND_HELMET"));
		assertFalse(SkinnedArmorDurability.isCosmeticHelmetName(null));
	}

	@Test
	void resolveMaxDamage_prefersCustomDurability() {
		assertEquals(400, SkinnedArmorDurability.resolveMaxDamage(363, 363, 400));
		assertEquals(363, SkinnedArmorDurability.resolveMaxDamage(363, 55, 0));
		assertEquals(55, SkinnedArmorDurability.resolveMaxDamage(0, 55, 0));
		assertEquals(0, SkinnedArmorDurability.resolveMaxDamage(0, 0, 0));
	}

	@Test
	void revertUndamageableDrain_onlyWhenTheMaterialCannotTakeDamage() {
		assertTrue(SkinnedArmorDurability.revertUndamageableDrain(false, true));
		assertFalse(SkinnedArmorDurability.revertUndamageableDrain(false, false));
		assertFalse(SkinnedArmorDurability.revertUndamageableDrain(true, true));
	}

	@Test
	void vanillaDamage_matchesMmoItemsBar() {
		assertEquals(0, SkinnedArmorDurability.vanillaDamage(100, 100, 100));
		assertEquals(1, SkinnedArmorDurability.vanillaDamage(99, 100, 100));
		assertEquals(50, SkinnedArmorDurability.vanillaDamage(50, 100, 100));
		assertEquals(100, SkinnedArmorDurability.vanillaDamage(0, 100, 100));
	}
}
