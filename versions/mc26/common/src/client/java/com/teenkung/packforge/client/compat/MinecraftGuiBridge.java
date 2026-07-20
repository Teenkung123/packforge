package com.teenkung.packforge.client.compat;

import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;

public interface MinecraftGuiBridge {
	void packforge$setScreen(Screen screen);

	Screen packforge$screen();

	void packforge$setOverlay(Overlay overlay);

	ToastManager packforge$toastManager();
}
