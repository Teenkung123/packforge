package com.teenkung.packforge.client.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.teenkung.packforge.concurrent.PreparationBudget;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolidifyQueueTest {
	@Test
	void fullAndAlternatingFifoUseExactlyOneUnchangedPixelSizedArray() {
		int pixels = 8192;
		SolidifyQueue queue = new SolidifyQueue(pixels);
		int[] storage = queue.storageForTesting();
		assertEquals(pixels, storage.length);
		for (int index = 0; index < pixels; index++) queue.enqueue(index);
		assertEquals(pixels, queue.size());
		for (int index = 0; index < pixels; index++) {
			assertEquals(index, queue.dequeueInt());
			assertSame(storage, queue.storageForTesting());
		}
		assertTrue(queue.isEmpty());
		queue.trim();
		assertSame(storage, queue.storageForTesting());
		queue.clear();
		for (int index = 0; index < pixels; index++) {
			queue.enqueue(index);
			assertEquals(index, queue.dequeueInt());
			assertSame(storage, queue.storageForTesting());
		}
		assertEquals(pixels, queue.capacity());
		assertThrows(IllegalStateException.class, () -> queue.enqueue(0));
	}

	@Test
	void admissionBypassesOversizedRetiredAndNestedWorkWithoutBorrowingAnotherQueue() {
		PreparationBudget budget = new PreparationBudget(1152);
		PreparationBudget.Scope scope = budget.openScope();
		IntArrayFIFOQueue original = new IntArrayFIFOQueue();
		try (SolidifyQueueWorkspace outer = SolidifyQueueWorkspace.open(scope, 16, 16)) {
			assertEquals(1152, budget.used());
			try (SolidifyQueueWorkspace inner = SolidifyQueueWorkspace.open(scope, 16, 16)) {
				assertSame(original, SolidifyQueueWorkspace.newQueue(() -> original));
			}
			assertInstanceOf(SolidifyQueue.class, SolidifyQueueWorkspace.newQueue());
			scope.retire();
			assertEquals(1152, budget.used());
			try (SolidifyQueueWorkspace retired = SolidifyQueueWorkspace.open(scope, 1, 1)) {
				assertSame(original, SolidifyQueueWorkspace.newQueue(() -> original));
			}
		}
		assertEquals(0, budget.used());
		try (SolidifyQueueWorkspace invalid = SolidifyQueueWorkspace.open(scope, Integer.MAX_VALUE, Integer.MAX_VALUE)) {
			assertSame(original, SolidifyQueueWorkspace.newQueue(() -> original));
		}
		assertSame(original, SolidifyQueueWorkspace.newQueue(() -> original));
	}

	@Test
	void failureReleasesWorkspaceAndRestoresOriginalConstructor() {
		PreparationBudget budget = new PreparationBudget(1152);
		assertThrows(IllegalArgumentException.class, () -> {
			try (SolidifyQueueWorkspace ignored = SolidifyQueueWorkspace.open(budget.openScope(), 16, 16)) {
				assertInstanceOf(SolidifyQueue.class, SolidifyQueueWorkspace.newQueue());
				throw new IllegalArgumentException("Original pixel operation failed");
			}
		});
		assertEquals(0, budget.used());
		assertFalse(SolidifyQueueWorkspace.newQueue() instanceof SolidifyQueue);
	}

	@Test
	void cancellationKeepsRunningWorkspaceChargedUntilWorkerActuallyStops() throws Exception {
		PreparationBudget budget = new PreparationBudget(1152);
		PreparationBudget.Scope scope = budget.openScope();
		ExecutorService workers = Executors.newSingleThreadExecutor();
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try {
			Future<?> running = workers.submit(() -> {
				try (SolidifyQueueWorkspace ignored = SolidifyQueueWorkspace.open(scope, 16, 16)) {
					assertInstanceOf(SolidifyQueue.class, SolidifyQueueWorkspace.newQueue());
					entered.countDown();
					assertTrue(release.await(5, TimeUnit.SECONDS));
				} catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
			});
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			assertTrue(running.cancel(false));
			scope.retire();
			assertEquals(1152, budget.used());
			try (SolidifyQueueWorkspace competing = SolidifyQueueWorkspace.open(budget.openScope(), 16, 16)) {
				assertFalse(SolidifyQueueWorkspace.newQueue() instanceof SolidifyQueue);
			}
			release.countDown();
			workers.shutdown();
			assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
			assertEquals(0, budget.used());
			assertEquals(1152, budget.peak());
		} finally {
			release.countDown();
			workers.shutdownNow();
			assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
		}
	}

	@Test
	void originalSolidifyBytecodeProducesIdenticalPixelsWithOnlyItsQueueConstructorReplaced() throws Exception {
		Method optimized = solidifyWithQueueConstructorSubstituted();
		assertMatchesVanilla(optimized, 1, 1, new int[]{0x00112233});
		assertMatchesVanilla(optimized, 1, 1, new int[]{0xFF112233});
		int[] opaque = new int[64];
		Arrays.fill(opaque, 0xFF55AA22);
		assertMatchesVanilla(optimized, 8, 8, opaque);
		int[] transparent = new int[64];
		Arrays.fill(transparent, 0x0055AA22);
		assertMatchesVanilla(optimized, 8, 8, transparent);
		assertMatchesVanilla(optimized, 5, 3, new int[]{
			0xFFFF0000, 0, 0, 0, 0xFF0000FF,
			0, 0x0100FF00, 0, 0x800000FF, 0,
			0, 0, 0xFFFF00FF, 0, 0
		});
		Random random = new Random(78139);
		for (int test = 0; test < 64; test++) {
			int width = 1 + random.nextInt(17);
			int height = 1 + random.nextInt(65); // Includes tall animation-like sheets and non-square frames.
			int[] pixels = new int[width * height];
			for (int i = 0; i < pixels.length; i++) pixels[i] = random.nextBoolean() ? random.nextInt() : random.nextInt() & 0x00FFFFFF;
			assertMatchesVanilla(optimized, width, height, pixels);
		}
	}

	private static void assertMatchesVanilla(Method optimized, int width, int height, int[] pixels) throws Exception {
		PreparationBudget budget = new PreparationBudget(4L * pixels.length + 128);
		try (NativeImage expected = new NativeImage(width, height, false); NativeImage actual = new NativeImage(width, height, false)) {
			for (int i = 0; i < pixels.length; i++) {
				expected.setPixel(i % width, i / width, pixels[i]);
				actual.setPixel(i % width, i / width, pixels[i]);
			}
			TextureUtil.solidify(expected);
			try (SolidifyQueueWorkspace ignored = SolidifyQueueWorkspace.open(budget.openScope(), width, height)) {
				try { optimized.invoke(null, actual); }
				catch (InvocationTargetException error) { throw new AssertionError("Original solidify bytecode failed", error.getCause()); }
			}
			int[] expectedPixels = new int[pixels.length];
			int[] actualPixels = new int[pixels.length];
			for (int i = 0; i < pixels.length; i++) {
				expectedPixels[i] = expected.getPixel(i % width, i / width);
				actualPixels[i] = actual.getPixel(i % width, i / width);
			}
			assertArrayEquals(expectedPixels, actualPixels, width + "x" + height);
		}
		assertEquals(0, budget.used());
	}

	/** Uses actual Minecraft algorithm bytecode, not a hand-copied second implementation. */
	private static Method solidifyWithQueueConstructorSubstituted() throws Exception {
		String queueName = "it/unimi/dsi/fastutil/ints/IntArrayFIFOQueue";
		ClassWriter output = new ClassWriter(0);
		int[] replacements = {0};
		try (InputStream input = TextureUtil.class.getResourceAsStream("TextureUtil.class")) {
			new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9, output) {
				@Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					MethodVisitor original = super.visitMethod(access, name, descriptor, signature, exceptions);
					if (!name.equals("solidify")) return original;
					return new MethodVisitor(Opcodes.ASM9, original) {
						private boolean constructing;
						@Override public void visitTypeInsn(int opcode, String type) {
							if (opcode == Opcodes.NEW && type.equals(queueName)) constructing = true;
							else super.visitTypeInsn(opcode, type);
						}
						@Override public void visitInsn(int opcode) {
							if (!constructing || opcode != Opcodes.DUP) super.visitInsn(opcode);
						}
						@Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
							if (constructing && opcode == Opcodes.INVOKESPECIAL && owner.equals(queueName) && name.equals("<init>") && descriptor.equals("()V")) {
								super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/teenkung/packforge/client/atlas/SolidifyQueueWorkspace", "newQueue", "()L" + queueName + ";", false);
								constructing = false;
								replacements[0]++;
							} else super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
						}
					};
				}
			}, 0);
		}
		assertEquals(1, replacements[0], "Expected exactly one vanilla FIFO construction");
		class OracleLoader extends ClassLoader {
			OracleLoader() { super(TextureUtil.class.getClassLoader()); }
			Class<?> define(byte[] bytes) { return defineClass(TextureUtil.class.getName(), bytes, 0, bytes.length); }
		}
		return new OracleLoader().define(output.toByteArray()).getMethod("solidify", NativeImage.class);
	}
}
