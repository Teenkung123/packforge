package com.teenkung.packforge.neoforge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Thin bridge across the legacy static and modern instance-based FML version APIs. */
public final class NeoForgeMinecraftVersion {
	private static final String FML_LOADER = "net.neoforged.fml.loading.FMLLoader";

	public static String current() {
		Class<?> loaderClass;
		try {
			loaderClass = Class.forName(FML_LOADER, false, NeoForgeMinecraftVersion.class.getClassLoader());
		} catch (ClassNotFoundException | LinkageError ignored) {
			return "";
		}

		String legacy = readLegacy(loaderClass);
		if (!legacy.isBlank()) {
			return legacy;
		}
		return readModern(loaderClass);
	}

	private static String readLegacy(Class<?> loaderClass) {
		try {
			return minecraftVersion(loaderClass.getMethod("versionInfo").invoke(null));
		} catch (NoSuchMethodException ignored) {
			return "";
		} catch (IllegalAccessException | InvocationTargetException | LinkageError ignored) {
			return "";
		}
	}

	private static String readModern(Class<?> loaderClass) {
		for (String accessor : new String[] {"getCurrentOrNull", "getCurrent"}) {
			try {
				Object loader = loaderClass.getMethod(accessor).invoke(null);
				if (loader == null) {
					continue;
				}
				Method getVersionInfo = loader.getClass().getMethod("getVersionInfo");
				return minecraftVersion(getVersionInfo.invoke(loader));
			} catch (NoSuchMethodException ignored) {
				// Try the other public accessor used by a neighboring FML generation.
			} catch (IllegalAccessException | InvocationTargetException | LinkageError ignored) {
				return "";
			}
		}
		return "";
	}

	private static String minecraftVersion(Object versionInfo) {
		if (versionInfo == null) {
			return "";
		}
		try {
			Object version = versionInfo.getClass().getMethod("mcVersion").invoke(versionInfo);
			return version == null ? "" : version.toString();
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | LinkageError ignored) {
			return "";
		}
	}

	private NeoForgeMinecraftVersion() {}
}
