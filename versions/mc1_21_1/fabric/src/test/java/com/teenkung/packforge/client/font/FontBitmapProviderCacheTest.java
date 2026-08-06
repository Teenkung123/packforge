package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class FontBitmapProviderCacheTest {
	private static final BitmapProvider.Definition DEFINITION = new BitmapProvider.Definition(
		ResourceLocation.fromNamespaceAndPath("packforge_test", "font.png"),
		8,
		7,
		new int[][]{{0}}
	);

	@AfterEach
	void resetCache() {
		FontBitmapProviderCache.resetForReload();
	}

	@Test
	void staleEpochReturnsFreshProviderWithoutPublishingIntoNewGeneration() {
		long epoch = FontBitmapProviderCache.captureEpoch();
		TrackingProvider current = new TrackingProvider();
		GlyphProvider owner = FontBitmapProviderCache.cache(epoch, null, DEFINITION, current);
		assertNotNull(owner);

		owner.close();
		FontBitmapProviderCache.resetForReload();

		TrackingProvider staleLoad = new TrackingProvider();
		assertSame(
			staleLoad,
			FontBitmapProviderCache.cache(epoch, null, DEFINITION, staleLoad)
		);
		assertEquals(0, staleLoad.closeCount());
		assertNull(FontBitmapProviderCache.get(FontBitmapProviderCache.captureEpoch(), null, DEFINITION));

		staleLoad.close();
		assertEquals(1, staleLoad.closeCount());
		assertEquals(1, current.closeCount());
	}

	@Test
	void repeatedProviderCloseAndResetRetireDelegateExactlyOnce() {
		long epoch = FontBitmapProviderCache.captureEpoch();
		TrackingProvider delegate = new TrackingProvider();
		GlyphProvider owner = FontBitmapProviderCache.cache(epoch, null, DEFINITION, delegate);

		owner.close();
		owner.close();
		FontBitmapProviderCache.resetForReload();
		FontBitmapProviderCache.resetForReload();

		assertEquals(1, delegate.closeCount());
	}

	private static final class TrackingProvider implements GlyphProvider {
		private final AtomicInteger closeCount = new AtomicInteger();

		@Override
		public GlyphInfo getGlyph(int codepoint) {
			return null;
		}

		@Override
		public IntSet getSupportedGlyphs() {
			return new IntOpenHashSet();
		}

		@Override
		public void close() {
			closeCount.incrementAndGet();
		}

		private int closeCount() {
			return closeCount.get();
		}
	}
}
