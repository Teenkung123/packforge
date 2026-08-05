package com.teenkung.packforge.concurrent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A loader-neutral ordered mapper with a bounded number of executor tasks.
 *
 * <p>Each worker claims a chunk from a shared cursor. Results stay in their
 * input slots, so completion order cannot change the returned order. A
 * non-null result is considered owned by this operation until the returned
 * future completes successfully; after a terminal failure or cancellation,
 * the disposer is called exactly once for every owned result that was
 * produced.</p>
 */
public final class OrderedAsync {
	private static final int TERMINAL_CHUNK = -1;

	private OrderedAsync() {
	}

	/**
	 * Maps inputs in parallel using at most a bounded number of worker tasks.
	 *
	 * @param inputs ordered input values; the list is snapshotted before work starts
	 * @param executor executor used for worker tasks
	 * @param workerBudget logical worker budget; non-positive values still permit one worker
	 * @param chunkSize number of input values claimed by one worker iteration
	 * @param mapper mapper for one input value
	 * @param disposer cleanup for a non-null mapped value when the operation fails or is cancelled
	 * @return an ordered future, preserving null mapped values
	 */
	public static <I, O> CompletableFuture<List<O>> map(
		List<? extends I> inputs,
		Executor executor,
		int workerBudget,
		int chunkSize,
		Function<? super I, ? extends O> mapper,
		Consumer<? super O> disposer
	) {
		Objects.requireNonNull(inputs, "inputs");
		Objects.requireNonNull(executor, "executor");
		Objects.requireNonNull(mapper, "mapper");
		Objects.requireNonNull(disposer, "disposer");
		if (chunkSize <= 0) {
			throw new IllegalArgumentException("chunkSize must be positive");
		}

		List<I> snapshot = new ArrayList<>(inputs.size());
		snapshot.addAll(inputs);
		if (snapshot.isEmpty()) {
			return CompletableFuture.completedFuture(List.of());
		}

		MappingFuture<O> future = new MappingFuture<>();
		MappingState<I, O> state = new MappingState<>(
			snapshot,
			executor,
			workerBudget,
			chunkSize,
			mapper,
			disposer,
			future
		);
		future.attach(state);
		state.start();
		return future;
	}

	/** Package-private retention proof used by focused lifecycle tests. */
	static RetentionDiagnostics retentionForTesting(CompletableFuture<?> future) {
		if (!(future instanceof MappingFuture<?> mappingFuture)) {
			return new RetentionDiagnostics(false, 0);
		}
		return mappingFuture.retentionSnapshot();
	}

	record RetentionDiagnostics(boolean stateAttached, int retainedResultSlots) {
	}

	private static final class MappingFuture<O> extends CompletableFuture<List<O>> {
		private final AtomicReference<MappingState<?, O>> state = new AtomicReference<>();
		private volatile int terminalRetainedResultSlots = -1;

		private void attach(MappingState<?, O> state) {
			this.state.set(state);
		}

		private void detach(MappingState<?, O> expected) {
			state.compareAndSet(expected, null);
		}

		private void recordTerminalRetention(int retainedResultSlots) {
			terminalRetainedResultSlots = retainedResultSlots;
		}

		private RetentionDiagnostics retentionSnapshot() {
			MappingState<?, O> current = state.get();
			return current == null
				? new RetentionDiagnostics(false, Math.max(0, terminalRetainedResultSlots))
				: current.retentionSnapshot();
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			MappingState<?, O> current = state.get();
			return current != null && current.cancel();
		}
	}

	private static final class MappingState<I, O> {
		private final List<I> inputs;
		private final Executor executor;
		private final int chunkSize;
		private final int chunkCount;
		private final int workerCount;
		private final Function<? super I, ? extends O> mapper;
		private final Consumer<? super O> disposer;
		private final MappingFuture<O> future;
		private final Object[] orderedResults;
		private final boolean[] produced;
		private final boolean[] disposed;
		private final AtomicInteger nextChunk = new AtomicInteger();
		private final AtomicInteger activeWorkers = new AtomicInteger();
		private final AtomicBoolean stopClaims = new AtomicBoolean();
		private final AtomicInteger failureSequence = new AtomicInteger();
		private final Object lifecycleLock = new Object();
		private final List<CompletableFuture<Void>> workerFutures = new ArrayList<>();
		private final List<Throwable> cleanupFailures = new ArrayList<>();

