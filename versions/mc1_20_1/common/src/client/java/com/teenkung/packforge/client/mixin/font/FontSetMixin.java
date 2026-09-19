package com.teenkung.packforge.client.mixin.font;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.font.GlyphProvider;
import com.teenkung.packforge.client.font.FontReloadDiagnostics;
import com.teenkung.packforge.client.font.RawFontPreparedSelection;
import com.teenkung.packforge.client.font.RawFontSelectionRegistry;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import net.minecraft.client.gui.font.FontSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(FontSet.class)
public abstract class FontSetMixin {
	@Shadow @Final private List<GlyphProvider> providers;
	@Shadow @Final private Int2ObjectMap<IntList> glyphsByWidth;

	@WrapMethod(method = "reload")
	private void packforge$timeReload(List<GlyphProvider> providers, Operation<Void> original) {
		long startNs = System.nanoTime();
		RawFontPreparedSelection selection = RawFontSelectionRegistry.beginReload(providers);
		boolean reusedPreparedSelection = false;
		try {
			original.call(providers);
			if (selection != null) {
				this.providers.clear();
				this.providers.addAll(selection.activeProviders());
				this.glyphsByWidth.clear();
				this.glyphsByWidth.putAll(selection.glyphsByWidth());
				reusedPreparedSelection = true;
			}
		} finally {
			RawFontSelectionRegistry.endReload();
			FontReloadDiagnostics.recordFontSet(System.nanoTime() - startNs, reusedPreparedSelection);
		}
	}

	@WrapOperation(
		method = "reload",
		require = 1,
		allow = 1,
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/font/GlyphProvider;getSupportedGlyphs()Lit/unimi/dsi/fastutil/ints/IntSet;"
		)
	)
	private IntSet packforge$skipPreparedUnion(GlyphProvider provider, Operation<IntSet> original) {
		return RawFontSelectionRegistry.currentReload() == null
			? original.call(provider) : IntSets.EMPTY_SET;
	}
}
