package net.tfminecraft.tlibs.armour.equipment;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Lists the entry names of a resource-pack zip from its central directory only.
 * ItemsAdder's unzip protection breaks the local headers and sizes, but the
 * central directory names stay readable, which is all this needs.
 */
public final class PackListing {
	private static final int END_SIGNATURE = 0x06054b50;
	private static final int ENTRY_SIGNATURE = 0x02014b50;
	private static final int END_SIZE = 22;
	private static final int MAX_COMMENT = 0xFFFF;

	private PackListing() {
	}

	public static Set<String> read(Path zip) throws IOException {
		try (RandomAccessFile file = new RandomAccessFile(zip.toFile(), "r")) {
			long length = file.length();
			int tailLength = (int) Math.min(length, END_SIZE + MAX_COMMENT);
			ByteBuffer tail = load(file, length - tailLength, tailLength);
			int end = findEnd(tail);
			if (end < 0) {
				throw new IOException("no end of central directory in " + zip);
			}
			long size = Integer.toUnsignedLong(tail.getInt(end + 12));
			long offset = Integer.toUnsignedLong(tail.getInt(end + 16));
			if (offset + size > length) {
				throw new IOException("central directory outside " + zip);
			}
			return names(load(file, offset, (int) size));
		}
	}

	private static ByteBuffer load(RandomAccessFile file, long position, int length) throws IOException {
		byte[] bytes = new byte[length];
		file.seek(position);
		file.readFully(bytes);
		return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
	}

	private static int findEnd(ByteBuffer tail) {
		for (int i = tail.capacity() - END_SIZE; i >= 0; i--) {
			if (tail.getInt(i) == END_SIGNATURE) {
				return i;
			}
		}
		return -1;
	}

	private static Set<String> names(ByteBuffer directory) throws IOException {
		Set<String> names = new HashSet<>();
		int position = 0;
		while (position < directory.capacity()) {
			if (position + 46 > directory.capacity() || directory.getInt(position) != ENTRY_SIGNATURE) {
				throw new IOException("broken central directory at byte " + position);
			}
			int nameLength = Short.toUnsignedInt(directory.getShort(position + 28));
			int extraLength = Short.toUnsignedInt(directory.getShort(position + 30));
			int commentLength = Short.toUnsignedInt(directory.getShort(position + 32));
			if (position + 46 + nameLength > directory.capacity()) {
				throw new IOException("broken central directory at byte " + position);
			}
			byte[] name = new byte[nameLength];
			directory.get(position + 46, name);
			names.add(new String(name, StandardCharsets.UTF_8));
			position += 46 + nameLength + extraLength + commentLength;
		}
		return names;
	}
}
