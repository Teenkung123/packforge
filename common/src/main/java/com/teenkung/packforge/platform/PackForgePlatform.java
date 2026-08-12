package com.teenkung.packforge.platform;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executor;

public interface PackForgePlatform {
	String loaderName();

	/** Returns the active Minecraft release reported by the loader. */
	default String minecraftVersion() {
		return "";
	}

	boolean isModLoaded(String modId);

	/** Returns a public loader metadata version when this loader exposes one. */
	default Optional<String> modVersion(String modId) {
		return Optional.empty();
	}

	Path configDirectory();

	Path gameDirectory();

	boolean isDevelopmentEnvironment();

	Executor backgroundExecutor();

	default void registerClientReloadListener(PackForgeReloadListener listener) {
		throw new UnsupportedOperationException(loaderName() + " reload listener bridge is not implemented");
	}

	default void registerClientCommandBridge(PackForgeCommandRegistrar registrar) {
		throw new UnsupportedOperationException(loaderName() + " command bridge is not implemented");
	}

	void logPlatformInfo();
}
