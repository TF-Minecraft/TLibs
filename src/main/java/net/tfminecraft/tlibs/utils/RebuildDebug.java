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
		logAlways(message);
	}

	public static void logAlways(String message) {
		TLibs plugin = TLibs.getInstance();
		Logger logger = plugin != null ? plugin.getLogger() : Logger.getLogger(TLibs.class.getName());
		logger.info("[MMORebuild] " + message);
	}
}
