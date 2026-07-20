package com.teenkung.packforge.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;

/** Crosses the Minecraft 26.1 to 26.2 GUI ownership move through typed mixins. */
public final class MinecraftGuiCompat {
	public static void setScreen(Minecraft minecraft, Screen screen) {
		bridge(minecraft).packforge$setScreen(screen);
	}

	public static Screen screen(Minecraft minecraft) {
		return bridge(minecraft).packforge$screen();
	}

	public static void setOverlay(Minecraft minecraft, Overlay overlay) {
		bridge(minecraft).packforge$setOverlay(overlay);
	}

	public static ToastManager toastManager(Minecraft minecraft) {
		return bridge(minecraft).packforge$toastManager();
	}

	private static MinecraftGuiBridge bridge(Minecraft minecraft) {
		if (minecraft instanceof MinecraftGuiBridge minecraftBridge) {
			return minecraftBridge;
		}
		if (minecraft.gui instanceof MinecraftGuiBridge guiBridge) {
			return guiBridge;
		}
		throw new IllegalStateException("PackForge GUI compatibility mixin is unavailable");
	}

	private MinecraftGuiCompat() {}
}
