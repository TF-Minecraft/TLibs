package net.tfminecraft.tlibs.armour.equipment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;

class EquipmentAssetSyncTest {
    private static final Key BRONZE = Key.key("tfmc_equipment", "tfmc_armor/bronze");
    private static final Key OTHER = Key.key("tfmc_equipment", "tfmc_armor/other");
    private static final Key LEATHER = Key.key("minecraft", "leather");
    private static final Key LEATHER_SOUND = Key.key("minecraft", "item.armor.equip_leather");
    private static final int BRONZE_RGB = 0xb38e5d;

    private final EquipmentAssetSync sync = new EquipmentAssetSync("tfmc_equipment", Map.of(BRONZE_RGB, BRONZE));

    @BeforeAll static void startServer() { MockBukkit.mock(); }
    @AfterAll static void stopServer() { MockBukkit.unmock(); }

    /** An equippable whose only non-default values are the asset and the equip sound. */
    private static Equippable equippable(Key asset, Key sound) {
        Equippable equippable = mock(Equippable.class);
        when(equippable.slot()).thenReturn(EquipmentSlot.CHEST);
        when(equippable.assetId()).thenReturn(asset);
        when(equippable.equipSound()).thenReturn(sound);
        when(equippable.dispensable()).thenReturn(true);
        when(equippable.swappable()).thenReturn(true);
        when(equippable.damageOnHurt()).thenReturn(true);
        return equippable;
    }

