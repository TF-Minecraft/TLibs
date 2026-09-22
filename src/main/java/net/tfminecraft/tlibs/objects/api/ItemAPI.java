package net.tfminecraft.tlibs.objects.api;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Server;

import net.tfminecraft.tlibs.objects.TLibAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ArmorMerger;
import net.tfminecraft.tlibs.objects.api.subapi.ItemChecker;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;
import net.tfminecraft.tlibs.objects.api.subapi.ItemPathHandler;

public class ItemAPI extends TLibAPI{
	private ItemCreator creator;
	private ItemChecker checker;
	private ArmorMerger armorMerger;
	private final Map<String, ItemPathHandler> pathHandlers = new ConcurrentHashMap<>();
	
	public void setup(Server s) {
		this.initialize(s);
		creator = new ItemCreator(this);
		checker = new ItemChecker(this);
		armorMerger = new ArmorMerger(this);
	}

	public void registerPathHandler(String prefix, ItemPathHandler handler) {
		if (prefix == null || prefix.isBlank() || handler == null) {
			return;
		}
		pathHandlers.put(prefix.toLowerCase(Locale.ROOT), handler);
	}

	public void unregisterPathHandler(String prefix) {
		if (prefix == null || prefix.isBlank()) {
			return;
		}
		pathHandlers.remove(prefix.toLowerCase(Locale.ROOT));
	}

	public ItemPathHandler getPathHandler(String prefix) {
		if (prefix == null || prefix.isBlank()) {
			return null;
		}
		return pathHandlers.get(prefix.toLowerCase(Locale.ROOT));
	}
	
	public ArmorMerger getArmorMerger() {
		return armorMerger;
	}
	
	public ItemCreator getCreator() {
		return creator;
	}
	
	public ItemChecker getChecker() {
		return checker;
	}
}
