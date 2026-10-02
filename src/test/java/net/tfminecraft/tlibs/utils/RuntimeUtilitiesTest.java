package net.tfminecraft.tlibs.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.md_5.bungee.api.ChatColor;
import net.tfminecraft.tlibs.enums.APIType;
import net.tfminecraft.tlibs.enums.NSEW;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import net.tfminecraft.tlibs.objects.utils.IntCounter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

class RuntimeUtilitiesTest {
    @Test
    void legacyAndHexFormattingPreservesOrdinaryAndMalformedText() {
        assertEquals("", StringFormatter.formatHex(null));
        assertEquals("", StringFormatter.formatHex(""));
        assertEquals("#12", StringFormatter.formatHex("#12"));
        assertEquals("§cRed " + ChatColor.of("#12ABef") + "Hex #12 #zzzzzz",
                StringFormatter.formatHex("&cRed #12ABefHex #12 #zzzzzz"));
        assertEquals("abc #12 #zzzzzz", StringFormatter.clean("a#123456bc #12 #zzzzzz"));
        assertEquals("", StringFormatter.clean(""));
    }

    @Test
    void gradientsInterpolateThroughAllStopsAndStripExistingColors() {
        assertEquals("", StringFormatter.applyColourGradient(null, List.of("red")));
        assertEquals("", StringFormatter.applyColourGradient("", List.of("red")));
        assertEquals("", StringFormatter.applyColourGradient("§c", List.of("red")));
        assertEquals("Name", StringFormatter.applyColourGradient("§cName", null));
        assertEquals("Name", StringFormatter.applyColourGradient("Name", List.of()));
        assertEquals("Name", StringFormatter.applyColourGradient("Name", Arrays.asList(null, " ", "invalid")));
        assertEquals(ChatColor.of("#aabbcc") + "Name", StringFormatter.applyColourGradient("§cName", List.of(" AABBCC ")));
        assertEquals(ChatColor.of("#000000") + "a" + ChatColor.of("#808080") + "b" + ChatColor.of("#ffffff") + "c",
                StringFormatter.applyColourGradient("abc", List.of("000000", "ffffff")));
        assertEquals(ChatColor.of("#ff0000") + "a" + ChatColor.of("#00ff00") + "b" + ChatColor.of("#0000ff") + "c",
                StringFormatter.applyColourGradient("abc", List.of("ff0000", "00ff00", "0000ff")));
        assertEquals(ChatColor.of("#ff0000") + "x", StringFormatter.applyColourGradient("x", List.of("ff0000", "0000ff")));
    }

    @Test
    void allLegacyColorsNormalizeAndInvalidTokensAreRejected() {
        String[] colors = {"000000", "0000aa", "00aa00", "00aaaa", "aa0000", "aa00aa", "ffaa00", "aaaaaa",
                "555555", "5555ff", "55ff55", "55ffff", "ff5555", "ff55ff", "ffff55", "ffffff"};
        String codes = "0123456789abcdef";
        for (int i = 0; i < codes.length(); i++) {
            assertEquals("#" + colors[i], StringFormatter.legacyColourToHex(codes.charAt(i)));
            assertEquals("#" + colors[i], StringFormatter.normalizeColourToken("&" + codes.charAt(i)));
            assertEquals("#" + colors[i], StringFormatter.normalizeColourToken("§" + codes.charAt(i)));
            assertEquals("#" + colors[i], StringFormatter.normalizeColourToken("" + Character.toUpperCase(codes.charAt(i))));
        }
        for (String invalid : Arrays.asList(null, "", " ", "&z", "§x", "z", "#12345", "gghhii")) {
            assertNull(StringFormatter.normalizeColourToken(invalid));
        }
        assertNull(StringFormatter.legacyColourToHex('z'));
        assertEquals("#aabbcc", StringFormatter.normalizeColourToken(" #AABBCC "));
    }

    @Test
    void stylesAndDisplayNamesComposeSupportedTokens() {
        assertEquals("", StringFormatter.applyNameStyles(null, List.of("bold")));
        assertEquals("Name", StringFormatter.applyNameStyles("Name", null));
        assertEquals("Name", StringFormatter.applyNameStyles("Name", List.of()));
        assertEquals("§l§o§n§n§m§mName", StringFormatter.applyNameStyles("Name",
                Arrays.asList(" bold ", "italic", "underline", "underlined", "strikethrough", "strike", "ignored", null)));
        assertEquals("", StringFormatter.formatDisplayName(null, null, null));
        assertEquals("§cName", StringFormatter.formatDisplayName("&cName", List.of("invalid"), List.of()));
        assertEquals("§l" + ChatColor.of("#ff5555") + "Name", StringFormatter.formatDisplayName("&aName", List.of("c"), List.of("bold")));
    }

    @Test
    void namesUseDisplayNameThenReadableMaterialName() {
        ItemStack item = mock(ItemStack.class);
        assertEquals("none", StringFormatter.getName(item));
        ItemMeta meta = mock(ItemMeta.class);
        when(item.getItemMeta()).thenReturn(meta);
        when(item.getType()).thenReturn(Material.DIAMOND_CHESTPLATE);
        assertEquals("Diamond Chestplate", StringFormatter.getName(item));
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn("§cNamed");
        assertEquals("§cNamed", StringFormatter.getName(item));
    }

