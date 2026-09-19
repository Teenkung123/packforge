package com.teenkung.packforge.client.atlas;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedSpriteDecodeTest {
	private static final BoundedSpriteDecode.Plan PLAN = new BoundedSpriteDecode.Plan(
		true, false, false, 256, false, 1, 1, 64, Set.of()
	);

	@Test
	void slowFirstImageDoesNotReserveTheRestOfItsConfiguredBatch() throws Exception {
		ExecutorService workers = Executors.newFixedThreadPool(2);
		CountDownLatch firstEntered = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		CountDownLatch remainingDecoded = new CountDownLatch(63);
		AtomicInteger submissions = new AtomicInteger();
		List<Integer> inputs = IntStream.range(0, 64).boxed().toList();
		try {
			CompletableFuture<List<Integer>> decoded = BoundedSpriteDecode.decode(
				inputs,
				command -> {
					submissions.incrementAndGet();
					workers.execute(command);
				},
				PLAN,
				index -> {
					if (index == 0) {
						firstEntered.countDown();
						await(releaseFirst);
					} else {
						remainingDecoded.countDown();
					}
					return index % 7 == 0 ? null : index;
				},
				value -> { throw new AssertionError("Successful decode must transfer ownership"); }
			);

			assertTrue(firstEntered.await(5, TimeUnit.SECONDS));
			assertTrue(remainingDecoded.await(5, TimeUnit.SECONDS));
			assertFalse(decoded.isDone());
			assertEquals(2, submissions.get());
			releaseFirst.countDown();
			assertEquals(inputs.stream().filter(index -> index % 7 != 0).toList(), decoded.get(5, TimeUnit.SECONDS));
		} finally {
			releaseFirst.countDown();
			workers.shutdownNow();
			assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
		}
	}

	@Test
	void emptyInputSubmitsNoWorkAndInlineExecutorTransfersResults() {
		AtomicInteger submissions = new AtomicInteger();
		Executor inline = command -> {
			submissions.incrementAndGet();
			command.run();
		};
		List<OwnedImage> images = List.of(new OwnedImage(), new OwnedImage());
		assertEquals(List.of(), BoundedSpriteDecode.decode(
			List.<OwnedImage>of(), inline, PLAN, image -> image, OwnedImage::close
		).join());
		assertEquals(0, submissions.get());
		assertEquals(images, BoundedSpriteDecode.decode(
			images, inline, PLAN, image -> image, OwnedImage::close
		).join());
		assertEquals(1, submissions.get());
		images.forEach(image -> assertEquals(0, image.closes.get()));
	}

	@Test
	void decodeFailureDisposesProducedImagesOnceAndStopsNewClaims() {
		ArrayDeque<Runnable> workers = new ArrayDeque<>();
		OwnedImage image = new OwnedImage();
		IllegalStateException failure = new IllegalStateException("Malformed image");
		AtomicInteger calls = new AtomicInteger();
		CompletableFuture<List<OwnedImage>> decoded = BoundedSpriteDecode.decode(
			List.of(0, 1, 2), workers::add, PLAN,
			index -> {
				calls.incrementAndGet();
				if (index == 1) throw failure;
				return image;
			},
			OwnedImage::close
		);
		while (!workers.isEmpty()) workers.remove().run();

		assertSame(failure, assertThrows(CompletionException.class, decoded::join).getCause());
		assertEquals(2, calls.get());
		assertEquals(1, image.closes.get());
	}

	@Test
	void executorRejectionStopsAlreadyQueuedWorkersWithoutDecoding() {
		ArrayDeque<Runnable> workers = new ArrayDeque<>();
		RejectedExecutionException failure = new RejectedExecutionException("Executor stopped");
		AtomicInteger calls = new AtomicInteger();
		CompletableFuture<List<OwnedImage>> decoded = BoundedSpriteDecode.decode(
			List.of(0, 1, 2),
			command -> {
				if (!workers.isEmpty()) throw failure;
				workers.add(command);
			},
			PLAN,
			index -> {
				calls.incrementAndGet();
				return new OwnedImage();
			},
			OwnedImage::close
		);
		workers.remove().run();

		assertSame(failure, assertThrows(CompletionException.class, decoded::join).getCause());
		assertEquals(0, calls.get());
	}

	@Test
	void cancellationClosesBothPublishedAndInFlightImagesExactlyOnce() throws Exception {
		ExecutorService workers = Executors.newSingleThreadExecutor();
		CountDownLatch secondEntered = new CountDownLatch(1);
		CountDownLatch releaseSecond = new CountDownLatch(1);
		List<OwnedImage> images = new ArrayList<>(List.of(new OwnedImage(), new OwnedImage(), new OwnedImage()));
		AtomicInteger calls = new AtomicInteger();
		try {
			CompletableFuture<List<OwnedImage>> decoded = BoundedSpriteDecode.decode(
				List.of(0, 1, 2), workers, PLAN,
				index -> {
					calls.incrementAndGet();
					if (index == 1) {
						secondEntered.countDown();
						await(releaseSecond);
					}
					return images.get(index);
				},
				OwnedImage::close
			);
			assertTrue(secondEntered.await(5, TimeUnit.SECONDS));
			assertTrue(decoded.cancel(false));
			assertTrue(decoded.isCancelled());
			assertEquals(1, images.get(0).closes.get());
			releaseSecond.countDown();
			workers.shutdown();
			assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
			assertEquals(2, calls.get());
			assertEquals(List.of(1, 1, 0), images.stream().map(image -> image.closes.get()).toList());
		} finally {
			releaseSecond.countDown();
			workers.shutdownNow();
			assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Worker did not receive release");
		} catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			throw new AssertionError(error);
		}
	}

	private static final class OwnedImage {
		private final AtomicInteger closes = new AtomicInteger();

		private void close() {
			closes.incrementAndGet();
		}
	}
}
