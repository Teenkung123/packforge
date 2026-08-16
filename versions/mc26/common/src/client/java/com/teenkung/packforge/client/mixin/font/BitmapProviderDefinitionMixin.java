package com.teenkung.packforge.client.mixin.font;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.font.GlyphProvider;
import com.teenkung.packforge.client.HeavyFixtureEvidence;
import com.teenkung.packforge.client.font.FontBitmapProviderCache;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BitmapProvider.Definition.class)
public abstract class BitmapProviderDefinitionMixin {
	@WrapMethod(method = "load")
	private GlyphProvider packforge$loadCached(ResourceManager resourceManager, Operation<GlyphProvider> original) throws Exception {
		HeavyFixtureEvidence.recordFontProviderAttempt();
		if (!FontBitmapProviderCache.enabled()) {
			GlyphProvider loaded = original.call(resourceManager);
			HeavyFixtureEvidence.recordFontProviderResult(loaded != null);
			return loaded;
		}
		BitmapProvider.Definition definition = (BitmapProvider.Definition)(Object)this;
		long epoch = FontBitmapProviderCache.captureEpoch();
		GlyphProvider cached = FontBitmapProviderCache.get(epoch, resourceManager, definition);
		if (cached != null) {
			HeavyFixtureEvidence.recordFontProviderResult(true);
			return cached;
		}
		GlyphProvider loaded = original.call(resourceManager);
		HeavyFixtureEvidence.recordFontProviderResult(loaded != null);
		return loaded == null ? null : FontBitmapProviderCache.cache(epoch, resourceManager, definition, loaded);
	}
}
