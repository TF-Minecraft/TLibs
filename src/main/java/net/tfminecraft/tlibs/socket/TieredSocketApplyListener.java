package net.tfminecraft.tlibs.socket;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.item.ItemTag;
import io.lumine.mythic.lib.api.item.NBTItem;
import net.tfminecraft.tlibs.mmoitem.MMOItemRebuildBridge;
import net.tfminecraft.tlibs.TLibs;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.event.item.ApplyGemStoneEvent;
import net.Indyuce.mmoitems.api.interaction.GemStone;
import net.Indyuce.mmoitems.api.interaction.UseItem;
import net.Indyuce.mmoitems.api.player.PlayerData;
import net.Indyuce.mmoitems.stat.data.GemSocketsData;
import net.Indyuce.mmoitems.stat.data.StringData;

public class TieredSocketApplyListener implements Listener {

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onInventoryClick(InventoryClickEvent event) {
		if (!TLibs.getSocketTierConfig().isEnabled()) {
			return;
		}
		if (event.getAction() != InventoryAction.SWAP_WITH_CURSOR) {
			return;
		}
		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}

		ItemStack cursor = event.getCursor();
		ItemStack current = event.getCurrentItem();
		if (cursor == null || current == null || cursor.getType().isAir() || current.getType().isAir()) {
			return;
		}

		NBTItem gemNbt = MythicLib.plugin.getVersion().getWrapper().getNBTItem(cursor);
		Type gemType = Type.get(gemNbt);
		if (gemType == null) {
			return;
		}

		PlayerData playerData = PlayerData.get(player);
		UseItem useItem = gemType.toUseItem(playerData, gemNbt);
		if (!(useItem instanceof GemStone) || !useItem.checkItemRequirements()) {
			return;
		}

		NBTItem hostNbt = MythicLib.plugin.getVersion().getWrapper().getNBTItem(current);
		if (!hostNbt.hasType()) {
			return;
		}

		String gemColor = gemNbt.getString(ItemStats.GEM_COLOR.getNBTPath());
		GemSocketsData sockets = GemSocketsNbtEditor.getSockets(current);
		if (sockets == null) {
			return;
		}

		if (sockets.getEmptySocket(gemColor) != null) {
			return;
		}

		String targetSocket = SocketTierRegistry.pickLowestCompatible(sockets, gemColor);
		if (targetSocket == null) {
			return;
		}

		boolean spoofed = !gemColor.equals(targetSocket);
		ItemStack spoofedGem = cursor.clone();
		NBTItem spoofedNbt = MythicLib.plugin.getVersion().getWrapper().getNBTItem(spoofedGem);
		if (spoofed) {
			spoofedNbt.addTag(new ItemTag(ItemStats.GEM_COLOR.getNBTPath(), targetSocket));
		}

		GemStone spoofedStone = new GemStone(playerData, spoofedNbt);
		GemStone.ApplyResult result = spoofedStone.applyOntoItem(hostNbt, Type.get(hostNbt.getType()));
		if (result.getType() == GemStone.ResultType.NONE) {
			PendingTieredSocketApply.clear(player);
			return;
		}

		event.setCancelled(true);
		gemNbt.getItem().setAmount(gemNbt.getItem().getAmount() - 1);

		if (result.getType() == GemStone.ResultType.FAILURE) {
			PendingTieredSocketApply.clear(player);
			return;
		}

		PendingTieredSocketApply.stash(player, cursor, targetSocket, spoofed);
		event.setCurrentItem(result.getResult());
		MMOItemRebuildBridge.confirmAndScheduleGemApply(player, hostNbt.getType(),
				hostNbt.getString("MMOITEMS_ITEM_ID"));
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onApplyGemTierGate(ApplyGemStoneEvent event) {
		if (!TLibs.getSocketTierConfig().isEnabled()) {
			return;
		}
		if (event.isCancelled() || event.getResult() == GemStone.ResultType.NONE) {
			return;
		}

		if (!event.getGemStone().hasData(ItemStats.GEM_COLOR)) {
			return;
		}

		String gemColor = ((StringData) event.getGemStone().getData(ItemStats.GEM_COLOR)).getString();
		if (!SocketTierRegistry.canFit(gemColor, gemColor)) {
			event.setCancelled(true);
			event.setResult(GemStone.ResultType.NONE);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onApplyGemStashCursor(ApplyGemStoneEvent event) {
		if (!TLibs.getSocketTierConfig().isEnabled()) {
			return;
		}
		if (event.getResult() != GemStone.ResultType.SUCCESS) {
			return;
		}

		Player player = event.getPlayer();
		ItemStack cursor = player.getItemOnCursor();
		if (cursor == null || cursor.getType().isAir()) {
			return;
		}

		NBTItem gemNbt = MythicLib.plugin.getVersion().getWrapper().getNBTItem(cursor);
		String gemColor = gemNbt.getString(ItemStats.GEM_COLOR.getNBTPath());
		PendingTieredSocketApply.stash(player, cursor, gemColor, false);
	}
}
