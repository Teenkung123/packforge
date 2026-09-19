package com.teenkung.packforge.internal.compat;

import com.teenkung.packforge.platform.OptimizationCompatibility;

import java.util.Objects;

/** Admission for targets with coordinated preparation; never reads configuration files. */
public final class MixinCompatibility {
	private static final String MAIN = "com.teenkung.packforge.mixin.";
	private static final String CLIENT = "com.teenkung.packforge.client.mixin.";

	public static boolean shouldApply(String mixinClassName, OptimizationCompatibility.State compatibility) {
		Objects.requireNonNull(mixinClassName, "mixinClassName");
		Objects.requireNonNull(compatibility, "compatibility");
		// Retained native bitmaps are not admitted by the shared preparation budget.
		if (mixinClassName.equals(CLIENT + "font.BitmapProviderDefinitionMixin")
			|| mixinClassName.equals(CLIENT + "font.BitmapProviderImageAccessor")) {
			return false;
		}
		if ((compatibility.quickPackPresent() || compatibility.rrlsPresent())
			&& mixinClassName.equals(CLIENT + "ui.LoadingOverlayMixin")) {
			return false;
		}
		if (!compatibility.quickPackPresent()) return true;
		// Omit the complete group, including its accessors and constructor hooks.
		// Keeping a late flag in those hooks would still transform shared owners.
		return !mixinClassName.startsWith(MAIN + "loader.")
			&& !mixinClassName.startsWith(CLIENT + "font.")
			&& !mixinClassName.startsWith(CLIENT + "atlas.")
			&& !mixinClassName.equals(CLIENT + "model.ModelManagerMixin")
			&& !mixinClassName.equals(MAIN + "startup.UtilExecutorMixin");
	}

	private MixinCompatibility() {}
}
