package com.teenkung.packforge.client.mixin.compat;

import com.teenkung.packforge.client.compat.MinecraftGuiBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Minecraft.class)
public abstract class MinecraftGui26_1Mixin implements MinecraftGuiBridge {
	@Shadow public Screen screen;

	@Shadow public abstract void setScreen(Screen screen);

	@Shadow public abstract void setOverlay(Overlay overlay);

	@Shadow public abstract ToastManager getToastManager();

	@Override
	@Unique
	public void packforge$setScreen(Screen screen) {
		this.setScreen(screen);
	}

	@Override
	@Unique
	public Screen packforge$screen() {
		return this.screen;
	}

	@Override
	@Unique
	public void packforge$setOverlay(Overlay overlay) {
		this.setOverlay(overlay);
	}

	@Override
	@Unique
	public ToastManager packforge$toastManager() {
		return this.getToastManager();
	}
}
