package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SpaceProvider;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import com.teenkung.packforge.client.mixin.font.FontProviderGlyphMapAccessor;
import com.teenkung.packforge.concurrent.PreparationBudget;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.font.AllMissingGlyphProvider;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import net.minecraft.client.gui.font.providers.UnihexProvider;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One coordinator per prepare invocation, carried by its supplied executor. */
public final class FontPreparationCoordinator implements AutoCloseable {
	// N = sum advertised counts, U <= N union glyphs, D <= U width buckets.
	// Planning streams one set and uses one packed plan: < 96N bytes including
	// growth slack. After planning returns, bucketing uses 8U scratch + 4U final
	// payload + <= 150D map/list/array overhead (< 162N). 192N leaves alignment
	// margin; provider/slot metadata and preliminary counting have separate charges.
	private static final long BYTES_PER_ADVERTISED_GLYPH = 192L;
	private static final long BOOKKEEPING_BYTES = 8192L;
	private static final ThreadLocal<FontPreparationCoordinator> CURRENT = new ThreadLocal<>();
	private final PreparationBudget.Scope budget;
	private final ReloadExecutionContext context;
	private final Set<FontOption> options;
	private final boolean diagnostics;
	private final boolean fixtureCounts;
	private Map<StackKey, Entry> entries = Map.of();
	private PreparationBudget.Reservation bookkeeping;
	private final AtomicLong queryCount = new AtomicLong();
	private final AtomicLong warmupCount = new AtomicLong();
	private final AtomicLong memoHits = new AtomicLong();
	private final AtomicLong computeNs = new AtomicLong();
	private final AtomicLong attempted = new AtomicLong();
	private final AtomicLong unknownFallbacks = new AtomicLong();
	private final AtomicLong budgetFallbacks = new AtomicLong();
	private final AtomicLong admitted = new AtomicLong();
	private final AtomicLong advertised = new AtomicLong();
	private final AtomicLong requestedBytes = new AtomicLong();
	private final AtomicLong genericSelections = new AtomicLong();
	private final AtomicLong selectionBudgetFallbacks = new AtomicLong();
	private final AtomicLong unsupportedSelectionFallbacks = new AtomicLong();
	private final AtomicLong selectionHits = new AtomicLong();
	private final AtomicLong transientReleasedBytes = new AtomicLong();
	private final AtomicLong retainedSummaryBytes = new AtomicLong();
	private long spaceCodecCalls, spaceCodecEntries, spaceCodecAdmitted, spaceCodecAdmittedEntries;
	private long spaceCodecSmallBypasses, spaceCodecInputBypasses, spaceCodecBudgetBypasses, spaceCodecStaleBypasses;
	private boolean closed;

	public FontPreparationCoordinator(ReloadExecutionContext context, Set<FontOption> options) {
		this(context == null ? null : context.preparationBudget(), context, options,
			context != null && context.features().fontReloadDiagnosticsEnabled());
	}

	FontPreparationCoordinator(PreparationBudget.Scope budget, Set<FontOption> options) {
		this(budget, null, options, false);
	}

	FontPreparationCoordinator(PreparationBudget.Scope budget, Set<FontOption> options, boolean diagnostics) {
		this(budget, null, options, diagnostics);
	}

	private FontPreparationCoordinator(PreparationBudget.Scope budget, ReloadExecutionContext context, Set<FontOption> options, boolean diagnostics) {
		this.fixtureCounts = context == null;
		this.budget = budget; this.context = context; this.options = Set.copyOf(options); this.diagnostics = diagnostics;
		// Reserve essential bookkeeping before provider loads can admit optional work.
		// Capacity denial is temporary: a later stack can retry after older owners release.
		ensureBookkeeping();
	}

	private synchronized boolean ensureBookkeeping() {
		if (closed || budget == null || budget.isRetired()) return false;
		if (bookkeeping != null) return true;
		PreparationBudget.Reservation allocation = budget.tryReserve(BOOKKEEPING_BYTES);
		if (allocation == null) return false;
		try {
			entries = new HashMap<>();
			bookkeeping = allocation;
			budget.onRetire(() -> {
				try { close(); }
				// The retirement callback retains this coordinator even after early close.
				finally { allocation.close(); }
			});
			return !closed;
		} catch (RuntimeException | Error failure) {
			entries = Map.of();
			bookkeeping = null;
			allocation.close();
			throw failure;
		}
	}

	public static FontPreparationCoordinator current() { return CURRENT.get(); }

