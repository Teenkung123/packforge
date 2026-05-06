package com.teenkung.packforge.client.font;

import net.minecraft.client.gui.font.FontOption;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.font.GlyphProvider;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record FontPreparationBundle(
	Set<FontOption> options,
	Map<Identifier, FontPreparedSelection> selections,
	FontReloadDiagnostics.Snapshot diagnostics
) {
	public FontPreparedSelection selectionFor(Identifier id, Set<FontOption> currentOptions) {
		if (!this.options.equals(currentOptions)) {
			return null;
		}
		return this.selections.get(id);
	}

	public FontPreparedSelection selectionFor(List<GlyphProvider.Conditional> providers, Set<FontOption> currentOptions) {
		if (!this.options.equals(currentOptions)) {
			return null;
		}
		for (Map.Entry<Identifier, FontPreparedSelection> entry : this.selections.entrySet()) {
			if (entry.getValue().providers().equals(providers)) {
				return entry.getValue();
			}
		}
		return null;
	}
}
