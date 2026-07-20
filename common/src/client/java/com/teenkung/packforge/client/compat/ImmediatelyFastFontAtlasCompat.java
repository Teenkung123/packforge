package com.teenkung.packforge.client.compat;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.loader.ReloadSessionTracker;
import com.teenkung.packforge.platform.PackForgeCompat;
import com.teenkung.packforge.platform.PackForgeServices;

import java.lang.reflect.Field;

public final class ImmediatelyFastFontAtlasCompat {
	private static volatile boolean disabledThisSession;

	public static void disableReenableForPackRemoval() {
		if (disabledThisSession || !FeatureFlags.immediatelyFastFontAtlasCompatEnabled()) {
			return;
		}
		if (!PackForgeServices.isInitialized() || !PackForgeCompat.isImmediatelyFastPresent()) {
			return;
		}
		ReloadSessionTracker.ReloadSession session = ReloadSessionTracker.current();
		if (session.removed().isEmpty()) {
			return;
		}
		boolean changed = setStaticConfigBoolean("config", "font_atlas_resizing", false);
		changed |= setStaticConfigBoolean("runtimeConfig", "font_atlas_resizing", false);
		if (changed) {
			disabledThisSession = true;
			PackForge.LOGGER.info("PackForge compat: disabled ImmediatelyFast font atlas resizing for this session after removing resource pack(s) {} to avoid render-thread re-enable stall",
				session.removed());
		}
	}

	private static boolean setStaticConfigBoolean(String configFieldName, String valueFieldName, boolean value) {
		try {
			Class<?> owner = Class.forName("net.raphimc.immediatelyfast.ImmediatelyFast");
			Field configField = owner.getDeclaredField(configFieldName);
			configField.setAccessible(true);
			Object config = configField.get(null);
			if (config == null) {
				return false;
			}
			Field valueField = config.getClass().getDeclaredField(valueFieldName);
			valueField.setAccessible(true);
			boolean oldValue = valueField.getBoolean(config);
			valueField.setBoolean(config, value);
			return oldValue != value;
		} catch (ReflectiveOperationException | LinkageError e) {
			PackForge.LOGGER.debug("PackForge compat: could not adjust ImmediatelyFast {}", configFieldName, e);
			return false;
		}
	}

	private ImmediatelyFastFontAtlasCompat() {}
}
