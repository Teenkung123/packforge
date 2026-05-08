package com.teenkung.packforge.neoforge;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.platform.PackForgePlatform;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public final class NeoForgePackForgePlatform implements PackForgePlatform {
	@Override
	public String loaderName() {
		return "neoforge";
	}

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	@Override
	public Path configDirectory() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public Path gameDirectory() {
		return FMLPaths.GAMEDIR.get();
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return !FMLPaths.GAMEDIR.get().resolve("mods").toFile().exists();
	}

	@Override
	public Executor backgroundExecutor() {
		return ForkJoinPool.commonPool();
	}

	@Override
	public void logPlatformInfo() {
		PackForge.LOGGER.info("PackForge platform: loader=neoforge dev={} gameDir={} configDir={}",
			isDevelopmentEnvironment(), gameDirectory(), configDirectory());
	}
}
