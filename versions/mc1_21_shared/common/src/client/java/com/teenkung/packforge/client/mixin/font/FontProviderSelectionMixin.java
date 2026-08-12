package com.teenkung.packforge.client.mixin.font;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.font.GlyphProvider;
import com.teenkung.packforge.client.font.FontSelectionRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

@Mixin(FontManager.class)
public abstract class FontProviderSelectionMixin {
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

	@Inject(method = "prepare", at = @At("RETURN"), cancellable = true)
	private void packforge$prepare(
		ResourceManager manager,
		Executor executor,
		CallbackInfoReturnable<CompletableFuture<?>> cir
	) {
		if (!FontSelectionRegistry.preparationHooksEnabled()) {
			return;
		}
		Set<FontOption> options = packforge$options(Minecraft.getInstance().options);
		cir.setReturnValue(cir.getReturnValue().thenCompose(
			preparation -> FontSelectionRegistry.prepareAsync(preparation, options, executor)
		));
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
