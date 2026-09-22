package net.tfminecraft.tlibs.mmoitem;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;

import net.tfminecraft.tlibs.mmoitem.MMOItemRebuildBaselineListener;
import net.tfminecraft.tlibs.mmoitem.MMOItemRebuildBridge;
import net.tfminecraft.tlibs.socket.TieredSocketApplyListener;
import net.tfminecraft.tlibs.socket.TieredSocketRebuildListener;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.utils.RebuildDebug;

public final class MMOItemRebuildRegistrar implements Listener {
	private boolean registered;
	private boolean tieredSocketsRegistered;

	public void tryRegister() {
		if (registered) {
			tryRegisterTieredSockets();
			return;
		}
		if (!TLibs.getRebuildConfig().isEnabled()) {
			RebuildDebug.logAlways("bridge NOT registered: mmo-item-rebuild.enabled=false");
			return;
		}
		if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
			RebuildDebug.logAlways("bridge NOT registered yet: MMOItems not enabled");
			return;
		}
		TLibs plugin = TLibs.getInstance();
		Bukkit.getPluginManager().registerEvents(new MMOItemRebuildBridge(), plugin);
		Bukkit.getPluginManager().registerEvents(new MMOItemRebuildBaselineListener(), plugin);
		registered = true;
		RebuildDebug.logAlways("bridge registered (MMOItemRebuildBridge + BaselineListener)");
		RebuildDebug.logAlways("config enabled=" + TLibs.getRebuildConfig().isEnabled()
				+ " debug-nbt=" + TLibs.getRebuildConfig().debugNbt()
				+ " copy-pdc=" + TLibs.getRebuildConfig().copyPersistentData());
		tryRegisterTieredSockets();
	}

	private void tryRegisterTieredSockets() {
		if (tieredSocketsRegistered || !registered) {
			return;
		}
		if (!TLibs.getSocketTierConfig().isEnabled()) {
			RebuildDebug.logAlways("tiered sockets NOT registered: tiered-sockets.enabled=false");
			return;
		}
		TLibs plugin = TLibs.getInstance();
		Bukkit.getPluginManager().registerEvents(new TieredSocketApplyListener(), plugin);
		Bukkit.getPluginManager().registerEvents(new TieredSocketRebuildListener(), plugin);
		tieredSocketsRegistered = true;
		RebuildDebug.logAlways("tiered socket listeners registered");
	}

	@EventHandler
	public void onPluginEnable(PluginEnableEvent event) {
		if (!event.getPlugin().getName().equalsIgnoreCase("MMOItems")) {
			return;
		}
		RebuildDebug.logAlways("MMOItems enabled event received, attempting bridge registration");
		tryRegister();
	}

	public boolean isRegistered() {
		return registered;
	}
}
