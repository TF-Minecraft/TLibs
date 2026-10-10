package net.tfminecraft.tlibs.armour.equipment;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * ItemsAdder's legacy custom-armour atlas: every armour side by side in the leather
 * layer textures, picked by its core shader from the leather colour. The atlas grows
 * 64 px per armour, and GPUs that only take 16384 px textures (most Intel graphics)
 * can't load a pack once it is wider than that. With every armour on an equipment
 * asset the atlas is never drawn, so it can leave the pack.
 */
public final class LegacyArmourAtlas {
	/** Leather layer textures, at the pack root or in an overlay, for 1.21.2+ and older clients. */
	private static final Pattern LAYER = Pattern.compile("(?:[^/]+/)?assets/minecraft/textures/"
			+ "(?:entity/equipment/humanoid(?:_leggings)?/leather|models/armor/leather_layer_[12])\\.png");
	private static final byte[] SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n' };
	private static final int SLOT = 64;
	/** Rows up to the shader's (7, 7) signature pixel. */
	private static final int ROWS = 8;
	/** 16384 armour slots; anything wider is not an ItemsAdder atlas and is not decoded. */
	private static final int MAX_WIDTH = SLOT * 16384;
	/** Undyed leather; its slot draws plain leather, not a custom armour. */
	static final int VANILLA_LEATHER = 0xA06540;

	private LegacyArmourAtlas() {
	}

	/** The pack being built. */
	public interface Pack {
		Set<String> paths();

		byte[] read(String path);

		void remove(String path);
	}

	/** Atlas textures that were removed, and armour colours that kept them in the pack. */
	public record Result(List<String> removed, Set<Integer> uncovered) {
		public Result {
			removed = List.copyOf(removed);
			uncovered = Collections.unmodifiableSet(new TreeSet<>(uncovered));
		}
	}

	/**
	 * Removes every atlas once each armour in it has an equipment asset. If any armour
	 * would lose its look, every atlas stays, so nothing renders differently.
	 */
	public static Result removeCovered(Pack pack, Set<Integer> covered) {
		List<String> atlases = new ArrayList<>();
		Set<Integer> uncovered = new TreeSet<>();
		for (String path : new TreeSet<>(pack.paths())) {
			if (!LAYER.matcher(path).matches()) {
				continue;
			}
			Set<Integer> colours = colours(pack.read(path));
			if (colours == null) {
				continue;
			}
			atlases.add(path);
			for (int colour : colours) {
				if (!covered.contains(colour)) {
					uncovered.add(colour);
				}
			}
		}
		if (!uncovered.isEmpty()) {
			return new Result(List.of(), uncovered);
		}
		atlases.forEach(pack::remove);
		return new Result(atlases, Set.of());
	}

	/** The armour colours in an ItemsAdder atlas, or null for any other texture (e.g. plain leather). */
	static Set<Integer> colours(byte[] png) {
		int[][] rows = topRows(png);
		if (rows == null) {
			return null;
		}
		int width = rows[0].length;
		// The shader's own check: a white pixel at (0, 1) and a clear one at (7, 7).
		if (rows[1][0] != 0xFFFFFFFF || rows[7][7] != 0) {
			return null;
		}
		Set<Integer> colours = new TreeSet<>();
		for (int x = SLOT; x < width; x += SLOT) {
			int marker = rows[0][x];
			int rgb = marker & 0xFFFFFF;
			if (marker >>> 24 == 0xFF && rgb != VANILLA_LEATHER) {
				colours.add(rgb);
			}
		}
		return colours;
	}

	/**
	 * Decodes the first rows of an 8-bit RGBA atlas as ARGB, or returns null for anything
	 * else. ItemsAdder's protected PNGs carry a bad zlib checksum, so the image data is
	 * inflated raw and the checksum is never read.
	 */
	static int[][] topRows(byte[] png) {
		if (png == null || png.length < 33) {
			return null;
		}
		for (int i = 0; i < SIGNATURE.length; i++) {
			if (png[i] != SIGNATURE[i]) {
				return null;
			}
		}
		ByteBuffer buffer = ByteBuffer.wrap(png);
		int width = buffer.getInt(16);
		int height = buffer.getInt(20);
		boolean rgba = png[24] == 8 && png[25] == 6 && png[28] == 0;
		// An atlas is many 64 px slots wide; plain or HD leather layers are 2:1.
		if (buffer.getInt(8) != 13 || buffer.getInt(12) != 0x49484452 || !rgba || height < ROWS || width <= SLOT
				|| width > MAX_WIDTH || width % SLOT != 0
				|| width < 4L * height) {
			return null;
		}
		ByteArrayOutputStream data = new ByteArrayOutputStream();
		int offset = 33;
		while (offset + 8 <= png.length) {
			int length = buffer.getInt(offset);
			int type = buffer.getInt(offset + 4);
			if (length < 0 || offset + 12L + length > png.length) {
				return null;
			}
			if (type == 0x49444154) { // IDAT
				data.write(png, offset + 8, length);
			}
			offset += 12 + length;
		}
		int stride = 1 + width * 4;
		byte[] raw = inflate(data.toByteArray(), stride * ROWS);
		return raw == null ? null : unfilter(raw, width, stride);
	}

	private static byte[] inflate(byte[] zlib, int wanted) {
		if (zlib.length <= 2) {
			return null;
		}
		Inflater inflater = new Inflater(true);
		try {
			inflater.setInput(zlib, 2, zlib.length - 2);
			byte[] out = new byte[wanted];
			int filled = 0;
			while (filled < wanted) {
				int read = inflater.inflate(out, filled, wanted - filled);
				if (read == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) {
					return null;
				}
				filled += read;
			}
			return out;
		} catch (DataFormatException e) {
			return null;
		} finally {
			inflater.end();
		}
	}

	private static int[][] unfilter(byte[] raw, int width, int stride) {
		int[][] rows = new int[ROWS][width];
		byte[] previous = new byte[stride - 1];
		for (int y = 0; y < ROWS; y++) {
			int filter = raw[y * stride];
			byte[] row = new byte[stride - 1];
			System.arraycopy(raw, y * stride + 1, row, 0, row.length);
			for (int i = 0; i < row.length; i++) {
				int left = i >= 4 ? row[i - 4] & 0xFF : 0;
				int up = previous[i] & 0xFF;
				int upLeft = i >= 4 ? previous[i - 4] & 0xFF : 0;
				int predictor = switch (filter) {
				case 0 -> 0;
				case 1 -> left;
				case 2 -> up;
				case 3 -> (left + up) / 2;
				case 4 -> paeth(left, up, upLeft);
				default -> -1;
				};
				if (predictor < 0) {
					return null;
				}
				row[i] = (byte) (row[i] + predictor);
			}
			for (int x = 0; x < width; x++) {
				int r = row[x * 4] & 0xFF, g = row[x * 4 + 1] & 0xFF, b = row[x * 4 + 2] & 0xFF, a = row[x * 4 + 3] & 0xFF;
				rows[y][x] = a << 24 | r << 16 | g << 8 | b;
			}
			previous = row;
		}
		return rows;
	}

	private static int paeth(int left, int up, int upLeft) {
		int estimate = left + up - upLeft;
		int toLeft = Math.abs(estimate - left), toUp = Math.abs(estimate - up), toUpLeft = Math.abs(estimate - upLeft);
		if (toLeft <= toUp && toLeft <= toUpLeft) {
			return left;
		}
		return toUp <= toUpLeft ? up : upLeft;
	}
}
