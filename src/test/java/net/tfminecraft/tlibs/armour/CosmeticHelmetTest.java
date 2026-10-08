package net.tfminecraft.tlibs.armour;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.NBTType;
import de.tr7zw.nbtapi.iface.ReadWriteItemNBT;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

class CosmeticHelmetTest {
    private final IdentityHashMap<ItemStack, Piece> pieces = new IdentityHashMap<>();
    private MockedStatic<NBT> nbt;

    @BeforeAll static void startServer() { MockBukkit.mock(); }
    @AfterAll static void stopServer() { MockBukkit.unmock(); }
    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void prepareNbtBoundary() {
        nbt = mockStatic(NBT.class, call -> {
            if (call.getMethod().getName().equals("get") || call.getMethod().getName().equals("modify")) {
                Piece piece = pieces.get((ItemStack) call.getArgument(0));
                ReadWriteItemNBT data = mock(ReadWriteItemNBT.class, access -> {
                    String method = access.getMethod().getName();
                    String key = access.getArguments().length == 0 ? null : access.getArgument(0);
                    return switch (method) {
                        case "hasTag" -> piece.tags.containsKey(key);
                        case "getKeys" -> piece.tags.keySet();
                        case "getInteger" -> piece.tags.getOrDefault(key, 0);
                        case "getString" -> piece.tags.getOrDefault(key, "");
                        case "getType" -> piece.tags.get(key) instanceof String ? NBTType.NBTTagString : NBTType.NBTTagInt;
                        case "setInteger", "setString" -> { piece.tags.put(key, access.getArgument(1)); yield null; }
                        default -> org.mockito.Answers.RETURNS_DEFAULTS.answer(access);
                    };
                });
                Object callback = call.getArgument(1);
                if (callback instanceof Function function) return function.apply(data);
                ((Consumer) callback).accept(data); return null;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(call);
        });
    }
    @AfterEach void cleanup() { nbt.close(); }

    @Test
    void durabilityHelpersHandleEmptyAndBoundaryValues() {
        assertFalse(SkinnedArmorDurability.isCosmeticHelmetMaterial(null));
        assertTrue(SkinnedArmorDurability.isCosmeticHelmetMaterial(Material.CARVED_PUMPKIN));
        assertTrue(SkinnedArmorDurability.isCosmeticHelmetName("PLAYER_HEAD"));
        assertTrue(SkinnedArmorDurability.isCosmeticHelmetName("SKELETON_SKULL"));
        assertFalse(SkinnedArmorDurability.isCosmeticHelmetName(null));
        assertFalse(SkinnedArmorDurability.isCosmeticHelmetMaterial(Material.DIAMOND_HELMET));
        assertEquals(100, SkinnedArmorDurability.resolveMaxDamage(50, 30, 100));
        assertEquals(50, SkinnedArmorDurability.resolveMaxDamage(50, 30, 0));
        assertEquals(30, SkinnedArmorDurability.resolveMaxDamage(0, 30, 0));
        assertEquals(0, SkinnedArmorDurability.resolveMaxDamage(0, -1, 0));
        assertTrue(SkinnedArmorDurability.revertUndamageableDrain(false, true));
        assertFalse(SkinnedArmorDurability.revertUndamageableDrain(true, true));
        assertFalse(SkinnedArmorDurability.revertUndamageableDrain(false, false));
        assertEquals(0, SkinnedArmorDurability.vanillaDamage(1, 0, 30));
        assertEquals(0, SkinnedArmorDurability.vanillaDamage(1, 10, 0));
        assertEquals(0, SkinnedArmorDurability.vanillaDamage(10, 10, 30));
        assertEquals(1, SkinnedArmorDurability.vanillaDamage(99, 100, 10));
        assertEquals(50, SkinnedArmorDurability.vanillaDamage(50, 100, 100));
    }

    @Test
    void protectionRequiresCosmeticMaterialOwnedTagsAndAnExplicitDamageComponent() {
        assertFalse(CosmeticHelmetFix.isProtectedHelmet(null));
        assertFalse(CosmeticHelmetFix.isProtectedHelmet(item(Material.AIR)));
        assertFalse(CosmeticHelmetFix.isProtectedHelmet(item(Material.DIAMOND_HELMET)));
        ItemStack plain = item(Material.CARVED_PUMPKIN);
        assertFalse(CosmeticHelmetFix.isProtectedHelmet(plain));
        pieces.get(plain).tags.put("MMOITEMS_ITEM_TYPE", "ARMOR");
        assertFalse(CosmeticHelmetFix.isProtectedHelmet(plain));
        meta(plain).setMaxDamage(100);
        assertTrue(CosmeticHelmetFix.isProtectedHelmet(plain));
        assertFalse(CosmeticHelmetFix.shouldRevertUndamageableDrain(plain));
        pieces.get(plain).tags.put("MMOITEMS_MAX_DURABILITY", 100);
        assertTrue(CosmeticHelmetFix.shouldRevertUndamageableDrain(plain));
        assertFalse(CosmeticHelmetFix.shouldRevertUndamageableDrain(null));
        ItemStack old = item(Material.PLAYER_HEAD);
        pieces.get(old).tags.put("MMOITEMS_MAX_DURABILITY", 100);
        assertFalse(CosmeticHelmetFix.shouldRevertUndamageableDrain(old));
    }

    @Test
    void appearanceInstallsDurabilityStackAndEquipmentPropertiesThenBecomesIdempotent() {
        ItemStack item = item(Material.CARVED_PUMPKIN);
        pieces.get(item).tags.putAll(Map.of("MMOITEMS_ITEM_TYPE", "ARMOR", "MMOITEMS_MAX_DURABILITY", 100, "MMOITEMS_DURABILITY", 25));
        CosmeticHelmetFix.afterAppearanceChange(item, Material.DIAMOND_HELMET, 20, 200, List.of());
        Meta configured = meta(item);
        assertEquals(100, configured.getMaxDamage());
        assertEquals(75, configured.getDamage());
        assertEquals(1, configured.getMaxStackSize());
        assertEquals(EquipmentSlot.HEAD, configured.getEquippable().getSlot());
        assertTrue(configured.getEquippable().isDamageOnHurt());
        assertTrue(configured.getEquippable().isSwappable());
        assertNull(configured.getEquippable().getCameraOverlay());
        clearInvocations(item);
        CosmeticHelmetFix.afterAppearanceChange(item, Material.DIAMOND_HELMET, 20, 200, List.of());
        verify(item, never()).setItemMeta(any());
    }

    @Test
    void appearanceHandlesMissingMaximumComponentsAndFallbackDamage() {
        ItemStack plain = item(Material.STONE);
        CosmeticHelmetFix.afterAppearanceChange(plain, Material.DIAMOND_HELMET, 0, null, List.of());
        verify(plain, never()).setItemMeta(any());
        ItemStack noScale = item(Material.PLAYER_HEAD);
        pieces.get(noScale).tags.put("MMOITEMS_ITEM_TYPE", "ARMOR");
        CosmeticHelmetFix.afterAppearanceChange(noScale, null, 20, null, List.of());
        assertFalse(meta(noScale).hasMaxDamage());
        CosmeticHelmetFix.afterAppearanceChange(noScale, Material.DIAMOND_HELMET, 20, null, List.of());
        assertEquals(363, meta(noScale).getMaxDamage());
        assertEquals(20, meta(noScale).getDamage());
        ItemStack full = item(Material.CARVED_PUMPKIN);
        pieces.get(full).tags.put("MMOITEMS_MAX_DURABILITY", 100);
        CosmeticHelmetFix.afterAppearanceChange(full, null, 99, 10, List.of());
        assertEquals(0, meta(full).getDamage());
        ItemStack undamageable = item(Material.PLAYER_HEAD);
        pieces.get(undamageable).tags.put("MMOITEMS_MAX_DURABILITY", 100);
        ItemMeta ordinaryMeta = mock(ItemMeta.class);
        when(ordinaryMeta.clone()).thenReturn(ordinaryMeta);
        pieces.get(undamageable).meta = ordinaryMeta;
        CosmeticHelmetFix.afterAppearanceChange(undamageable, null, 0, null, List.of());
        verify(undamageable, never()).setItemMeta(any());
        assertFalse(CosmeticHelmetFix.isProtectedHelmet(undamageable));
        CosmeticHelmetFix.syncVanillaDamage(undamageable);
    }

    @Test
    void appearanceRepairsIncompleteEquipmentAndPumpkinOverlay() {
        ItemStack item = item(Material.CARVED_PUMPKIN);
        pieces.get(item).tags.put("MMOITEMS_MAX_DURABILITY", 100);
        CosmeticHelmetFix.afterAppearanceChange(item, null, 0, null, List.of());
        for (int broken = 0; broken < 5; broken++) {
            Meta meta = meta(item);
            EquippableComponent equipment = meta.getEquippable();
            switch (broken) {
                case 0 -> equipment.setSlot(EquipmentSlot.CHEST);
                case 1 -> equipment.setDamageOnHurt(false);
                case 2 -> equipment.setSwappable(false);
                case 3 -> equipment.setCameraOverlay(NamespacedKey.minecraft("misc/pumpkinblur"));
                case 4 -> equipment.setModel(NamespacedKey.fromString("tfmc_equipment:tfmc_submissions/old_armour"));
            }
            meta.setEquippable(equipment);
            CosmeticHelmetFix.afterAppearanceChange(item, null, 0, null, List.of());
            assertEquals(EquipmentSlot.HEAD, meta(item).getEquippable().getSlot());
            assertTrue(meta(item).getEquippable().isDamageOnHurt());
            assertTrue(meta(item).getEquippable().isSwappable());
            assertNull(meta(item).getEquippable().getCameraOverlay());
            assertNull(meta(item).getEquippable().getModel(), "no armour layer under the model");
        }
    }

    @Test
    void synchronizesVanillaDamageOnlyWhenOutsideOnePointTolerance() {
        ItemStack item = item(Material.CARVED_PUMPKIN);
        pieces.get(item).tags.putAll(Map.of("MMOITEMS_ITEM_TYPE", "ARMOR", "MMOITEMS_MAX_DURABILITY", 100, "MMOITEMS_DURABILITY", 25));
        CosmeticHelmetFix.syncVanillaDamage(item);
        verify(item, never()).setItemMeta(any());
        meta(item).setMaxDamage(100);
        CosmeticHelmetFix.syncVanillaDamage(item);
        assertEquals(75, meta(item).getDamage());
        clearInvocations(item);
        meta(item).setDamage(74);
        CosmeticHelmetFix.syncVanillaDamage(item);
        verify(item, never()).setItemMeta(any());
        CosmeticHelmetFix.syncVanillaDamage(null);
        CosmeticHelmetFix.syncVanillaDamage(item(Material.STONE));
    }

    @Test
    void protectedHelmetPlacementIsCancelled() {
        CosmeticHelmetListener listener = new CosmeticHelmetListener();
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        ItemStack stone = item(Material.STONE);
        when(event.getItemInHand()).thenReturn(stone);
        listener.onPlace(event);
        verify(event, never()).setCancelled(true);
        ItemStack helmet = protectedHelmet();
        when(event.getItemInHand()).thenReturn(helmet);
        listener.onPlace(event);
        verify(event).setCancelled(true);
    }

    @Test
    void damageRestoresTheSavedMetadataOrTheWholeDestroyedArmorPiece() {
        CosmeticHelmetListener listener = new CosmeticHelmetListener();
        ItemStack helmet = protectedHelmet();
        meta(helmet).setDamage(20);
        Player player = player();
        ItemStack[] armor = {null, item(Material.AIR), item(Material.DIAMOND_CHESTPLATE), helmet};
        when(player.getInventory().getArmorContents()).thenReturn(armor);
        EntityDamageEvent event = damage(player);
        listener.onDamageLowest(event);
        meta(helmet).setDamage(21);
        listener.onDamageMonitor(event);
        assertEquals(20, meta(helmet).getDamage());
        verify(player.getInventory(), never()).setArmorContents(any());
        listener.onDamageLowest(event);
        armor[3] = null;
        listener.onDamageMonitor(event);
        assertNotNull(armor[3]);
        assertEquals(20, meta(armor[3]).getDamage());
        verify(player.getInventory()).setArmorContents(armor);
        listener.onDamageLowest(event);
        armor[3] = item(Material.AIR);
        listener.onDamageMonitor(event);
        assertEquals(Material.CARVED_PUMPKIN, armor[3].getType());
    }

    @Test
    void cancelledOrUnprimedDamageAndNonPlayerEventsDoNothing() {
        CosmeticHelmetListener listener = new CosmeticHelmetListener();
        Player player = player();
        ItemStack helmet = protectedHelmet();
        ItemStack[] armor = {helmet};
        when(player.getInventory().getArmorContents()).thenReturn(armor);
        EntityDamageEvent event = damage(player);
        listener.onDamageMonitor(event);
        listener.onDamageLowest(event);
        meta(helmet).setDamage(10);
        when(event.isCancelled()).thenReturn(true);
        listener.onDamageMonitor(event);
        assertEquals(10, meta(helmet).getDamage());
        when(event.isCancelled()).thenReturn(false);
        listener.onDamageMonitor(event);
        assertEquals(10, meta(helmet).getDamage());
        listener.onDamageLowest(damage(mock(Entity.class)));
        listener.onDamageMonitor(damage(mock(Entity.class)));
        ItemStack[] emptyArmor = {null, item(Material.STONE)};
        when(player.getInventory().getArmorContents()).thenReturn(emptyArmor);
        listener.onDamageLowest(event);
        listener.onDamageMonitor(event);
        verify(player.getInventory(), never()).setArmorContents(any());
    }


    @Test
    void damageDoesNotOverwriteAReplacementArmorItem() {
        CosmeticHelmetListener listener = new CosmeticHelmetListener();
        Player player = player();
        ItemStack previous = protectedHelmet();
        ItemStack replacement = item(Material.DIAMOND_HELMET);
        meta(replacement).setMaxDamage(400);
        meta(replacement).setDamage(5);
        ItemStack[] armor = {previous};
        when(player.getInventory().getArmorContents()).thenReturn(armor);
        EntityDamageEvent event = damage(player);
        listener.onDamageLowest(event);
        armor[0] = replacement; // Another damage listener equips a different item.
        listener.onDamageMonitor(event);
        assertEquals(400, meta(replacement).getMaxDamage());
        assertEquals(5, meta(replacement).getDamage());
        assertFalse(pieces.get(replacement).tags.containsKey("MMOITEMS_MAX_DURABILITY"));
    }

    @Test
    void itemDamageSynchronizesEveryArmorPiece() {
        Player player = player();
        ItemStack helmet = protectedHelmet();
        pieces.get(helmet).tags.put("MMOITEMS_DURABILITY", 50);
        ItemStack[] armor = {null, item(Material.STONE), helmet};
        when(player.getInventory().getArmorContents()).thenReturn(armor);
        PlayerItemDamageEvent event = mock(PlayerItemDamageEvent.class);
        when(event.getPlayer()).thenReturn(player);
        new CosmeticHelmetListener().onItemDamage(event);
        assertEquals(50, meta(helmet).getDamage());
    }

    private ItemStack protectedHelmet() {
        ItemStack item = item(Material.CARVED_PUMPKIN);
        pieces.get(item).tags.put("MMOITEMS_MAX_DURABILITY", 100);
        meta(item).setMaxDamage(100);
        return item;
    }
    private Player player() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }
    private EntityDamageEvent damage(Entity entity) {
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(entity);
        return event;
    }
    private ItemStack item(Material material) {
        Piece piece = new Piece(material, material.isAir() ? null : new Meta());
        ItemStack item = mock(ItemStack.class);
        pieces.put(item, piece);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenReturn(1);
        when(item.getItemMeta()).thenAnswer(call -> piece.meta == null ? null : piece.meta.clone());
        when(item.setItemMeta(any())).thenAnswer(call -> { ItemMeta value = call.getArgument(0); piece.meta = value == null ? null : value.clone(); return true; });
        when(item.clone()).thenAnswer(call -> {
            ItemStack clone = item(material);
            pieces.get(clone).meta = piece.meta == null ? null : piece.meta.clone();
            pieces.get(clone).tags.putAll(piece.tags);
            return clone;
        });
        return item;
    }
    private Meta meta(ItemStack item) { return (Meta) pieces.get(item).meta; }
    private static final class Piece {
        final Material material;
        ItemMeta meta;
        final Map<String, Object> tags = new LinkedHashMap<>();
        Piece(Material material, ItemMeta meta) { this.material = material; this.meta = meta; }
    }
    private static final class Meta extends org.mockbukkit.mockbukkit.inventory.meta.ItemMetaMock {
        private Equipment equipment = new Equipment();
        private boolean hasEquipment;
        Meta() {}
        Meta(Meta original) { super(original); equipment = new Equipment(original.equipment); hasEquipment = original.hasEquipment; }
        @Override public boolean hasEquippable() { return hasEquipment; }
        @Override public EquippableComponent getEquippable() { return new Equipment(equipment).component(); }
        @Override public void setEquippable(EquippableComponent component) {
            equipment.slot = component.getSlot(); equipment.damage = component.isDamageOnHurt();
            equipment.swap = component.isSwappable(); equipment.overlay = component.getCameraOverlay();
            equipment.model = component.getModel(); hasEquipment = true;
        }
        @Override public Meta clone() { return new Meta(this); }
    }
    private static final class Equipment {
        EquipmentSlot slot = EquipmentSlot.HAND;
        boolean damage;
        boolean swap;
        NamespacedKey overlay;
        NamespacedKey model;
        Equipment() {}
        Equipment(Equipment original) {
            slot = original.slot; damage = original.damage; swap = original.swap; overlay = original.overlay; model = original.model;
        }
        EquippableComponent component() {
            EquippableComponent component = mock(EquippableComponent.class);
            when(component.getSlot()).thenAnswer(call -> slot);
            doAnswer(call -> { slot = call.getArgument(0); return null; }).when(component).setSlot(any());
            when(component.isDamageOnHurt()).thenAnswer(call -> damage);
            doAnswer(call -> { damage = call.getArgument(0); return null; }).when(component).setDamageOnHurt(anyBoolean());
            when(component.isSwappable()).thenAnswer(call -> swap);
            doAnswer(call -> { swap = call.getArgument(0); return null; }).when(component).setSwappable(anyBoolean());
            when(component.getCameraOverlay()).thenAnswer(call -> overlay);
            doAnswer(call -> { overlay = call.getArgument(0); return null; }).when(component).setCameraOverlay(any());
            when(component.getModel()).thenAnswer(call -> model);
            doAnswer(call -> { model = call.getArgument(0); return null; }).when(component).setModel(any());
            return component;
        }
    }
}
