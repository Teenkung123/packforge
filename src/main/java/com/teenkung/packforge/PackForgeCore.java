package com.teenkung.packforge;

import com.teenkung.packforge.config.PackForgeConfig;
import com.teenkung.packforge.platform.PackForgeServices;

public final class PackForgeCore {
	public static void init() {
		PackForgeServices.platform().logPlatformInfo();
		PackForgeConfig.load();
		PackForge.LOGGER.info("PackForge initialized (loader={}, loaderIndex={}, atlasCap={}, atlasRetry={})",
			PackForgeServices.platform().loaderName(),
			PackForgeConfig.get().loaderIndexEnabled,
			PackForgeConfig.get().atlasCapEnabled,
			PackForgeConfig.get().atlasRetryEnabled);
	}

	private PackForgeCore() {}
}
