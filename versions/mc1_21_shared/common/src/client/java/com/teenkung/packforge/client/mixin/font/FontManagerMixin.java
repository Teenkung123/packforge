package com.teenkung.packforge.client.mixin.font;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.font.GlyphProvider;
import com.teenkung.packforge.client.font.FontPreparationBundle;
import com.teenkung.packforge.client.font.FontPreparationCoordinator;
import com.teenkung.packforge.client.font.FontReloadDiagnostics;
import com.teenkung.packforge.client.font.FontSelectionRegistry;
import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(FontManager.class)
public abstract class FontManagerMixin {
	@WrapOperation(
		method = "apply",
		at = @At(
			value = "INVOKE",
			target = "Ljava/util/Map;forEach(Ljava/util/function/BiConsumer;)V"
		)
	)
	private void packforge$bindFontId(
		Map<ResourceLocation, List<GlyphProvider.Conditional>> fontSets,
		BiConsumer<ResourceLocation, List<GlyphProvider.Conditional>> action,
		Operation<Void> original
	) {
		original.call(fontSets, (BiConsumer<ResourceLocation, List<GlyphProvider.Conditional>>) (id, providers) -> {
			FontSelectionRegistry.beginFontSet(id);
			try {
				action.accept(id, providers);
			} finally {
				FontSelectionRegistry.endFontSet();
			}
		});
	}

	@WrapMethod(method = "prepare")
	private CompletableFuture<?> packforge$prepare(
		ResourceManager manager,
		Executor executor,
		Operation<CompletableFuture<?>> original
	) {
		if (!FontSelectionRegistry.preparationHooksEnabled()) return original.call(manager, executor);
		Set<FontOption> options = packforge$options(Minecraft.getInstance().options);
		ReloadExecutionContext context = ReloadExecutionContext.current();
		boolean selection = context == null ? FeatureFlags.fontPrepareProviderSelectionEnabled()
			: context.features().fontPrepareProviderSelectionEnabled();
		FontPreparationCoordinator coordinator = selection && context != null
			? new FontPreparationCoordinator(context, options) : null;
		Executor preparationExecutor = coordinator == null ? executor : coordinator.executor(executor);
		boolean priority = selection && context != null;
		if (priority) context.beginFontPreparation();
		try {
			return original.call(manager, preparationExecutor)
				.thenCompose(preparation -> FontSelectionRegistry.prepareAsync(
					preparation, options, preparationExecutor, coordinator))
				.whenComplete((ignored, error) -> {
					try {
						if (error != null && coordinator != null) coordinator.close();
					} finally {
						if (priority) context.endFontPreparation();
					}
				});
		} catch (Throwable error) {
			try {
				if (coordinator != null) coordinator.close();
			} finally {
				if (priority) context.endFontPreparation();
			}
			throw error;
		}
	}

	@WrapMethod(method = "finalizeProviderLoading")
	private void packforge$coordinateWarmup(List<GlyphProvider.Conditional> providers,
		GlyphProvider.Conditional fallback, Operation<Void> original) {
		FontPreparationCoordinator coordinator = FontPreparationCoordinator.current();
		if (coordinator == null || !coordinator.finalizeProviders(providers, fallback)) original.call(providers, fallback);
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
			FontReloadDiagnostics.finishApply(bundle);
			FontSelectionRegistry.clear();
		}
	}

	private static Set<FontOption> packforge$options(Options options) {
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
