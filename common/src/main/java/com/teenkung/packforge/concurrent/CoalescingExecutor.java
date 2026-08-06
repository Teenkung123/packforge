package com.teenkung.packforge.concurrent;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * An executor adapter that drains many commands through a bounded number of
 * tasks submitted to a caller-owned executor.
 *
 * <p>The adapter owns no threads and never replaces the supplied executor. A
 * worker drains a FIFO queue until it is empty, so a burst of per-item
 * submissions is represented by at most {@code workerBudget} delegate
 * submissions. Direct and reentrant executors are handled with a synchronous
 * continuation so they do not cause one delegate submission per command.</p>
 */
public final class CoalescingExecutor implements Executor {
	private final Executor delegate;
	private final int workerBudget;
	private final Object lock = new Object();
	private final ArrayDeque<Entry> queue = new ArrayDeque<>();
	private final ThreadLocal<Drainer> currentDrainer = new ThreadLocal<>();
	private final ThreadLocal<InlineState> inlineState = new ThreadLocal<>();

	private int activeDrainers;
	private boolean submissionInProgress;
	private Thread submissionThread;
	private Throwable terminalFailure;

	public CoalescingExecutor(Executor delegate, int workerBudget) {
		this.delegate = Objects.requireNonNull(delegate, "delegate");
		this.workerBudget = Math.max(1, workerBudget);
	}

	/**
	 * Returns a bounded executor that uses {@code delegate} for all asynchronous
	 * work. This factory keeps call sites readable at version-specific hooks.
	 */
	public static Executor bounded(Executor delegate, int workerBudget) {
		return new CoalescingExecutor(delegate, workerBudget);
	}

	@Override
	public void execute(Runnable command) {
		Objects.requireNonNull(command, "command");
		Entry entry = new Entry(command);
		Drainer drainer = null;
		InlineState inline = null;

		while (true) {
			synchronized (lock) {
				if (terminalFailure != null) {
					inlineState.remove();
					throwUnchecked(terminalFailure);
				}

				Thread caller = Thread.currentThread();
				if (submissionInProgress && submissionThread != caller && currentDrainer.get() == null) {
					try {
						lock.wait();
					} catch (InterruptedException exception) {
						Thread.currentThread().interrupt();
						throw new RejectedExecutionException("Interrupted while coalescing executor submission", exception);
					}
					continue;
				}

				InlineState availableInline = inlineState.get();
				if (availableInline != null
					&& !availableInline.draining
					&& !submissionInProgress
					&& activeDrainers == 0
					&& currentDrainer.get() == null) {
					queue.addLast(entry);
					activeDrainers++;
					availableInline.draining = true;
					inline = availableInline;
					break;
				}
				if (availableInline != null && currentDrainer.get() == null
					&& (submissionInProgress || activeDrainers > 0)) {
					inlineState.remove();
				}

				queue.addLast(entry);
				if (submissionInProgress || activeDrainers >= workerBudget || currentDrainer.get() != null) {
					break;
				}

				activeDrainers++;
				submissionInProgress = true;
				submissionThread = caller;
				Entry first = queue.pollFirst();
				drainer = new Drainer(first, entry);
				break;
			}
		}

		if (inline != null) {
			try {
				drain(new Drainer(null, null));
			} finally {
				inline.draining = false;
			}
			return;
		}
		if (drainer != null) {
			submit(drainer);
		}
	}

	private void submit(Drainer drainer) {
		try {
			delegate.execute(drainer);
		} catch (RuntimeException | Error failure) {
			if (drainer.started) {
				completeSubmission(drainer);
				throw failure;
			}
			rejectSubmission(drainer, failure);
			throw failure;
		}
		completeSubmission(drainer);
	}

	private void completeSubmission(Drainer drainer) {
		synchronized (lock) {
			submissionInProgress = false;
			submissionThread = null;
			lock.notifyAll();
		}
		if (drainer.completed && drainer.owner == Thread.currentThread()) {
			inlineState.set(new InlineState());
		}
	}

	private void rejectSubmission(Drainer drainer, Throwable failure) {
		synchronized (lock) {
			queue.remove(drainer.trigger);
			if (drainer.first != drainer.trigger) {
				queue.addFirst(drainer.first);
			}
			activeDrainers--;
			terminalFailure = failure;
			submissionInProgress = false;
			submissionThread = null;
			lock.notifyAll();
		}
		inlineState.remove();
	}

	private void drain(Drainer drainer) {
		drainer.started = true;
		drainer.owner = Thread.currentThread();
		Drainer previous = currentDrainer.get();
		currentDrainer.set(drainer);
		Throwable firstFailure = null;
		try {
			if (drainer.first != null) {
				firstFailure = runEntry(drainer.first, firstFailure);
			}
			while (true) {
				Entry next;
				synchronized (lock) {
					next = queue.pollFirst();
					if (next == null) {
						activeDrainers--;
						lock.notifyAll();
						break;
					}
				}
				firstFailure = runEntry(next, firstFailure);
			}
		} finally {
			drainer.completed = true;
			if (previous == null) {
				currentDrainer.remove();
			} else {
				currentDrainer.set(previous);
			}
		}
		if (firstFailure != null) {
			throwUnchecked(firstFailure);
		}
	}

	private Throwable runEntry(Entry entry, Throwable firstFailure) {
		try {
			entry.command.run();
		} catch (Throwable failure) {
			if (firstFailure == null) {
				return failure;
			}
			if (firstFailure != failure) {
				firstFailure.addSuppressed(failure);
			}
		}
		return firstFailure;
	}

	private static void throwUnchecked(Throwable failure) {
		if (failure instanceof RuntimeException runtimeException) {
			throw runtimeException;
		}
		if (failure instanceof Error error) {
			throw error;
		}
		throw new RuntimeException(failure);
	}

	private static final class Entry {
		private final Runnable command;

		private Entry(Runnable command) {
			this.command = command;
		}
	}

	private final class Drainer implements Runnable {
		private final Entry first;
		private final Entry trigger;
		private volatile boolean started;
		private volatile boolean completed;
		private volatile Thread owner;

		private Drainer(Entry first, Entry trigger) {
			this.first = first;
			this.trigger = trigger;
		}

		@Override
		public void run() {
			drain(this);
		}
	}

	private static final class InlineState {
		private boolean draining;
	}
}
