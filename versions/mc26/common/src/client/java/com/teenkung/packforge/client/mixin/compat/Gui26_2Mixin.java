package com.teenkung.packforge.client.mixin.compat;

import com.teenkung.packforge.client.compat.MinecraftGuiBridge;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Gui.class)
public abstract class Gui26_2Mixin implements MinecraftGuiBridge {
	@Shadow(remap = false) public abstract Screen screen();

	@Shadow(remap = false) public abstract void setScreen(Screen screen);

	@Shadow(remap = false) public abstract void setOverlay(Overlay overlay);

	@Shadow(remap = false) public abstract ToastManager toastManager();

	@Override
	@Unique
	public void packforge$setScreen(Screen screen) {
		this.setScreen(screen);
	}

	@Override
	@Unique
	public Screen packforge$screen() {
		return this.screen();
	}

	@Override
	@Unique
	public void packforge$setOverlay(Overlay overlay) {
		this.setOverlay(overlay);
	}

	@Override
	@Unique
	public ToastManager packforge$toastManager() {
		return this.toastManager();
	}
}
