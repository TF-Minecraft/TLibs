package net.tfminecraft.tlibs.objects.api.subapi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.md_5.bungee.api.ChatColor;

public class StringFormatter {
	
	public static String formatHex(String s) {
		if (s == null || s.isEmpty()) {
			return "";
		}
		// Legacy & codes → section sign (ItemCreator / kit lore / names all go through here)
		String formatted = org.bukkit.ChatColor.translateAlternateColorCodes('&', s);
		StringBuilder out = new StringBuilder(formatted.length() + 16);
		for (int i = 0; i < formatted.length(); i++) {
			char bit = formatted.charAt(i);
			if (bit != '#') {
				out.append(bit);
				continue;
			}
			if (i + 6 >= formatted.length()) {
				out.append(bit);
				continue;
			}
			String code = formatted.substring(i, i + 7);
			if (!code.substring(1).matches("[0-9a-fA-F]{6}")) {
				out.append(bit);
				continue;
			}
			try {
				out.append(ChatColor.of(code));
				i += 6;
			} catch (Exception e) {
				out.append(bit);
			}
		}
		return out.toString();
	}

	/**
	 * Applies solid or gradient colour to plain text.
	 *
	 * @param text visible text without colour codes
	 * @param hexCodes one or more hex strings ({@code #RRGGBB} or {@code RRGGBB}); null/empty returns text unchanged
	 * @return legacy chat string with colour applied per character (gradient) or once (solid)
	 */
	public static String applyColourGradient(String text, List<String> hexCodes) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		String plain = ChatColor.stripColor(text);
		if (plain.isEmpty()) {
			return "";
		}
		if (hexCodes == null || hexCodes.isEmpty()) {
			return plain;
		}

		List<String> normalized = new ArrayList<>();
		for (String code : hexCodes) {
			String hex = normalizeHex(code);
			if (hex != null) {
				normalized.add(hex);
			}
		}
		if (normalized.isEmpty()) {
			return plain;
		}
		if (normalized.size() == 1) {
			return ChatColor.of(normalized.get(0)) + plain;
		}

