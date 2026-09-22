package net.tfminecraft.tlibs.objects.api.subapi;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;

import dev.lone.itemsadder.api.CustomBlock;
import dev.lone.itemsadder.api.CustomFurniture;
import net.tfminecraft.tlibs.objects.TLibAPI;
import net.tfminecraft.tlibs.objects.api.BlockAPI;

public class BlockChecker extends TLibAPI {
	public BlockChecker(BlockAPI api) {
		this.initialize(api.getServer());
	}

	public boolean checkBlock(Block b, String configPath) {
		if (configPath == null || configPath.isBlank() || b == null) {
			return false;
		}
		String trimmed = configPath.trim();

		int openParen = trimmed.indexOf('(');
		int closeParen = trimmed.indexOf(')', openParen + 1);
		if (openParen >= 0 && closeParen > openParen) {
			String type = trimmed.substring(0, openParen);
			String path = trimmed.substring(openParen + 1, closeParen);
			return checkBlockTyped(b, type, path);
		}

		int dot = trimmed.indexOf('.');
		if (dot > 0) {
			String type = trimmed.substring(0, dot);
			String path = trimmed.substring(dot + 1);
			return checkBlockTyped(b, type, path);
		}

		if (trimmed.contains(":")) {
			return blockIsIA(b, trimmed);
		}

		return checkVanillaMaterial(b, trimmed);
	}

	private boolean checkBlockTyped(Block b, String type, String path) {
		if (type.equalsIgnoreCase("v")) {
			return checkVanillaMaterial(b, path);
		}
		if (type.equalsIgnoreCase("iaf")) {
			return blockIsFurniture(b, path);
		}
		if (type.equalsIgnoreCase("iab") || type.equalsIgnoreCase("ia")) {
			return blockIsIA(b, path);
		}
		return false;
	}

	private boolean checkVanillaMaterial(Block b, String materialName) {
		if (materialName == null || materialName.isBlank()) {
			return false;
		}
		try {
			Material material = Material.valueOf(materialName.trim().toUpperCase());
			return b.getType() == material;
		} catch (IllegalArgumentException ex) {
			return false;
		}
	}

	/** Tallest furniture hitbox, in blocks, that a click on an upper block is traced down through. */
	private static final int MAX_FURNITURE_HEIGHT = 4;

	private boolean blockIsFurniture(Block b, String furniture) {
		if (!b.getType().equals(Material.BARRIER)) return false;
		if (!(this.getPluginChecker().checkPlugin("ItemsAdder"))) {
			Bukkit.getLogger().info("[TLibs] ERROR! This operation requires ItemsAdder and LoneLibs!");
			return false;
		}
		// Tall furniture (hitbox height > 1) only has its item frame in the bottom block, so walk
		// down the column of barriers until the frame that owns the clicked block is found.
		Block current = b;
		for (int depth = 0; depth < MAX_FURNITURE_HEIGHT && current.getType().equals(Material.BARRIER); depth++) {
			for (Entity a : current.getWorld().getNearbyEntities(current.getLocation().clone().add(0.5, 0, 0.5), 0.4, 0.4, 0.4)) {
				if (!(a instanceof ItemFrame)) continue;
				CustomFurniture f = CustomFurniture.byAlreadySpawned(a);
				if (f != null && (f.getNamespace() + ":" + f.getId()).equalsIgnoreCase(furniture)) {
					return true;
				}
			}
			current = current.getRelative(0, -1, 0);
		}
		return false;
	}

	private boolean blockIsIA(Block b, String path) {
		if (!(this.getPluginChecker().checkPlugin("ItemsAdder"))) {
			Bukkit.getLogger().info("[TLibs] ERROR! This operation requires ItemsAdder and LoneLibs!");
			return false;
		}
		CustomBlock block = CustomBlock.byAlreadyPlaced(b);
		if (block != null) {
			return (block.getNamespace() + ":" + block.getId()).equalsIgnoreCase(path);
		}
		return false;
	}
}