	public Executor executor(Executor supplied) {
		return command -> supplied.execute(() -> {
			FontPreparationCoordinator previous = CURRENT.get();
			CURRENT.set(this);
			try {
				if (context == null) command.run();
				else try (ReloadExecutionContext.Scope ignored = ReloadExecutionContext.bind(context)) { command.run(); }
			} finally {
				if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
			}
		});
	}

	/** Returns false before touching the list when generic vanilla warmup is required. */
	public boolean finalizeProviders(List<GlyphProvider.Conditional> original, GlyphProvider.Conditional fallback) {
		if (diagnostics) attempted.incrementAndGet();
		if (budget == null || budget.isRetired()) return false;
		if (!ensureBookkeeping()) { if (diagnostics) budgetFallbacks.incrementAndGet(); return false; }
		PreparationBudget.Reservation preliminary = budget.tryReserve(2048L + (original.size() + 1L) * 256L);
		if (preliminary == null) { if (diagnostics) budgetFallbacks.incrementAndGet(); return false; }
		try { return finalizeReserved(original, fallback); }
		finally { preliminary.close(); }
	}

	private boolean finalizeReserved(List<GlyphProvider.Conditional> original, GlyphProvider.Conditional fallback) {
		ArrayList<GlyphProvider.Conditional> providers = new ArrayList<>(original.size() + 1);
		for (int index = original.size() - 1; index >= 0; index--) providers.add(original.get(index));
		providers.add(fallback);
		if (!known(providers)) { if (diagnostics) unknownFallbacks.incrementAndGet(); return false; }
		StackKey key = new StackKey(providers);
		Entry entry;
		synchronized (this) {
			if (closed) { if (diagnostics) budgetFallbacks.incrementAndGet(); return false; }
			entry = entries.get(key);
			if (entry != null) entry.references.incrementAndGet();
		}
		if (entry != null && entry.owner == Thread.currentThread() && !entry.result.isDone()) { entry.release(); return false; }
		if (entry == null) {
			long count = 0;
			for (GlyphProvider.Conditional provider : providers) {
				int size = supportedCount(provider.provider());
				if (size < 0) { if (diagnostics) unknownFallbacks.incrementAndGet(); return false; }
				count += size;
			}
			// Includes union/index maps, immutable summaries, snapshots and map nodes.
			long requested = 8192L + providers.size() * 512L + count * BYTES_PER_ADVERTISED_GLYPH;
			if (diagnostics) { advertised.addAndGet(count); requestedBytes.addAndGet(requested); }
			PreparationBudget.Reservation allocation = budget.tryReserve(requested);
			if (allocation == null) { if (diagnostics) budgetFallbacks.incrementAndGet(); return false; }
			Entry candidate = new Entry(allocation);
			synchronized (this) {
				if (closed || budget.isRetired()) { allocation.close(); return false; }
				entry = entries.putIfAbsent(key, candidate);
				if (entry != null) entry.references.incrementAndGet();
			}
			if (entry == null) {
				if (diagnostics) admitted.incrementAndGet();
				try {
					original.add(0, fallback);
					FontSelectionSummary summary = compute(providers);
					// Planning maps and the ordered scratch arrays are unreachable after compute returns.
					long retainedBytes = 4096L + providers.size() * 128L + summary.retainedBytes();
					long transientBytes = allocation.bytes() - retainedBytes;
					allocation.reduceTo(retainedBytes);
					if (diagnostics) {
						transientReleasedBytes.addAndGet(transientBytes);
						retainedSummaryBytes.addAndGet(retainedBytes);
					}
					candidate.result.complete(summary);
				} catch (Throwable error) {
					synchronized (this) {
						if (!closed) {
							entries.remove(key, candidate);
							if (entries.isEmpty()) entries = new HashMap<>();
						}
					}
					candidate.result.completeExceptionally(error);
					candidate.releaseCache();
					throw error;
				} finally { candidate.release(); }
				return true;
			}
			allocation.close();
		}
		try {
			original.add(0, fallback);
			entry.result.join();
			if (diagnostics) memoHits.incrementAndGet();
			return true;
		} finally { entry.release(); }
	}