		private boolean schedulingComplete;
		private boolean finalized;
		private boolean cancelled;
		private Failure failure;

		private MappingState(
			List<I> inputs,
			Executor executor,
			int workerBudget,
			int chunkSize,
			Function<? super I, ? extends O> mapper,
			Consumer<? super O> disposer,
			MappingFuture<O> future
		) {
			this.inputs = Collections.unmodifiableList(inputs);
			this.executor = executor;
			this.chunkSize = chunkSize;
			this.chunkCount = chunkCount(inputs.size(), chunkSize);
			this.workerCount = Math.min(this.chunkCount, workerLimit(workerBudget));
			this.mapper = mapper;
			this.disposer = disposer;
			this.future = future;
			this.orderedResults = new Object[inputs.size()];
			this.produced = new boolean[inputs.size()];
			this.disposed = new boolean[inputs.size()];
		}

		private void start() {
			for (int i = 0; i < workerCount; i++) {
				if (future.isDone() || stopClaims.get() || nextChunk.get() >= chunkCount) {
					break;
				}

				CompletableFuture<Void> workerFuture = new CompletableFuture<>();
				synchronized (lifecycleLock) {
					if (stopClaims.get() || nextChunk.get() >= chunkCount) {
						break;
					}
					workerFutures.add(workerFuture);
					activeWorkers.incrementAndGet();
				}

				try {
					executor.execute(() -> runWorker(workerFuture));
				} catch (Throwable throwable) {
					recordFailure(inputs.size(), throwable);
					finishWorker(workerFuture);
					break;
				}
			}

			synchronized (lifecycleLock) {
				schedulingComplete = true;
			}
			finishIfReady();
		}

		private void runWorker(CompletableFuture<Void> workerFuture) {
			int currentIndex = -1;
			try {
				while (!stopClaims.get()) {
					int chunk = claimChunk();
					if (chunk == TERMINAL_CHUNK) {
						break;
					}

					int from = chunk * chunkSize;
					int to = (int) Math.min((long) inputs.size(), (long) from + chunkSize);
					for (int index = from; index < to; index++) {
						if (stopClaims.get()) {
							break;
						}
						currentIndex = index;
						O value = mapper.apply(inputs.get(index));
						publish(index, value);
					}
				}
			} catch (Throwable throwable) {
				recordFailure(currentIndex >= 0 ? currentIndex : inputs.size(), throwable);
			} finally {
				finishWorker(workerFuture);
			}
		}

		private void publish(int index, O value) {
			boolean disposeImmediately;
			synchronized (lifecycleLock) {
				produced[index] = true;
				if (stopClaims.get()) {
					disposed[index] = value != null;
					disposeImmediately = value != null;
				} else {
					orderedResults[index] = value;
					disposeImmediately = false;
				}
			}
			if (disposeImmediately) {
				disposeOne(value);
			}
		}

		private void recordFailure(int index, Throwable cause) {
			List<Object> toDispose;
			synchronized (lifecycleLock) {
				if (!cancelled) {
					Failure candidate = new Failure(index, failureSequence.getAndIncrement(), cause);
					if (failure == null || candidate.compareTo(failure) < 0) {
						failure = candidate;
					}
				}
				nextChunk.set(TERMINAL_CHUNK);
				stopClaims.set(true);
				toDispose = takeOwnedResultsLocked();
			}
			disposeAll(toDispose);
		}

		private boolean cancel() {
			CancellationException cause = new CancellationException("OrderedAsync mapping cancelled");
			List<Object> toDispose;
			synchronized (lifecycleLock) {
				if (finalized || cancelled || future.isDone()) {
					return false;
				}
				cancelled = true;
				nextChunk.set(TERMINAL_CHUNK);
				stopClaims.set(true);
				failure = new Failure(Integer.MIN_VALUE, failureSequence.getAndIncrement(), cause);
				finalized = true;
				toDispose = takeOwnedResultsLocked();
				future.recordTerminalRetention(retainedResultSlotsLocked());
				future.detach(this);
			}
			disposeAll(toDispose);
			attachCleanupFailures(cause);
			future.completeExceptionally(cause);
			finishIfReady();
			return true;
		}

