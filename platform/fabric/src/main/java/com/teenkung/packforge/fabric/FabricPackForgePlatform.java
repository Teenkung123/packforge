package com.teenkung.packforge.fabric;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.platform.PackForgePlatform;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public final class FabricPackForgePlatform implements PackForgePlatform {
	private final FabricLoader loader = FabricLoader.getInstance();

	@Override
	public String loaderName() {
		return "fabric";
	}

	@Override
	public boolean isModLoaded(String modId) {
		return loader.isModLoaded(modId);
	}

	@Override
	public Path configDirectory() {
		return loader.getConfigDir();
	}

	@Override
	public Path gameDirectory() {
		return loader.getGameDir();
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return loader.isDevelopmentEnvironment();
	}

	@Override
	public Executor backgroundExecutor() {
		return ForkJoinPool.commonPool();
	}

	@Override
	public void logPlatformInfo() {
		PackForge.LOGGER.info("PackForge platform: loader=fabric dev={} gameDir={} configDir={}",
			isDevelopmentEnvironment(), gameDirectory(), configDirectory());
	}
}
