package net.tfminecraft.tlibs.listener;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public class FurnitureRepairListener implements Listener {

    public static boolean HIDE_ITEMSADDER_FURNITURE_STANDS = false;

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!HIDE_ITEMSADDER_FURNITURE_STANDS) return;

        Chunk chunk = event.getChunk();
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof ArmorStand armorStand)) continue;

            if (!armorStand.isCustomNameVisible()) continue;

            String name = armorStand.getCustomName();
            if ("ItemsAdder_furniture".equals(name)) {
                // Bukkit.getPlayer("drefvelin").sendMessage("Found armor stand with name (and removing): " + name);
                armorStand.setCustomNameVisible(false);
            }
        }
    }
}