	/** Returns null only when complete vanilla selection is required by ownership or capacity. */
	public FontPreparedSelection selection(List<GlyphProvider.Conditional> providers, Set<FontOption> currentOptions) {
		if (!ensureBookkeeping()) { if (diagnostics) selectionBudgetFallbacks.incrementAndGet(); return null; }
		// Covers the lookup key and active-provider binding before either is allocated.
		PreparationBudget.Reservation binding = budget.tryReserve(2048L + providers.size() * 512L);
		if (binding == null) { if (diagnostics) selectionBudgetFallbacks.incrementAndGet(); return null; }
		boolean transferred = false;
		try {
			Entry entry;
			synchronized (this) {
				if (closed) { if (diagnostics) selectionBudgetFallbacks.incrementAndGet(); return null; }
				entry = entries.isEmpty() || !options.equals(currentOptions) ? null : entries.get(new StackKey(providers));
				if (entry != null) entry.references.incrementAndGet();
			}
			if (entry != null) {
				try {
					FontSelectionSummary summary = entry.result.join();
					FontPreparedSelection selection = new FontPreparedSelection(providers, summary, () -> {
						binding.close(); entry.release();
					});
					transferred = true;
					if (diagnostics) selectionHits.incrementAndGet();
					return selection;
				} finally { if (!transferred) entry.release(); }
			}
			// A missed coordinated warmup still prepares known fonts on this worker.
			// Unknown providers may allocate arbitrary supported sets, so retain their
			// complete original warmup and selection rather than an unbounded PF copy.
			if (!known(providers)) { if (diagnostics) unsupportedSelectionFallbacks.incrementAndGet(); return null; }
			long count = 0;
			for (GlyphProvider.Conditional provider : providers) {
				int size = supportedCount(provider.provider());
				if (size < 0) { if (diagnostics) unsupportedSelectionFallbacks.incrementAndGet(); return null; }
				count += size;
			}
			PreparationBudget.Reservation allocation = budget.tryReserve(8192L + providers.size() * 512L + count * 256L);
			if (allocation == null) { if (diagnostics) selectionBudgetFallbacks.incrementAndGet(); return null; }
			if (diagnostics) genericSelections.incrementAndGet();
			return FontPreparedSelection.compute(providers, currentOptions, allocation);
		} finally { if (!transferred) binding.close(); }
	}

	private int supportedCount(GlyphProvider provider) {
		// Plain JVM fixtures are not transformed by Mixin. Production never uses this seam.
		return fixtureCounts ? provider.getSupportedGlyphs().size() : supportedCountWithoutAllocation(provider);
	}

	static int supportedCountWithoutAllocation(GlyphProvider provider) {
		if (provider.getClass() == AllMissingGlyphProvider.class) return 0;
		// SpaceProvider returns an unmodifiable view of its existing primitive map's keys.
		if (provider.getClass() == SpaceProvider.class) return provider.getSupportedGlyphs().size();
		if (!(provider instanceof FontProviderGlyphMapAccessor accessor)) return -1;
		int[] count = {0};
		accessor.packforge$glyphMap().forEach((codepoint, glyph) -> count[0]++);
		return count[0];
	}

	private FontSelectionSummary compute(List<GlyphProvider.Conditional> providers) {
		long started = System.nanoTime();
		OrderedGlyphs ordered = prepareOrdered(providers);
		// All provider sets, union sets and packed plans have left scope before bucket allocation.
		Int2IntOpenHashMap counts = new Int2IntOpenHashMap();
		for (int index = 0; index < ordered.size; index++) counts.addTo(ordered.widths[index], 1);
		Int2ObjectMap<IntList> buckets = new Int2ObjectOpenHashMap<>(counts.size());
		for (var entry : counts.int2IntEntrySet()) {
			buckets.put(entry.getIntKey(), IntArrayList.wrap(new int[entry.getIntValue()]));
			entry.setValue(0);
		}
		for (int index = 0; index < ordered.size; index++) {
			int width = ordered.widths[index];
			buckets.get(width).set(counts.addTo(width, 1), ordered.codepoints[index]);
		}
		FontSelectionSummary summary = new FontSelectionSummary(ordered.winningSlots, ordered.selectedSlots, buckets, System.nanoTime() - started);
		if (diagnostics) computeNs.addAndGet(System.nanoTime() - started);
		return summary;
	}