		StringBuilder out = new StringBuilder(plain.length() * 8);
		int length = plain.length();
		int stops = normalized.size();
		for (int i = 0; i < length; i++) {
			double t = length == 1 ? 0.0 : (double) i / (length - 1);
			int segment = (int) Math.floor(t * (stops - 1));
			if (segment >= stops - 1) {
				segment = stops - 2;
			}
			double localT = t * (stops - 1) - segment;
			int[] rgb = lerpRgb(parseRgb(normalized.get(segment)), parseRgb(normalized.get(segment + 1)), localT);
			out.append(ChatColor.of(rgbToHex(rgb)));
			out.append(plain.charAt(i));
		}
		return out.toString();
	}

	private static String normalizeHex(String code) {
		if (code == null) {
			return null;
		}
		String trimmed = code.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		// Legacy §c / &c → fixed hex
		if (trimmed.length() == 2
			&& (trimmed.charAt(0) == '\u00A7' || trimmed.charAt(0) == '&')) {
			String mapped = legacyColourToHex(trimmed.charAt(1));
			if (mapped != null) {
				return mapped;
			}
		}
		if (trimmed.length() == 1) {
			String mapped = legacyColourToHex(trimmed.charAt(0));
			if (mapped != null) {
				return mapped;
			}
		}
		if (!trimmed.startsWith("#")) {
			trimmed = "#" + trimmed;
		}
		if (!trimmed.matches("^#[0-9a-fA-F]{6}$")) {
			return null;
		}
		return trimmed.toLowerCase();
	}

	/** Maps legacy colour code char to {@code #RRGGBB}, or null if unknown. */
	public static String legacyColourToHex(char code) {
		switch (Character.toLowerCase(code)) {
			case '0': return "#000000";
			case '1': return "#0000aa";
			case '2': return "#00aa00";
			case '3': return "#00aaaa";
			case '4': return "#aa0000";
			case '5': return "#aa00aa";
			case '6': return "#ffaa00";
			case '7': return "#aaaaaa";
			case '8': return "#555555";
			case '9': return "#5555ff";
			case 'a': return "#55ff55";
			case 'b': return "#55ffff";
			case 'c': return "#ff5555";
			case 'd': return "#ff55ff";
			case 'e': return "#ffff55";
			case 'f': return "#ffffff";
			default: return null;
		}
	}

	/**
	 * Normalize a colour token ({@code #RRGGBB}, {@code §c}, {@code &c}) to hex, or null.
	 */
	public static String normalizeColourToken(String token) {
		return normalizeHex(token);
	}

	/**
	 * Apply optional style codes (bold/italic/underline/strikethrough) before coloured text.
	 */
	public static String applyNameStyles(String colouredText, List<String> styles) {
		if (colouredText == null) {
			return "";
		}
		if (styles == null || styles.isEmpty()) {
			return colouredText;
		}
		StringBuilder prefix = new StringBuilder();
		for (String style : styles) {
			if (style == null) {
				continue;
			}
			switch (style.trim().toLowerCase()) {
				case "bold":
					prefix.append(ChatColor.BOLD);
					break;
				case "italic":
					prefix.append(ChatColor.ITALIC);
					break;
				case "underline":
				case "underlined":
					prefix.append(ChatColor.UNDERLINE);
					break;
				case "strikethrough":
				case "strike":
					prefix.append(ChatColor.STRIKETHROUGH);
					break;
				default:
					break;
			}
		}
		return prefix + colouredText;
	}

	/**
	 * Format plain name with colour stop(s) and optional styles.
	 */
	public static String formatDisplayName(String plainName, List<String> colourTokens, List<String> styles) {
		// Resolve & / # in the raw name first (same path as ItemCreator lore/names).
		String plain = formatHex(plainName == null ? "" : plainName);
		List<String> hexes = new ArrayList<>();
		if (colourTokens != null) {
			for (String token : colourTokens) {
				String hex = normalizeColourToken(token);
				if (hex != null) {
					hexes.add(hex);
				}
			}
		}
		String coloured = hexes.isEmpty() ? plain : applyColourGradient(plain, hexes);
		return applyNameStyles(coloured, styles);
	}

	private static int[] parseRgb(String hex) {
		int value = Integer.parseInt(hex.substring(1), 16);
		return new int[] {
				(value >> 16) & 0xFF,
				(value >> 8) & 0xFF,
				value & 0xFF
		};
	}

	private static int[] lerpRgb(int[] from, int[] to, double t) {
		double clamped = Math.max(0.0, Math.min(1.0, t));
		return new int[] {
				(int) Math.round(from[0] + (to[0] - from[0]) * clamped),
				(int) Math.round(from[1] + (to[1] - from[1]) * clamped),
				(int) Math.round(from[2] + (to[2] - from[2]) * clamped)
		};
	}

	private static String rgbToHex(int[] rgb) {
		return String.format("#%02x%02x%02x", rgb[0], rgb[1], rgb[2]);
	}

	public static String clean(String s) {
		StringBuilder cleaned = new StringBuilder();
		int i = 0;
		while (i < s.length()) {
			char ch = s.charAt(i);
			if (ch == '#' && i + 6 < s.length()) {
				// Skip the color code (7 characters total: '#' + 6 hex digits)
				String potentialHex = s.substring(i + 1, i + 7);
				if (potentialHex.matches("[0-9a-fA-F]{6}")) {
					i += 7;
					continue;
				}
			}
			cleaned.append(ch);
			i++;
		}
		return cleaned.toString();
	}

	public static String getName(ItemStack i) {
		ItemMeta m = i.getItemMeta();
		if(m == null) return "none";
		if(m.hasDisplayName()) return m.getDisplayName();
		return getVanillaName(i.getType());
	}

	public static String getVanillaName(Material material) {
		String[] words = material.name().toLowerCase().split("_");
		return Arrays.stream(words)
					.map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
					.collect(Collectors.joining(" "));
	}

	public static String extractHexColor(String s) {
		if (s == null) return null;

		// --- Adventure hex format (#RRGGBB) ---
		int hashIndex = s.indexOf('#');
		if (hashIndex != -1 && hashIndex + 7 <= s.length()) {
			String hex = s.substring(hashIndex, hashIndex + 7);
			if (hex.matches("^#[0-9a-fA-F]{6}$"))
				return hex;
		}

		// --- Vanilla RGB formatting: §x§R§R§G§G§B§B ---
		// We search for "§x" + 12 characters (6 hex digits, each prefixed by §)
		int x = s.indexOf("§x");
		if (x != -1 && x + 14 <= s.length()) {
			StringBuilder hex = new StringBuilder("#");
			for (int i = x + 2; i < x + 14; i += 2) {
				char c = s.charAt(i + 1);
				if (!Character.toString(c).matches("[0-9a-fA-F]")) return null;
				hex.append(c);
			}
			return hex.toString();
		}

		return null; // No hex color found
	}
}
