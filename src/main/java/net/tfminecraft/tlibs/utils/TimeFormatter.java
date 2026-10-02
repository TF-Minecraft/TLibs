package net.tfminecraft.tlibs.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeFormatter {
	private static final Pattern BARE_SECONDS = Pattern.compile("\\d+");
	private static final Pattern DURATION_TOKEN = Pattern.compile("(\\d+)\\s*(w|d|h|m|s)", Pattern.CASE_INSENSITIVE);

	public static int StringTimeToMillis(String time) {
		Pattern pattern = Pattern.compile("(\\d+)([smhd]|mo)");
		Matcher matcher = pattern.matcher(time);

		if (!matcher.matches()) {
			throw new IllegalArgumentException("Invalid time format: " + time);
		}

		int value = Integer.parseInt(matcher.group(1));
		String unit = matcher.group(2);

		// The regex above admits only these units; long multiplication prevents wrapping.
		long multiplier = switch (unit) {
			case "s" -> 1000L;
			case "m" -> 60000L;
			case "h" -> 3600000L;
			case "d" -> 86400000L;
			default -> 2592000000L; // mo
		};
		long millis = value * multiplier;
		if (millis > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Time too large: " + time);
		}
		return (int) millis;
	}

	/**
	 * Parses durations such as {@code 3d}, {@code 4h 6s}, {@code 1w 3d 13h 20m 43s},
	 * concatenated tokens like {@code 4h6s}, or a bare integer as seconds.
	 */
	public static int parseSeconds(String time) {
		if (time == null || time.isBlank()) {
			throw new IllegalArgumentException("Invalid time format: " + time);
		}
		String trimmed = time.trim();
		if (BARE_SECONDS.matcher(trimmed).matches()) {
			long value = Long.parseLong(trimmed);
			if (value > Integer.MAX_VALUE) {
				throw new IllegalArgumentException("Time too large: " + time);
			}
			return (int) value;
		}

		String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
		Matcher matcher = DURATION_TOKEN.matcher(lower);
		long total = 0;
		int lastEnd = 0;
		boolean found = false;
		while (matcher.find()) {
			if (!lower.substring(lastEnd, matcher.start()).isBlank()) {
				throw new IllegalArgumentException("Invalid time format: " + time);
			}
			found = true;
			lastEnd = matcher.end();
			long value = Long.parseLong(matcher.group(1));
			total = Math.addExact(total, Math.multiplyExact(value, secondsPerUnit(matcher.group(2))));
			if (total > Integer.MAX_VALUE) {
				throw new IllegalArgumentException("Time too large: " + time);
			}
		}
		if (!found || (lastEnd < lower.length() && !lower.substring(lastEnd).isBlank())) {
			throw new IllegalArgumentException("Invalid time format: " + time);
		}
		return (int) total;
	}

	private static long secondsPerUnit(String unit) {
		return switch (unit.toLowerCase(java.util.Locale.ROOT)) {
			case "m" -> 60L;
			case "h" -> 3600L;
			case "d" -> 86400L;
			case "w" -> 604800L;
			default -> 1L; // s; DURATION_TOKEN validates the unit before this private call.
		};
	}

	public static String formatTime(int seconds) {
	    int days = seconds / 86400;
	    seconds %= 86400;

	    int hours = seconds / 3600;
	    seconds %= 3600;

	    int minutes = seconds / 60;
	    seconds %= 60;

	    StringBuilder result = new StringBuilder();

	    if (days > 0) result.append(days).append("d ");
	    if (hours > 0) result.append(hours).append("h ");
	    if (minutes > 0) result.append(minutes).append("m ");
	    if (seconds > 0 || result.length() == 0) result.append(seconds).append("s");

	    return result.toString().trim();
	}
}
