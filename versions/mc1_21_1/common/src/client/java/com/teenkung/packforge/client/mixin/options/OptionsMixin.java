package com.teenkung.packforge.client.mixin.options;

import com.teenkung.packforge.client.compat.ImmediatelyFastFontAtlasCompat;
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
	private void packforge$rememberResourcePacks(PackRepository packs, CallbackInfo ci) {
		this.packforge$oldResourcePacks = List.copyOf(this.resourcePacks);
	}

	@Inject(
		method = "updateResourcePacks",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/Minecraft;reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;"
		)
	)
	private void packforge$capturePackDiff(PackRepository packs, CallbackInfo ci) {
		List<String> removed = this.packforge$oldResourcePacks.stream()
			.filter(pack -> !this.resourcePacks.contains(pack))
			.toList();
		ReloadSessionTracker.capturePackDiff(this.packforge$oldResourcePacks, this.resourcePacks);
		ImmediatelyFastFontAtlasCompat.disableReenableForPackRemoval(removed);
	}
}