    /** Leather armour whose clone, once reset, reports the vanilla leather equippable. */
    private static ItemStack leather(Integer rgb, Equippable current) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.LEATHER_CHESTPLATE);
        when(item.getData(DataComponentTypes.EQUIPPABLE)).thenReturn(current);
        if (rgb != null) {
            DyedItemColor dyed = mock(DyedItemColor.class);
            when(dyed.color()).thenReturn(Color.fromRGB(rgb));
            when(item.getData(DataComponentTypes.DYED_COLOR)).thenReturn(dyed);
        }
        ItemStack plain = mock(ItemStack.class);
        Equippable prototype = equippable(LEATHER, LEATHER_SOUND);
        when(plain.getData(DataComponentTypes.EQUIPPABLE)).thenReturn(prototype);
        when(item.clone()).thenReturn(plain);
        return item;
    }

    /** Makes {@code base.toBuilder().assetId(asset).build()} return a marker component. */
    private static Equippable built(Equippable base, Key asset) {
        Equippable.Builder builder = mock(Equippable.Builder.class);
        Equippable result = mock(Equippable.class);
        when(base.toBuilder()).thenReturn(builder);
        when(builder.assetId(asset)).thenReturn(builder);
        when(builder.build()).thenReturn(result);
        return result;
    }

    private static Equippable prototypeOf(ItemStack item) {
        return item.clone().getData(DataComponentTypes.EQUIPPABLE);
    }

    @Test void ignoresMissingAndNonLeatherItems() {
        assertFalse(sync.sync(null));
        ItemStack iron = mock(ItemStack.class);
        when(iron.getType()).thenReturn(Material.IRON_CHESTPLATE);
        assertFalse(sync.sync(iron));
        Equippable noAsset = equippable(null, null);
        when(iron.getData(DataComponentTypes.EQUIPPABLE)).thenReturn(noAsset);
        assertFalse(sync.sync(iron));
        Equippable vanillaAsset = equippable(LEATHER, null);
        when(iron.getData(DataComponentTypes.EQUIPPABLE)).thenReturn(vanillaAsset);
        assertFalse(sync.sync(iron));
        verify(iron, never()).setData(any(io.papermc.paper.datacomponent.DataComponentType.Valued.class), any(Object.class));
        assertFalse(sync.sync(leather(BRONZE_RGB, null)));
    }

    @Test void ownAssetIsRemovedFromAPieceThatIsNoLongerLeather() {
        // A helmet skin made the bronze helmet a carved-pumpkin model; it must not draw bronze armour underneath.
        ItemStack pumpkin = mock(ItemStack.class);
        when(pumpkin.getType()).thenReturn(Material.CARVED_PUMPKIN);
        Equippable stale = equippable(BRONZE, LEATHER_SOUND);
        when(pumpkin.getData(DataComponentTypes.EQUIPPABLE)).thenReturn(stale);
        Equippable cleared = built(stale, null);
        assertTrue(sync.sync(pumpkin));
        verify(pumpkin).setData(DataComponentTypes.EQUIPPABLE, cleared);
    }

    @Test void plainLeatherAndOtherAssetsStayAsTheyAre() {
        ItemStack undyed = leather(null, equippable(LEATHER, LEATHER_SOUND));
        assertFalse(sync.sync(undyed));
        ItemStack unknownColour = leather(0x123456, equippable(LEATHER, LEATHER_SOUND));
        assertFalse(sync.sync(unknownColour));
        ItemStack noAsset = leather(0x123456, equippable(null, LEATHER_SOUND));
        assertFalse(sync.sync(noAsset));
        verify(undyed, never()).setData(any(io.papermc.paper.datacomponent.DataComponentType.Valued.class), any(Object.class));
        verify(unknownColour, never()).resetData(any());
    }

    @Test void customArmourColourGetsItsAssetOnTheLeatherDefaults() {
        ItemStack item = leather(BRONZE_RGB, equippable(LEATHER, LEATHER_SOUND));
        Equippable result = built(prototypeOf(item), BRONZE);
        assertTrue(sync.sync(item));
        verify(item).setData(DataComponentTypes.EQUIPPABLE, result);
        verify(item.clone()).resetData(DataComponentTypes.EQUIPPABLE);
    }

    @Test void anotherPluginsEquippableKeepsItsSettings() {
        Equippable custom = equippable(LEATHER, LEATHER_SOUND);
        when(custom.swappable()).thenReturn(false);
        ItemStack item = leather(BRONZE_RGB, custom);
        Equippable result = built(custom, BRONZE);
        assertTrue(sync.sync(item));
        verify(item).setData(DataComponentTypes.EQUIPPABLE, result);
    }

    @Test void correctAssetIsLeftAlone() {
        ItemStack item = leather(BRONZE_RGB, equippable(BRONZE, LEATHER_SOUND));
        assertFalse(sync.sync(item));
        verify(item, never()).resetData(any());
    }

    @Test void ownAssetWithALostSoundOrWrongSetIsRebuilt() {
        ItemStack lostSound = leather(BRONZE_RGB, equippable(BRONZE, null));
        Equippable restored = built(prototypeOf(lostSound), BRONZE);
        assertTrue(sync.sync(lostSound));
        verify(lostSound).setData(DataComponentTypes.EQUIPPABLE, restored);

        ItemStack otherSet = leather(BRONZE_RGB, equippable(OTHER, LEATHER_SOUND));
        Equippable switched = built(prototypeOf(otherSet), BRONZE);
        assertTrue(sync.sync(otherSet));
        verify(otherSet).setData(DataComponentTypes.EQUIPPABLE, switched);
    }

    @Test void ownAssetIsRemovedWhenTheColourNoLongerMatches() {
        ItemStack recoloured = leather(0x0000ff, equippable(BRONZE, LEATHER_SOUND));
        assertTrue(sync.sync(recoloured));
        verify(recoloured).resetData(DataComponentTypes.EQUIPPABLE);
        ItemStack undyed = leather(null, equippable(OTHER, null));
        assertTrue(sync.sync(undyed));
        verify(undyed).resetData(DataComponentTypes.EQUIPPABLE);
    }

    @Test void comparesEveryEquippableSettingExceptTheAsset() {
        Equippable a = equippable(BRONZE, LEATHER_SOUND);
        assertTrue(EquipmentAssetSync.sameApartFromAsset(a, equippable(LEATHER, LEATHER_SOUND)));
        assertFalse(EquipmentAssetSync.sameApartFromAsset(a, equippable(LEATHER, null)));
        Equippable slot = equippable(LEATHER, LEATHER_SOUND);
        when(slot.slot()).thenReturn(EquipmentSlot.HEAD);
        assertFalse(EquipmentAssetSync.sameApartFromAsset(a, slot));
        Equippable shear = equippable(LEATHER, LEATHER_SOUND);
        when(shear.shearSound()).thenReturn(LEATHER_SOUND);
        assertFalse(EquipmentAssetSync.sameApartFromAsset(a, shear));
    }
}
