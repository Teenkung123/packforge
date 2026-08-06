package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record FontPreparationBundle(
	Set<FontOption> options,
	Map<ResourceLocation, FontPreparedSelection> selections,
	Map<FontProviderStackKey, FontPreparedSelection> selectionsByStack,
	FontReloadDiagnostics.Snapshot diagnostics
) {
	public FontPreparationBundle(
		Set<FontOption> options,
		Map<ResourceLocation, FontPreparedSelection> selections,
		FontReloadDiagnostics.Snapshot diagnostics
	) {
		this(options, selections, stackIndex(selections), diagnostics);
	}

	public FontPreparationBundle {
		options = Set.copyOf(options);
		selections = Map.copyOf(selections);
		selectionsByStack = Map.copyOf(selectionsByStack);
	}

	public FontPreparedSelection selectionFor(ResourceLocation id, Set<FontOption> currentOptions) {
		return options.equals(currentOptions) ? selections.get(id) : null;
	}

	public FontPreparedSelection selectionFor(List<GlyphProvider.Conditional> providers, Set<FontOption> currentOptions) {
		return options.equals(currentOptions)
			? selectionsByStack.get(FontProviderStackKey.of(providers))
			: null;
	}

	private static Map<FontProviderStackKey, FontPreparedSelection> stackIndex(
		Map<ResourceLocation, FontPreparedSelection> selections
	) {
		Map<FontProviderStackKey, FontPreparedSelection> result = new LinkedHashMap<>();
		for (FontPreparedSelection selection : selections.values()) {
			result.putIfAbsent(FontProviderStackKey.of(selection.providers()), selection);
		}
		return result;
	}
}
