package com.teenkung.packforge.client.mixin.startup;

import com.teenkung.packforge.startup.StartupStatus;
import com.teenkung.packforge.startup.StartupTimings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftStartupMixin {
	@Unique private long packforge$tickStartNs;
	@Unique private long packforge$renderStartNs;
	@Unique private int packforge$sampledTicks;
	@Unique private int packforge$sampledFrames;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void packforge$constructorEnd(GameConfig config, CallbackInfo ci) {
		// Constructor HEAD injections execute before the mandatory super call and are
		// rejected by production Mixin runtimes. TAIL is the first portable boundary.
		StartupTimings.markBoundary("minecraft_constructor_complete", "pre_ui_bootstrap");
		StartupStatus.update("Waiting for", "loading overlay");
	}

	@Inject(method = "run", at = @At("HEAD"))
	private void packforge$runStart(CallbackInfo ci) {
		StartupStatus.update("Starting", "game loop");
		StartupTimings.markBoundary("game_loop_start", "window_to_loading_overlay");
	}

	@Inject(method = "buildInitialScreens", at = @At("HEAD"), require = 0)
	private void packforge$initialScreensStart(@Coerce Object cookie, CallbackInfoReturnable<Runnable> cir) {
		StartupStatus.update("Building", "initial screens");
		StartupTimings.event("initial_screen_build_start");
	}

	@Inject(method = "buildInitialScreens", at = @At("RETURN"), require = 0)
	private void packforge$initialScreensEnd(@Coerce Object cookie, CallbackInfoReturnable<Runnable> cir) {
		StartupTimings.markBoundary("initial_screen_build_complete", "initial_screen_build");
	}

	@Inject(method = "onResourceLoadFinished", at = @At("HEAD"))
	private void packforge$resourceLoadFinished(@Coerce Object cookie, CallbackInfo ci) {
		StartupStatus.update("Resource load", "finished");
		StartupTimings.event("resource_load_finished");
	}

	@Inject(method = "onGameLoadFinished", at = @At("HEAD"))
	private void packforge$gameLoadFinished(@Coerce Object cookie, CallbackInfo ci) {
		StartupStatus.update("Game load", "finished");
		StartupTimings.event("game_load_finished");
		StartupTimings.complete();
		StartupStatus.finish();
	}

	@Inject(method = "runTick", at = @At("HEAD"))
	private void packforge$tickStart(boolean renderLevel, CallbackInfo ci) {
		this.packforge$sampleStart("tick");
	}

	@Inject(method = "runTick", at = @At("TAIL"))
	private void packforge$tickEnd(boolean renderLevel, CallbackInfo ci) {
		this.packforge$sampleEnd("tick");
	}

	@Inject(method = "renderFrame", at = @At("HEAD"))
	private void packforge$renderStart(boolean tick, CallbackInfo ci) {
		this.packforge$sampleStart("renderFrame");
	}

	@Inject(method = "renderFrame", at = @At("TAIL"))
	private void packforge$renderEnd(boolean tick, CallbackInfo ci) {
		this.packforge$sampleEnd("renderFrame");
	}

	@Unique
	private void packforge$sampleStart(String source) {
		if (source.equals("tick")) {
			if (this.packforge$sampledTicks >= 120) return;
			this.packforge$tickStartNs = System.nanoTime();
			return;
		}
		if (this.packforge$sampledFrames >= 120) return;
		this.packforge$renderStartNs = System.nanoTime();
	}

	@Unique
	private void packforge$sampleEnd(String source) {
		long startNs = source.equals("tick") ? this.packforge$tickStartNs : this.packforge$renderStartNs;
		if (startNs == 0L) return;
		if (source.equals("tick")) {
			this.packforge$sampledTicks++;
			this.packforge$tickStartNs = 0L;
		} else {
			this.packforge$sampledFrames++;
			this.packforge$renderStartNs = 0L;
		}
		StartupTimings.recordStall(source, System.nanoTime() - startNs);
	}
}
