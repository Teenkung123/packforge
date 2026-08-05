package com.teenkung.packforge.concurrent;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderedAsyncTest {
	@Test
	void emptyInputCompletesWithoutSubmittingWork() {
		AtomicInteger submissions = new AtomicInteger();
		CompletableFuture<List<Integer>> future = OrderedAsync.<Integer, Integer>map(
			List.of(),
			command -> {
				submissions.incrementAndGet();
				command.run();
			},
			1,
			1,
			value -> value,
			value -> {}
		);

		assertTrue(future.isDone());
		assertEquals(List.of(), future.join());
		assertEquals(0, submissions.get());
	}

	@Test
	void oneItemDirectExecutorIsSafeAndUsesNoOwnedThread() {
		Thread caller = Thread.currentThread();
		AtomicReference<Thread> mapperThread = new AtomicReference<>();

		List<Integer> result = OrderedAsync.map(
			List.of(7),
			Runnable::run,
			0,
			64,
			value -> {
				mapperThread.set(Thread.currentThread());
				return value * 2;
			},
			value -> {}
		).join();

		assertEquals(List.of(14), result);
		assertSame(caller, mapperThread.get());
	}

	@Test
	void mapsLargeInputInOrderAndPreservesNulls() {
		ExecutorService executor = Executors.newFixedThreadPool(4);
		try {
			List<Integer> inputs = new ArrayList<>(100_000);
			List<Integer> expected = new ArrayList<>(100_000);
			for (int i = 0; i < 100_000; i++) {
				inputs.add(i);
				expected.add(i % 17 == 0 ? null : i * 3);
			}

			List<Integer> actual = OrderedAsync.map(
				inputs,
				executor,
				2,
				37,
				value -> value % 17 == 0 ? null : value * 3,
				value -> {}
			).join();

			assertEquals(expected, actual);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void boundsSubmittedWorkersActiveWorkAndQueuedWork() throws Exception {
		ExecutorService delegate = Executors.newFixedThreadPool(4);
		TrackingExecutor executor = new TrackingExecutor(delegate);
		CountDownLatch entered = new CountDownLatch(4);
		CountDownLatch release = new CountDownLatch(1);
		try {
			List<Integer> inputs = new ArrayList<>();
			for (int i = 0; i < 100; i++) {
				inputs.add(i);
			}

			CompletableFuture<List<Integer>> future = OrderedAsync.map(
				inputs,
				executor,
				2,
				1,
				value -> {
					entered.countDown();
					assertTrue(await(release));
					return value;
				},
				value -> {}
			);

			assertTrue(entered.await(5, TimeUnit.SECONDS));
			assertTrue(executor.submissions.get() <= 4);
			assertTrue(executor.maxActive.get() <= 4);
			assertTrue(executor.maxPending.get() <= 4);
			release.countDown();
			assertEquals(inputs, future.get(5, TimeUnit.SECONDS));
		} finally {
			release.countDown();
			delegate.shutdownNow();
		}
	}

	@Test
	void mapperFailureDisposesEveryProducedValueExactlyOnce() {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		Set<Owned> created = ConcurrentHashMap.newKeySet();
		ConcurrentHashMap<Owned, AtomicInteger> disposed = new ConcurrentHashMap<>();
		RuntimeException expected = new RuntimeException("mapper failure");
		try {
			CompletableFuture<List<Owned>> future = OrderedAsync.map(
				List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
				executor,
				1,
				1,
				value -> {
					if (value == 0) {
						throw expected;
					}
					Owned owned = new Owned(value);
					created.add(owned);
					return owned;
				},
				value -> disposed.computeIfAbsent(value, ignored -> new AtomicInteger()).incrementAndGet()
			);

			assertSame(expected, failureCause(future));
			assertEquals(created, disposed.keySet());
			for (Owned value : created) {
				assertEquals(1, disposed.get(value).get());
			}
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void lowestInputFailureWinsWhenWorkersRace() {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch firstStarted = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		RuntimeException firstFailure = new RuntimeException("index zero");
		RuntimeException secondFailure = new RuntimeException("index one");
		try {
			CompletableFuture<List<Integer>> future = OrderedAsync.map(
				List.of(0, 1),
				executor,
				1,
				1,
				value -> {
					if (value == 0) {
						firstStarted.countDown();
						assertTrue(await(releaseFirst));
						throw firstFailure;
					}
					assertTrue(await(firstStarted));
					releaseFirst.countDown();
					throw secondFailure;
				},
				value -> {}
			);

			assertSame(firstFailure, failureCause(future));
		} finally {
			releaseFirst.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void executorRejectionBecomesTerminalFailure() {
		HoldFirstThenReject executor = new HoldFirstThenReject();
		RejectedExecutionException rejection = new RejectedExecutionException("full");
		CompletableFuture<List<Integer>> future = OrderedAsync.map(
			List.of(1, 2, 3),
			executor.withRejection(rejection),
			1,
			1,
			value -> value,
			value -> {}
		);

		assertFalse(future.isDone());
		assertNotNull(executor.first);
		executor.first.run();
		assertSame(rejection, failureCause(future));
	}

	@Test
	void cleanupFailuresAreSuppressedOnTheMapperFailure() {
		Owned owned = new Owned(1);
		RuntimeException mapperFailure = new RuntimeException("primary");
		RuntimeException cleanupFailure = new RuntimeException("cleanup");
		CompletableFuture<List<Owned>> future = OrderedAsync.map(
			List.of(1, 2),
			Runnable::run,
			1,
			1,
			value -> {
				if (value == 2) {
					throw mapperFailure;
				}
				return owned;
			},
			value -> {
				throw cleanupFailure;
			}
		);

		Throwable actual = failureCause(future);
		assertSame(mapperFailure, actual);
		assertEquals(List.of(cleanupFailure), List.of(actual.getSuppressed()));
	}

	@Test
	void cancellationDisposesCompletedAndLateOwnedValuesOnce() throws Exception {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		CountDownLatch firstProduced = new CountDownLatch(1);
		CountDownLatch secondStarted = new CountDownLatch(1);
		CountDownLatch releaseSecond = new CountDownLatch(1);
		CountDownLatch disposed = new CountDownLatch(2);
		ConcurrentHashMap<Owned, AtomicInteger> disposeCounts = new ConcurrentHashMap<>();
		AtomicInteger created = new AtomicInteger();
		try {
			CompletableFuture<List<Owned>> future = OrderedAsync.map(
				List.of(0, 1, 2),
				executor,
				1,
				1,
				value -> {
					Owned owned = new Owned(value);
					created.incrementAndGet();
					if (value == 0) {
						firstProduced.countDown();
					} else if (value == 1) {
						secondStarted.countDown();
						assertTrue(await(releaseSecond));
					}
					return owned;
				},
				value -> {
					disposeCounts.computeIfAbsent(value, ignored -> new AtomicInteger()).incrementAndGet();
					disposed.countDown();
				}
			);

			assertTrue(firstProduced.await(5, TimeUnit.SECONDS));
			assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
			assertTrue(future.cancel(false));
			assertTrue(future.isCancelled());
			releaseSecond.countDown();
			assertThrows(CancellationException.class, future::join);
			assertTrue(disposed.await(5, TimeUnit.SECONDS));
			assertEquals(2, created.get());
			for (AtomicInteger count : disposeCounts.values()) {
				assertEquals(1, count.get());
			}
		} finally {
			releaseSecond.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void oneThreadExecutorAndReentrantExecutorDoNotDeadlock() throws Exception {
		ExecutorService oneThread = Executors.newSingleThreadExecutor();
		try {
			assertEquals(
				List.of(0, 1, 4, 9, 16, 25),
				OrderedAsync.map(
					List.of(0, 1, 2, 3, 4, 5),
					oneThread,
					4,
					1,
					value -> value * value,
					value -> {}
				).get(5, TimeUnit.SECONDS)
			);
		} finally {
			oneThread.shutdownNow();
		}

		ReentrantExecutor reentrant = new ReentrantExecutor();
		assertEquals(
			List.of(2, 4, 6),
			OrderedAsync.map(
				List.of(1, 2, 3),
				reentrant,
				1,
				1,
				value -> {
					if (value == 1) {
						reentrant.execute(() -> {});
					}
					return value * 2;
				},
				value -> {}
			).join()
		);
	}

	private static Throwable failureCause(CompletableFuture<?> future) {
		return assertThrows(java.util.concurrent.CompletionException.class, future::join).getCause();
	}

	private static boolean await(CountDownLatch latch) {
		try {
			return latch.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private record Owned(int id) {
	}

	private static final class TrackingExecutor implements Executor {
		private final ExecutorService delegate;
		private final AtomicInteger submissions = new AtomicInteger();
		private final AtomicInteger active = new AtomicInteger();
		private final AtomicInteger maxActive = new AtomicInteger();
		private final AtomicInteger pending = new AtomicInteger();
		private final AtomicInteger maxPending = new AtomicInteger();

		private TrackingExecutor(ExecutorService delegate) {
			this.delegate = delegate;
		}

		@Override
		public void execute(Runnable command) {
			submissions.incrementAndGet();
			int pendingNow = pending.incrementAndGet();
			maxPending.accumulateAndGet(pendingNow, Math::max);
			delegate.execute(() -> {
				pending.decrementAndGet();
				int activeNow = active.incrementAndGet();
				maxActive.accumulateAndGet(activeNow, Math::max);
				try {
					command.run();
				} finally {
					active.decrementAndGet();
				}
			});
		}
	}

	private static final class HoldFirstThenReject {
		private Runnable first;
		private RejectedExecutionException rejection;
		private int calls;

		private Executor withRejection(RejectedExecutionException rejection) {
			this.rejection = rejection;
			return command -> {
				synchronized (this) {
					if (calls++ == 0) {
						first = command;
						return;
					}
				}
				throw this.rejection;
			};
		}
	}

	private static final class ReentrantExecutor implements Executor {
		private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
		private boolean draining;

		@Override
		public void execute(Runnable command) {
			boolean startDrain;
			synchronized (this) {
				queue.addLast(command);
				startDrain = !draining;
				if (startDrain) {
					draining = true;
				}
			}
			if (!startDrain) {
				return;
			}
			try {
				while (true) {
					Runnable next;
					synchronized (this) {
						next = queue.pollFirst();
						if (next == null) {
							draining = false;
							return;
						}
					}
					next.run();
				}
			} finally {
				synchronized (this) {
					draining = false;
				}
			}
		}
	}
}
