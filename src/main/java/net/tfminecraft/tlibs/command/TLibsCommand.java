package net.tfminecraft.tlibs.command;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import net.tfminecraft.tlibs.TLibs;

public class TLibsCommand implements CommandExecutor, TabCompleter {
	private static final String PERMISSION = "tlibs.admin";

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!sender.hasPermission(PERMISSION)) {
			sender.sendMessage("§cYou do not have permission.");
			return true;
		}
		if (args.length == 0) {
			sender.sendMessage("§e/tlibs reload §7- reload config.yml");
			return true;
		}
		if (args[0].equalsIgnoreCase("reload")) {
			TLibs.getInstance().reloadPluginConfig();
			sender.sendMessage("§a[TLibs] Config reloaded.");
			return true;
		}
		sender.sendMessage("§cUnknown subcommand. Use: reload");
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (!sender.hasPermission(PERMISSION)) {
			return Collections.emptyList();
		}
		if (args.length == 1) {
			return "reload".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("reload") : Collections.emptyList();
		}
		return Collections.emptyList();
	}
}
