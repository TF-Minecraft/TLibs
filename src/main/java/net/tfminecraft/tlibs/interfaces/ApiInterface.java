package net.tfminecraft.tlibs.interfaces;

import org.bukkit.Server;

public interface ApiInterface {
	public void initialize(Server s);
	public Server getServer();
}
