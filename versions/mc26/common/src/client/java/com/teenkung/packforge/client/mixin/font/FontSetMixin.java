package com.teenkung.packforge.client.mixin.font;

import com.mojang.blaze3d.font.GlyphProvider;
import com.teenkung.packforge.client.font.FontPreparedSelection;
import com.teenkung.packforge.client.font.FontReloadDiagnostics;
import com.teenkung.packforge.client.font.FontSelectionRegistry;
import com.teenkung.packforge.client.font.PackForgeFontSetAccess;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;

@Mixin(FontSet.class)
public abstract class FontSetMixin implements PackForgeFontSetAccess {
	@Shadow @Mutable private List<GlyphProvider.Conditional> allProviders;
	@Shadow @Mutable private List<GlyphProvider> activeProviders;
	@Shadow @Final private Int2ObjectMap<IntList> glyphsByWidth;

	@Invoker("resetTextures")
	protected abstract void packforge$resetTextures();

	@Inject(method = "reload(Ljava/util/List;Ljava/util/Set;)V", at = @At("HEAD"), cancellable = true)
	private void packforge$reloadPreselectedIfAvailable(List<GlyphProvider.Conditional> providers, Set<FontOption> options, CallbackInfo ci) {
		long startNs = System.nanoTime();
		boolean optimized = false;
		try {
			FontPreparedSelection selection = FontSelectionRegistry.currentSelection(providers, options);
			if (selection == null) {
				return;
			}
			this.packforge$reloadPreselected(providers, selection);
			optimized = true;
			ci.cancel();
		} finally {
			FontReloadDiagnostics.recordFontSetCreate(System.nanoTime() - startNs, optimized);
		}
	}

	@Override
	public void packforge$reloadPreselected(List<GlyphProvider.Conditional> providers, FontPreparedSelection selection) {
		this.allProviders = providers;
		this.activeProviders = List.of();
		this.packforge$resetTextures();
		this.glyphsByWidth.putAll(selection.glyphsByWidth());
		this.activeProviders = selection.activeProviders();
	}
}
