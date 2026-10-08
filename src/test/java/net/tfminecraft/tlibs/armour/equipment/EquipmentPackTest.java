package net.tfminecraft.tlibs.armour.equipment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import net.kyori.adventure.key.Key;

class EquipmentPackTest {
    @TempDir Path root;
    private final Logger logger = mock(Logger.class);

    private Path contents() { return root.resolve("ItemsAdder/contents"); }

    private void config(String pack, String file, String yaml) throws IOException {
        Path path = contents().resolve(pack).resolve("configs").resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, yaml);
    }

    private void texture(String pack, String namespace, String path, String content) throws IOException {
        Path file = contents().resolve(pack).resolve("resourcepack/assets").resolve(namespace).resolve("textures").resolve(path + ".png");
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test void renderingPathsAreSafeAndLayersMayNameANamespace() {
        ArmourRendering rendering = new ArmourRendering("Tfmc_Armor", "Bronze Set!", 1, "a/b", "other:c/d");
        assertEquals("tfmc_armor/bronze_set_", rendering.assetPath());
        assertArrayEquals(new String[] { "Tfmc_Armor", "a/b" }, rendering.texture("a/b"));
        assertArrayEquals(new String[] { "other", "c/d" }, rendering.texture("other:c/d"));
        assertEquals("ok-1.2_", ArmourRendering.clean("OK-1.2/"));
    }

    @Test void parsesColoursLikeItemsAdder() {
        assertEquals(0xb38e5d, ArmourRenderingScanner.parseColour(" #b38e5d "));
        assertEquals(255, ArmourRenderingScanner.parseColour("255"));
        assertNull(ArmourRenderingScanner.parseColour(null));
        assertNull(ArmourRenderingScanner.parseColour("#fff"));
        assertNull(ArmourRenderingScanner.parseColour("#gggggg"));
        assertNull(ArmourRenderingScanner.parseColour("-1"));
        assertNull(ArmourRenderingScanner.parseColour("16777216"));
    }

    @Test void scannerReadsEveryArmourRenderingAndSkipsBrokenOnes() throws IOException {
        config("tfmc_armor", "bronze.yml", """
            info:
              namespace: tfmc_armor
            armors_rendering:
              bronze:
                color: '#b38e5d'
                layer_1: armor_layers/bronze/bronze_layer_1
                layer_2: armor_layers/bronze/bronze_layer_2
              no_colour:
                layer_1: a
                layer_2: b
              no_layer_2:
                color: '#000001'
                layer_1: a
              scalar: nothing
            """);
        config("tfmc_armor", "items_only.yml", "info:\n  namespace: tfmc_armor\nitems: {}\n");
        config("broken", "no_namespace.yml", "armors_rendering:\n  x:\n    color: '#000002'\n");
        config("broken", "blank_namespace.yml", "info:\n  namespace: ' '\narmors_rendering: {}\n");
        Files.writeString(contents().resolve("broken/configs/notes.txt"), "ignored");
        Files.createDirectories(contents().resolve("tfmc_armor/configs/folder.yml"));
        Path outside = contents().resolve("tfmc_armor/elsewhere.yml");
        Files.writeString(outside, "armors_rendering:\n  y:\n    color: '#000003'\n");

        List<ArmourRendering> found = ArmourRenderingScanner.scan(contents(), logger);
        assertEquals(List.of(new ArmourRendering("tfmc_armor", "bronze", 0xb38e5d,
                "armor_layers/bronze/bronze_layer_1", "armor_layers/bronze/bronze_layer_2")), found);
        verify(logger, times(2)).warning(contains("has armors_rendering but no info.namespace"));
        verify(logger, times(3)).warning(contains("needs color, layer_1 and layer_2"));
    }

    @Test void scannerReportsMissingOrUnreadableFolders() {
        assertEquals(List.of(), ArmourRenderingScanner.scan(root.resolve("missing"), logger));
        verify(logger).warning(contains("contents folder not found"));
        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.walk(any(Path.class))).thenThrow(new IOException("denied"));
            assertEquals(List.of(), ArmourRenderingScanner.scan(root, logger));
        }
        verify(logger).warning(contains("denied"));
    }

    @Test void buildsModelsAndTexturesForEveryUniqueColour() throws IOException {
        texture("tfmc_armor", "tfmc_armor", "layers/bronze_1", "humanoid");
        texture("tfmc_armor", "tfmc_armor", "layers/bronze_2", "leggings");
        texture("shared", "shared", "dup", "x");
        List<ArmourRendering> renderings = List.of(
                new ArmourRendering("tfmc_armor", "bronze", 0xb38e5d, "layers/bronze_1", "layers/bronze_2"),
                new ArmourRendering("tfmc_armor", "missing", 0x000010, "layers/none", "layers/bronze_2"),
                new ArmourRendering("tfmc_armor", "dup_a", 0x000020, "shared:dup", "shared:dup"),
                new ArmourRendering("tfmc_armor", "dup_b", 0x000020, "shared:dup", "shared:dup"));
        EquipmentPack pack = EquipmentPack.build(contents(), "tfmc_equipment", renderings, logger);

        assertEquals(Map.of(0xb38e5d, Key.key("tfmc_equipment", "tfmc_armor/bronze")), pack.assets());
        assertEquals("{\"layers\":{\"humanoid\":[{\"texture\":\"tfmc_equipment:tfmc_armor/bronze\"}],"
                + "\"humanoid_leggings\":[{\"texture\":\"tfmc_equipment:tfmc_armor/bronze\"}]}}",
                new String(pack.entries().get("assets/tfmc_equipment/equipment/tfmc_armor/bronze.json"), StandardCharsets.UTF_8));
        assertEquals("humanoid", new String(pack.entries().get(
                "assets/tfmc_equipment/textures/entity/equipment/humanoid/tfmc_armor/bronze.png"), StandardCharsets.UTF_8));
        assertEquals("leggings", new String(pack.entries().get(
                "assets/tfmc_equipment/textures/entity/equipment/humanoid_leggings/tfmc_armor/bronze.png"), StandardCharsets.UTF_8));
        assertEquals(3, pack.entries().size());
        verify(logger).warning(contains("layer texture not found"));
        verify(logger, times(2)).warning(contains("another armour uses colour #000020"));

        Set<String> all = pack.entries().keySet();
        assertEquals(pack.assets(), pack.publishedIn(all));
        for (String missing : all) {
            Set<String> partial = new java.util.HashSet<>(all);
            partial.remove(missing);
            assertEquals(Map.of(), pack.publishedIn(partial));
        }
        assertEquals(Map.of(), EquipmentPack.EMPTY.publishedIn(all));
    }

    @Test void unreadableContentsMeanNoTexture() {
        List<ArmourRendering> renderings = List.of(new ArmourRendering("a", "b", 1, "c", "d"));
        assertEquals(Map.of(), EquipmentPack.build(root.resolve("absent"), "tfmc_equipment", renderings, logger).assets());
    }

    @Test void listsZipEntriesFromTheCentralDirectory() throws IOException {
        Path zip = root.resolve("pack.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.setComment("comment");
            for (String name : List.of("pack.mcmeta", "assets/a/b.json")) {
                ZipEntry entry = new ZipEntry(name);
                entry.setExtra(new byte[] { 1, 2, 3, 4 });
                entry.setComment("note");
                out.putNextEntry(entry);
                out.write(1);
                out.closeEntry();
            }
        }
        assertEquals(Set.of("pack.mcmeta", "assets/a/b.json"), PackListing.read(zip));
    }

    @Test void rejectsFilesThatAreNotZips() throws IOException {
        Path empty = root.resolve("empty.zip");
        Files.write(empty, new byte[0]);
        assertThrows(IOException.class, () -> PackListing.read(empty));
        Path text = root.resolve("text.zip");
        Files.writeString(text, "x".repeat(100));
        assertThrows(IOException.class, () -> PackListing.read(text));
        assertThrows(IOException.class, () -> PackListing.read(root.resolve("missing.zip")));
    }

    @Test void rejectsBrokenCentralDirectories() throws IOException {
        // Directory claims to extend past the end of the file.
        assertThrows(IOException.class, () -> PackListing.read(zipWithDirectory(new byte[0], 1000)));
        // Directory record without its signature.
        assertThrows(IOException.class, () -> PackListing.read(zipWithDirectory(new byte[46], 46)));
        // Record too short for its fixed header.
        assertThrows(IOException.class, () -> PackListing.read(zipWithDirectory(new byte[10], 10)));
        // Name length runs past the directory.
        ByteBuffer record = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN);
        record.putInt(0, 0x02014b50).putShort(28, (short) 50);
        assertThrows(IOException.class, () -> PackListing.read(zipWithDirectory(record.array(), 46)));
    }

    private Path zipWithDirectory(byte[] directory, int claimedSize) throws IOException {
        Path zip = Files.createTempFile(root, "broken", ".zip");
        ByteBuffer end = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
        end.putInt(0, 0x06054b50).putInt(12, claimedSize).putInt(16, 0);
        try (OutputStream out = Files.newOutputStream(zip)) {
            out.write(directory);
            out.write(end.array());
        }
        return zip;
    }
}