	private OrderedGlyphs prepareOrdered(List<GlyphProvider.Conditional> providers) {
		IntOpenHashSet warmUnion = new IntOpenHashSet();
		Int2LongOpenHashMap plans = new Int2LongOpenHashMap();
		plans.defaultReturnValue(-1L);
		// Stream and discard one set at a time. Preserve the exact vanilla addAll order.
		for (int slot = providers.size() - 1; slot >= 0; slot--) {
			GlyphProvider provider = providers.get(slot).provider();
			IntSet supported = provider.getSupportedGlyphs();
			warmUnion.addAll((IntCollection) supported);
			if (provider.getClass() == AllMissingGlyphProvider.class) {
				for (int codepoint : warmUnion) plans.put(codepoint, pair(slot, -1));
			} else {
				for (int codepoint : supported) plans.put(codepoint, pair(slot, -1));
			}
		}
		IntOpenHashSet selectedUnion = new IntOpenHashSet();
		boolean[] selected = new boolean[providers.size()];
		int firstMissing = -1;
		for (int slot = 0; slot < providers.size(); slot++) {
			selected[slot] = providers.get(slot).filter().apply(options);
			if (!selected[slot]) continue;
			GlyphProvider provider = providers.get(slot).provider();
			IntSet supported = provider.getSupportedGlyphs();
			selectedUnion.addAll((IntCollection) supported);
			if (provider.getClass() == AllMissingGlyphProvider.class) {
				if (firstMissing < 0) firstMissing = slot;
				for (int codepoint : selectedUnion) setSelectionIfAbsent(plans, codepoint, firstMissing);
			} else {
				for (int codepoint : supported) setSelectionIfAbsent(plans, codepoint, firstMissing < 0 ? slot : firstMissing);
			}
		}
		boolean[] active = new boolean[providers.size()];
		Lookup warm = new Lookup(), chosen = new Lookup();
		long queries = 0, warmed = 0;
		for (int codepoint : warmUnion) {
			long plan = plans.get(codepoint);
			int warmSlot = (int) (plan >> 32), selectionSlot = (int) plan;
			warm.clear();
			if (codepoint != 32 && warmSlot >= 0) {
				resolve(providers, codepoint, warmSlot, null, warm);
				queries += warm.queries; warmed++;
			}
			plans.put(codepoint, pair(0, -1));
			if (selectionSlot < 0) continue;
			Lookup result;
			if (codepoint != 32 && selectionSlot == warmSlot && warm.slot >= 0 && selected[warm.slot]) result = warm;
			else {
				resolve(providers, codepoint, selectionSlot, selected, chosen);
				queries += chosen.queries; result = chosen;
			}
			if (result.glyph == null) continue;
			active[result.slot] = true;
			if (result.glyph.info() != SpecialGlyphs.MISSING) {
				plans.put(codepoint, pair(Mth.ceil(result.glyph.info().getAdvance(false)), result.slot));
			}
		}
		int[] codepoints = new int[selectedUnion.size()], widths = new int[selectedUnion.size()];
		int size = 0;
		for (int codepoint : selectedUnion) {
			long plan = plans.get(codepoint);
			if ((int) plan < 0) continue;
			codepoints[size] = codepoint; widths[size] = (int) (plan >> 32); size++;
		}
		ArrayList<Integer> winningSlots = new ArrayList<>(), selectedSlots = new ArrayList<>();
		for (int slot = 0; slot < providers.size(); slot++) {
			if (active[slot]) winningSlots.add(slot);
			if (selected[slot]) selectedSlots.add(slot);
		}
		if (diagnostics) { queryCount.addAndGet(queries); warmupCount.addAndGet(warmed); }
		return new OrderedGlyphs(codepoints, widths, size, winningSlots, selectedSlots);
	}

	private static void setSelectionIfAbsent(Int2LongOpenHashMap plans, int codepoint, int slot) {
		long plan = plans.get(codepoint);
		if ((int) plan < 0) plans.put(codepoint, pair((int) (plan >> 32), slot));
	}

	private static long pair(int high, int low) { return ((long) high << 32) | (low & 0xffffffffL); }
	private record OrderedGlyphs(int[] codepoints, int[] widths, int size, List<Integer> winningSlots, List<Integer> selectedSlots) {}
	private static void resolve(List<GlyphProvider.Conditional> providers, int codepoint, int first,
		boolean[] selected, Lookup result) {
		result.clear();
		for (int slot = first; slot < providers.size(); slot++) {
			if (selected != null && !selected[slot]) continue;
			UnbakedGlyph glyph = providers.get(slot).provider().getGlyph(codepoint);
			result.queries++;
			if (glyph != null) { result.slot = slot; result.glyph = glyph; return; }
		}
	}

	private static final class Lookup {
		int slot, queries;
		UnbakedGlyph glyph;
		void clear() { slot = -1; queries = 0; glyph = null; }
	}
	static boolean known(List<GlyphProvider.Conditional> providers) {
		for (GlyphProvider.Conditional conditional : providers) {
			if (conditional.filter().getClass() != FontOption.Filter.class) return false;
			GlyphProvider provider = conditional.provider();
			Class<?> type = provider.getClass();
			if (type != SpaceProvider.class && type != BitmapProvider.class && type != UnihexProvider.class
				&& type != TrueTypeGlyphProvider.class && type != AllMissingGlyphProvider.class) return false;
		}
		return true;
	}