		private void finishWorker(CompletableFuture<Void> workerFuture) {
			if (!workerFuture.complete(null)) {
				return;
			}
			activeWorkers.decrementAndGet();
			finishIfReady();
		}

		private void finishIfReady() {
			List<Object> toDispose = List.of();
			List<O> result = null;
			Throwable failureCause = null;
			synchronized (lifecycleLock) {
				if (!schedulingComplete || activeWorkers.get() != 0 || finalized) {
					return;
				}
				finalized = true;
				if (failure != null || stopClaims.get()) {
					toDispose = takeOwnedResultsLocked();
					failureCause = failure == null
						? new CancellationException("OrderedAsync mapping stopped")
						: failure.cause();
				} else {
					result = orderedResultList();
				}
				future.recordTerminalRetention(retainedResultSlotsLocked());
				future.detach(this);
			}

			disposeAll(toDispose);
			if (failureCause != null) {
				attachCleanupFailures(failureCause);
				future.completeExceptionally(failureCause);
			} else {
				future.complete(result);
			}
		}

		private List<O> orderedResultList() {
			List<O> result = new ArrayList<>(orderedResults.length);
			for (int index = 0; index < orderedResults.length; index++) {
				Object value = orderedResults[index];
				@SuppressWarnings("unchecked")
				O typedValue = (O) value;
				result.add(typedValue);
				orderedResults[index] = null;
				disposed[index] = true;
			}
			return Collections.unmodifiableList(result);
		}

		private List<Object> takeOwnedResultsLocked() {
			if (orderedResults.length == 0) {
				return List.of();
			}
			List<Object> owned = new ArrayList<>();
			for (int i = 0; i < orderedResults.length; i++) {
				if (produced[i] && !disposed[i] && orderedResults[i] != null) {
					disposed[i] = true;
					Object value = orderedResults[i];
					orderedResults[i] = null;
					owned.add(value);
				}
			}
			return owned;
		}

		private RetentionDiagnostics retentionSnapshot() {
			synchronized (lifecycleLock) {
				return new RetentionDiagnostics(true, retainedResultSlotsLocked());
			}
		}

		private int retainedResultSlotsLocked() {
			int retained = 0;
			for (Object value : orderedResults) {
				if (value != null) {
					retained++;
				}
			}
			return retained;
		}

		private void disposeAll(List<Object> values) {
			for (Object value : values) {
				disposeOne(value);
			}
		}

		@SuppressWarnings("unchecked")
		private void disposeOne(Object value) {
			try {
				disposer.accept((O) value);
			} catch (Throwable throwable) {
				synchronized (lifecycleLock) {
					cleanupFailures.add(throwable);
				}
			}
		}

		private void attachCleanupFailures(Throwable cause) {
			synchronized (lifecycleLock) {
				for (Throwable cleanupFailure : cleanupFailures) {
					if (cleanupFailure != cause) {
						cause.addSuppressed(cleanupFailure);
					}
				}
				cleanupFailures.clear();
			}
		}

		private int claimChunk() {
			while (true) {
				int current = nextChunk.get();
				if (current < 0 || current >= chunkCount) {
					return TERMINAL_CHUNK;
				}
				if (nextChunk.compareAndSet(current, current + 1)) {
					return current;
				}
			}
		}

		private static int chunkCount(int inputCount, int chunkSize) {
			return inputCount / chunkSize + (inputCount % chunkSize == 0 ? 0 : 1);
		}

		private static int workerLimit(int workerBudget) {
			long doubled = (long) workerBudget * 2L;
			return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, doubled));
		}
	}

	private record Failure(int index, int sequence, Throwable cause) implements Comparable<Failure> {
		@Override
		public int compareTo(Failure other) {
			int byIndex = Integer.compare(index, other.index);
			return byIndex != 0 ? byIndex : Integer.compare(sequence, other.sequence);
		}
	}
}
