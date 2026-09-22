package net.tfminecraft.tlibs.objects;

import org.bukkit.Server;

import net.tfminecraft.tlibs.interfaces.ApiInterface;
import net.tfminecraft.tlibs.internalutils.PluginChecker;

public class TLibAPI implements ApiInterface{
	private PluginChecker pc;
	private Server server;
	
	@Override
	public void initialize(Server s) {
		this.server = s;
		pc = new PluginChecker(s);
	}
	public Server getServer() {
		return server;
	}
	public PluginChecker getPluginChecker() {
		return pc;
	}
}
