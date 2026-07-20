package com.teenkung.packforge.client.mixin.options;

import com.teenkung.packforge.loader.ReloadSessionTracker;
import net.minecraft.client.Options;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Options.class)
public abstract class OptionsMixin {
	@Shadow public List<String> resourcePacks;
	@Unique private List<String> packforge$oldResourcePacks = List.of();

	@Inject(method = "updateResourcePacks", at = @At("HEAD"))
	private void packforge$captureOldPacks(PackRepository packRepository, CallbackInfo ci) {
		this.packforge$oldResourcePacks = List.copyOf(this.resourcePacks);
	}

	@Inject(
		method = "updateResourcePacks",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/Minecraft;reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;"
		)
	)
	private void packforge$capturePackDiff(PackRepository packRepository, CallbackInfo ci) {
		ReloadSessionTracker.capturePackDiff(this.packforge$oldResourcePacks, this.resourcePacks);
	}
}
