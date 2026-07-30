package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import com.teenkung.packforge.PackForge;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class FontBitmapProviderCache {
	private static final AtomicLong GENERATION = new AtomicLong();
	private static final Map<Key, SharedState> CACHE = new ConcurrentHashMap<>();

	public static GlyphProvider get(ResourceManager manager, BitmapProvider.Definition definition) {
		SharedState state = CACHE.get(Key.from(GENERATION.get(), manager, definition));
		return state == null ? null : state.retain();
	}

	public static GlyphProvider cache(ResourceManager manager, BitmapProvider.Definition definition, GlyphProvider loaded) {
		Key key = Key.from(GENERATION.get(), manager, definition);
		SharedState candidate = new SharedState(loaded);
		SharedState existing = CACHE.putIfAbsent(key, candidate);
		if (existing != null) {
			close(loaded, "duplicate");
			return existing.retain();
		}
		return candidate.retain();
	}

	public static void resetForReload() {
		GENERATION.incrementAndGet();
		CACHE.values().forEach(SharedState::retire);
		CACHE.clear();
	}

	private static void close(GlyphProvider provider, String reason) {
		try {
			provider.close();
		} catch (Exception error) {
			PackForge.LOGGER.warn("PackForge failed to close {} bitmap font provider", reason, error);
		}
	}

	private static final class SharedState {
		private final GlyphProvider delegate;
		private final AtomicInteger references = new AtomicInteger();
		private boolean retired;
		private boolean closed;

		private SharedState(GlyphProvider delegate) {
			this.delegate = delegate;
		}

		private synchronized GlyphProvider retain() {
			if (closed) return null;
			references.incrementAndGet();
			return new SharedProvider(this);
		}

		private synchronized void retire() {
			retired = true;
			closeIfUnused();
		}

		private synchronized void release() {
			if (references.decrementAndGet() < 0) {
				references.set(0);
				return;
			}
			closeIfUnused();
		}

		private void closeIfUnused() {
			if (retired && references.get() == 0 && !closed) {
				closed = true;
				close(delegate, "retired");
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

	private record Key(long generation, IdentityKey manager, String file, int height, int ascent, int gridHash, int[][] grid) {
		private static Key from(long generation, ResourceManager manager, BitmapProvider.Definition definition) {
			int[][] copy = Arrays.stream(definition.codepointGrid()).map(int[]::clone).toArray(int[][]::new);
			return new Key(
				generation,
				new IdentityKey(manager),
				definition.file().toString(),
				definition.height(),
				definition.ascent(),
				Arrays.deepHashCode(copy),
				copy
			);
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Key key
				&& generation == key.generation
				&& manager.equals(key.manager)
				&& height == key.height
				&& ascent == key.ascent
				&& gridHash == key.gridHash
				&& file.equals(key.file)
				&& Arrays.deepEquals(grid, key.grid);
		}

		@Override
		public int hashCode() {
			int result = Long.hashCode(generation);
			result = 31 * result + manager.hashCode();
			result = 31 * result + file.hashCode();
			result = 31 * result + height;
			result = 31 * result + ascent;
			return 31 * result + gridHash;
		}
	}

	private static final class IdentityKey {
		private final Object value;
		private final int hash;

		private IdentityKey(Object value) {
			this.value = value;
			this.hash = System.identityHashCode(value);
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof IdentityKey key && value == key.value;
		}

		@Override
		public int hashCode() {
			return hash;
		}
	}

	private FontBitmapProviderCache() {}
}
