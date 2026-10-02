package net.tfminecraft.tlibs.objects.api.subapi;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Locale;
import dev.lone.itemsadder.api.CustomBlock;
import dev.lone.itemsadder.api.CustomFurniture;
import net.tfminecraft.tlibs.objects.api.BlockAPI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class BlockPathsTest {
    private BlockChecker checker;
    private PluginManager plugins;

    @BeforeEach
    void initializeApi() {
        Server server = mock(Server.class);
        plugins = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(plugins);
        BlockAPI api = new BlockAPI();
        api.setup(server);
        assertSame(server, api.getServer());
        assertFalse(api.getPluginChecker().checkPlugin("ItemsAdder"));
        checker = api.getChecker();
    }

    private void enableItemsAdder() { when(plugins.getPlugin("ItemsAdder")).thenReturn(mock(Plugin.class)); }
    private static Block block(Material type) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(type);
        return block;
    }

    @Test
    void vanillaBlockPathsAcceptBareDottedAndParenthesizedForms() {
        Block stone = block(Material.STONE);
        for (String path : List.of("stone", " STONE ", "v.stone", "V( stone )")) {
            assertTrue(checker.checkBlock(stone, path), path);
        }
        assertFalse(checker.checkBlock(stone, "v.dirt"));
        assertFalse(checker.checkBlock(stone, "unknown(stone)"));
        assertFalse(checker.checkBlock(stone, "unknown.stone"));
        assertFalse(checker.checkBlock(stone, "v."));
        assertFalse(checker.checkBlock(stone, "v( )"));
        assertFalse(checker.checkBlock(stone, "invalid"));
        assertFalse(checker.checkBlock(stone, "v(stone"));
        assertFalse(checker.checkBlock(stone, ""));
        assertFalse(checker.checkBlock(stone, null));
        assertFalse(checker.checkBlock(null, "v.stone"));
    }

    @Test
    void materialLookupIgnoresTheHostLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertTrue(checker.checkBlock(block(Material.DIAMOND_BLOCK), "v.diamond_block"));
        } finally { Locale.setDefault(previous); }
    }

    @Test
    void missingItemsAdderRejectsCustomBlocksAndFurniture() {
        // These branches log through Bukkit, whose static logger exists without a loaded server.
        try (MockedStatic<org.bukkit.Bukkit> bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(org.bukkit.Bukkit::getLogger).thenReturn(java.util.logging.Logger.getLogger("BlockPathsTest"));
            assertFalse(checker.checkBlock(block(Material.BARRIER), "iaf.tfmc:chair"));
            assertFalse(checker.checkBlock(block(Material.STONE), "ia.tfmc:stone"));
            assertFalse(checker.checkBlock(block(Material.STONE), "iaf.tfmc:chair"));
        }
    }

    @Test
    void customBlocksRecognizeNamespaceAndAllSupportedPrefixes() {
        enableItemsAdder();
        Block placed = block(Material.STONE);
        CustomBlock custom = mock(CustomBlock.class);
        when(custom.getNamespace()).thenReturn("tfmc");
        when(custom.getId()).thenReturn("stone");
        try (MockedStatic<CustomBlock> blocks = mockStatic(CustomBlock.class)) {
            blocks.when(() -> CustomBlock.byAlreadyPlaced(placed)).thenReturn(custom);
            for (String path : List.of("tfmc:stone", "ia.tfmc:stone", "iab(TFMC:STONE)")) {
                assertTrue(checker.checkBlock(placed, path), path);
            }
            assertFalse(checker.checkBlock(placed, "ia.tfmc:other"));
            assertFalse(checker.checkBlock(block(Material.DIRT), "iab.tfmc:stone"));
        }
    }

    @Test
    void furnitureFindsMatchingFrameBelowTallBarrierHitboxes() {
        enableItemsAdder();
        World world = mock(World.class);
        Block top = block(Material.BARRIER);
        Block bottom = block(Material.BARRIER);
        Block floor = block(Material.STONE);
        when(top.getWorld()).thenReturn(world);
        when(bottom.getWorld()).thenReturn(world);
        when(top.getLocation()).thenReturn(new Location(world, 2, 11, 4));
        when(bottom.getLocation()).thenReturn(new Location(world, 2, 10, 4));
        when(top.getRelative(0, -1, 0)).thenReturn(bottom);
        when(bottom.getRelative(0, -1, 0)).thenReturn(floor);
        ItemFrame unrelated = mock(ItemFrame.class);
        ItemFrame notFurniture = mock(ItemFrame.class);
        ItemFrame matching = mock(ItemFrame.class);
        CustomFurniture other = mock(CustomFurniture.class);
        when(other.getNamespace()).thenReturn("tfmc");
        when(other.getId()).thenReturn("other");
        CustomFurniture wanted = mock(CustomFurniture.class);
        when(wanted.getNamespace()).thenReturn("tfmc");
        when(wanted.getId()).thenReturn("chair");
        when(world.getNearbyEntities(any(Location.class), eq(0.4), eq(0.4), eq(0.4))).thenAnswer(call -> {
            Location at = call.getArgument(0);
            assertEquals(2.5, at.getX());
            assertEquals(4.5, at.getZ());
            return at.getY() == 11 ? List.of(mock(Entity.class), notFurniture, unrelated) : List.of(matching);
        });
        try (MockedStatic<CustomFurniture> furniture = mockStatic(CustomFurniture.class)) {
            furniture.when(() -> CustomFurniture.byAlreadySpawned(unrelated)).thenReturn(other);
            furniture.when(() -> CustomFurniture.byAlreadySpawned(matching)).thenReturn(wanted);
            assertTrue(checker.checkBlock(top, "iaf.TFMC:CHAIR"));
            assertFalse(checker.checkBlock(top, "iaf.tfmc:missing"));
        }
    }

    @Test
    void furnitureSearchStopsAfterFourBarrierBlocks() {
        enableItemsAdder();
        World world = mock(World.class);
        Block[] column = new Block[5];
        for (int i = 0; i < column.length; i++) {
            column[i] = block(Material.BARRIER);
            when(column[i].getWorld()).thenReturn(world);
            when(column[i].getLocation()).thenReturn(new Location(world, 0, 20 - i, 0));
            if (i > 0) when(column[i - 1].getRelative(0, -1, 0)).thenReturn(column[i]);
        }
        when(world.getNearbyEntities(any(Location.class), eq(0.4), eq(0.4), eq(0.4))).thenReturn(List.of());
        assertFalse(checker.checkBlock(column[0], "iaf.tfmc:chair"));
        verify(world, times(4)).getNearbyEntities(any(Location.class), eq(0.4), eq(0.4), eq(0.4));
        verify(column[4], never()).getWorld();
    }
}
