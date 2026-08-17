package com.teenkung.packforge.client.mixin.observe;

import com.teenkung.packforge.client.RuntimeSmokeMinecraftBridge;
import com.teenkung.packforge.client.RuntimeSmokeScenarioController;
import com.teenkung.packforge.client.compat.MinecraftGuiCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

/**
 * 26.x runtime-smoke seam. Minecraft 26.2 moved overlay ownership to Gui, so
 * this variant uses the existing typed GUI compatibility bridge instead of
 * shadowing the removed Minecraft#setOverlay method.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftRuntimeSmoke26Mixin implements RuntimeSmokeMinecraftBridge {
	@Shadow private CompletableFuture<Void> pendingReload;

	@Shadow public abstract CompletableFuture<Void> reloadResourcePacks();

	@Inject(method = "rollbackResourcePacks", at = @At("HEAD"), cancellable = true)
	private void packforge$suppressScenarioRollback(CallbackInfo callbackInfo) {
		if (!RuntimeSmokeScenarioController.shouldSuppressVanillaRollback()) {
			return;
		}
		this.pendingReload = null;
		MinecraftGuiCompat.setOverlay((Minecraft) (Object) this, (Overlay) null);
		RuntimeSmokeScenarioController.recordOverlayCleared();
		callbackInfo.cancel();
	}

	@Override
	public CompletableFuture<Void> packforge$startRuntimeSmokeReload() {
		this.pendingReload = null;
		MinecraftGuiCompat.setOverlay((Minecraft) (Object) this, (Overlay) null);
		RuntimeSmokeScenarioController.recordOverlayCleared();
		return this.reloadResourcePacks();
	}
}
