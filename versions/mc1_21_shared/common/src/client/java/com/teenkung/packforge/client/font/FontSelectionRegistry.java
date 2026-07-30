package com.teenkung.packforge.client.font;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.font.GlyphProvider;
import com.teenkung.packforge.client.mixin.font.FontManagerPreparationAccessor;
import com.teenkung.packforge.config.FeatureFlags;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.resources.ResourceLocation;

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

	public static CompletableFuture<Object> prepareAsync(Object preparation, Set<FontOption> options, Executor executor) {
		if (!FeatureFlags.fontPrepareProviderSelectionEnabled() && !FeatureFlags.fontReloadDiagnosticsEnabled()) {
			return CompletableFuture.completedFuture(preparation);
		}
		Map<ResourceLocation, List<GlyphProvider.Conditional>> fontSets =
			((FontManagerPreparationAccessor) preparation).packforge$fontSets();
		ConcurrentHashMap<ResourceLocation, FontPreparedSelection> selections = new ConcurrentHashMap<>();
		ConcurrentHashMap<List<GlyphProvider.Conditional>, FontPreparedSelection> memoized = new ConcurrentHashMap<>();
		LongAdder selectionNs = new LongAdder();
		LongAdder memoHits = new LongAdder();
		LongAdder memoMisses = new LongAdder();
		CompletableFuture<?>[] tasks = FeatureFlags.fontPrepareProviderSelectionEnabled()
			? fontSets.entrySet().stream().map(entry -> CompletableFuture.runAsync(() -> {
				List<GlyphProvider.Conditional> providers = List.copyOf(Lists.reverse(entry.getValue()));
				FontPreparedSelection selection = memoized.get(providers);
				if (selection == null) {
					FontPreparedSelection computed = FontPreparedSelection.compute(providers, options);
					FontPreparedSelection existing = memoized.putIfAbsent(providers, computed);
					selection = existing == null ? computed : existing;
					if (existing == null) memoMisses.increment(); else memoHits.increment();
				} else {
					memoHits.increment();
				}
				selectionNs.add(selection.elapsedNs());
				selections.put(entry.getKey(), selection);
			}, executor)).toArray(CompletableFuture[]::new)
			: new CompletableFuture<?>[0];
		return CompletableFuture.allOf(tasks).thenApply(ignored -> {
			FontReloadDiagnostics.Snapshot diagnostics = FontReloadDiagnostics.snapshot(
				fontSets, selectionNs.sum(), memoHits.intValue(), memoMisses.intValue(), memoized.size()
			);
			synchronized (PREPARED) {
				PREPARED.put(preparation, new FontPreparationBundle(Set.copyOf(options), new HashMap<>(selections), diagnostics));
			}
			return preparation;
		});
	}

	public static void beginApply(Object preparation) {
		synchronized (PREPARED) {
			APPLYING.set(PREPARED.remove(preparation));
		}
	}

	public static FontPreparedSelection currentSelection(List<GlyphProvider.Conditional> providers, Set<FontOption> options) {
		FontPreparationBundle bundle = APPLYING.get();
		return bundle == null ? null : bundle.selectionFor(providers, options);
	}

	public static FontPreparationBundle currentBundle() {
		return APPLYING.get();
	}

	public static void clear() {
		APPLYING.remove();
	}

	public static void resetForReload() {
		clear();
		synchronized (PREPARED) {
			PREPARED.clear();
		}
	}

	private FontSelectionRegistry() {}
}