    @Test
    void extractsHexFromHashOrLegacyRepresentation() {
        assertNull(StringFormatter.extractHexColor(null));
        assertNull(StringFormatter.extractHexColor("plain"));
        assertNull(StringFormatter.extractHexColor("#123"));
        assertNull(StringFormatter.extractHexColor("#xyzxyz"));
        assertNull(StringFormatter.extractHexColor("§x§1"));
        assertNull(StringFormatter.extractHexColor("§x§1§2§3§4§5§g"));
        assertEquals("#12ABef", StringFormatter.extractHexColor("prefix #12ABef text"));
        assertEquals("#12ABef", StringFormatter.extractHexColor("prefix §x§1§2§A§B§e§f text"));
    }

    @Test
    void utilitiesIgnoreTheHostLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertAll(
                    () -> assertEquals("§oName", StringFormatter.applyNameStyles("Name", List.of("ITALIC"))),
                    () -> assertEquals("Diamond Chestplate", StringFormatter.getVanillaName(Material.DIAMOND_CHESTPLATE)),
                    () -> {
                        List<String> choices = new ArrayList<>(List.of("IRON", "item", "stone"));
                        TabCleaner.filterPrefix(choices, "i");
                        assertEquals(List.of("IRON", "item"), choices);
                    });
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void durationsParseValidUnitsAndFormatWithoutZeroComponents() {
        assertEquals(2000, TimeFormatter.StringTimeToMillis("2s"));
        assertEquals(120000, TimeFormatter.StringTimeToMillis("2m"));
        assertEquals(7200000, TimeFormatter.StringTimeToMillis("2h"));
        assertEquals(172800000, TimeFormatter.StringTimeToMillis("2d"));
        assertEquals(0, TimeFormatter.StringTimeToMillis("0mo"));
        assertThrows(IllegalArgumentException.class, () -> TimeFormatter.StringTimeToMillis("2w"));
        assertEquals(0, TimeFormatter.parseSeconds("0"));
        assertEquals(Integer.MAX_VALUE, TimeFormatter.parseSeconds("2147483647"));
        assertEquals(694861, TimeFormatter.parseSeconds("1W 1D 1H 1M 1S"));
        assertEquals(0, TimeFormatter.parseSeconds("0s "));
        for (String invalid : Arrays.asList(null, "", " ", "no duration", "1h nope", "bad1h", "1h!2s", "2147483648", "2147483648s")) {
            assertThrows(IllegalArgumentException.class, () -> TimeFormatter.parseSeconds(invalid), String.valueOf(invalid));
        }
        assertEquals("1d 1h 1m 1s", TimeFormatter.formatTime(90061));
        assertEquals("1d", TimeFormatter.formatTime(86400));
        assertEquals("1h", TimeFormatter.formatTime(3600));
        assertEquals("1m", TimeFormatter.formatTime(60));
        assertEquals("0s", TimeFormatter.formatTime(0));
        assertEquals("5s", TimeFormatter.formatTime(5));
    }

    @Test
    void millisecondDurationsRejectOverflowInsteadOfBecomingNegative() {
        for (String overflow : List.of("1mo", "25d", "597h", "35792m", "2147484s")) {
            assertThrows(IllegalArgumentException.class, () -> TimeFormatter.StringTimeToMillis(overflow), overflow);
        }
    }

    @Test
    void parsersAndCountersRetainTheirPublicContracts() {
        assertEquals(1.25, ParseUtils.parseDouble("1.25"));
        assertEquals(0.0, ParseUtils.parseDouble("bad"));
        assertEquals(42, ParseUtils.parseInt("42"));
        assertEquals(0, ParseUtils.parseInt("bad"));
        assertFalse(ParseUtils.isPositive((Double) null));
        assertFalse(ParseUtils.isPositive(-1.0));
        assertTrue(ParseUtils.isPositive(0.5));
        assertFalse(ParseUtils.isPositive((Integer) null));
        assertFalse(ParseUtils.isPositive(0));
        assertTrue(ParseUtils.isPositive(1));
        IntCounter counter = new IntCounter();
        assertEquals(0, counter.getCurrent());
        assertEquals(0, counter.getNeeded());
        assertTrue(counter.isEqual());
        assertEquals(0, counter.getPercentage());
        counter.setCurrent(1);
        counter.setNeeded(3);
        assertFalse(counter.isEqual());
        assertEquals(33, counter.getPercentage());
        counter.increaseCurrent(2);
        counter.increaseNeeded(0);
        assertTrue(counter.isEqual());
        assertEquals(100, counter.getPercentage());
        assertEquals(8, NSEW.values().length);
        assertSame(NSEW.SOUTH_WEST, NSEW.valueOf("SOUTH_WEST"));
        assertArrayEquals(new APIType[]{APIType.ITEM_API, APIType.BLOCK_API}, APIType.values());
        assertNotNull(new ParseUtils());
        assertNotNull(new TimeFormatter());
        assertNotNull(new TabCleaner());
        assertNotNull(new StringFormatter());
    }

    @Test
    void tabCleanupHandlesMissingArgumentsAndNullSuggestions() {
        List<String> options = new ArrayList<>(Arrays.asList("Iron", null, "ice", "stone"));
        TabCleaner.cleanTab(options, null);
        TabCleaner.cleanTab(options, new String[0]);
        TabCleaner.cleanTab(null, new String[]{"i"}, 0);
        TabCleaner.cleanTab(options, null, 0);
        TabCleaner.cleanTab(options, new String[]{"i"}, -1);
        TabCleaner.cleanTab(options, new String[]{"i"}, 1);
        TabCleaner.filterPrefix(null, "i");
        TabCleaner.filterPrefix(options, null);
        TabCleaner.filterPrefix(options, "");
        assertEquals(4, options.size());
        TabCleaner.cleanTab(options, new String[]{"i"});
        assertEquals(List.of("Iron", "ice"), options);
    }
}
