package com.teenkung.packforge.client.mixin.font;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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

import java.util.List;
import java.util.Set;

@Mixin(FontSet.class)
public abstract class FontSetMixin implements PackForgeFontSetAccess {
	@Shadow @Mutable private List<GlyphProvider.Conditional> allProviders;
	@Shadow @Mutable private List<GlyphProvider> activeProviders;
	@Shadow @Final private Int2ObjectMap<IntList> glyphsByWidth;

	@Invoker("resetTextures")
	protected abstract void packforge$resetTextures();

	@WrapMethod(method = "reload(Ljava/util/List;Ljava/util/Set;)V")
	private void packforge$reloadPreselectedIfAvailable(List<GlyphProvider.Conditional> providers, Set<FontOption> options, Operation<Void> original) {
		long startNs = System.nanoTime();
		boolean optimized = false;
		try {
			FontPreparedSelection selection = FontSelectionRegistry.currentSelection(options);
			if (selection == null) {
				selection = FontSelectionRegistry.currentSelection(providers, options);
			}
			if (selection == null) {
				original.call(providers, options);
				return;
			}
			this.packforge$reloadPreselected(providers, selection);
			optimized = true;
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
