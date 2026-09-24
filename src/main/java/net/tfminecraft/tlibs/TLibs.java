package net.tfminecraft.tlibs;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.tlibs.armour.ArmorEquipEvent;
import net.tfminecraft.tlibs.command.TLibsCommand;
import net.tfminecraft.tlibs.config.RebuildConfig;
import net.tfminecraft.tlibs.config.SocketTierConfig;
import net.tfminecraft.tlibs.enums.APIType;
import net.tfminecraft.tlibs.itemscan.ItemScanService;
import net.tfminecraft.tlibs.listener.FurnitureRepairListener;
import net.tfminecraft.tlibs.mmoitem.GemApplyPrimer;
import net.tfminecraft.tlibs.mmoitem.MMOItemRebuildRegistrar;
import net.tfminecraft.tlibs.objects.TLibAPI;
import net.tfminecraft.tlibs.objects.api.BlockAPI;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.utils.RebuildDebug;

public class TLibs extends JavaPlugin {
	private static TLibs instance;
	private static final BlockAPI bApi = new BlockAPI();
	private static final ItemAPI iApi = new ItemAPI();
	private final RebuildConfig rebuildConfig = new RebuildConfig();
	private final SocketTierConfig socketTierConfig = new SocketTierConfig();
	private final TLibsCommand tlibsCommand = new TLibsCommand();
	private final MMOItemRebuildRegistrar rebuildRegistrar = new MMOItemRebuildRegistrar();

	@Override
	public void onEnable() {
		instance = this;
		Bukkit.getLogger().info("[TLibs] Initializing...");

		saveDefaultConfig();
		reloadPluginConfig();
		initializeAPIs();
		ItemScanService.start(this);
		GemApplyPrimer.init(this);
		ArmorEquipEvent.registerListener(this);
		Bukkit.getPluginManager().registerEvents(new FurnitureRepairListener(), this);
		registerRebuildBridge();
		RebuildDebug.logAlways("startup complete bridgeRegistered=" + rebuildRegistrar.isRegistered());

		var tlibsCmd = getCommand("tlibs");
		if (tlibsCmd != null) {
			tlibsCmd.setExecutor(tlibsCommand);
			tlibsCmd.setTabCompleter(tlibsCommand);
		}

		Bukkit.getLogger().info("[TLibs] Complete!");
	}

	@Override
	public void onDisable() {
		if (ItemScanService.get() != null) {
			ItemScanService.get().stop();
		}
	}

	private void registerRebuildBridge() {
		getServer().getPluginManager().registerEvents(rebuildRegistrar, this);
		rebuildRegistrar.tryRegister();
	}

	public void reloadPluginConfig() {
		reloadConfig();
		rebuildConfig.reload(getConfig());
		socketTierConfig.reload(getConfig());
		RebuildDebug.logAlways("config reloaded enabled=" + rebuildConfig.isEnabled()
				+ " debug-nbt=" + rebuildConfig.debugNbt()
				+ " tiered-sockets=" + socketTierConfig.isEnabled());
		rebuildRegistrar.tryRegister();
	}

	private void initializeAPIs() {
		bApi.setup(getServer());
		iApi.setup(getServer());
	}

	@Deprecated
	public static TLibAPI getApiInstance(APIType t) {
		if (t.equals(APIType.ITEM_API)) {
			return iApi;
		} else if (t.equals(APIType.BLOCK_API)) {
			return bApi;
		}
		return null;
	}

	public static TLibs getInstance() {
		return instance;
	}

	public static RebuildConfig getRebuildConfig() {
		return instance.rebuildConfig;
	}

	public static SocketTierConfig getSocketTierConfig() {
		return instance.socketTierConfig;
	}

	public static ItemAPI getItemAPI() {
		return iApi;
	}

	public static BlockAPI getBlockAPI() {
		return bApi;
	}
}
