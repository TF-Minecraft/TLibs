package net.tfminecraft.tlibs.objects.api.subapi;

import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadableItemNBT;
import de.tr7zw.nbtapi.iface.ReadableNBT;
import de.tr7zw.nbtapi.iface.ReadWriteItemNBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import io.lumine.mythic.lib.api.item.ItemTag;
import io.lumine.mythic.lib.api.item.NBTItem;
import net.tfminecraft.tlibs.config.RebuildConfig;
import net.tfminecraft.tlibs.utils.PersistentDataCopier;

public final class ItemRebuildMerger {
	private ItemRebuildMerger() {
	}

	public static ItemStack applyBaseline(ItemStack oldItem, ItemStack newItem, RebuildConfig config) {
		return applyBaseline(oldItem, newItem, config, null);
	}

	public static ItemStack applyBaseline(ItemStack oldItem, ItemStack newItem, RebuildConfig config,
			PersistentDataContainer pdcSnapshot) {
		if (oldItem == null || newItem == null || oldItem.getType().isAir()) {
			return newItem;
		}
		ItemStack result = newItem.clone();
		copyPreserveNbt(oldItem, result, config.getPreserveNbtTags());
		if (config.copyAppearance() && ItemSkinPreserver.hasSkinData(oldItem)) {
			result = ItemSkinPreserver.applyAppearanceFromSkin(oldItem, result);
		}
		if (config.copyPersistentData()) {
			copyPersistentData(oldItem, result, pdcSnapshot);
		}
		return result;
	}

	private static void copyPreserveNbt(ItemStack oldItem, ItemStack result, List<String> tags) {
		if (tags.isEmpty()) {
			return;
		}
		NBTItem oldNbt = NBTItem.get(oldItem);
		NBTItem resultNbt = NBTItem.get(result);
		boolean scalarChanged = false;

		for (String tag : tags) {
			if (isCompoundTag(oldItem, tag)) {
				copyCompoundTag(oldItem, result, tag);
				continue;
			}
			if (oldNbt.hasTag(tag)) {
				resultNbt.addTag(new ItemTag(tag, oldNbt.getString(tag)));
				scalarChanged = true;
			}
		}

		if (scalarChanged) {
			ItemStack rebuilt = resultNbt.toItem();
			ItemMeta rebuiltMeta = rebuilt.getItemMeta();
			if (rebuiltMeta != null) {
				result.setItemMeta(rebuiltMeta);
			}
		}
	}

	private static boolean isCompoundTag(ItemStack item, String tag) {
		final boolean[] compound = { false };
		NBT.get(item, (ReadableItemNBT nbt) -> {
			ReadableNBT value = nbt.getCompound(tag);
			if (value != null) {
				compound[0] = true;
			}
		});
		return compound[0];
	}

	private static void copyCompoundTag(ItemStack oldItem, ItemStack result, String tag) {
		String serialized = readCompoundSnbt(oldItem, tag);
		if (serialized == null) {
			return;
		}
		NBT.modify(result, (ReadWriteItemNBT newNbt) -> {
			ReadWriteNBT target = newNbt.getOrCreateCompound(tag);
			target.mergeCompound(NBT.parseNBT(serialized));
		});
	}

	private static void copyPersistentData(ItemStack oldItem, ItemStack result, PersistentDataContainer pdcSnapshot) {
		PersistentDataContainer snapshot = pdcSnapshot;
		if (snapshot == null || snapshot.getKeys().isEmpty()) {
			snapshot = PersistentDataCopier.snapshot(oldItem);
		}
		if (snapshot != null && !snapshot.getKeys().isEmpty()) {
			PersistentDataCopier.applySnapshot(result, snapshot);
		}
		copyPublicBukkitValuesNbt(oldItem, result);
	}

	private static void copyPublicBukkitValuesNbt(ItemStack oldItem, ItemStack result) {
		String serialized = readCompoundSnbt(oldItem, PersistentDataCopier.publicBukkitValuesTag());
		if (serialized == null) {
			return;
		}
		NBT.modify(result, (ReadWriteItemNBT newNbt) -> {
			ReadWriteNBT target = newNbt.getOrCreateCompound(PersistentDataCopier.publicBukkitValuesTag());
			target.mergeCompound(NBT.parseNBT(serialized));
		});
	}

	private static String readCompoundSnbt(ItemStack item, String tag) {
		final String[] serialized = { null };
		NBT.get(item, (ReadableItemNBT nbt) -> {
			if (!nbt.hasTag(tag)) {
				return;
			}
			ReadableNBT source = nbt.getCompound(tag);
			if (source != null) {
				serialized[0] = source.toString();
			}
		});
		return serialized[0];
	}
}
