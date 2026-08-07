package com.teenkung.packforge.client.font;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.client.diagnostics.AsyncDiagnosticCsv;
import com.teenkung.packforge.client.mixin.font.FontManagerPreparationAccessor;
import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.config.ReloadFeatureSnapshot;
import com.teenkung.packforge.concurrent.OrderedAsync;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Reload-scoped unique-stack font preparation for 1.21.11. */
public final class FontOptimizationState {
	private static final ThreadLocal<Bundle> APPLYING = new ThreadLocal<>();
	private static final ThreadLocal<ApplyStats> APPLY_STATS = new ThreadLocal<>();
	private static final ThreadLocal<Identifier> CURRENT_FONT_ID = new ThreadLocal<>();
	private static final Map<Object, Bundle> PREPARED = new IdentityHashMap<>();

	public static boolean preparationHooksEnabled() {
		ReloadFeatureSnapshot features = reloadFeatures();
		return features == null
			? FeatureFlags.fontPrepareProviderSelectionEnabled() || FeatureFlags.fontReloadDiagnosticsEnabled()
			: features.fontPrepareProviderSelectionEnabled() || features.fontReloadDiagnosticsEnabled();
	}

	public static CompletableFuture<Object> prepareAsync(
		Object preparation,
		Set<FontOption> options,
		Executor executor
	) {
		ReloadFeatureSnapshot features = reloadFeatures();
		boolean selectionEnabled = features == null
			? FeatureFlags.fontPrepareProviderSelectionEnabled()
			: features.fontPrepareProviderSelectionEnabled();
		boolean diagnosticsEnabled = features == null
			? FeatureFlags.fontReloadDiagnosticsEnabled()
			: features.fontReloadDiagnosticsEnabled();
		if (!selectionEnabled && !diagnosticsEnabled) {
			return CompletableFuture.completedFuture(preparation);
		}
		Map<Identifier, List<GlyphProvider.Conditional>> fontSets =
			((FontManagerPreparationAccessor) preparation).packforge$fontSets();
		List<StackGroup> groups = groupFontSets(fontSets);
		int workerBudget = features == null ? fallbackWorkerBudget() : features.workerBudget();
		ReloadExecutionContext context = ReloadExecutionContext.current();
		CompletableFuture<List<Selection>> selectionsFuture = selectionEnabled
			? OrderedAsync.map(
				groups,
				executor,
				workerBudget,
				1,
				group -> Selection.compute(group.providers(), options),
				selection -> { }
			)
			: CompletableFuture.completedFuture(List.of());
		CompletableFuture<Object> result = selectionsFuture.thenApply(selections -> {
			if (context != null && !ReloadExecutionContext.isCurrent(context)) {
				return preparation;
			}
			return store(preparation, options, fontSets, groups, selections, diagnosticsEnabled, selectionEnabled);
		});
		result.whenComplete((ignored, error) -> {
			if (error != null) {
				discard(preparation);
			}
		});
		return result;
	}

	public static void beginApply(Object preparation) {
		synchronized (PREPARED) {
			APPLYING.set(PREPARED.remove(preparation));
		}
		if (diagnosticsEnabled()) {
			APPLY_STATS.set(new ApplyStats(System.nanoTime()));
		}
	}

	public static void beginFontSet(Identifier id) {
		CURRENT_FONT_ID.set(id);
	}

	public static void endFontSet() {
		CURRENT_FONT_ID.remove();
	}

	public static Selection currentSelection(Set<FontOption> options) {
		Bundle bundle = APPLYING.get();
		Identifier id = CURRENT_FONT_ID.get();
		return bundle == null || id == null || !bundle.options().equals(options)
			? null
			: bundle.selections().get(id);
	}

	public static Selection currentSelection(List<GlyphProvider.Conditional> providers, Set<FontOption> options) {
		Bundle bundle = APPLYING.get();
		if (bundle == null || !bundle.options().equals(options)) {
			return null;
		}
		Identifier id = CURRENT_FONT_ID.get();
		return id == null
			? bundle.selectionsByStack().get(FontProviderStackKey.of(providers))
			: bundle.selections().get(id);
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
		CURRENT_FONT_ID.remove();
		if (!diagnosticsEnabled() || stats == null) {
			return;
		}
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
		CURRENT_FONT_ID.remove();
		synchronized (PREPARED) {
			PREPARED.clear();
		}
	}

	static List<StackGroup> groupFontSets(
		Map<Identifier, List<GlyphProvider.Conditional>> fontSets
	) {
		Map<FontProviderStackKey, GroupBuilder> grouped = new LinkedHashMap<>();
		for (Map.Entry<Identifier, List<GlyphProvider.Conditional>> entry : fontSets.entrySet()) {
			List<GlyphProvider.Conditional> providers = List.copyOf(Lists.reverse(entry.getValue()));
			FontProviderStackKey key = FontProviderStackKey.of(providers);
			grouped.computeIfAbsent(key, ignored -> new GroupBuilder(key, providers)).ids.add(entry.getKey());
		}
		List<StackGroup> result = new ArrayList<>(grouped.size());
		for (GroupBuilder group : grouped.values()) {
			result.add(new StackGroup(group.key, group.providers, List.copyOf(group.ids)));
		}
		return List.copyOf(result);
	}

