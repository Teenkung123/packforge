package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphProvider;
import com.teenkung.packforge.concurrent.PreparationBudget;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class FontPreparationBundle implements AutoCloseable {
	private final Set<FontOption> options;
	private Map<ResourceLocation, FontPreparedSelection> selections;
	private Map<FontProviderStackKey, FontPreparedSelection> selectionsByStack;
	private final FontReloadDiagnostics.Snapshot diagnostics;
	private final FontPreparationCoordinator coordinator;
	private final PreparationBudget.Reservation bookkeeping;
	private boolean closed;

	public Set<FontOption> options() { return options; }
	public Map<ResourceLocation, FontPreparedSelection> selections() { return selections; }
	public Map<FontProviderStackKey, FontPreparedSelection> selectionsByStack() { return selectionsByStack; }
	public FontReloadDiagnostics.Snapshot diagnostics() { return diagnostics; }
	public FontPreparationCoordinator coordinator() { return coordinator; }

	@Override public void close() {
		Map<ResourceLocation, FontPreparedSelection> byId;
		Map<FontProviderStackKey, FontPreparedSelection> byStack;
		synchronized (this) {
			if (closed) return;
			closed = true;
			byId = selections;
			byStack = selectionsByStack;
			selections = Map.of();
			selectionsByStack = Map.of();
		}
		try {
			byId.values().forEach(FontPreparedSelection::close);
			byStack.values().forEach(FontPreparedSelection::close);
			if (coordinator != null) coordinator.close();
		} finally { bookkeeping.close(); }
	}

	FontPreparationBundle(Set<FontOption> options, Map<ResourceLocation, FontPreparedSelection> selections,
		Map<FontProviderStackKey, FontPreparedSelection> selectionsByStack,
		FontReloadDiagnostics.Snapshot diagnostics, FontPreparationCoordinator coordinator,
		PreparationBudget.Reservation bookkeeping) {
		this.bookkeeping = Objects.requireNonNull(bookkeeping, "Font bundle requires admitted bookkeeping");
		this.options = Set.copyOf(options);
		this.selections = Map.copyOf(selections);
		this.selectionsByStack = Map.copyOf(selectionsByStack);
		this.diagnostics = diagnostics;
		this.coordinator = coordinator;
	}

	public FontPreparedSelection selectionFor(ResourceLocation id, Set<FontOption> currentOptions) {
		if (!this.options.equals(currentOptions)) return null;
		return this.selections.get(id);
	}

	public FontPreparedSelection selectionFor(List<GlyphProvider.Conditional> providers,
		Set<FontOption> currentOptions) {
		if (!this.options.equals(currentOptions)) return null;
		// FontSet normally resolves by ID. Its occasional identity lookup must not
		// allocate a provider-sized key on the render thread outside admission.
		for (FontPreparedSelection selection : this.selectionsByStack.values()) {
			List<GlyphProvider.Conditional> expected = selection.providers();
			if (expected.size() != providers.size()) continue;
			boolean same = true;
			for (int index = 0; index < expected.size(); index++) {
				if (expected.get(index).provider() != providers.get(index).provider()
					|| expected.get(index).filter() != providers.get(index).filter()) {
					same = false;
					break;
				}
			}
			if (same) return selection;
		}
		return null;
	}
}
