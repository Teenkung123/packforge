package com.teenkung.packforge.neoforge;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.platform.PackForgePlatform;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public final class NeoForgePackForgePlatform implements PackForgePlatform {
	@Override
	public String loaderName() {
		return "neoforge";
	}

	@Override
	public String minecraftVersion() {
		return NeoForgeMinecraftVersion.current();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	@Override
	public Optional<String> modVersion(String modId) {
		return ModList.get().getModContainerById(modId)
			.map(container -> String.valueOf(container.getModInfo().getVersion()));
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
		PackForge.LOGGER.info("PackForge platform: loader=neoforge minecraft={} dev={} gameDir={} configDir={}",
			minecraftVersion(), isDevelopmentEnvironment(), gameDirectory(), configDirectory());
	}
}