	synchronized SpaceAdvanceCodec.Outcome admitSpaceCodec() {
		if (ensureBookkeeping()) return SpaceAdvanceCodec.Outcome.ADMITTED;
		return closed || budget == null || budget.isRetired() ? SpaceAdvanceCodec.Outcome.STALE : SpaceAdvanceCodec.Outcome.BUDGET;
	}

	void recordSpaceCodec(int count, SpaceAdvanceCodec.Outcome outcome) {
		if (!diagnostics) return;
		synchronized (this) {
			// These primitive fields already exist in the coordinator. Updating terminal
			// counters allocates nothing and remains synchronized after close/retirement.
			spaceCodecCalls++; spaceCodecEntries += count;
			switch (outcome) {
				case ADMITTED -> { spaceCodecAdmitted++; spaceCodecAdmittedEntries += count; }
				case SMALL -> spaceCodecSmallBypasses++;
				case INPUT -> spaceCodecInputBypasses++;
				case BUDGET -> spaceCodecBudgetBypasses++;
				case STALE -> spaceCodecStaleBypasses++;
			}
		}
	}

	public synchronized Statistics diagnostics() {
		long cachedBytes = 0;
		synchronized (this) { for (Entry entry : entries.values()) cachedBytes += entry.allocation.bytes(); }
		return new Statistics(queryCount.get(), warmupCount.get(), memoHits.get(), computeNs.get(), attempted.get(),
			unknownFallbacks.get(), budgetFallbacks.get(), admitted.get(), advertised.get(), requestedBytes.get(),
			genericSelections.get(), selectionHits.get(), cachedBytes, budget == null ? 0 : budget.used(),
			transientReleasedBytes.get(), retainedSummaryBytes.get(), selectionBudgetFallbacks.get(), unsupportedSelectionFallbacks.get(),
			spaceCodecCalls, spaceCodecEntries, spaceCodecAdmitted, spaceCodecAdmittedEntries,
			spaceCodecSmallBypasses, spaceCodecInputBypasses, spaceCodecBudgetBypasses, spaceCodecStaleBypasses);
	}

	public record Statistics(long queries, long warmup, long memoHits, long computeNs, long attempted,
		long unknownFallbacks, long budgetFallbacks, long admittedStacks, long advertisedEntries,
		long requestedBytes, long genericSelections, long selectionHits, long cachedReservationBytes, long scopeUsedBytes,
		long transientReleasedBytes, long retainedSummaryBytes, long selectionBudgetFallbacks, long unsupportedSelectionFallbacks,
		long spaceCodecCalls, long spaceCodecEntries, long spaceCodecAdmitted, long spaceCodecAdmittedEntries,
		long spaceCodecSmallBypasses, long spaceCodecInputBypasses, long spaceCodecBudgetBypasses, long spaceCodecStaleBypasses) {}

	@Override public void close() {
		List<Entry> retired;
		synchronized (this) {
			if (closed) return;
			closed = true; retired = new ArrayList<>(entries.values()); entries = Map.of();
		}
		for (Entry entry : retired) entry.releaseCache();
	}

	private static final class Entry {
		final PreparationBudget.Reservation allocation;
		final CompletableFuture<FontSelectionSummary> result = new CompletableFuture<>();
		final Thread owner = Thread.currentThread();
		// Cache ownership and active computation are independent of selection bindings.
		final AtomicInteger references = new AtomicInteger(2);
		final AtomicBoolean cacheReleased = new AtomicBoolean();
		Entry(PreparationBudget.Reservation allocation) { this.allocation = allocation; }
		void releaseCache() { if (cacheReleased.compareAndSet(false, true)) release(); }
		void release() { if (references.decrementAndGet() == 0) allocation.close(); }
	}

	private static final class StackKey {
		final Object[] identities;
		final int hash;
		StackKey(List<GlyphProvider.Conditional> providers) {
			identities = new Object[providers.size() * 2];
			int value = 1;
			for (int index = 0; index < providers.size(); index++) {
				identities[index * 2] = providers.get(index).provider();
				identities[index * 2 + 1] = providers.get(index).filter();
				value = 31 * value + System.identityHashCode(identities[index * 2]);
				value = 31 * value + System.identityHashCode(identities[index * 2 + 1]);
			}
			hash = value;
		}
		@Override public int hashCode() { return hash; }
		@Override public boolean equals(Object other) {
			if (!(other instanceof StackKey key) || identities.length != key.identities.length) return false;
			for (int index = 0; index < identities.length; index++) if (identities[index] != key.identities[index]) return false;
			return true;
		}
	}
}
