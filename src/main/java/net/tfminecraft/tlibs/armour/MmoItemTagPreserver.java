package net.tfminecraft.tlibs.armour;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.NBTType;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.nbtapi.iface.ReadableNBT;

/**
 * Keeps MMOItems tags, including gem sockets, across a material change.
 * Paper rewrites item meta when the type changes, and that rewrite can drop
 * custom tags that were only stored for the previous material.
 */
public final class MmoItemTagPreserver {
	static final String ITEM_TYPE = "MMOITEMS_ITEM_TYPE";
	static final String GEM_STONES = "MMOITEMS_GEM_STONES";
	static final String DURABILITY = "MMOITEMS_DURABILITY";
	static final String MAX_DURABILITY = "MMOITEMS_MAX_DURABILITY";

	private MmoItemTagPreserver() {
	}

	public record SavedTag(String key, NBTType type, String stringValue, int intValue, double doubleValue,
			byte byteValue, String compoundSnbt) {
	}

	public static List<SavedTag> snapshot(ItemStack item) {
		List<SavedTag> saved = new ArrayList<>();
		if (item == null || item.getType().isAir()) {
			return saved;
		}
		NBT.get(item, nbt -> {
			for (String key : nbt.getKeys()) {
				if (!key.startsWith("MMOITEMS_")) {
					continue;
				}
				saved.add(readTag(nbt, key));
			}
			return null;
		});
		return saved;
	}

	public static void restoreMissing(ItemStack item, List<SavedTag> saved) {
		if (item == null || item.getType().isAir() || saved == null || saved.isEmpty()) {
			return;
		}
		NBT.modify(item, nbt -> {
			for (SavedTag tag : saved) {
				if (tag == null || tag.key() == null || nbt.hasTag(tag.key())) {
					continue;
				}
				writeTag(nbt, tag);
			}
		});
	}

	public static boolean hasItemType(ItemStack item) {
		return hasTag(item, ITEM_TYPE);
	}

	public static boolean hasCustomDurability(ItemStack item) {
		return hasTag(item, MAX_DURABILITY);
	}

	public static int customDurability(ItemStack item) {
		return integerTag(item, DURABILITY);
	}

	public static boolean hasDurabilityValue(ItemStack item) {
		return hasTag(item, DURABILITY);
	}

	public static int customMaxDurability(ItemStack item) {
		return integerTag(item, MAX_DURABILITY);
	}

	private static boolean hasTag(ItemStack item, String key) {
		if (item == null || item.getType().isAir()) {
			return false;
		}
		Boolean present = NBT.get(item, nbt -> {
			return nbt.hasTag(key);
		});
		return Boolean.TRUE.equals(present);
	}

	private static int integerTag(ItemStack item, String key) {
		if (!hasTag(item, key)) {
			return 0;
		}
		Integer value = NBT.get(item, nbt -> {
			return nbt.getInteger(key);
		});
		return value == null ? 0 : value;
	}

	private static SavedTag readTag(ReadableNBT nbt, String key) {
		NBTType type = nbt.getType(key);
		String stringValue = null;
		int intValue = 0;
		double doubleValue = 0;
		byte byteValue = 0;
		String compoundSnbt = null;
		if (type == NBTType.NBTTagString) {
			stringValue = nbt.getString(key);
		} else if (type == NBTType.NBTTagInt) {
			intValue = nbt.getInteger(key);
		} else if (type == NBTType.NBTTagDouble) {
			doubleValue = nbt.getDouble(key);
		} else if (type == NBTType.NBTTagByte) {
			Byte boxed = nbt.getByte(key);
			byteValue = boxed == null ? 0 : boxed;
		} else if (type == NBTType.NBTTagCompound) {
			ReadableNBT compound = nbt.getCompound(key);
			if (compound != null) {
				compoundSnbt = compound.toString();
			}
		}
		return new SavedTag(key, type, stringValue, intValue, doubleValue, byteValue, compoundSnbt);
	}

	private static void writeTag(ReadWriteNBT nbt, SavedTag tag) {
		if (tag.type() == NBTType.NBTTagString && tag.stringValue() != null) {
			nbt.setString(tag.key(), tag.stringValue());
		} else if (tag.type() == NBTType.NBTTagInt) {
			nbt.setInteger(tag.key(), tag.intValue());
		} else if (tag.type() == NBTType.NBTTagDouble) {
			nbt.setDouble(tag.key(), tag.doubleValue());
		} else if (tag.type() == NBTType.NBTTagByte) {
			nbt.setByte(tag.key(), tag.byteValue());
		} else if (tag.type() == NBTType.NBTTagCompound && tag.compoundSnbt() != null) {
			nbt.getOrCreateCompound(tag.key()).mergeCompound(NBT.parseNBT(tag.compoundSnbt()));
		}
	}
}
