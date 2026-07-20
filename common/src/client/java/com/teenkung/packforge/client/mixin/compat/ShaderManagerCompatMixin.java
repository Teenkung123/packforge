package com.teenkung.packforge.client.mixin.compat;

import com.teenkung.packforge.client.compat.ImmediatelyFastFontAtlasCompat;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShaderManager.class)
public abstract class ShaderManagerCompatMixin {
	@Inject(method = "apply", at = @At("HEAD"))
	private void packforge$beforeShaderApply(@Coerce Object configs, ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
		ImmediatelyFastFontAtlasCompat.disableReenableForPackRemoval();
	}
}
