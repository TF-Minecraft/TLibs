package net.tfminecraft.tlibs.listener;

import java.util.Collection;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
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
        int removed = sweep(event.getEntities());
        if (removed > 0) {
            Chunk chunk = event.getChunk();
            logger.info("Removed " + removed + " leftover MythicLib indicator hologram(s) in "
                    + chunk.getWorld().getName() + " chunk " + chunk.getX() + "," + chunk.getZ() + ".");
        }
    }

    /** One pass over chunks whose entities loaded before TLibs was enabled. */
    public void sweepLoaded() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            removed += sweep(world.getEntitiesByClass(TextDisplay.class));
        }
        if (removed > 0) {
            logger.info("Removed " + removed + " leftover MythicLib indicator hologram(s) from loaded chunks.");
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
