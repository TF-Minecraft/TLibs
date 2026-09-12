package me.Plugins.TLibs.Utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TimeFormatterTest {
	@Test
	void parseSeconds_bareInteger() {
		assertEquals(120, TimeFormatter.parseSeconds("120"));
		assertEquals(10, TimeFormatter.parseSeconds(" 10 "));
	}

	@Test
	void parseSeconds_singleUnits() {
		assertEquals(3 * 86400, TimeFormatter.parseSeconds("3d"));
		assertEquals(4 * 3600, TimeFormatter.parseSeconds("4H"));
		assertEquals(6, TimeFormatter.parseSeconds("6s"));
		assertEquals(7 * 86400, TimeFormatter.parseSeconds("1w"));
	}

	@Test
	void parseSeconds_mixedAndConcatenated() {
		assertEquals(4 * 3600 + 6, TimeFormatter.parseSeconds("4h 6s"));
		assertEquals(4 * 3600 + 6, TimeFormatter.parseSeconds("4h6s"));
		assertEquals(604800 + 3 * 86400 + 13 * 3600 + 20 * 60 + 43,
				TimeFormatter.parseSeconds("1w 3d 13h 20m 43s"));
	}

	@Test
	void parseSeconds_rejectsInvalid() {
		assertThrows(IllegalArgumentException.class, () -> TimeFormatter.parseSeconds(""));
		assertThrows(IllegalArgumentException.class, () -> TimeFormatter.parseSeconds("3d extra"));
		assertThrows(IllegalArgumentException.class, () -> TimeFormatter.parseSeconds("abc"));
	}
}
