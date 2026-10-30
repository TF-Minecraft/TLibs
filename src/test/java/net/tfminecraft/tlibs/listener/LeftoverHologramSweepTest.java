package net.tfminecraft.tlibs.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class LeftoverHologramSweepTest {

    private static TextDisplay display(boolean mythicLib) {
        return display(mythicLib, null, 0, 0);
    }

    private static TextDisplay display(boolean mythicLib, World world, double x, double z) {
        TextDisplay display = mock(TextDisplay.class);
        when(display.getLocation()).thenReturn(new Location(world, x, 70, z));
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(data.has(LeftoverHologramSweep.MYTHICLIB_HOLOGRAM)).thenReturn(mythicLib);
        when(display.getPersistentDataContainer()).thenReturn(data);
        return display;
    }

    @Test
    void removesOnlyMythicLibIndicatorTextDisplays() {
        TextDisplay leftover = display(true);
        TextDisplay otherPlugin = display(false);
        ArmorStand stand = mock(ArmorStand.class);
        List<Entity> entities = List.of(leftover, otherPlugin, stand);

        assertEquals(1, LeftoverHologramSweep.sweep(entities));
        verify(leftover).remove();
        verify(otherPlugin, never()).remove();
        verify(stand, never()).remove();
        verifyNoInteractions(stand);
    }

    @Test
    void logsOnceForAChunkWithLeftoversAndStaysQuietOtherwise() {
        Logger logger = mock(Logger.class);
        LeftoverHologramSweep sweep = new LeftoverHologramSweep(logger);
        World world = mock(World.class);
        when(world.getName()).thenReturn("TFMC_Map");
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(chunk.getX()).thenReturn(257);
        when(chunk.getZ()).thenReturn(158);

        sweep.onEntitiesLoad(new EntitiesLoadEvent(chunk, List.of(display(true), display(true), display(false))));
        verify(logger).info("Removed 2 leftover MythicLib indicator hologram(s) in TFMC_Map chunk 257,158.");

        sweep.onEntitiesLoad(new EntitiesLoadEvent(chunk, List.of(display(false))));
        verifyNoMoreInteractions(logger);
    }

    @Test
    void startupPassCoversEveryLoadedWorldAndLogsPerChunk() {
        Logger logger = mock(Logger.class);
        LeftoverHologramSweep sweep = new LeftoverHologramSweep(logger);
        World first = mock(World.class);
        when(first.getName()).thenReturn("TFMC_Map");
        World second = mock(World.class);
        TextDisplay leftover = display(true, first, 4126.5, 2533.5);
        TextDisplay sameChunk = display(true, first, 4120.2, 2530.9);
        TextDisplay westOfZero = display(true, first, -0.5, -17);
        TextDisplay kept = display(false, first, 4126.5, 2533.5);
        when(first.getEntitiesByClass(TextDisplay.class)).thenReturn(List.of(leftover, kept, westOfZero, sameChunk));
        when(second.getEntitiesByClass(TextDisplay.class)).thenReturn(List.of());
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(second));
            sweep.sweepLoaded();
            verifyNoInteractions(logger);

            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(first, second));
            sweep.sweepLoaded();
        }
        verify(leftover).remove();
        verify(sameChunk).remove();
        verify(westOfZero).remove();
        verify(kept, never()).remove();
        verify(logger).info("Removed 2 leftover MythicLib indicator hologram(s) in TFMC_Map chunk 257,158.");
        verify(logger).info("Removed 1 leftover MythicLib indicator hologram(s) in TFMC_Map chunk -1,-2.");
        verifyNoMoreInteractions(logger);
    }
}
