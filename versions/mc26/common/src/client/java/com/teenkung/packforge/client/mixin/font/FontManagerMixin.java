package com.teenkung.packforge.client.mixin.font;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.font.GlyphProvider;
import com.teenkung.packforge.client.font.FontPreparationBundle;
import com.teenkung.packforge.client.font.FontReloadDiagnostics;
import com.teenkung.packforge.client.font.FontSelectionRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(FontManager.class)
public abstract class FontManagerMixin {
	@WrapMethod(method = "createFontSet")
	private FontSet packforge$bindFontId(
		Identifier id,
		List<GlyphProvider.Conditional> providers,
		Set<FontOption> options,
		Operation<FontSet> original
	) {
		FontSelectionRegistry.beginFontSet(id);
		try {
			return original.call(id, providers, options);
		} finally {
			FontSelectionRegistry.endFontSet();
		}
	}

	@Inject(method = "prepare", at = @At("RETURN"), cancellable = true)
	private void packforge$prepareProviderSelection(ResourceManager manager, Executor executor, CallbackInfoReturnable<CompletableFuture<?>> cir) {
		if (!FontSelectionRegistry.preparationHooksEnabled()) {
			return;
		}
		Set<FontOption> options = packforge$fontOptions(Minecraft.getInstance().options);
		CompletableFuture<?> original = cir.getReturnValue();
		cir.setReturnValue(original.thenCompose(preparation -> FontSelectionRegistry.prepareAsync(preparation, options, executor)));
	}

	@WrapMethod(method = "apply")
	private void packforge$apply(
		@Coerce Object preparation,
		ProfilerFiller profiler,
		Operation<Void> original
	) {
		FontSelectionRegistry.beginApply(preparation);
		FontReloadDiagnostics.startApply();
		try {
			original.call(preparation, profiler);
		} finally {
			FontPreparationBundle bundle = FontSelectionRegistry.currentBundle();
			FontReloadDiagnostics.finishApply(preparation, bundle);
			FontSelectionRegistry.clear();
		}
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
