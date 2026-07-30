package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.teenkung.packforge.PackForge;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class FontBitmapProviderCache {
	private static final ConcurrentHashMap<Key, SharedState> CACHE = new ConcurrentHashMap<>();
	private static final AtomicLong GENERATION = new AtomicLong();

	public static GlyphProvider get(ResourceManager manager, BitmapProvider.Definition definition) {
		SharedState state = CACHE.get(Key.from(GENERATION.get(), manager, definition));
		return state == null ? null : state.retain();
	}

	public static GlyphProvider cache(ResourceManager manager, BitmapProvider.Definition definition, GlyphProvider loaded) {
		Key key = Key.from(GENERATION.get(), manager, definition);
		SharedState candidate = new SharedState(loaded);
		SharedState existing = CACHE.putIfAbsent(key, candidate);
		if (existing != null) {
			try {
				loaded.close();
			} catch (Exception exception) {
				PackForge.LOGGER.warn("PackForge failed to close duplicate bitmap font provider", exception);
			}
			return existing.retain();
		}
		return candidate.retain();
	}

	public static void resetForReload() {
		GENERATION.incrementAndGet();
		CACHE.values().forEach(SharedState::retire);
		CACHE.clear();
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
			if (this.closed) {
				return null;
			}
			this.references.incrementAndGet();
			return new SharedProvider(this);
		}

		private synchronized void retire() {
			this.retired = true;
			this.closeIfUnused();
		}

		private synchronized void release() {
			if (this.references.decrementAndGet() < 0) {
				this.references.set(0);
				return;
			}
			this.closeIfUnused();
		}

		private void closeIfUnused() {
			if (this.retired && this.references.get() == 0 && !this.closed) {
				this.closed = true;
				try {
					this.delegate.close();
				} catch (Exception exception) {
					PackForge.LOGGER.warn("PackForge failed to close cached bitmap font provider", exception);
				}
			}
		}
	}

	private record SharedProvider(SharedState state) implements GlyphProvider {
		@Override
		public GlyphInfo getGlyph(int codepoint) {
			return this.state.delegate.getGlyph(codepoint);
		}

		@Override
		public IntSet getSupportedGlyphs() {
			return this.state.delegate.getSupportedGlyphs();
		}

		@Override
		public void close() {
			this.state.release();
		}
	}

	private static final class Key {
		private final long generation;
		private final ResourceManager manager;
		private final String file;
		private final int height;
		private final int ascent;
		private final int[][] grid;
		private final int hash;

		private Key(long generation, ResourceManager manager, BitmapProvider.Definition definition) {
			this.generation = generation;
			this.manager = manager;
			this.file = definition.file().toString();
			this.height = definition.height();
			this.ascent = definition.ascent();
			this.grid = Arrays.stream(definition.codepointGrid()).map(int[]::clone).toArray(int[][]::new);
			int value = Long.hashCode(generation);
			value = 31 * value + System.identityHashCode(manager);
			value = 31 * value + this.file.hashCode();
			value = 31 * value + this.height;
			value = 31 * value + this.ascent;
			value = 31 * value + Arrays.deepHashCode(this.grid);
			this.hash = value;
		}

		private static Key from(long generation, ResourceManager manager, BitmapProvider.Definition definition) {
			return new Key(generation, manager, definition);
		}

		@Override
		public int hashCode() {
			return this.hash;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof Key key)) {
				return false;
			}
			return this.generation == key.generation
				&& this.manager == key.manager
				&& this.height == key.height
				&& this.ascent == key.ascent
				&& this.file.equals(key.file)
				&& Arrays.deepEquals(this.grid, key.grid);
		}
	}

	private FontBitmapProviderCache() {}
}
