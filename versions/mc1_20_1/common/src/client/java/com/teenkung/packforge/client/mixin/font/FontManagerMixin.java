package com.teenkung.packforge.client.mixin.font;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.font.GlyphProvider;
import com.teenkung.packforge.client.font.FontReloadDiagnostics;
import com.teenkung.packforge.client.font.RawFontSelectionRegistry;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(FontManager.class)
public abstract class FontManagerMixin {
	@WrapMethod(method = "prepare")
	private CompletableFuture<?> packforge$prepareRawProviderSelection(
		ResourceManager manager,
		Executor executor,
		Operation<CompletableFuture<?>> original
	) {
		ReloadExecutionContext context = ReloadExecutionContext.current();
		if (context == null || !context.features().fontPrepareProviderSelectionEnabled()) {
			return original.call(manager, executor);
		}
		RawFontSelectionRegistry.Session session = RawFontSelectionRegistry.beginPreparation(context);
		try {
			return original.call(manager, session.executor(executor))
				.thenApply(preparation -> {
					RawFontSelectionRegistry.store(preparation, session);
					return preparation;
				})
				.whenComplete((ignored, failure) -> { if (failure != null) session.close(); });
		} catch (Throwable failure) {
			session.close();
			throw failure;
		}
	}

	@WrapMethod(method = "finalizeProviderLoading")
	private void packforge$prepareRawSelection(
		List<GlyphProvider> providers,
		GlyphProvider fallback,
		Operation<Void> original
	) {
		RawFontSelectionRegistry.Session session = RawFontSelectionRegistry.currentPreparation();
		if (session == null || !session.finalizeProviders(providers, fallback)) {
			original.call(providers, fallback);
		}
	}

	@WrapMethod(method = "apply")
	private void packforge$apply(@Coerce Object preparation, ProfilerFiller profiler, Operation<Void> original) {
		FontManagerPreparationAccessor accessor = (FontManagerPreparationAccessor)preparation;
		RawFontSelectionRegistry.beginApply(preparation);
		FontReloadDiagnostics.startApply(accessor.packforge$providers());
		try {
			original.call(preparation, profiler);
		} finally {
			try {
				FontReloadDiagnostics.finishApply();
			} finally {
				RawFontSelectionRegistry.clear();
			}
		}
	}
}
