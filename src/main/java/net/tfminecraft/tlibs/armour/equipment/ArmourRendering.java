package net.tfminecraft.tlibs.armour.equipment;

import java.util.Locale;

/**
 * One ItemsAdder {@code armors_rendering} entry: the leather colour that marks the
 * armour and the two layer textures the legacy shader draws for it.
 */
public record ArmourRendering(String namespace, String name, int rgb, String layer1, String layer2) {

	/** Path of the equipment asset, unique across ItemsAdder namespaces. */
	public String assetPath() {
		return clean(namespace) + "/" + clean(name);
	}

	/** Resolves a layer reference ({@code path} or {@code namespace:path}) to its texture namespace and path. */
	public String[] texture(String layer) {
		int colon = layer.indexOf(':');
		if (colon < 0) {
			return new String[] { namespace, layer };
		}
		return new String[] { layer.substring(0, colon), layer.substring(colon + 1) };
	}

	static String clean(String value) {
		String lower = value.toLowerCase(Locale.ROOT);
		StringBuilder out = new StringBuilder(lower.length());
		for (char c : lower.toCharArray()) {
			boolean valid = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.';
			out.append(valid ? c : '_');
		}
		return out.toString();
	}
}
