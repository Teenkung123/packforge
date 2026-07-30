package com.teenkung.packforge.client.font;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.client.diagnostics.AsyncDiagnosticCsv;
import com.teenkung.packforge.client.mixin.font.FontManagerPreparationAccessor;
import com.teenkung.packforge.config.FeatureFlags;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.LongAdder;

public final class FontOptimizationState {
	private static final ThreadLocal<Bundle> APPLYING = new ThreadLocal<>();
	private static final ThreadLocal<ApplyStats> APPLY_STATS = new ThreadLocal<>();
	private static final Map<Object, Bundle> PREPARED = new IdentityHashMap<>();

	public static CompletableFuture<Object> prepareAsync(Object preparation, Set<FontOption> options, Executor executor) {
		if (!FeatureFlags.fontPrepareProviderSelectionEnabled() && !FeatureFlags.fontReloadDiagnosticsEnabled()) {
			return CompletableFuture.completedFuture(preparation);
		}
		Map<Identifier, List<GlyphProvider.Conditional>> fontSets =
			((FontManagerPreparationAccessor) preparation).packforge$fontSets();
		ConcurrentHashMap<Identifier, Selection> selections = new ConcurrentHashMap<>();
		ConcurrentHashMap<List<GlyphProvider.Conditional>, Selection> memoized = new ConcurrentHashMap<>();
		LongAdder selectionNs = new LongAdder();
		LongAdder hits = new LongAdder();
		LongAdder misses = new LongAdder();
		CompletableFuture<?>[] tasks = FeatureFlags.fontPrepareProviderSelectionEnabled()
			? fontSets.entrySet().stream().map(entry -> CompletableFuture.runAsync(() -> {
				List<GlyphProvider.Conditional> providers = List.copyOf(Lists.reverse(entry.getValue()));
				Selection selection = memoized.get(providers);
				if (selection == null) {
					Selection computed = Selection.compute(providers, options);
					Selection existing = memoized.putIfAbsent(providers, computed);
					selection = existing == null ? computed : existing;
					if (existing == null) misses.increment(); else hits.increment();
				} else {
					hits.increment();
				}
				selectionNs.add(selection.elapsedNs());
				selections.put(entry.getKey(), selection);
			}, executor)).toArray(CompletableFuture[]::new)
			: new CompletableFuture<?>[0];
		return CompletableFuture.allOf(tasks).thenApply(ignored -> {
			Snapshot snapshot = snapshot(fontSets, selectionNs.sum(), hits.intValue(), misses.intValue(), memoized.size());
			synchronized (PREPARED) {
				PREPARED.put(preparation, new Bundle(Set.copyOf(options), new HashMap<>(selections), snapshot));
			}
			return preparation;
		});
	}

	public static void beginApply(Object preparation) {
		synchronized (PREPARED) {
			APPLYING.set(PREPARED.remove(preparation));
		}
		if (FeatureFlags.fontReloadDiagnosticsEnabled()) {
			APPLY_STATS.set(new ApplyStats(System.nanoTime()));
		}
	}

	public static Selection currentSelection(List<GlyphProvider.Conditional> providers, Set<FontOption> options) {
		Bundle bundle = APPLYING.get();
		if (bundle == null || !bundle.options().equals(options)) return null;
		for (Selection selection : bundle.selections().values()) {
			if (selection.providers().equals(providers)) return selection;
		}
		return null;
	}

	public static void recordFontSet(long elapsedNs, boolean optimized) {
		ApplyStats stats = APPLY_STATS.get();
		if (stats != null) {
			stats.fontSetNs += elapsedNs;
			stats.fontSets++;
			if (optimized) stats.optimized++;
		}
	}

