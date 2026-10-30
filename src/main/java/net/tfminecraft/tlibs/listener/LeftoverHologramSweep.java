package net.tfminecraft.tlibs.listener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;

/**
 * MythicLib's indicator holograms (EXP gains, damage numbers) show for a second or two, but one that
 * is still showing when its chunk unloads or the server stops is saved with the chunk and never
 * removed. This removes those leftovers when their chunk's entities load, and once at startup.
 */
public class LeftoverHologramSweep implements Listener {

    static final NamespacedKey MYTHICLIB_HOLOGRAM = NamespacedKey.fromString("mythiclib:hologram");

    private final Logger logger;

    public LeftoverHologramSweep(Logger logger) {
        this.logger = logger;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        Chunk chunk = event.getChunk();
        report(chunk.getWorld().getName(), chunk.getX(), chunk.getZ(), sweep(event.getEntities()));
    }

    /** One pass over chunks whose entities loaded before TLibs was enabled, logged per chunk. */
    public void sweepLoaded() {
        for (World world : Bukkit.getWorlds()) {
            Map<Long, List<TextDisplay>> byChunk = new LinkedHashMap<>();
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                Location at = display.getLocation();
                long key = ((long) (at.getBlockX() >> 4) << 32) | ((at.getBlockZ() >> 4) & 0xFFFFFFFFL);
                byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(display);
            }
            byChunk.forEach((key, displays) -> report(world.getName(), (int) (key >> 32), (int) (long) key, sweep(displays)));
        }
    }

    private void report(String world, int chunkX, int chunkZ, int removed) {
        if (removed > 0) {
            logger.info("Removed " + removed + " leftover MythicLib indicator hologram(s) in "
                    + world + " chunk " + chunkX + "," + chunkZ + ".");
        }
    }

    static int sweep(Collection<? extends Entity> entities) {
        int removed = 0;
        for (Entity entity : entities) {
            if (entity instanceof TextDisplay display
                    && display.getPersistentDataContainer().has(MYTHICLIB_HOLOGRAM)) {
                display.remove();
                removed++;
            }
        }
        return removed;
    }
}
