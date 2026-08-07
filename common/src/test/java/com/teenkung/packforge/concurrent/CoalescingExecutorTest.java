package com.teenkung.packforge.concurrent;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoalescingExecutorTest {
	@Test
	void boundsDelegateSubmissionsForAQueuedBurst() {
		ManualExecutor delegate = new ManualExecutor();
		CoalescingExecutor executor = new CoalescingExecutor(delegate, 4);
		AtomicInteger completed = new AtomicInteger();

		for (int i = 0; i < 100; i++) {
			executor.execute(completed::incrementAndGet);
		}

		assertEquals(4, delegate.submissions());
		delegate.runAll();
		assertEquals(100, completed.get());
	}

	@Test
	void directExecutorUsesSynchronousContinuationAndPreservesOrder() {
		AtomicInteger submissions = new AtomicInteger();
		List<Integer> values = new ArrayList<>();
		Executor delegate = command -> {
			submissions.incrementAndGet();
			command.run();
		};
		CoalescingExecutor executor = new CoalescingExecutor(delegate, 8);

		for (int i = 0; i < 100; i++) {
			int value = i;
			executor.execute(() -> values.add(value));
		}

		assertEquals(1, submissions.get());
		assertEquals(100, values.size());
		for (int i = 0; i < values.size(); i++) {
			assertEquals(i, values.get(i));
		}
	}

	@Test
	void oneThreadExecutorCompletesWithoutDeadlock() throws Exception {
		ExecutorService delegate = Executors.newSingleThreadExecutor();
		try {
			CoalescingExecutor executor = new CoalescingExecutor(delegate, 4);
			AtomicInteger completed = new AtomicInteger();
			for (int i = 0; i < 100; i++) {
				executor.execute(completed::incrementAndGet);
			}
			delegate.shutdown();
			assertTrue(delegate.awaitTermination(5, TimeUnit.SECONDS));
			assertEquals(100, completed.get());
		} finally {
			delegate.shutdownNow();
		}
	}

	@Test
	void reentrantExecutorDoesNotRecursivelySubmitEveryCommand() {
		ReentrantExecutor delegate = new ReentrantExecutor();
		CoalescingExecutor executor = new CoalescingExecutor(delegate, 8);
		List<Integer> values = new ArrayList<>();

		for (int i = 0; i < 100; i++) {
			int value = i;
			executor.execute(() -> values.add(value));
		}

		assertEquals(1, delegate.submissions.get());
		assertEquals(100, values.size());
	}

	@Test
	void rejectionIsPropagatedAndDoesNotLeaveAcceptedWork() {
		RejectedExecutionException rejection = new RejectedExecutionException("full");
		CoalescingExecutor executor = new CoalescingExecutor(command -> {
			throw rejection;
		}, 2);

		assertSame(rejection, assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {})));
		assertSame(rejection, assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {})));
	}

	@Test
	void rejectedLaterSubmissionDoesNotRunTriggerOrLoseQueuedWork() throws Exception {
		CountDownLatch releaseRejection = new CountDownLatch(1);
		RejectedAfterFirstExecutor delegate = new RejectedAfterFirstExecutor(releaseRejection);
		CoalescingExecutor executor = new CoalescingExecutor(delegate, 2);
		CountDownLatch firstStarted = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		CountDownLatch submitterEntered = new CountDownLatch(1);
		CountDownLatch queuedWorkAdded = new CountDownLatch(1);
		AtomicInteger acceptedQueuedWork = new AtomicInteger();
		AtomicInteger rejectedTrigger = new AtomicInteger();
		AtomicReference<Throwable> submitterFailure = new AtomicReference<>();

		executor.execute(() -> {
			firstStarted.countDown();
			await(releaseFirst);
			executor.execute(acceptedQueuedWork::incrementAndGet);
			queuedWorkAdded.countDown();
		});
		Thread firstRunner = new Thread(delegate::runFirst, "coalescing-first-drainer-test");
		firstRunner.start();
		assertTrue(firstStarted.await(5, TimeUnit.SECONDS));

		Thread rejectedSubmitter = new Thread(() -> {
			try {
				submitterEntered.countDown();
				assertThrows(RejectedExecutionException.class, () -> executor.execute(rejectedTrigger::incrementAndGet));
			} catch (Throwable failure) {
				submitterFailure.set(failure);
			}
		}, "coalescing-rejection-test");
		rejectedSubmitter.start();
		assertTrue(submitterEntered.await(5, TimeUnit.SECONDS));
		assertTrue(delegate.secondSubmissionEntered.await(5, TimeUnit.SECONDS));

		releaseFirst.countDown();
		assertTrue(queuedWorkAdded.await(5, TimeUnit.SECONDS));
		releaseRejection.countDown();

		firstRunner.join(5000);
		rejectedSubmitter.join(5000);
		assertTrue(!firstRunner.isAlive());
		assertTrue(!rejectedSubmitter.isAlive());
		assertEquals(null, submitterFailure.get());

		assertEquals(0, rejectedTrigger.get());
		assertEquals(1, acceptedQueuedWork.get());
	}

	@Test
	void rejectedTriggerIsNotRequeuedWhenItWasTheFirstEntry() {
		RejectedExecutionException rejection = new RejectedExecutionException("first entry rejected");
		CoalescingExecutor executor = new CoalescingExecutor(command -> {
			throw rejection;
		}, 1);
		AtomicInteger runs = new AtomicInteger();

		assertSame(rejection, assertThrows(RejectedExecutionException.class, () -> executor.execute(runs::incrementAndGet)));
		assertEquals(0, runs.get());
	}

	@Test
	void separateInstancesShareDelegateWithoutSharingState() {
		ManualExecutor delegate = new ManualExecutor();
		CoalescingExecutor first = new CoalescingExecutor(delegate, 1);
		CoalescingExecutor second = new CoalescingExecutor(delegate, 1);
		AtomicInteger completed = new AtomicInteger();

		first.execute(completed::incrementAndGet);
		second.execute(completed::incrementAndGet);
		assertEquals(2, delegate.submissions());
		delegate.runAll();
		assertEquals(2, completed.get());
	}

	private static final class ManualExecutor implements Executor {
		private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
		private int submissions;

		@Override
		public synchronized void execute(Runnable command) {
			submissions++;
			queue.addLast(command);
		}

		private synchronized int submissions() {
			return submissions;
		}

		private void runAll() {
			while (true) {
				Runnable command;
				synchronized (this) {
					command = queue.pollFirst();
				}
				if (command == null) {
					return;
				}
				command.run();
			}
		}
	}

	private static final class RejectedAfterFirstExecutor implements Executor {
		private final CountDownLatch secondSubmissionEntered = new CountDownLatch(1);
		private final CountDownLatch releaseRejection;
		private volatile Runnable first;
		private int submissions;

		private RejectedAfterFirstExecutor(CountDownLatch releaseRejection) {
			this.releaseRejection = releaseRejection;
		}

		@Override
		public synchronized void execute(Runnable command) {
			if (submissions++ == 0) {
				first = command;
				return;
			}
			secondSubmissionEntered.countDown();
			await(releaseRejection);
			throw new RejectedExecutionException("later submission rejected");
		}

		private void runFirst() {
			first.run();
		}
	}

	private static final class ReentrantExecutor implements Executor {
		private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
		private final AtomicInteger submissions = new AtomicInteger();
		private boolean draining;

		@Override
		public void execute(Runnable command) {
			submissions.incrementAndGet();
			boolean start;
			synchronized (this) {
				queue.addLast(command);
				start = !draining;
				if (start) {
					draining = true;
				}
			}
			if (!start) {
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

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new AssertionError("timed out waiting for test latch");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError("interrupted while waiting for test latch", exception);
		}
	}
}
