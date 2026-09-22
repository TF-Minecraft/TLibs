package net.tfminecraft.tlibs.utils;

import java.util.List;

/**
 * Optional prefix filter for tab suggestions. Bukkit/Paper {@code TabCompleter}
 * results are already filtered against the argument being typed, so callers
 * implementing {@code TabCompleter} should not need this.
 */
public class TabCleaner {
	/**
	 * @param argIndex 0-based index of the argument to match against (not the
	 *                 command label). Pass {@code args.length - 1} for the token
	 *                 currently being completed.
	 */
	public static void cleanTab(List<String> completions, String[] args, int argIndex) {
		if (completions == null || args == null || argIndex < 0 || argIndex >= args.length) {
			return;
		}
		filterPrefix(completions, args[argIndex]);
	}

	/** Filters against {@code args[args.length - 1]} (the token being typed). */
	public static void cleanTab(List<String> completions, String[] args) {
		if (args == null || args.length == 0) {
			return;
		}
		cleanTab(completions, args, args.length - 1);
	}

	public static void filterPrefix(List<String> completions, String prefix) {
		if (completions == null || prefix == null || prefix.isEmpty()) {
			return;
		}
		String lowerPrefix = prefix.toLowerCase();
		for (int i = 0; i < completions.size(); i++) {
			String completion = completions.get(i);
			if (completion == null || !completion.toLowerCase().startsWith(lowerPrefix)) {
				completions.remove(i);
				i--;
			}
		}
	}
}
