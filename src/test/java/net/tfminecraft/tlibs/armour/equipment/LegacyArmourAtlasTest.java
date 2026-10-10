package net.tfminecraft.tlibs.armour.equipment;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;

import org.junit.jupiter.api.Test;

class LegacyArmourAtlasTest {
    private static final int CUSTOM = 0x123456;
    private static final int OTHER = 0x654321;
    private static final String HUMANOID = "ia_overlay_1_21_2_plus/assets/minecraft/textures/entity/equipment/humanoid/leather.png";
    private static final String LEGGINGS = "assets/minecraft/textures/entity/equipment/humanoid_leggings/leather.png";
    private static final String OLD_LAYER = "assets/minecraft/textures/models/armor/leather_layer_1.png";

    /** An atlas: a base slot, plain leather, the custom armours, then empty slots up to 4 slots. */
    private static int[][] atlas(int... colours) {
        int[][] pixels = new int[8][64 * Math.max(4, colours.length + 2)];
        pixels[1][0] = 0xFFFFFFFF;
        pixels[0][64] = 0xFF000000 | LegacyArmourAtlas.VANILLA_LEATHER;
        for (int i = 0; i < colours.length; i++) {
            pixels[0][128 + 64 * i] = 0xFF000000 | colours[i];
        }
        pixels[3][70] = 0x80FF00FF; // armour pixels in a slot
        return pixels;
    }

    /** Encodes ARGB rows as an RGBA PNG, one PNG filter type per row. */
    private static byte[] png(int width, int height, int[][] pixels, int[] filters, boolean protectedChecksum) {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        byte[] previous = new byte[width * 4];
        for (int y = 0; y < pixels.length; y++) {
            byte[] row = new byte[width * 4];
            for (int x = 0; x < width; x++) {
                int p = pixels[y][x];
                row[x * 4] = (byte) (p >> 16);
                row[x * 4 + 1] = (byte) (p >> 8);
                row[x * 4 + 2] = (byte) p;
                row[x * 4 + 3] = (byte) (p >>> 24);
            }
            int filter = filters[y % filters.length];
            raw.write(filter);
            for (int i = 0; i < row.length; i++) {
                int left = i >= 4 ? row[i - 4] & 0xFF : 0;
                int up = previous[i] & 0xFF;
                int upLeft = i >= 4 ? previous[i - 4] & 0xFF : 0;
                int predictor = switch (filter) {
                case 1 -> left;
                case 2 -> up;
                case 3 -> (left + up) / 2;
                case 4 -> paeth(left, up, upLeft);
                default -> 0;
                };
                raw.write((row[i] & 0xFF) - predictor);
            }
            previous = row;
        }
        byte[] zlib = deflate(raw.toByteArray());
        if (protectedChecksum) {
            // ItemsAdder's protected PNGs carry a wrong zlib checksum.
            zlib[zlib.length - 1] ^= 0x5A;
        }
        int half = zlib.length / 2;
        return file(ihdr(width, height, 8, 6), List.of(Arrays.copyOfRange(zlib, 0, half), Arrays.copyOfRange(zlib, half, zlib.length)));
    }

    private static int paeth(int left, int up, int upLeft) {
        int estimate = left + up - upLeft;
        int a = Math.abs(estimate - left), b = Math.abs(estimate - up), c = Math.abs(estimate - upLeft);
        return a <= b && a <= c ? left : b <= c ? up : upLeft;
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return out.toByteArray();
    }

    private static byte[] ihdr(int width, int height, int depth, int colourType) {
        return ByteBuffer.allocate(13).putInt(width).putInt(height).put((byte) depth).put((byte) colourType)
                .put((byte) 0).put((byte) 0).put((byte) 0).array();
    }

