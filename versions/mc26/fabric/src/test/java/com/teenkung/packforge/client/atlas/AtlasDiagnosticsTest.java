package com.teenkung.packforge.client.atlas;

import com.teenkung.packforge.concurrent.PreparationBudget;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtlasDiagnosticsTest {
	private static final Identifier ATLAS = Identifier.fromNamespaceAndPath("test", "atlas");
	private static final Identifier SPRITE = Identifier.fromNamespaceAndPath("test", "sprite");

	@Test
	void queuedAndNestedInvocationsRestoreExactAtlasIdentityAfterFailure() {
		AtlasLoadInvocation first = new AtlasLoadInvocation(ATLAS, false);
		AtlasLoadInvocation second = new AtlasLoadInvocation(Identifier.fromNamespaceAndPath("test", "second"), false);
		ArrayDeque<Runnable> queue = new ArrayDeque<>();
		Executor executor = first.bind(queue::add);
		executor.execute(() -> {
			assertSame(first, AtlasLoadInvocation.current());
			assertThrows(IllegalStateException.class, () -> second.call(() -> {
				assertSame(second, AtlasLoadInvocation.current());
				throw new IllegalStateException("nested failure");
			}));
			assertSame(first, AtlasLoadInvocation.current());
		});
		assertNull(AtlasLoadInvocation.current());
		second.call(() -> {
			queue.remove().run();
			assertSame(second, AtlasLoadInvocation.current());
			return null;
		});
		assertNull(AtlasLoadInvocation.current());
	}

	@Test
	void spriteReadProbePreservesBytesCloseAndNullResultWithoutCrossReloadMixing() {
		PreparationBudget budget = new PreparationBudget(16384);
		PreparationBudget.Scope scope = budget.openScope();
		AtlasDiagnostics first = diagnostics(scope, 11);
		AtlasDiagnostics second = diagnostics(scope, 12);
		AtomicInteger closed = new AtomicInteger();
		SpriteResourceLoader delegate = (id, resource) -> {
			assertSame(SPRITE, id);
			try (InputStream input = AtlasDiagnostics.observeStream(new ByteArrayInputStream(new byte[]{1, 2, 3, 4}) {
				@Override public void close() { closed.incrementAndGet(); }
			}, System.nanoTime())) {
				assertEquals(1, input.read());
				assertEquals(3, input.read(new byte[8], 1, 6));
				assertEquals(-1, input.read());
			} catch (IOException error) {
				throw new AssertionError(error);
			}
			return null;
		};
		try {
			assertNull(first.wrap(delegate).loadSprite(SPRITE, null));
			assertFalse(AtlasDiagnostics.observingRead());
			assertEquals(1, closed.get());
			assertEquals(11, first.snapshot().reloadId());
			assertEquals(4, first.snapshot().readBytes());
			assertEquals(1, first.snapshot().spriteCount());
			assertEquals(12, second.snapshot().reloadId());
			assertEquals(0, second.snapshot().spriteCount());
		} finally {
			first.close();
			first.close();
			second.close();
		}
		assertEquals(0, budget.used());
	}

	@Test
	void nestedReadProbeRestoresOuterProbeAndFailureDoesNotLeakBinding() {
		PreparationBudget budget = new PreparationBudget(16384);
		PreparationBudget.Scope scope = budget.openScope();
		try (AtlasDiagnostics outer = diagnostics(scope, 1); AtlasDiagnostics inner = diagnostics(scope, 2)) {
			SpriteResourceLoader nestedFailure = inner.wrap((id, resource) -> {
				assertTrue(AtlasDiagnostics.observingRead());
				throw new IllegalArgumentException("bad metadata");
			});
			outer.wrap((id, resource) -> {
				assertThrows(IllegalArgumentException.class, () -> nestedFailure.loadSprite(id, resource));
				assertTrue(AtlasDiagnostics.observingRead());
				return null;
			}).loadSprite(SPRITE, null);
			assertFalse(AtlasDiagnostics.observingRead());
			assertEquals(1, outer.snapshot().spriteCount());
			assertEquals(1, inner.snapshot().spriteCount());
		}
		assertEquals(0, budget.used());
	}

	@Test
	void cancelledOuterFutureKeepsQueuedSourceChargedAndRejectsLateObservation() {
		PreparationBudget budget = new PreparationBudget(16384);
		PreparationBudget.Scope scope = budget.openScope();
		AtlasDiagnostics diagnostics = diagnostics(scope, 19);
		ArrayDeque<Runnable> queue = new ArrayDeque<>();
		List<SpriteSource.Loader> loaders = List.of(loader -> null);
		CompletableFuture<List<SpriteSource.Loader>> source = CompletableFuture.supplyAsync(() -> loaders, diagnostics.bind(queue::add));
		CompletableFuture<List<SpriteSource.Loader>> decode = source.thenApply(diagnostics::observeLoaders);
		CompletableFuture<List<SpriteSource.Loader>> outer = decode.thenApply(value -> value);
		outer.whenComplete((ignored, failure) -> diagnostics.close());

		assertTrue(outer.cancel(false));
		assertEquals(4096, budget.used());
		queue.remove().run();
		assertSame(loaders, decode.join());
		assertSame(loaders, diagnostics.observeLoaders(loaders));
		assertEquals(0, budget.used());
	}

	@Test
	void cancellingOuterFutureDoesNotReleaseActiveLoaderOrPendingExecutorOwnership() throws Exception {
		PreparationBudget budget = new PreparationBudget(16384);
		PreparationBudget.Scope scope = budget.openScope();
		AtlasDiagnostics diagnostics = diagnostics(scope, 20);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		List<SpriteSource.Loader> observed = diagnostics.observeLoaders(List.of(loader -> {
			entered.countDown();
			try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
			catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
			return null;
		}));
		long admitted = budget.used();
		try {
			CompletableFuture<Void> decode = CompletableFuture.runAsync(() -> observed.get(0).get((id, resource) -> null), diagnostics.bind(executor));
			CompletableFuture<Void> outer = decode.thenApply(value -> value);
			outer.whenComplete((ignored, failure) -> diagnostics.close());
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			assertTrue(outer.cancel(false));
			assertEquals(admitted, budget.used());
			diagnostics.close();
			assertEquals(admitted, budget.used());
			release.countDown();
			decode.get(5, TimeUnit.SECONDS);
			executor.shutdown();
			assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
			assertEquals(0, budget.used());
		} finally {
			release.countDown();
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
			diagnostics.close();
		}
	}

	@Test
	void repeatedLoaderObservationCannotOverwriteTheOriginalReservation() {
		PreparationBudget budget = new PreparationBudget(16384);
		PreparationBudget.Scope scope = budget.openScope();
		try (AtlasDiagnostics diagnostics = diagnostics(scope, 21)) {
			List<SpriteSource.Loader> first = List.of(loader -> null);
			List<SpriteSource.Loader> second = List.of(loader -> null, loader -> null);
			diagnostics.observeLoaders(first);
			long admitted = budget.used();
			assertSame(second, diagnostics.observeLoaders(second));
			assertEquals(admitted, budget.used());
		}
		assertEquals(0, budget.used());
	}

	@Test
	void rejectedTaskReleasesItsClaimAndClosedResourceLoaderRemainsOriginal() {
		PreparationBudget budget = new PreparationBudget(16384);
		AtlasDiagnostics diagnostics = diagnostics(budget.openScope(), 22);
		Executor rejected = diagnostics.bind(command -> { throw new RejectedExecutionException(); });
		assertThrows(RejectedExecutionException.class, () -> rejected.execute(() -> {}));
		diagnostics.close();
		SpriteResourceLoader original = (id, resource) -> null;
		assertSame(original, diagnostics.wrap(original));
		assertEquals(0, budget.used());
	}

	@Test
	void disabledReadProbeReturnsOriginalStreamAndSupplierDetailsRespectBudget() {
		InputStream input = new ByteArrayInputStream(new byte[]{1});
		assertSame(input, AtlasDiagnostics.observeStream(input, 0));
		PreparationBudget budget = new PreparationBudget(4096);
		PreparationBudget.Scope scope = budget.openScope();
		try (AtlasDiagnostics diagnostics = diagnostics(scope, 1)) {
			var loaders = List.<SpriteSource.Loader>of(loader -> null);
			assertSame(loaders, diagnostics.observeLoaders(loaders));
			assertEquals(4096, budget.used());
		}
		assertEquals(0, budget.used());
	}

	private static AtlasDiagnostics diagnostics(PreparationBudget.Scope scope, long id) {
		return new AtlasDiagnostics(scope, scope.tryReserve(4096), id, ATLAS.toString());
	}
}
