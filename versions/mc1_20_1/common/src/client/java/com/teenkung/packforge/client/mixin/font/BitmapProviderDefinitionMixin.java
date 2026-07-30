package com.teenkung.packforge.client.mixin.font;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.font.GlyphProvider;
import com.teenkung.packforge.client.font.FontBitmapProviderCache;
import com.teenkung.packforge.config.FeatureFlags;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BitmapProvider.Definition.class)
public abstract class BitmapProviderDefinitionMixin {
	@WrapMethod(method = "load")
	private GlyphProvider packforge$loadCached(ResourceManager manager, Operation<GlyphProvider> original) throws Exception {
		if (!FeatureFlags.fontBitmapProviderCacheEnabled()) {
			return original.call(manager);
		}
		BitmapProvider.Definition definition = (BitmapProvider.Definition)(Object)this;
		GlyphProvider cached = FontBitmapProviderCache.get(manager, definition);
		if (cached != null) {
			return cached;
		}
		GlyphProvider loaded = original.call(manager);
		return loaded == null ? null : FontBitmapProviderCache.cache(manager, definition, loaded);
	}
}
