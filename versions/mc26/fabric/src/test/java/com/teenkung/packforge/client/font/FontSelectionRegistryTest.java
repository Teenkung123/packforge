package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SpaceProvider;
import com.teenkung.packforge.client.mixin.font.FontManagerPreparationAccessor;
import com.teenkung.packforge.concurrent.PreparationBudget;
import com.teenkung.packforge.config.ReloadFeatureSnapshot;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FontSelectionRegistryTest {
	private static final Identifier FONT = Identifier.fromNamespaceAndPath("test", "font");

	@Test void fullBudgetSkipsGroupingAndSchedulingThenKnownFontsPrepareAfterRelease() {
		ReloadExecutionContext context = start();
		PreparationBudget.Scope scope = context.preparationBudget();
		PreparationBudget.Reservation occupied = scope.tryReserve(context.features().optimizationMemoryMiB() * 1024L * 1024L);
		FontManagerPreparationAccessor preparation = preparation(new SpaceProvider(Map.of(65, 4f)));
		AtomicInteger tasks = new AtomicInteger();
		try {
			assertSame(preparation, FontSelectionRegistry.prepareAsync(preparation, Set.of(), command -> {
				tasks.incrementAndGet(); command.run();
			}).join());
			assertEquals(0, tasks.get(), "Unadmitted grouping cannot queue optimization work");
			occupied.close();
			FontSelectionRegistry.prepareAsync(preparation, Set.of(), command -> { tasks.incrementAndGet(); command.run(); }).join();
			assertTrue(tasks.get() > 0);
			assertTrue(scope.used() > 0);
			FontSelectionRegistry.beginApply(preparation);
			FontPreparedSelection selection = FontSelectionRegistry.currentSelection(FONT, Set.of());
			assertNotNull(selection);
			assertEquals(IntList.of(65), selection.glyphsByWidth().get(4));
		} finally {
			FontSelectionRegistry.clear(); occupied.close(); ReloadExecutionContext.finish(context);
		}
		assertEquals(0, scope.used());
	}

	@Test void cancelledQueuedPreparationStaysChargedUntilItsWorkerReleasesReferences() {
		ReloadExecutionContext context = start();
		PreparationBudget.Scope scope = context.preparationBudget();
		ArrayDeque<Runnable> queued = new ArrayDeque<>();
		FontManagerPreparationAccessor preparation = preparation(new SpaceProvider(Map.of(65, 4f)));
		try {
			var result = FontSelectionRegistry.prepareAsync(preparation, Set.of(), queued::add);
			assertFalse(result.isDone());
			assertTrue(result.cancel(false));
			ReloadExecutionContext.finish(context);
			assertTrue(scope.used() > 0, "Queued grouping and worker references must stay accounted after cancellation/retirement");
			while (!queued.isEmpty()) queued.remove().run();
			assertEquals(0, scope.used());
			FontSelectionRegistry.beginApply(preparation);
			assertNull(FontSelectionRegistry.currentBundle(), "Cancelled results must never publish a bundle");
		} finally { FontSelectionRegistry.clear(); ReloadExecutionContext.finish(context); }
	}

	@Test void unsupportedProviderKeepsOriginalProcessingWithoutQueryingOrPublishingSelection() {
		ReloadExecutionContext context = start();
		GlyphProvider custom = new GlyphProvider() {
			@Override public IntSet getSupportedGlyphs() { throw new AssertionError("Custom provider belongs to vanilla"); }
		};
		FontManagerPreparationAccessor preparation = preparation(custom);
		try {
			assertSame(preparation, FontSelectionRegistry.prepareAsync(preparation, Set.of(), Runnable::run).join());
			FontSelectionRegistry.beginApply(preparation);
			assertNull(FontSelectionRegistry.currentSelection(FONT, Set.of()));
			assertTrue(FontSelectionRegistry.currentBundle().selections().isEmpty());
		} finally { FontSelectionRegistry.clear(); ReloadExecutionContext.finish(context); }
		assertEquals(0, context.preparationBudget().used());
	}

	@Test void staleQueuedPreparationCannotPublishOrReleaseAnotherReloadsBundle() {
		ReloadExecutionContext older = start();
		ArrayDeque<Runnable> queued = new ArrayDeque<>();
		FontManagerPreparationAccessor oldPreparation = preparation(new SpaceProvider(Map.of(65, 4f)));
		var oldResult = FontSelectionRegistry.prepareAsync(oldPreparation, Set.of(), queued::add);
		ReloadExecutionContext newer = start();
		FontManagerPreparationAccessor newPreparation = preparation(new SpaceProvider(Map.of(66, 5f)));
		try {
			FontSelectionRegistry.prepareAsync(newPreparation, Set.of(), Runnable::run).join();
			long newerBytes = newer.preparationBudget().used();
			while (!queued.isEmpty()) queued.remove().run();
			assertSame(oldPreparation, oldResult.join());
			assertEquals(0, older.preparationBudget().used());
			assertEquals(newerBytes, newer.preparationBudget().used());
			FontSelectionRegistry.beginApply(oldPreparation);
			assertNull(FontSelectionRegistry.currentBundle());
			FontSelectionRegistry.beginApply(newPreparation);
			assertEquals(IntList.of(66), FontSelectionRegistry.currentSelection(FONT, Set.of()).glyphsByWidth().get(5));
		} finally {
			FontSelectionRegistry.clear(); ReloadExecutionContext.finish(older); ReloadExecutionContext.finish(newer);
		}
		assertEquals(0, newer.preparationBudget().used());
	}

	private static FontManagerPreparationAccessor preparation(GlyphProvider provider) {
		return () -> Map.of(FONT, List.of(new GlyphProvider.Conditional(provider, FontOption.Filter.ALWAYS_PASS)));
	}

	private static ReloadExecutionContext start() {
		ReloadFeatureSnapshot features = ReloadFeatureSnapshot.capture();
		assertTrue(features.fontPrepareProviderSelectionEnabled(), "Fixture requires mc26 font preparation capability");
		return ReloadExecutionContext.startForTesting(features);
	}
}
