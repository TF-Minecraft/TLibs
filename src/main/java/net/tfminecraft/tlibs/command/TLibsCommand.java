package net.tfminecraft.tlibs.command;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.armour.equipment.EquipmentAssets;

public class TLibsCommand implements CommandExecutor, TabCompleter {
	private static final String PERMISSION = "tlibs.admin";
	private static final List<String> SUBCOMMANDS = List.of("reload", "equipment");

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!sender.hasPermission(PERMISSION)) {
			sender.sendMessage("§cYou do not have permission.");
			return true;
		}
		if (args.length == 0) {
			sender.sendMessage("§e/tlibs reload §7- reload config.yml");
			sender.sendMessage("§e/tlibs equipment [refresh] §7- custom armour equipment assets");
			return true;
		}
		if (args[0].equalsIgnoreCase("reload")) {
			TLibs.getInstance().reloadPluginConfig();
			sender.sendMessage("§a[TLibs] Config reloaded.");
			return true;
		}
		if (args[0].equalsIgnoreCase("equipment")) {
			EquipmentAssets assets = TLibs.getInstance().getEquipmentAssets();
			if (args.length > 1 && args[1].equalsIgnoreCase("refresh")) {
				assets.refreshSources();
				assets.refreshPublished();
				TLibs.getInstance().getEquipmentListener().resyncAll();
			}
			sender.sendMessage("§a[TLibs] Equipment assets " + (assets.isEnabled() ? "enabled" : "disabled") + ": "
					+ assets.sourceCount() + " armour sets found, " + assets.publishedCount() + " in the served pack.");
			return true;
		}
		sender.sendMessage("§cUnknown subcommand. Use: reload, equipment");
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (!sender.hasPermission(PERMISSION)) {
			return Collections.emptyList();
		}
		if (args.length == 1) {
			String typed = args[0].toLowerCase(Locale.ROOT);
			return SUBCOMMANDS.stream().filter(sub -> sub.startsWith(typed)).toList();
		}
		if (args.length == 2 && args[0].equalsIgnoreCase("equipment")) {
			return "refresh".startsWith(args[1].toLowerCase(Locale.ROOT)) ? List.of("refresh") : Collections.emptyList();
		}
		return Collections.emptyList();
	}
}
