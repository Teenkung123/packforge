package com.teenkung.packforge.forge;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.platform.PackForgePlatform;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.VersionInfo;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public final class ForgePackForgePlatform implements PackForgePlatform {
	@Override
	public String loaderName() {
		return "forge";
	}

	@Override
	public String minecraftVersion() {
		VersionInfo versionInfo = FMLLoader.versionInfo();
		return versionInfo == null ? "" : versionInfo.mcVersion();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return ForgeModListCompat.isLoaded(modId);
	}

	@Override
	public Optional<String> modVersion(String modId) {
		return ForgeModListCompat.version(modId);
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
		PackForge.LOGGER.info("PackForge platform: loader=forge minecraft={} dev={} gameDir={} configDir={}",
			minecraftVersion(), isDevelopmentEnvironment(), gameDirectory(), configDirectory());
	}
}
