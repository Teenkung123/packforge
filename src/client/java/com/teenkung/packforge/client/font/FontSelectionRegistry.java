package com.teenkung.packforge.client.font;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.font.GlyphProvider;
import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.config.FeatureFlags;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FontSelectionRegistry {
	private static final ThreadLocal<FontPreparationBundle> APPLYING = new ThreadLocal<>();
	private static final Map<Object, FontPreparationBundle> PREPARED = new IdentityHashMap<>();

	public static Object prepare(Object preparation, Set<FontOption> options) {
		if (!FeatureFlags.fontPrepareProviderSelectionEnabled() && !FeatureFlags.fontReloadDiagnosticsEnabled()) {
			return preparation;
		}
		Map<Identifier, List<GlyphProvider.Conditional>> fontSets = fontSets(preparation);
		if (fontSets == null) {
			return preparation;
		}
		HashMap<Identifier, FontPreparedSelection> selections = new HashMap<>();
		long selectionNs = 0L;
		if (FeatureFlags.fontPrepareProviderSelectionEnabled()) {
			for (Map.Entry<Identifier, List<GlyphProvider.Conditional>> entry : fontSets.entrySet()) {
				FontPreparedSelection selection = FontPreparedSelection.compute(Lists.reverse(entry.getValue()), options);
				selectionNs += selection.elapsedNs();
				selections.put(entry.getKey(), selection);
			}
		}
		FontReloadDiagnostics.Snapshot snapshot = FontReloadDiagnostics.snapshot(fontSets, selectionNs);
		synchronized (PREPARED) {
			PREPARED.put(preparation, new FontPreparationBundle(Set.copyOf(options), selections, snapshot));
		}
		return preparation;
	}

	public static void beginApply(Object preparation) {
		synchronized (PREPARED) {
			APPLYING.set(PREPARED.remove(preparation));
		}
	}

	public static FontPreparedSelection currentSelection(Identifier id, Set<FontOption> currentOptions) {
		FontPreparationBundle bundle = APPLYING.get();
		return bundle == null ? null : bundle.selectionFor(id, currentOptions);
	}

	public static FontPreparedSelection currentSelection(List<GlyphProvider.Conditional> providers, Set<FontOption> currentOptions) {
		FontPreparationBundle bundle = APPLYING.get();
		return bundle == null ? null : bundle.selectionFor(providers, currentOptions);
	}

	public static FontPreparationBundle currentBundle() {
		return APPLYING.get();
	}

	public static void clear() {
		APPLYING.remove();
	}

	@SuppressWarnings("unchecked")
	private static Map<Identifier, List<GlyphProvider.Conditional>> fontSets(Object preparation) {
		try {
			Method method = preparation.getClass().getDeclaredMethod("fontSets");
			method.setAccessible(true);
			return (Map<Identifier, List<GlyphProvider.Conditional>>)method.invoke(preparation);
		} catch (ReflectiveOperationException e) {
			PackForge.LOGGER.warn("PackForge could not inspect FontManager preparation", e);
			return null;
		}
	}

	private FontSelectionRegistry() {}
}
