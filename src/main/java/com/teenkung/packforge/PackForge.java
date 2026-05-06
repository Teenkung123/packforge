package com.teenkung.packforge;

import com.teenkung.packforge.config.PackForgeConfig;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PackForge implements ModInitializer {
	public static final String MOD_ID = "packforge";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PackForgeConfig.load();
		LOGGER.info("PackForge initialized (loaderIndex={}, atlasCap={}, atlasRetry={})",
			PackForgeConfig.get().loaderIndexEnabled,
			PackForgeConfig.get().atlasCapEnabled,
			PackForgeConfig.get().atlasRetryEnabled);
	}
}
