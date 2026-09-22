package net.tfminecraft.tlibs.objects.api;

import org.bukkit.Server;

import net.tfminecraft.tlibs.objects.TLibAPI;
import net.tfminecraft.tlibs.objects.api.subapi.BlockChecker;

public class BlockAPI extends TLibAPI{
	private BlockChecker checker;
	
	public void setup(Server s) {
		this.initialize(s);
		checker = new BlockChecker(this);
	}
	
	public BlockChecker getChecker() {
		return checker;
	}
}
