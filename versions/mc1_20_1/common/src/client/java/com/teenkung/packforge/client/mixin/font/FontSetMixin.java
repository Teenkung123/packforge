package com.teenkung.packforge.client.mixin.font;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.font.GlyphProvider;
import com.teenkung.packforge.client.font.FontReloadDiagnostics;
import net.minecraft.client.gui.font.FontSet;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

@Mixin(FontSet.class)
public abstract class FontSetMixin {
	@WrapMethod(method = "reload")
	private void packforge$timeReload(List<GlyphProvider> providers, Operation<Void> original) {
		long startNs = System.nanoTime();
		try {
			original.call(providers);
		} finally {
			FontReloadDiagnostics.recordFontSet(System.nanoTime() - startNs);
		}
	}
}
