package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SpaceProvider;
import com.mojang.blaze3d.platform.NativeImage;
import com.teenkung.packforge.concurrent.PreparationBudget;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.font.AllMissingGlyphProvider;
import net.minecraft.client.gui.font.CodepointMap;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import net.minecraft.client.gui.font.providers.UnihexProvider;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FontPreparationCoordinatorTest {
	private final PreparationBudget memory = new PreparationBudget();
	private final PreparationBudget.Scope scope = memory.openScope();
	private final GlyphProvider.Conditional fallback = always(new AllMissingGlyphProvider());
	private final List<FontPreparedSelection> ownedSelections = new ArrayList<>();
	@AfterEach void retire() { ownedSelections.forEach(FontPreparedSelection::close); scope.retire(); }

	@Test void initialBookkeepingDenialCanRetryAfterCapacityIsReleased() {
		PreparationBudget.Reservation occupied = scope.tryReserve(memory.limit());
		try (FontPreparationCoordinator coordinator = new FontPreparationCoordinator(scope, Set.of(), true)) {
			ArrayList<GlyphProvider.Conditional> original = reverse(List.of(always(new SpaceProvider(Map.of(65, 4f)))));
			List<GlyphProvider.Conditional> before = List.copyOf(original);
			assertFalse(coordinator.finalizeProviders(original, fallback));
			assertEquals(before, original, "Capacity bypass must leave vanilla's provider list untouched");
			assertEquals(memory.limit(), scope.used());
			occupied.close();
			assertTrue(coordinator.finalizeProviders(original, fallback));
			try (FontPreparedSelection selection = coordinator.selection(reverse(original), Set.of())) {
				assertEquals(IntList.of(65), selection.glyphsByWidth().get(4));
			}
			assertEquals(1, coordinator.diagnostics().budgetFallbacks());
			assertEquals(1, coordinator.diagnostics().admittedStacks());
			assertEquals(1, coordinator.diagnostics().selectionHits());
		} finally { occupied.close(); scope.retire(); }
		assertEquals(0, memory.used());
	}

	@Test void deniedCoordinatorCannotReviveAfterExplicitCloseOrScopeRetirement() {
		PreparationBudget.Reservation occupied = scope.tryReserve(memory.limit());
		FontPreparationCoordinator explicitlyClosed = new FontPreparationCoordinator(scope, Set.of());
		FontPreparationCoordinator retired = new FontPreparationCoordinator(scope, Set.of());
		explicitlyClosed.close();
		occupied.close();
		List<GlyphProvider.Conditional> stack = List.of(always(new SpaceProvider(Map.of(65, 4f))));
		assertFalse(explicitlyClosed.finalizeProviders(reverse(stack), fallback));
		assertEquals(0, scope.used(), "Explicit close forbids later bookkeeping allocation");
		scope.retire();
		assertFalse(retired.finalizeProviders(reverse(stack), fallback));
		retired.close(); retired.close(); explicitlyClosed.close();
		assertEquals(0, memory.used());
	}

	@Test void concurrentRetriesShareOneAccountedBookkeepingAndStack() throws Exception {
		PreparationBudget.Reservation occupied = scope.tryReserve(memory.limit());
		try (FontPreparationCoordinator coordinator = new FontPreparationCoordinator(scope, Set.of(), true);
			var executor = Executors.newFixedThreadPool(8)) {
			occupied.close();
			CountDownLatch start = new CountDownLatch(1);
			List<GlyphProvider.Conditional> stack = List.of(always(new SpaceProvider(Map.of(65, 4f))));
			ArrayList<CompletableFuture<Boolean>> jobs = new ArrayList<>();
			for (int index = 0; index < 8; index++) jobs.add(CompletableFuture.supplyAsync(() -> {
				try { assertTrue(start.await(5, TimeUnit.SECONDS)); }
				catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
				return coordinator.finalizeProviders(reverse(stack), fallback);
			}, executor));
			start.countDown();
			for (CompletableFuture<Boolean> job : jobs) assertTrue(job.get(5, TimeUnit.SECONDS));
			assertEquals(1, coordinator.diagnostics().admittedStacks());
			assertEquals(7, coordinator.diagnostics().memoHits());
			assertTrue(scope.used() < 32 * 1024, "Only one bookkeeping reservation and compact summary remain");
			assertTrue(memory.peak() <= memory.limit());
		} finally { occupied.close(); scope.retire(); }
		assertEquals(0, memory.used());
	}

	@Test void closeRacingWithDelayedAdmissionCannotLeaveLiveEntries() throws Exception {
		try (var executor = Executors.newFixedThreadPool(2)) {
			for (int attempt = 0; attempt < 32; attempt++) {
				PreparationBudget.Scope work = memory.openScope();
				PreparationBudget.Reservation occupied = work.tryReserve(memory.limit());
				FontPreparationCoordinator coordinator = new FontPreparationCoordinator(work, Set.of());
				occupied.close();
				CountDownLatch start = new CountDownLatch(1);
				CompletableFuture<Boolean> preparation = CompletableFuture.supplyAsync(() -> {
					try { assertTrue(start.await(5, TimeUnit.SECONDS)); }
					catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
					return coordinator.finalizeProviders(reverse(List.of(always(new SpaceProvider(Map.of(65, 4f))))), fallback);
				}, executor);
				CompletableFuture<Void> closing = CompletableFuture.runAsync(() -> {
					start.countDown(); coordinator.close();
				}, executor);
				try {
					closing.get(5, TimeUnit.SECONDS); preparation.get(5, TimeUnit.SECONDS);
					assertEquals(0, coordinator.diagnostics().cachedReservationBytes());
					assertFalse(coordinator.finalizeProviders(reverse(List.of(always(new SpaceProvider(Map.of(65, 4f))))), fallback));
				} finally { coordinator.close(); work.retire(); }
				assertEquals(0, memory.used());
			}
		}
	}

	@Test void failureAfterRetirementReleasesWorkerOwnershipWithoutMaskingOriginalError() throws Exception {
		CountDownLatch entered = new CountDownLatch(1), finish = new CountDownLatch(1);
		RuntimeException failure = new RuntimeException("retired worker failure");
		Map<FontOption, Boolean> flags = new AbstractMap<>() {
			@Override public Set<Entry<FontOption, Boolean>> entrySet() {
				entered.countDown();
				try { assertTrue(finish.await(5, TimeUnit.SECONDS)); }
				catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
				throw failure;
			}
		};
		try (FontPreparationCoordinator coordinator = new FontPreparationCoordinator(scope, Set.of());
			var executor = Executors.newSingleThreadExecutor()) {
			CompletableFuture<Boolean> running = CompletableFuture.supplyAsync(() -> coordinator.finalizeProviders(reverse(List.of(
				new GlyphProvider.Conditional(new SpaceProvider(Map.of(65, 4f)), new FontOption.Filter(flags)))), fallback), executor);
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			try { scope.retire(); assertTrue(scope.used() > 0); }
			finally { finish.countDown(); }
			var error = assertThrows(ExecutionException.class, () -> running.get(5, TimeUnit.SECONDS));
			assertSame(failure, error.getCause());
			assertEquals(0, memory.used());
		}
	}

	@Test void knownProvidersMatchActualVanillaBucketsIncludingSpaceAndUnusualAdvances() throws Exception {
		SpaceProvider first = new SpaceProvider(Map.of(0, Float.NaN, 32, 4f, 65, -2.25f, 67, 40.25f,
			68, Float.NEGATIVE_INFINITY, 69, Float.POSITIVE_INFINITY));
		SpaceProvider second = new SpaceProvider(Map.of(65, 8f, 66, 4f));
		check(List.of(always(first), always(second)), Set.of());
	}

	@Test void missingProviderCanWinUnadvertisedCodepointsAndSuppressWidthBuckets() throws Exception {
		FontPreparedSelection selection = check(List.of(always(new AllMissingGlyphProvider()),
			always(new SpaceProvider(Map.of(65, 4f)))), Set.of());
		assertTrue(selection.glyphsByWidth().isEmpty());
		assertEquals(1, selection.activeProviders().size());
	}

	@Test void repeatedProviderInstancesPreserveAllSelectedSlotsAndFilters() throws Exception {
		SpaceProvider same = new SpaceProvider(Map.of(65, 4f));
		List<GlyphProvider.Conditional> stack = List.of(always(same), always(same),
			new GlyphProvider.Conditional(same, new FontOption.Filter(Map.of(FontOption.UNIFORM, true))));
		assertEquals(2, check(stack, Set.of()).activeProviders().size());
		assertEquals(3, check(stack, Set.of(FontOption.UNIFORM)).activeProviders().size());
	}

	@Test void nonemptyUnihexProviderMatchesActualVanillaSelection() throws Exception {
		UnihexProvider.LineData pixels = new UnihexProvider.LineData() {
			public int line(int row) { return 0x7e000000; }
			public int bitWidth() { return 8; }
		};
		Class<?> glyph = Class.forName("net.minecraft.client.gui.font.providers.UnihexProvider$Glyph");
		var glyphConstructor = glyph.getDeclaredConstructor(UnihexProvider.LineData.class, int.class, int.class);
		glyphConstructor.setAccessible(true);
		CodepointMap<Object> glyphs = new CodepointMap<>(Object[]::new, Object[][]::new);
		glyphs.put(0x0e01, glyphConstructor.newInstance(pixels, 1, 6));
		var constructor = UnihexProvider.class.getDeclaredConstructor(CodepointMap.class); constructor.setAccessible(true);
		GlyphProvider provider = (GlyphProvider) constructor.newInstance(glyphs);
		FontPreparedSelection selection = check(List.of(always(provider)), Set.of());
		assertEquals(List.of(provider), selection.activeProviders());
		assertFalse(selection.glyphsByWidth().isEmpty());
	}

	@Test void filteredWarmupWinnerDoesNotOverrideSelectedFallback() throws Exception {
		SpaceProvider uniform = new SpaceProvider(Map.of(32, 9f, 65, 9f));
		SpaceProvider normal = new SpaceProvider(Map.of(32, 4f, 65, 4f));
		List<GlyphProvider.Conditional> stack = List.of(
			new GlyphProvider.Conditional(uniform, new FontOption.Filter(Map.of(FontOption.UNIFORM, true))), always(normal));
		assertEquals(List.of(normal), check(stack, Set.of()).activeProviders());
		assertEquals(List.of(uniform), check(stack, Set.of(FontOption.UNIFORM)).activeProviders());
	}

	@Test void unknownProviderKeepsCompleteOriginalWarmupAndSelectionWithoutPackForgeQueries() {
		GlyphProvider unknown = new GlyphProvider() {
			public IntSet getSupportedGlyphs() { throw new AssertionError("Unknown provider ownership must remain with vanilla"); }
		};
		List<GlyphProvider.Conditional> stack = List.of(always(unknown), always(new SpaceProvider(Map.of(65, 4f))));
		try (FontPreparationCoordinator coordinator = new FontPreparationCoordinator(scope, Set.of(), true)) {
			ArrayList<GlyphProvider.Conditional> lowFirst = reverse(stack);
			assertFalse(coordinator.finalizeProviders(lowFirst, fallback));
			assertEquals(reverse(stack), lowFirst, "Generic fallback must receive its untouched list");
			assertNull(coordinator.selection(stack, Set.of()));
			assertEquals(1, coordinator.diagnostics().unsupportedSelectionFallbacks());
		}
	}

	@Test void coordinatedQueriesPreserveUnfilteredWarmupAndSpaceSkipWithoutRepeatingIdenticalWork() {
		List<GlyphProvider.Conditional> stack = List.of(
			new GlyphProvider.Conditional(new SpaceProvider(Map.of(65, 9f, 32, 9f)), new FontOption.Filter(Map.of(FontOption.UNIFORM, true))),
			always(new SpaceProvider(Map.of(65, 4f, 32, 4f))));
		try (FontPreparationCoordinator coordinator = new FontPreparationCoordinator(scope, Set.of(), true)) {
			assertTrue(coordinator.finalizeProviders(reverse(stack), fallback));
			assertEquals(1, coordinator.diagnostics().warmup(), "Only non-space glyphs are warmed");
			assertEquals(3, coordinator.diagnostics().queries(), "One unfiltered warmup lookup and two filtered selection lookups");
			assertTrue(coordinator.finalizeProviders(reverse(stack), fallback));
			assertEquals(3, coordinator.diagnostics().queries());
			assertEquals(1, coordinator.diagnostics().memoHits());
		}
	}

	@Test void zeroBudgetUsesCompleteVanillaPathWithoutUnaccountedSelection() {
		PreparationBudget.Scope zero = new PreparationBudget(0).openScope();
		try (FontPreparationCoordinator coordinator = new FontPreparationCoordinator(zero, Set.of())) {
			List<GlyphProvider.Conditional> stack = List.of(always(new SpaceProvider(Map.of(65, 4f))));
			assertFalse(coordinator.finalizeProviders(reverse(stack), fallback));
			assertNull(coordinator.selection(stack, Set.of()));
		}
		assertEquals(0, zero.used());
	}

	@Test void substantialUncachedSelectionBypassesAtCapacityThenPreparesOnWorkerAfterRelease() throws Exception {
		PreparationBudget bounded = new PreparationBudget(8L * 1024 * 1024);
		PreparationBudget.Scope work = bounded.openScope();
		PreparationBudget.Reservation occupied = work.tryReserve(bounded.limit());
		Map<Integer, Float> advances = new HashMap<>();
		for (int codepoint = 32; codepoint < 20032; codepoint++) advances.put(codepoint, (float) (codepoint % 17 - 3));
		List<GlyphProvider.Conditional> stack = List.of(always(new SpaceProvider(advances)));
		try (FontPreparationCoordinator coordinator = new FontPreparationCoordinator(work, Set.of(), true);
			var executor = Executors.newSingleThreadExecutor()) {
			assertNull(CompletableFuture.supplyAsync(() -> coordinator.selection(stack, Set.of()), executor).get());
			assertEquals(bounded.limit(), work.used());
			occupied.close();
			FontPreparedSelection selection = CompletableFuture.supplyAsync(() -> coordinator.selection(stack, Set.of()), executor).get();
			assertNotNull(selection, "Known cache misses still prepare on the supplied worker when capacity returns");
			assertVanilla(stack, Set.of(), selection);
			assertEquals(1, coordinator.diagnostics().genericSelections());
			assertEquals(1, coordinator.diagnostics().selectionBudgetFallbacks());
			assertTrue(work.used() > 20000L * Integer.BYTES);
			assertTrue(work.used() < 256 * 1024, "Transient sets and growing bucket arrays must be released after preparation");
			assertTrue(bounded.peak() <= bounded.limit());
			work.retire();
			assertTrue(work.used() > 0, "Retirement cannot uncharge live generic selection ownership");
			selection.close(); selection.close();
			assertEquals(0, bounded.used());
		} finally { occupied.close(); work.retire(); }
	}

	@Test void admittedSummaryBindingBypassesAtCapacityAndCanBindAfterRelease() {
		List<GlyphProvider.Conditional> stack = List.of(always(new SpaceProvider(Map.of(65, 4f))));
		try (FontPreparationCoordinator coordinator = new FontPreparationCoordinator(scope, Set.of(), true)) {
			ArrayList<GlyphProvider.Conditional> original = reverse(stack);
			assertTrue(coordinator.finalizeProviders(original, fallback));
			long before = scope.used();
			try (PreparationBudget.Reservation occupied = scope.tryReserve(memory.limit() - memory.used())) {
				assertNull(coordinator.selection(reverse(original), Set.of()));
				assertEquals(memory.limit(), memory.used());
			}
			assertEquals(before, scope.used());
			try (FontPreparedSelection selection = coordinator.selection(reverse(original), Set.of())) {
				assertEquals(IntList.of(65), selection.glyphsByWidth().get(4));
			}
			assertEquals(before, scope.used());
		}
	}

	@Test void uncachedSelectionFailureReleasesItsTransientLease() {
		RuntimeException failure = new RuntimeException("generic filter failure");
		Map<FontOption, Boolean> flags = new AbstractMap<>() {
			@Override public Set<Entry<FontOption, Boolean>> entrySet() { throw failure; }
		};
		List<GlyphProvider.Conditional> stack = List.of(new GlyphProvider.Conditional(new SpaceProvider(Map.of(65, 4f)), new FontOption.Filter(flags)));
		try (FontPreparationCoordinator coordinator = new FontPreparationCoordinator(scope, Set.of())) {
			long before = scope.used();
			assertSame(failure, assertThrows(RuntimeException.class, () -> coordinator.selection(stack, Set.of())));
			assertEquals(before, scope.used());
		}
	}

	@Test void failedComputationReleasesItsEntryAndAllowsTheSameStackToRetry() {
		RuntimeException failure = new RuntimeException("filter failure");
		AtomicBoolean fail = new AtomicBoolean(true);
		Map<FontOption, Boolean> flags = new AbstractMap<>() {
			@Override public Set<Entry<FontOption, Boolean>> entrySet() {
				if (fail.getAndSet(false)) throw failure;
				return Set.of();
			}
		};
		List<GlyphProvider.Conditional> stack = List.of(new GlyphProvider.Conditional(
			new SpaceProvider(Map.of(65, 4f)), new FontOption.Filter(flags)));
		try (FontPreparationCoordinator coordinator = new FontPreparationCoordinator(scope, Set.of())) {
			long bookkeeping = scope.used();
			assertSame(failure, assertThrows(RuntimeException.class, () -> coordinator.finalizeProviders(reverse(stack), fallback)));
			assertEquals(bookkeeping, scope.used(), "Failed entries must not retain their reservation");
			ArrayList<GlyphProvider.Conditional> retried = reverse(stack);
			assertTrue(coordinator.finalizeProviders(retried, fallback));
			try (FontPreparedSelection selection = coordinator.selection(reverse(retried), Set.of())) {
				assertEquals(IntList.of(65), selection.glyphsByWidth().get(4));
			}
		}
		scope.retire(); assertEquals(0, memory.used());
	}

	@Test void transientPeakIsReleasedSoMultipleLargeStacksCanFitTheSameHardBudget() {
		PreparationBudget bounded = new PreparationBudget(10L * 1024 * 1024);
		PreparationBudget.Scope work = bounded.openScope();
		try (FontPreparationCoordinator coordinator = new FontPreparationCoordinator(work, Set.of(), true)) {
			Map<Integer, Float> advances = new HashMap<>();
			for (int codepoint = 64; codepoint < 20064; codepoint++) advances.put(codepoint, 4f);
			for (int stack = 0; stack < 3; stack++) {
				ArrayList<GlyphProvider.Conditional> original = reverse(List.of(always(new SpaceProvider(advances))));
				assertTrue(coordinator.finalizeProviders(original, fallback), "Completed stacks must not retain their transient peak");
				try (FontPreparedSelection selection = coordinator.selection(reverse(original), Set.of())) {
					assertEquals(20000, selection.glyphsByWidth().get(4).size());
				}
			}
			assertEquals(3, coordinator.diagnostics().admittedStacks());
			assertEquals(0, coordinator.diagnostics().budgetFallbacks());
			assertTrue(coordinator.diagnostics().requestedBytes() > bounded.limit());
			assertTrue(coordinator.diagnostics().transientReleasedBytes() > 0);
			assertTrue(work.used() < 1024L * 1024, "Only compact retained summaries should remain");
			assertTrue(bounded.peak() <= bounded.limit());
		} finally { work.retire(); }
		assertEquals(0, bounded.used());
	}

	@Test void codepointMapCountDoesNotConstructASupportedGlyphSet() {
		class IndexedProvider implements GlyphProvider, FontProviderGlyphMapAccess {
			final CodepointMap<Object> glyphs = new CodepointMap<>(Object[]::new, Object[][]::new);
			public IntSet getSupportedGlyphs() { throw new AssertionError("Counting must not allocate keySet"); }
			public CodepointMap<?> packforge$glyphMap() { return glyphs; }
		}
		IndexedProvider provider = new IndexedProvider();
		provider.glyphs.put(65, new Object()); provider.glyphs.put(0x0e01, new Object());
		assertEquals(2, FontPreparationCoordinator.supportedCountWithoutAllocation(provider));
	}

	@Test void seventeenConcurrentLargeStacksFitWithoutSpeculativeBudgetAdmission() throws Exception {
		PreparationBudget bounded = new PreparationBudget();
		PreparationBudget.Scope work = bounded.openScope();
		CountDownLatch entered = new CountDownLatch(17), finish = new CountDownLatch(1);
		Map<FontOption, Boolean> flags = new AbstractMap<>() {
			@Override public Set<Entry<FontOption, Boolean>> entrySet() {
				entered.countDown();
				try { assertTrue(finish.await(30, TimeUnit.SECONDS)); }
				catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
				return Set.of();
			}
		};
		try (FontPreparationCoordinator coordinator = new FontPreparationCoordinator(work, Set.of(), true);
			var executor = Executors.newFixedThreadPool(17)) {
			Map<Integer, Float> advances = new HashMap<>();
			for (int codepoint = 64; codepoint < 35064; codepoint++) advances.put(codepoint, 4f);
			ArrayList<CompletableFuture<Boolean>> jobs = new ArrayList<>();
			for (int index = 0; index < 17; index++) {
				ArrayList<GlyphProvider.Conditional> original = reverse(List.of(new GlyphProvider.Conditional(
					new SpaceProvider(advances), new FontOption.Filter(flags))));
				jobs.add(CompletableFuture.supplyAsync(() -> coordinator.finalizeProviders(original, fallback), executor));
			}
			try {
				assertTrue(entered.await(30, TimeUnit.SECONDS), "All stacks should obtain independently accounted transient capacity");
				assertTrue(bounded.peak() <= bounded.limit());
			} finally { finish.countDown(); }
			for (CompletableFuture<Boolean> job : jobs) assertTrue(job.get(30, TimeUnit.SECONDS));
			assertEquals(17, coordinator.diagnostics().admittedStacks());
			assertEquals(0, coordinator.diagnostics().budgetFallbacks());
			assertTrue(work.used() < 4L * 1024 * 1024);
		} finally { finish.countDown(); work.retire(); }
		assertEquals(0, bounded.used());
	}

	@Test void oversizedTransientWorkBypassesWithoutLeakingOrBlockingASmallerStack() {
		PreparationBudget bounded = new PreparationBudget(2L * 1024 * 1024);
		PreparationBudget.Scope work = bounded.openScope();
		try (FontPreparationCoordinator coordinator = new FontPreparationCoordinator(work, Set.of(), true)) {
			Map<Integer, Float> advances = new HashMap<>();
			for (int codepoint = 64; codepoint < 20064; codepoint++) advances.put(codepoint, 4f);
			ArrayList<GlyphProvider.Conditional> original = reverse(List.of(always(new SpaceProvider(advances))));
			List<GlyphProvider.Conditional> before = List.copyOf(original);
			long bookkeeping = work.used();
			assertFalse(coordinator.finalizeProviders(original, fallback));
			assertEquals(before, original);
			assertEquals(bookkeeping, work.used());
			assertTrue(coordinator.finalizeProviders(reverse(List.of(always(new SpaceProvider(Map.of(65, 4f))))), fallback));
			assertEquals(1, coordinator.diagnostics().budgetFallbacks());
			assertTrue(bounded.peak() <= bounded.limit());
		} finally { work.retire(); }
		assertEquals(0, bounded.used());
	}

	@Test void retainedEstimateCoversDistinctWidthBucketsAndPreservesRetiredBindingOwnership() {
		try (FontPreparationCoordinator coordinator = new FontPreparationCoordinator(scope, Set.of(), true)) {
			Map<Integer, Float> advances = new HashMap<>();
			for (int index = 1; index <= 1000; index++) advances.put(index, (float) index);
			ArrayList<GlyphProvider.Conditional> original = reverse(List.of(always(new SpaceProvider(advances))));
			assertTrue(coordinator.finalizeProviders(original, fallback));
			FontPreparedSelection selection = coordinator.selection(reverse(original), Set.of());
			assertEquals(1000, selection.glyphsByWidth().size());
			assertTrue(coordinator.diagnostics().transientReleasedBytes() > 0);
			scope.retire();
			assertTrue(scope.used() > 0);
			selection.close();
			assertEquals(0, scope.used());
		}
	}

	@Test void retiredScopeKeepsLiveSelectionChargedUntilItsBundleReleasesIt() {
		FontPreparationCoordinator coordinator = new FontPreparationCoordinator(scope, Set.of());
		ArrayList<GlyphProvider.Conditional> original = reverse(List.of(always(new SpaceProvider(Map.of(65, 4f)))));
		assertTrue(coordinator.finalizeProviders(original, fallback));
		FontPreparedSelection selection = coordinator.selection(reverse(original), Set.of());
		FontPreparationBundle bundle = new FontPreparationBundle(Set.of(), Map.of(Identifier.fromNamespaceAndPath("test", "font"), selection),
			Map.of(), FontReloadDiagnostics.Snapshot.EMPTY, coordinator, scope.tryReserve(16384));
		IntList transferred = selection.glyphsByWidth().get(4);
		scope.retire();
		assertTrue(scope.used() > 0, "A live binding still retains the candidate summary");
		assertFalse(selection.glyphsByWidth().isEmpty());
		bundle.close(); bundle.close();
		assertTrue(bundle.selections().isEmpty());
		assertTrue(selection.glyphsByWidth().isEmpty());
		assertEquals(IntList.of(65), transferred, "Closing metadata must not mutate lists already transferred to FontSet");
		assertEquals(0, memory.used());
	}

	@Test void retirementDuringComputationKeepsMemoryChargedUntilTheWorkerFinishes() throws Exception {
		CountDownLatch entered = new CountDownLatch(1), finish = new CountDownLatch(1);
		Map<FontOption, Boolean> flags = new AbstractMap<>() {
			@Override public Set<Entry<FontOption, Boolean>> entrySet() {
				entered.countDown();
				try { assertTrue(finish.await(5, TimeUnit.SECONDS)); }
				catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
				return Set.of();
			}
		};
		List<GlyphProvider.Conditional> stack = List.of(new GlyphProvider.Conditional(
			new SpaceProvider(Map.of(65, 4f)), new FontOption.Filter(flags)));
		try (FontPreparationCoordinator coordinator = new FontPreparationCoordinator(scope, Set.of());
			var executor = Executors.newSingleThreadExecutor()) {
			CompletableFuture<Boolean> running = CompletableFuture.supplyAsync(() -> coordinator.finalizeProviders(reverse(stack), fallback), executor);
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			try {
				scope.retire();
				assertTrue(scope.used() > 0, "A retired scope cannot uncharge a still-running computation");
			} finally { finish.countDown(); }
			assertTrue(running.get(5, TimeUnit.SECONDS));
			assertEquals(0, memory.used());
			assertFalse(coordinator.finalizeProviders(reverse(stack), fallback));
		}
	}

	@Test void coordinatorExecutorBindingIsRestoredAcrossOverlappingPreparations() throws Exception {
		PreparationBudget.Scope otherScope = memory.openScope();
		try (FontPreparationCoordinator first = new FontPreparationCoordinator(scope, Set.of());
			FontPreparationCoordinator second = new FontPreparationCoordinator(otherScope, Set.of(FontOption.UNIFORM));
			var executor = Executors.newSingleThreadExecutor()) {
			assertSame(first, CompletableFuture.supplyAsync(FontPreparationCoordinator::current, first.executor(executor)).get());
			assertSame(second, CompletableFuture.supplyAsync(FontPreparationCoordinator::current, second.executor(executor)).get());
			assertNull(CompletableFuture.supplyAsync(FontPreparationCoordinator::current, executor).get());
			first.close();
			List<GlyphProvider.Conditional> stack = List.of(always(new SpaceProvider(Map.of(65, 4f))));
			assertTrue(second.finalizeProviders(reverse(stack), fallback));
		} finally { otherScope.retire(); scope.retire(); }
		assertEquals(0, memory.used());
	}

	@Test void equalBitmapDefinitionsKeepIndependentMinecraftOwnersAndDuplicateSlots() throws Exception {
		GlyphProvider first = bitmap(), second = bitmap(), third = bitmap();
		try (FontPreparationCoordinator coordinator = new FontPreparationCoordinator(scope, Set.of())) {
			ArrayList<GlyphProvider.Conditional> one = reverse(List.of(always(first), always(first)));
			ArrayList<GlyphProvider.Conditional> two = reverse(List.of(always(second), always(third)));
			assertTrue(coordinator.finalizeProviders(one, fallback));
			assertTrue(coordinator.finalizeProviders(two, fallback));
			List<GlyphProvider.Conditional> oneActual = reverse(one), twoActual = reverse(two);
			FontPreparedSelection a = coordinator.selection(oneActual, Set.of());
			FontPreparedSelection b = coordinator.selection(twoActual, Set.of());
			assertVanilla(oneActual, Set.of(), a); assertVanilla(twoActual, Set.of(), b);
			assertEquals(List.of(first, first), a.activeProviders());
			assertEquals(List.of(second), b.activeProviders());
			assertNotSame(a.glyphsByWidth(), b.glyphsByWidth(), "Equal source data cannot substitute another Minecraft-owned provider");
			assertThrows(UnsupportedOperationException.class, () -> a.glyphsByWidth().get(4).add(100));
			a.close(); b.close();
		} finally { first.close(); second.close(); third.close(); }
		scope.retire(); assertEquals(0, memory.used());
	}

	private FontPreparedSelection check(List<GlyphProvider.Conditional> stack, Set<FontOption> options) throws Exception {
		try (FontPreparationCoordinator coordinator = new FontPreparationCoordinator(scope, options)) {
			ArrayList<GlyphProvider.Conditional> lowFirst = reverse(stack);
			assertTrue(coordinator.finalizeProviders(lowFirst, fallback));
			List<GlyphProvider.Conditional> actual = reverse(lowFirst);
			FontPreparedSelection selection = coordinator.selection(actual, options);
			assertVanilla(actual, options, selection);
			ownedSelections.add(selection);
			return selection;
		}
	}

	@SuppressWarnings("unchecked")
	private static void assertVanilla(List<GlyphProvider.Conditional> providers, Set<FontOption> options, FontPreparedSelection result) throws Exception {
		FontSet vanilla = new FontSet(null);
		var select = FontSet.class.getDeclaredMethod("selectProviders", List.class, Set.class); select.setAccessible(true);
		var widths = FontSet.class.getDeclaredField("glyphsByWidth"); widths.setAccessible(true);
		assertEquals(select.invoke(vanilla, providers, options), result.activeProviders());
		assertEquals((Int2ObjectMap<IntList>) widths.get(vanilla), result.glyphsByWidth());
	}

	private static ArrayList<GlyphProvider.Conditional> reverse(List<GlyphProvider.Conditional> providers) {
		ArrayList<GlyphProvider.Conditional> copy = new ArrayList<>(providers); Collections.reverse(copy); return copy;
	}
	private static GlyphProvider.Conditional always(GlyphProvider provider) { return new GlyphProvider.Conditional(provider, FontOption.Filter.ALWAYS_PASS); }

	private static GlyphProvider bitmap() throws Exception {
		NativeImage image = new NativeImage(1, 1, false);
		try {
			image.setPixel(0, 0, 0xffffffff);
			Class<?> glyph = Class.forName("net.minecraft.client.gui.font.providers.BitmapProvider$Glyph");
			Class<?> imageType = glyph.getRecordComponents()[1].getType();
			Object imageData = image;
			// These are test-only constructors. newer Minecraft moved image ownership into a holder;
			// production still lets Minecraft construct and close the complete provider.
			if (imageType != NativeImage.class) {
				assertEquals("net.minecraft.client.gui.font.providers.BitmapProvider$ImageDataHolder", imageType.getName());
				var holder = imageType.getDeclaredConstructor(Identifier.class, NativeImage.class);
				holder.setAccessible(true);
				imageData = holder.newInstance(Identifier.fromNamespaceAndPath("packforge", "test/bitmap"), image);
			}
			var glyphConstructor = glyph.getDeclaredConstructor(float.class, imageType, int.class, int.class, int.class, int.class, int.class, int.class);
			glyphConstructor.setAccessible(true);
			CodepointMap<Object> glyphs = new CodepointMap<>(Object[]::new, Object[][]::new);
			glyphs.put(65, glyphConstructor.newInstance(1f, imageData, 0, 0, 1, 1, 4, 1));
			var constructor = BitmapProvider.class.getDeclaredConstructor(imageType, CodepointMap.class);
			constructor.setAccessible(true);
			return (GlyphProvider) constructor.newInstance(imageData, glyphs);
		} catch (Exception | Error failure) {
			image.close();
			throw failure;
		}
	}
}
