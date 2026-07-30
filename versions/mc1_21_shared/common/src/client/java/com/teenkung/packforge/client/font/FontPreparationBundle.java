package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record FontPreparationBundle(
	Set<FontOption> options,
	Map<ResourceLocation, FontPreparedSelection> selections,
	FontReloadDiagnostics.Snapshot diagnostics
) {
	public FontPreparedSelection selectionFor(List<GlyphProvider.Conditional> providers, Set<FontOption> currentOptions) {
		if (!options.equals(currentOptions)) {
			return null;
		}
		for (FontPreparedSelection selection : selections.values()) {
			if (selection.providers().equals(providers)) {
				return selection;
			}
		}
		return null;
	}
}
