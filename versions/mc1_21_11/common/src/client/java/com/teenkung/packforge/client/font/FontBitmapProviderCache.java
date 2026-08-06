package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.config.FeatureFlags;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class FontBitmapProviderCache {
	private static final Object CACHE_LOCK = new Object();
	private static final Map<Key, SharedState> CACHE = new HashMap<>();
	private static long generation;

	public static boolean enabled() {
		ReloadExecutionContext context = ReloadExecutionContext.current();
		return context == null
			? FeatureFlags.fontBitmapProviderCacheEnabled()
			: context.features().fontBitmapProviderCacheEnabled();
	}

	public static GlyphProvider get(ResourceManager manager, BitmapProvider.Definition definition) {
		return get(captureEpoch(), manager, definition);
	}

	public static long captureEpoch() {
		synchronized (CACHE_LOCK) {
			return generation;
		}
	}

	public static GlyphProvider get(long epoch, ResourceManager manager, BitmapProvider.Definition definition) {
		synchronized (CACHE_LOCK) {
			if (epoch != generation) {
				return null;
			}
			SharedState state = CACHE.get(Key.from(epoch, manager, definition));
			return state == null ? null : state.retain();
		}
	}

	public static GlyphProvider cache(ResourceManager manager, BitmapProvider.Definition definition, GlyphProvider loaded) {
		return cache(captureEpoch(), manager, definition, loaded);
	}

	public static GlyphProvider cache(
		long epoch,
		ResourceManager manager,
		BitmapProvider.Definition definition,
		GlyphProvider loaded
	) {
		boolean duplicate = false;
		GlyphProvider result;
		synchronized (CACHE_LOCK) {
			if (epoch != generation) {
				return loaded;
			}
			Key key = Key.from(epoch, manager, definition);
			SharedState candidate = new SharedState(loaded);
			SharedState existing = CACHE.putIfAbsent(key, candidate);
			if (existing == null) {
				result = candidate.retain();
			} else {
				result = existing.retain();
				if (result == null) {
					return loaded;
				}
				duplicate = true;
			}
		}
		if (duplicate) {
			close(loaded, "duplicate");
		}
		return result;
	}

	public static void resetForReload() {
		synchronized (CACHE_LOCK) {
			generation++;
			CACHE.values().forEach(SharedState::retire);
			CACHE.clear();
		}
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

	private static final class SharedProvider implements GlyphProvider {
		private final SharedState state;
		private final AtomicBoolean released = new AtomicBoolean();

		private SharedProvider(SharedState state) {
			this.state = state;
		}

		@Override
		public UnbakedGlyph getGlyph(int codepoint) {
			return this.state.delegate.getGlyph(codepoint);
		}

		@Override
		public IntSet getSupportedGlyphs() {
			return this.state.delegate.getSupportedGlyphs();
		}

		@Override
		public void close() {
			if (this.released.compareAndSet(false, true)) {
				this.state.release();
			}
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
