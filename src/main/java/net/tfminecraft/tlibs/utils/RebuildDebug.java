package net.tfminecraft.tlibs.utils;

import java.util.logging.Logger;

import net.tfminecraft.tlibs.TLibs;

public final class RebuildDebug {
	private RebuildDebug() {
	}

	public static boolean enabled() {
		TLibs plugin = TLibs.getInstance();
		if (plugin == null) {
			return true;
		}
		return TLibs.getRebuildConfig().debugNbt();
	}

	public static void log(String message) {
		if (!enabled()) {
			return;
		}
		String line = "[TLibs][MMORebuild] " + message;
		System.out.println(line);
		Logger logger = TLibs.getInstance() != null ? TLibs.getInstance().getLogger() : null;
		if (logger != null) {
			logger.info(line);
		}
	}

	public static void logAlways(String message) {
		String line = "[TLibs][MMORebuild] " + message;
		System.out.println(line);
		if (TLibs.getInstance() != null) {
			TLibs.getInstance().getLogger().info(line);
		}
	}
}
