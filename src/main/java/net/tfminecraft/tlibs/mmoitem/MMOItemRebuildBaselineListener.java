package net.tfminecraft.tlibs.mmoitem;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.tfminecraft.tlibs.event.MMOItemRebuildEvent;
import net.tfminecraft.tlibs.objects.api.subapi.ItemRebuildMerger;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.utils.RebuildDebug;

public class MMOItemRebuildBaselineListener implements Listener {

	@EventHandler(priority = EventPriority.LOWEST)
	public void onRebuild(MMOItemRebuildEvent event) {
		RebuildDebug.log("MMOItemRebuildEvent BaselineListener LOWEST reason=" + event.getReason());
		event.setNewItem(ItemRebuildMerger.applyBaseline(event.getOldItem(), event.getNewItem(),
				TLibs.getRebuildConfig(), event.getPdcSnapshot()));
		RebuildDebug.log("MMOItemRebuildEvent BaselineListener done");
	}
}