    private static byte[] file(byte[] header, List<byte[]> idat) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n' });
        chunk(out, "IHDR", header);
        for (byte[] data : idat) {
            chunk(out, "IDAT", data);
        }
        chunk(out, "iaPR", "IA".getBytes(StandardCharsets.US_ASCII));
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        out.writeBytes(ByteBuffer.allocate(8).putInt(data.length).put(type.getBytes(StandardCharsets.US_ASCII)).array());
        out.writeBytes(data);
        out.writeBytes(new byte[4]); // The decoder never reads CRCs.
    }

    static byte[] atlasPng(int... colours) {
        int[][] pixels = atlas(colours);
        return png(pixels[0].length, 64, pixels, new int[] { 0, 1, 2, 3, 4 }, true);
    }

    @Test void readsArmourColoursFromEveryFilterTypeDespiteTheProtectedChecksum() {
        assertEquals(Set.of(CUSTOM, OTHER), LegacyArmourAtlas.colours(atlasPng(CUSTOM, OTHER)));
        int[][] pixels = atlas(CUSTOM);
        for (int filter = 0; filter <= 4; filter++) {
            byte[] png = png(256, 64, pixels, new int[] { filter }, false);
            assertEquals(Set.of(CUSTOM), LegacyArmourAtlas.colours(png), "filter " + filter);
            assertArrayEquals(pixels[3], LegacyArmourAtlas.topRows(png)[3]);
        }
        // Clear marker slots are empty.
        pixels[0][128] = 0x00123456;
        assertEquals(Set.of(), LegacyArmourAtlas.colours(png(256, 64, pixels, new int[] { 0 }, false)));
    }

    @Test void plainOrHdLeatherAndOtherImagesAreNotAtlases() {
        int[][] plain = new int[8][64];
        plain[1][0] = 0xFFFFFFFF;
        assertNull(LegacyArmourAtlas.colours(png(64, 32, plain, new int[] { 0 }, false)));
        int[][] hd = new int[8][128];
        hd[1][0] = 0xFFFFFFFF;
        assertNull(LegacyArmourAtlas.colours(png(128, 64, hd, new int[] { 0 }, false)));
        int[][] odd = new int[8][200];
        assertNull(LegacyArmourAtlas.colours(png(200, 8, odd, new int[] { 0 }, false)));
        assertNull(LegacyArmourAtlas.colours(png(256, 4, new int[4][256], new int[] { 0 }, false)));

        int[][] noWhite = atlas(CUSTOM);
        noWhite[1][0] = 0xFFFFFFFE;
        assertNull(LegacyArmourAtlas.colours(png(256, 64, noWhite, new int[] { 0 }, false)));
        int[][] notClear = atlas(CUSTOM);
        notClear[7][7] = 0x01000000;
        assertNull(LegacyArmourAtlas.colours(png(256, 64, notClear, new int[] { 0 }, false)));
    }

    @Test void malformedFilesAreNotAtlases() {
        byte[] good = atlasPng(CUSTOM);
        assertNull(LegacyArmourAtlas.topRows(null));
        assertNull(LegacyArmourAtlas.topRows(Arrays.copyOf(good, 32)));
        byte[] signature = good.clone();
        signature[1] = 'X';
        assertNull(LegacyArmourAtlas.topRows(signature));
        byte[] headerLength = good.clone();
        headerLength[11] = 12;
        assertNull(LegacyArmourAtlas.topRows(headerLength));
        byte[] headerType = good.clone();
        headerType[12] = 'X';
        assertNull(LegacyArmourAtlas.topRows(headerType));
        assertNull(LegacyArmourAtlas.topRows(file(ihdr(256, 64, 16, 6), List.of(new byte[8]))));
        assertNull(LegacyArmourAtlas.topRows(file(ihdr(256, 64, 8, 2), List.of(new byte[8]))));
        byte[] interlaced = good.clone();
        interlaced[28] = 1;
        assertNull(LegacyArmourAtlas.topRows(interlaced));

        // A width whose buffer size would overflow is refused before anything is allocated.
        byte[] huge = good.clone();
        ByteBuffer.wrap(huge).putInt(16, 67_108_864);
        assertNull(LegacyArmourAtlas.topRows(huge));

        // Chunks that run past the end of the file, or claim a negative length.
        byte[] truncated = Arrays.copyOf(good, 60);
        assertNull(LegacyArmourAtlas.topRows(truncated));
        byte[] negative = good.clone();
        ByteBuffer.wrap(negative).putInt(33, -1);
        assertNull(LegacyArmourAtlas.topRows(negative));

        // Missing, short, corrupt and unfinished image data.
        assertNull(LegacyArmourAtlas.topRows(file(ihdr(256, 64, 8, 6), List.of())));
        assertNull(LegacyArmourAtlas.topRows(file(ihdr(256, 64, 8, 6), List.of(new byte[] { 0x78, 0x5e }))));
        assertNull(LegacyArmourAtlas.topRows(file(ihdr(256, 64, 8, 6), List.of(new byte[] { 0x78, 0x5e, (byte) 0xFF, (byte) 0xFF, 0, 0 }))));
        byte[] zlib = deflate(new byte[(1 + 256 * 4) * 3]);
        assertNull(LegacyArmourAtlas.topRows(file(ihdr(256, 64, 8, 6), List.of(zlib))));
        assertNull(LegacyArmourAtlas.topRows(file(ihdr(256, 64, 8, 6), List.of(Arrays.copyOf(zlib, 6)))));

        // An unknown PNG filter type.
        byte[] raw = new byte[(1 + 256 * 4) * 8];
        raw[0] = 5;
        assertNull(LegacyArmourAtlas.topRows(file(ihdr(256, 64, 8, 6), List.of(deflate(raw)))));
    }

    /** Pack entries; removed paths drop out of the listing. */
    private static final class FakePack implements LegacyArmourAtlas.Pack {
        final Map<String, byte[]> entries = new LinkedHashMap<>();
        final List<String> removed = new ArrayList<>();

        @Override public Set<String> paths() { return entries.keySet(); }
        @Override public byte[] read(String path) { return entries.get(path); }
        @Override public void remove(String path) { removed.add(path); entries.remove(path); }
    }

    private static FakePack pack() {
        FakePack pack = new FakePack();
        pack.entries.put(HUMANOID, atlasPng(CUSTOM, OTHER));
        pack.entries.put(LEGGINGS, atlasPng(CUSTOM, OTHER));
        pack.entries.put(OLD_LAYER, atlasPng(CUSTOM));
        int[][] plain = new int[8][64];
        pack.entries.put("assets/minecraft/textures/models/armor/leather_layer_2.png", png(64, 32, plain, new int[] { 0 }, false));
        pack.entries.put("assets/minecraft/textures/entity/equipment/humanoid/leather_overlay.png", atlasPng(0xABCDEF));
        pack.entries.put("assets/tfmc/textures/wide.png", atlasPng(0xABCDEF));
        return pack;
    }

    @Test void everyAtlasLeavesOnceAllItsArmoursHaveAssets() {
        FakePack pack = pack();
        LegacyArmourAtlas.Result result = LegacyArmourAtlas.removeCovered(pack, Set.of(CUSTOM, OTHER, 0x111111));
        assertEquals(List.of(LEGGINGS, OLD_LAYER, HUMANOID), result.removed());
        assertEquals(result.removed(), pack.removed);
        assertTrue(result.uncovered().isEmpty());
        assertEquals(3, pack.entries.size());
    }

    @Test void anArmourWithoutAnAssetKeepsEveryAtlas() {
        FakePack pack = pack();
        LegacyArmourAtlas.Result result = LegacyArmourAtlas.removeCovered(pack, Set.of(CUSTOM));
        assertEquals(List.of(), result.removed());
        assertEquals(Set.of(OTHER), result.uncovered());
        assertTrue(pack.removed.isEmpty());
    }

    @Test void packsWithoutAnAtlasAreLeftAlone() {
        FakePack pack = new FakePack();
        pack.entries.put("assets/minecraft/textures/models/armor/leather_layer_2.png", new byte[] { 1, 2, 3 });
        LegacyArmourAtlas.Result result = LegacyArmourAtlas.removeCovered(pack, Set.of());
        assertEquals(List.of(), result.removed());
        assertEquals(Set.of(), result.uncovered());
    }
}
