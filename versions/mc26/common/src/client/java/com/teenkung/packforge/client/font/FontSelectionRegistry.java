package com.teenkung.packforge.client.font;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.font.GlyphProvider;
import com.teenkung.packforge.client.mixin.font.FontManagerPreparationAccessor;
import com.teenkung.packforge.config.FeatureFlags;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.LongAdder;

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

	public static CompletableFuture<Object> prepareAsync(Object preparation, Set<FontOption> options, Executor executor) {
		if (!FeatureFlags.fontPrepareProviderSelectionEnabled() && !FeatureFlags.fontReloadDiagnosticsEnabled()) {
			return CompletableFuture.completedFuture(preparation);
		}
		Map<Identifier, List<GlyphProvider.Conditional>> fontSets = fontSets(preparation);
		if (fontSets == null) {
			return CompletableFuture.completedFuture(preparation);
		}
		ConcurrentHashMap<Identifier, FontPreparedSelection> selections = new ConcurrentHashMap<>();
		ConcurrentHashMap<List<GlyphProvider.Conditional>, FontPreparedSelection> memoizedStacks = new ConcurrentHashMap<>();
		LongAdder selectionNs = new LongAdder();
		LongAdder memoHits = new LongAdder();
		LongAdder memoMisses = new LongAdder();
		CompletableFuture<?>[] tasks;
		if (FeatureFlags.fontPrepareProviderSelectionEnabled()) {
			tasks = fontSets.entrySet().stream()
				.map(entry -> CompletableFuture.runAsync(() -> {
					List<GlyphProvider.Conditional> providers = Lists.reverse(entry.getValue());
					List<GlyphProvider.Conditional> key = List.copyOf(providers);
					FontPreparedSelection selection = memoizedStacks.get(key);
					if (selection == null) {
						FontPreparedSelection computed = FontPreparedSelection.compute(providers, options);
						FontPreparedSelection existing = memoizedStacks.putIfAbsent(key, computed);
						selection = existing == null ? computed : existing;
						if (existing == null) {
							memoMisses.increment();
						} else {
							memoHits.increment();
						}
					} else {
						memoHits.increment();
					}
					selectionNs.add(selection.elapsedNs());
					selections.put(entry.getKey(), selection);
				}, executor))
				.toArray(CompletableFuture[]::new);
		} else {
			tasks = new CompletableFuture<?>[0];
		}
		return CompletableFuture.allOf(tasks).thenApply(ignored -> {
			FontReloadDiagnostics.Snapshot snapshot = FontReloadDiagnostics.snapshot(fontSets, selectionNs.sum(), memoHits.intValue(), memoMisses.intValue(), memoizedStacks.size());
			synchronized (PREPARED) {
				PREPARED.put(preparation, new FontPreparationBundle(Set.copyOf(options), new HashMap<>(selections), snapshot));
			}
			return preparation;
		});
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

	private static Map<Identifier, List<GlyphProvider.Conditional>> fontSets(Object preparation) {
		return ((FontManagerPreparationAccessor) preparation).packforge$fontSets();
	}

	private FontSelectionRegistry() {}
}