	private static Object store(
		Object preparation,
		Set<FontOption> options,
		Map<Identifier, List<GlyphProvider.Conditional>> fontSets,
		List<StackGroup> groups,
		List<Selection> selections,
		boolean diagnosticsEnabled,
		boolean selectionEnabled
	) {
		Map<Identifier, Selection> byId = new LinkedHashMap<>();
		Map<FontProviderStackKey, Selection> byStack = new LinkedHashMap<>();
		long selectionNs = 0L;
		for (int i = 0; i < groups.size(); i++) {
			Selection selection = selectionEnabled ? selections.get(i) : null;
			if (selection == null) {
				continue;
			}
			StackGroup group = groups.get(i);
			selectionNs += selection.elapsedNs();
			byStack.put(group.key(), selection);
			for (Identifier id : group.ids()) {
				byId.put(id, selection);
			}
		}
		Snapshot snapshot = snapshot(
			fontSets,
			selectionNs,
			selectionEnabled ? Math.max(0, fontSets.size() - groups.size()) : 0,
			selectionEnabled ? groups.size() : 0,
			selectionEnabled ? groups.size() : 0,
			diagnosticsEnabled
		);
		Bundle bundle = new Bundle(options, byId, byStack, snapshot);
		synchronized (PREPARED) {
			PREPARED.put(preparation, bundle);
		}
		return preparation;
	}

	private static Snapshot snapshot(
		Map<Identifier, List<GlyphProvider.Conditional>> fontSets,
		long selectionNs,
		int hits,
		int misses,
		int uniqueStacks,
		boolean enabled
	) {
		if (!enabled) {
			return Snapshot.EMPTY;
		}
		int providers = fontSets.values().stream().mapToInt(List::size).sum();
		return new Snapshot(fontSets.size(), providers, selectionNs, hits, misses, uniqueStacks);
	}

	private static boolean diagnosticsEnabled() {
		ReloadFeatureSnapshot features = reloadFeatures();
		return features == null ? FeatureFlags.fontReloadDiagnosticsEnabled() : features.fontReloadDiagnosticsEnabled();
	}

	private static ReloadFeatureSnapshot reloadFeatures() {
		ReloadExecutionContext context = ReloadExecutionContext.current();
		return context == null ? null : context.features();
	}

	private static int fallbackWorkerBudget() {
		return Math.max(1, Math.min(32, Runtime.getRuntime().availableProcessors()));
	}

	private static void discard(Object preparation) {
		synchronized (PREPARED) {
			PREPARED.remove(preparation);
		}
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
		public Selection {
			providers = List.copyOf(providers);
			activeProviders = List.copyOf(activeProviders);
			Int2ObjectOpenHashMap<IntList> copiedWidths = new Int2ObjectOpenHashMap<>();
			for (Int2ObjectMap.Entry<IntList> entry : glyphsByWidth.int2ObjectEntrySet()) {
				copiedWidths.put(entry.getIntKey(), IntLists.unmodifiable(new IntArrayList(entry.getValue())));
			}
			glyphsByWidth = Int2ObjectMaps.unmodifiable(copiedWidths);
		}

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

			Set<GlyphProvider> used = Collections.newSetFromMap(new IdentityHashMap<>());
			Int2ObjectOpenHashMap<IntList> widths = new Int2ObjectOpenHashMap<>();
			supported.forEach(codepoint -> {
				for (GlyphProvider provider : selected) {
					UnbakedGlyph glyph = provider.getGlyph(codepoint);
					if (glyph == null) {
						continue;
					}
					used.add(provider);
					if (glyph.info() != SpecialGlyphs.MISSING) {
						widths.computeIfAbsent(Mth.ceil(glyph.info().getAdvance(false)), ignored -> new IntArrayList()).add(codepoint);
					}
					break;
				}
			});
			return new Selection(
				providers,
				selected.stream().filter(used::contains).toList(),
				widths,
				System.nanoTime() - startNs
			);
		}
	}

	private record Bundle(
		Set<FontOption> options,
		Map<Identifier, Selection> selections,
		Map<FontProviderStackKey, Selection> selectionsByStack,
		Snapshot snapshot
	) {
		private Bundle {
			options = Set.copyOf(options);
			selections = Map.copyOf(selections);
			selectionsByStack = Map.copyOf(selectionsByStack);
		}
	}

	static record StackGroup(
		FontProviderStackKey key,
		List<GlyphProvider.Conditional> providers,
		List<Identifier> ids
	) {}

	private record Snapshot(int fonts, int providers, long selectionNs, int memoHits, int memoMisses, int uniqueStacks) {
		private static final Snapshot EMPTY = new Snapshot(0, 0, 0L, 0, 0, 0);
	}

	private static final class GroupBuilder {
		private final FontProviderStackKey key;
		private final List<GlyphProvider.Conditional> providers;
		private final List<Identifier> ids = new ArrayList<>();

		private GroupBuilder(FontProviderStackKey key, List<GlyphProvider.Conditional> providers) {
			this.key = key;
			this.providers = providers;
		}
	}

	private static final class ApplyStats {
		private final long startNs;
		private long fontSetNs;
		private int fontSets;
		private int optimized;

		private ApplyStats(long startNs) {
			this.startNs = startNs;
		}
	}

	private FontOptimizationState() {}
}
