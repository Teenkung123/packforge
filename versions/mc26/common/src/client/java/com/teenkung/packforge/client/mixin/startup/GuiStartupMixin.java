package com.teenkung.packforge.client.mixin.startup;

import com.teenkung.packforge.startup.StartupStatus;
import com.teenkung.packforge.startup.StartupTimings;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Gui.class)
public abstract class GuiStartupMixin {
	@Inject(method = "buildInitialScreens", at = @At("HEAD"), require = 0)
	private void packforge$initialScreensStart(@Coerce Object cookie, CallbackInfoReturnable<Runnable> cir) {
		StartupStatus.update("Building", "initial screens");
		StartupTimings.event("initial_screen_build_start");
	}

	@Inject(method = "buildInitialScreens", at = @At("RETURN"), require = 0)
	private void packforge$initialScreensEnd(@Coerce Object cookie, CallbackInfoReturnable<Runnable> cir) {
		StartupTimings.markBoundary("initial_screen_build_complete", "initial_screen_build");
	}
}
