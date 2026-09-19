package com.teenkung.packforge.client.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.teenkung.packforge.concurrent.PreparationBudget;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolidifyKernelTest {
	@Test
	void partialAlphaAndBlackSeedsRemainBitForBitEqualToVanilla() {
		assertMatchesVanilla(1, 1, new int[]{0x01000000});
		assertMatchesVanilla(7, 1, new int[]{0x01000000, 0x00abcdef, 0, 0, 0, 0, 0x80010203});
		assertMatchesVanilla(1, 7, new int[]{0x01000000, 0, 0, 0, 0, 0, 0xff0000ff});
		assertMatchesVanilla(5, 3, new int[]{
			0xffff0000, 0, 0, 0, 0xff0000ff,
			0, 0x0100ff00, 0, 0x800000ff, 0,
			0, 0, 0xffff00ff, 0, 0
		});
	}

	@Test
	void transparentColorsDoNotBecomeSeedsAndOpaquePixelsKeepAlpha() {
		int[] transparent = new int[64];
		Arrays.fill(transparent, 0x0055aa22);
		assertArrayEquals(new int[64], assertMatchesVanilla(8, 8, transparent));
		int[] opaque = new int[64];
		Arrays.fill(opaque, 0xff55aa22);
		assertArrayEquals(opaque, assertMatchesVanilla(8, 8, opaque));
		assertArrayEquals(new int[]{0}, assertMatchesVanilla(1, 1, new int[]{0x00112233}));
	}

	@Test
	void equalDistanceTiesMatchVanillaFifoOrder() {
		int[] pixels = new int[25];
		pixels[0 + 4 * 5] = 0x01ff0000;
		pixels[4] = 0x800000ff;
		int[] actual = assertMatchesVanilla(5, 5, pixels);
		assertEquals(0x00ff0000, actual[2 + 2 * 5]);
		for (int mask = 0; mask < 512; mask++) {
			int[] grid = new int[9];
			for (int index = 0; index < grid.length; index++) {
				int rgb = index * 0x010307;
				grid[index] = (mask & (1 << index)) == 0 ? rgb
					: rgb | ((index % 3 == 0 ? 1 : index % 3 == 1 ? 127 : 255) << 24);
			}
			assertMatchesVanilla(3, 3, grid);
		}
	}

	@Test
	void everyFourByFourSeedMaskMatchesVanilla() {
		for (int mask = 0; mask < 65536; mask++) {
			int[] pixels = new int[16];
			for (int index = 0; index < pixels.length; index++) {
				int rgb = (index + 1) * 0x010307;
				pixels[index] = (mask & (1 << index)) == 0 ? rgb : rgb | ((1 + index * 16) << 24);
			}
			assertMatchesVanilla(4, 4, pixels);
		}
	}

	@Test
	void seededRandomGridsMatchTheActualMinecraftImplementation() {
		Random random = new Random(78139);
		for (int test = 0; test < 256; test++) {
			int width = 1 + random.nextInt(33), height = 1 + random.nextInt(129);
			int[] pixels = new int[width * height];
			for (int index = 0; index < pixels.length; index++) {
				int color = random.nextInt();
				pixels[index] = switch (random.nextInt(4)) {
					case 0 -> color & 0x00ffffff;
					case 1 -> color & 0x00ffffff | 0x01000000;
					default -> color;
				};
			}
			assertMatchesVanilla(width, height, pixels);
		}
	}

	@Test
	void budgetDenialAndRetirementLeaveTheImageUntouched() {
		int[] pixels = {0xff010203, 0x00112233, 0x00778899, 0};
		PreparationBudget budget = new PreparationBudget(4L * pixels.length + 128L);
		PreparationBudget.Scope scope = budget.openScope();
		try (NativeImage image = image(2, 2, pixels)) {
			assertFalse(SolidifyKernel.solidify(image, scope));
			assertArrayEquals(pixels, read(image));
			assertEquals(0, budget.used());
			scope.retire();
			assertFalse(SolidifyKernel.solidify(image, scope));
			assertFalse(SolidifyKernel.solidify(image, null));
			assertArrayEquals(pixels, read(image));
		}
		assertEquals(0, budget.used());
	}

	@Test
	void unsupportedFormatsDoNotAllocateAndFailuresReleaseAdmission() {
		PreparationBudget budget = new PreparationBudget(4096);
		PreparationBudget.Scope scope = budget.openScope();
		try (NativeImage image = new NativeImage(NativeImage.Format.RGB, 2, 2, false)) {
			assertFalse(SolidifyKernel.solidify(image, scope));
			assertEquals(0, budget.peak());
		}
		NativeImage closed = new NativeImage(2, 2, false);
		closed.close();
		assertThrows(IllegalStateException.class, () -> SolidifyKernel.solidify(closed, scope));
		assertEquals(0, budget.used());
		assertEquals(8L * 4 + 256, budget.peak());
		scope.retire();
	}

	private static int[] assertMatchesVanilla(int width, int height, int[] pixels) {
		PreparationBudget budget = new PreparationBudget(8L * pixels.length + 256L);
		PreparationBudget.Scope scope = budget.openScope();
		try (NativeImage expected = image(width, height, pixels); NativeImage actual = image(width, height, pixels)) {
			TextureUtil.solidify(expected);
			assertTrue(SolidifyKernel.solidify(actual, scope));
			int[] result = read(actual);
			assertArrayEquals(read(expected), result, width + "x" + height);
			assertEquals(0, budget.used());
			assertEquals(8L * pixels.length + 256L, budget.peak());
			return result;
		} finally {
			scope.retire();
		}
	}

	private static NativeImage image(int width, int height, int[] pixels) {
		NativeImage image = new NativeImage(width, height, false);
		for (int index = 0; index < pixels.length; index++) {
			image.setPixel(index % width, index / width, pixels[index]);
		}
		return image;
	}

	private static int[] read(NativeImage image) {
		int[] pixels = new int[image.getWidth() * image.getHeight()];
		for (int index = 0; index < pixels.length; index++) {
			pixels[index] = image.getPixel(index % image.getWidth(), index / image.getWidth());
		}
		return pixels;
	}
}