	public static void finishApply() {
		Bundle bundle = APPLYING.get();
		ApplyStats stats = APPLY_STATS.get();
		APPLYING.remove();
		APPLY_STATS.remove();
		if (!FeatureFlags.fontReloadDiagnosticsEnabled() || stats == null) return;
		long totalNs = System.nanoTime() - stats.startNs;
		Snapshot snapshot = bundle == null ? Snapshot.EMPTY : bundle.snapshot();
		PackForge.LOGGER.info(
			"PackForge font reload: apply={}ms fontSetCreate={}ms fontSets={} optimized={} fonts={} providers={} selectionPrepare={}ms",
			ms(totalNs), ms(stats.fontSetNs), stats.fontSets, stats.optimized,
			snapshot.fonts(), snapshot.providers(), ms(snapshot.selectionNs())
		);
		String row = System.currentTimeMillis() + "," + ms(totalNs) + "," + ms(stats.fontSetNs) + ","
			+ stats.fontSets + "," + stats.optimized + "," + snapshot.fonts() + "," + snapshot.providers() + ","
			+ ms(snapshot.selectionNs()) + "," + snapshot.memoHits() + "," + snapshot.memoMisses() + ","
			+ snapshot.uniqueStacks();
		AsyncDiagnosticCsv.append(
			Path.of("logs", "packforge-font-timings.csv"),
			"timestamp,apply_ms,font_set_create_ms,font_sets,optimized_font_sets,fonts,providers,selection_prepare_ms,memo_hits,memo_misses,unique_stacks",
			List.of(row)
		);
	}

	public static void resetForReload() {
		APPLYING.remove();
		APPLY_STATS.remove();
		synchronized (PREPARED) {
			PREPARED.clear();
		}
	}

	private static Snapshot snapshot(
		Map<Identifier, List<GlyphProvider.Conditional>> fontSets,
		long selectionNs,
		int hits,
		int misses,
		int uniqueStacks
	) {
		if (!FeatureFlags.fontReloadDiagnosticsEnabled()) return Snapshot.EMPTY;
		int providers = fontSets.values().stream().mapToInt(List::size).sum();
		return new Snapshot(fontSets.size(), providers, selectionNs, hits, misses, uniqueStacks);
	}

	private static long ms(long ns) {
		return ns / 1_000_000L;
	}

	public record Selection(
		List<GlyphProvider.Conditional> providers,
		List<GlyphProvider> activeProviders,
		Int2ObjectMap<IntList> glyphsByWidth,
		long elapsedNs
	) {
		private static Selection compute(List<GlyphProvider.Conditional> providers, Set<FontOption> options) {
			long startNs = System.nanoTime();
			List<GlyphProvider> selected = new ArrayList<>();
			IntOpenHashSet supported = new IntOpenHashSet();
			for (GlyphProvider.Conditional conditional : providers) {
				if (conditional.filter().apply(options)) {
					selected.add(conditional.provider());
					supported.addAll(conditional.provider().getSupportedGlyphs());
				}
			}
			Set<GlyphProvider> used = new HashSet<>();
			Int2ObjectOpenHashMap<IntList> widths = new Int2ObjectOpenHashMap<>();
			supported.forEach(codepoint -> {
				for (GlyphProvider provider : selected) {
					UnbakedGlyph glyph = provider.getGlyph(codepoint);
					if (glyph == null) continue;
					used.add(provider);
					if (glyph.info() != SpecialGlyphs.MISSING) {
						widths.computeIfAbsent(Mth.ceil(glyph.info().getAdvance(false)), ignored -> new IntArrayList()).add(codepoint);
					}
					break;
				}
			});
			return new Selection(
				List.copyOf(providers),
				selected.stream().filter(used::contains).toList(),
				widths,
				System.nanoTime() - startNs
			);
		}
	}

	private record Bundle(Set<FontOption> options, Map<Identifier, Selection> selections, Snapshot snapshot) {}
	private record Snapshot(int fonts, int providers, long selectionNs, int memoHits, int memoMisses, int uniqueStacks) {
		private static final Snapshot EMPTY = new Snapshot(0, 0, 0L, 0, 0, 0);
	}
	private static final class ApplyStats {
		private final long startNs;
		private long fontSetNs;
		private int fontSets;
		private int optimized;
		private ApplyStats(long startNs) { this.startNs = startNs; }
	}

	private FontOptimizationState() {}
}
