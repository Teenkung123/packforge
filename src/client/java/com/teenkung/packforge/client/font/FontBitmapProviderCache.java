package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import com.teenkung.packforge.PackForge;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.font.providers.BitmapProvider;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class FontBitmapProviderCache {
	private static final ConcurrentHashMap<Key, SharedState> CACHE = new ConcurrentHashMap<>();

	public static GlyphProvider cache(BitmapProvider.Definition definition, GlyphProvider loadedProvider) {
		Key key = Key.from(definition);
		SharedState loaded = new SharedState(loadedProvider);
		SharedState existing = CACHE.putIfAbsent(key, loaded);
		if (existing != null) {
			try {
				loadedProvider.close();
			} catch (Exception e) {
				PackForge.LOGGER.warn("PackForge failed to close duplicate bitmap font provider", e);
			}
			return existing.retain();
		}
		return loaded.retain();
	}

	public static GlyphProvider get(BitmapProvider.Definition definition) {
		SharedState state = CACHE.get(Key.from(definition));
		return state == null ? null : state.retain();
	}

	public static void resetForReload() {
		CACHE.clear();
	}

	private static final class SharedState {
		private final GlyphProvider delegate;
		private final AtomicInteger references = new AtomicInteger();
		private volatile boolean closed;

		private SharedState(GlyphProvider delegate) {
			this.delegate = delegate;
		}

		private GlyphProvider retain() {
			references.incrementAndGet();
			return new SharedProvider(this);
		}

		private void release() {
			if (references.decrementAndGet() == 0 && !closed) {
				closed = true;
				try {
					delegate.close();
				} catch (Exception e) {
					PackForge.LOGGER.warn("PackForge failed to close cached bitmap font provider", e);
				}
			}
		}
	}

	private record SharedProvider(SharedState state) implements GlyphProvider {
		@Override
		public UnbakedGlyph getGlyph(int codepoint) {
			return state.delegate.getGlyph(codepoint);
		}

		@Override
		public IntSet getSupportedGlyphs() {
			return state.delegate.getSupportedGlyphs();
		}

		@Override
		public void close() {
			state.release();
		}
	}

	private record Key(String file, int height, int ascent, int gridHash, int[][] grid) {
		static Key from(BitmapProvider.Definition definition) {
			int[][] copy = Arrays.stream(definition.codepointGrid())
				.map(int[]::clone)
				.toArray(int[][]::new);
			return new Key(definition.file().toString(), definition.height(), definition.ascent(), Arrays.deepHashCode(copy), copy);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Key other)) return false;
			return height == other.height
				&& ascent == other.ascent
				&& gridHash == other.gridHash
				&& file.equals(other.file)
				&& Arrays.deepEquals(grid, other.grid);
		}

		@Override
		public int hashCode() {
			int result = file.hashCode();
			result = 31 * result + height;
			result = 31 * result + ascent;
			result = 31 * result + gridHash;
			return result;
		}
	}

	private FontBitmapProviderCache() {}
}
