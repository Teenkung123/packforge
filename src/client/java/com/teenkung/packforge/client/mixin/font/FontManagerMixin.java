package com.teenkung.packforge.client.mixin.font;

import com.teenkung.packforge.client.font.FontPreparationBundle;
import com.teenkung.packforge.client.font.FontReloadDiagnostics;
import com.teenkung.packforge.client.font.FontSelectionRegistry;
import com.teenkung.packforge.config.FeatureFlags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(FontManager.class)
public abstract class FontManagerMixin {
	@Inject(method = "prepare", at = @At("RETURN"), cancellable = true)
	private void packforge$prepareProviderSelection(ResourceManager manager, Executor executor, CallbackInfoReturnable<CompletableFuture<?>> cir) {
		if (!FeatureFlags.fontPrepareProviderSelectionEnabled() && !FeatureFlags.fontReloadDiagnosticsEnabled()) {
			return;
		}
		Set<FontOption> options = packforge$fontOptions(Minecraft.getInstance().options);
		CompletableFuture<?> original = cir.getReturnValue();
		cir.setReturnValue(original.thenCompose(preparation -> FontSelectionRegistry.prepareAsync(preparation, options, executor)));
	}

	@Inject(method = "apply", at = @At("HEAD"))
	private void packforge$fontApplyStart(@Coerce Object preparations, ProfilerFiller profiler, CallbackInfo ci) {
		FontSelectionRegistry.beginApply(preparations);
		FontReloadDiagnostics.startApply();
	}

	@Inject(method = "apply", at = @At("TAIL"))
	private void packforge$fontApplyEnd(@Coerce Object preparations, ProfilerFiller profiler, CallbackInfo ci) {
		FontPreparationBundle bundle = FontSelectionRegistry.currentBundle();
		FontReloadDiagnostics.finishApply(preparations, bundle);
		FontSelectionRegistry.clear();
	}

	private static Set<FontOption> packforge$fontOptions(Options options) {
		EnumSet<FontOption> result = EnumSet.noneOf(FontOption.class);
		if (options.forceUnicodeFont().get()) {
			result.add(FontOption.UNIFORM);
		}
		if (options.japaneseGlyphVariants().get()) {
			result.add(FontOption.JAPANESE_VARIANTS);
		}
		return result;
	}
}
