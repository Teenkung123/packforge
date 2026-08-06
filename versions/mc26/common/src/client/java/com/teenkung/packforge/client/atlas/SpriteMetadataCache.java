package com.teenkung.packforge.client.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Reload-scoped atlas ownership for the optional mc26 stitch recovery path.
 *
 * <p>The cache never clones a decoded image.  A record points at the image
 * owned by its {@link SpriteContents}; that ownership is transferred only
 * after a replacement has been constructed successfully.</p>
 */
public final class SpriteMetadataCache {
	private static final Object NO_CONTEXT = new Object();
	private static final Map<Object, Map<Identifier, List<AtlasState>>> STATES = new IdentityHashMap<>();
	private static final ThreadLocal<Scope> CURRENT_SCOPE = new ThreadLocal<>();

	public static AtlasState bind(Identifier atlas, BoundedSpriteDecode.Plan plan) {
		Object contextKey = contextKey();
		synchronized (STATES) {
			Map<Identifier, List<AtlasState>> byAtlas = STATES.computeIfAbsent(contextKey, ignored -> new java.util.HashMap<>());
			AtlasState state = new AtlasState(contextKey, atlas, plan);
			byAtlas.computeIfAbsent(atlas, ignored -> new ArrayList<>()).add(state);
			return state;
		}
	}

	public static Scope enter(Identifier atlas, BoundedSpriteDecode.Plan plan) {
		return enter(bind(atlas, plan));
	}

	public static Scope enter(AtlasState state) {
		return new Scope(state, CURRENT_SCOPE.get());
	}

	public static SpriteContents withAtlas(
		Identifier atlas,
		BoundedSpriteDecode.Plan plan,
		Supplier<SpriteContents> loader
	) {
		return withAtlas(bind(atlas, plan), loader);
	}

	public static SpriteContents withAtlas(AtlasState state, Supplier<SpriteContents> loader) {
		Scope scope = enter(state);
		try {
			SpriteContents contents = loader.get();
			scope.complete(contents);
			return contents;
		} catch (RuntimeException | Error failure) {
			scope.abort();
			throw failure;
		} finally {
			scope.close();
		}
	}

	public static void recordConstructed(
		SpriteContents contents,
		Identifier sprite,
		FrameSize frameSize,
		NativeImage image,
		Optional<AnimationMetadataSection> animation,
		List<MetadataSectionType.WithValue<?>> additional,
		Optional<TextureMetadataSection> texture
	) {
		Scope scope = CURRENT_SCOPE.get();
		if (scope == null) {
			return;
		}
		SpriteRecord record = new SpriteRecord(
			sprite,
			image,
			frameSize,
			animation,
			additional,
			texture
		);
		if (scope.state.addConstructed(contents, record)) {
			scope.created.add(contents);
		}
	}

	public static AtlasState findState(Identifier atlas, List<SpriteContents> contents) {
		synchronized (STATES) {
			AtlasState match = null;
			for (Map<Identifier, List<AtlasState>> byAtlas : STATES.values()) {
				List<AtlasState> candidates = byAtlas.get(atlas);
				if (candidates == null) {
					continue;
				}
				for (AtlasState candidate : candidates) {
					if (!candidate.matches(contents)) {
						continue;
					}
					if (match != null && match != candidate) {
						return null;
					}
					match = candidate;
				}
			}
			return match;
		}
	}

	public static boolean contains(AtlasState state) {
		if (state == null) {
			return false;
		}
		synchronized (STATES) {
			Map<Identifier, List<AtlasState>> byAtlas = STATES.get(state.contextKey);
			if (byAtlas == null) {
				return false;
			}
			List<AtlasState> states = byAtlas.get(state.atlas);
			if (states == null) {
				return false;
			}
			for (AtlasState candidate : states) {
				if (candidate == state) {
					return true;
				}
			}
			return false;
		}
	}

	public static void finish(AtlasState state) {
		if (state == null) {
			return;
		}
		synchronized (STATES) {
			removeLocked(state);
		}
	}

	public static void fail(AtlasState state, List<SpriteContents> contents) {
		if (state == null) {
			closeDistinct(contents);
			return;
		}
		synchronized (STATES) {
			removeLocked(state);
		}
		state.closeAll(contents);
	}

	public static void resetForReload() {
		List<AtlasState> states;
		synchronized (STATES) {
			states = STATES.values().stream()
				.flatMap(byAtlas -> byAtlas.values().stream())
				.flatMap(List::stream)
				.toList();
			STATES.clear();
		}
		for (AtlasState state : states) {
			state.closeAll(List.of());
		}
	}

	private static void removeLocked(AtlasState state) {
		Map<Identifier, List<AtlasState>> byAtlas = STATES.get(state.contextKey);
		if (byAtlas == null) {
			return;
		}
		List<AtlasState> states = byAtlas.get(state.atlas);
		if (states == null || !states.removeIf(candidate -> candidate == state)) {
			return;
		}
		if (states.isEmpty()) {
			byAtlas.remove(state.atlas);
		}
		if (byAtlas.isEmpty()) {
			STATES.remove(state.contextKey);
		}
	}

	private static Object contextKey() {
		ReloadExecutionContext context = ReloadExecutionContext.current();
		return context == null ? NO_CONTEXT : context;
	}

	private static void closeDistinct(List<SpriteContents> contents) {
		if (contents == null || contents.isEmpty()) {
			return;
		}
		Set<SpriteContents> closed = Collections.newSetFromMap(new IdentityHashMap<>());
		for (SpriteContents content : contents) {
			if (content == null || !closed.add(content)) {
				continue;
			}
			try {
				content.close();
			} catch (Throwable ignored) {
				// Cleanup must not replace the original reload failure.
			}
		}
	}

	public static final class Scope implements AutoCloseable {
		private final AtlasState state;
		private final Scope previous;
		private final Set<SpriteContents> created = Collections.newSetFromMap(new IdentityHashMap<>());
		private SpriteContents returned;
		private boolean aborted;
		private boolean closed;

		private Scope(AtlasState state, Scope previous) {
			this.state = state;
			this.previous = previous;
			CURRENT_SCOPE.set(this);
		}

		public void complete(SpriteContents contents) {
			returned = contents;
		}

		public void abort() {
			aborted = true;
		}

		@Override
		public void close() {
			if (closed) {
				return;
			}
			closed = true;
			try {
				for (SpriteContents content : new ArrayList<>(created)) {
					if (content != returned) {
						state.removeAndClose(content);
					}
				}
				if (aborted || returned == null) {
					if (returned != null) {
						state.removeAndClose(returned);
					}
				}
			} finally {
				CURRENT_SCOPE.set(previous);
			}
		}
	}

	public static final class AtlasState {
		private final Object contextKey;
		private final Identifier atlas;
		private final BoundedSpriteDecode.Plan plan;
		private final Map<SpriteContents, SpriteRecord> records = new IdentityHashMap<>();
		private final Set<SpriteContents> closed = Collections.newSetFromMap(new IdentityHashMap<>());
		private int spriteCount;
		private int replacementCount;
		private int stitchAttempts;

		private AtlasState(Object contextKey, Identifier atlas, BoundedSpriteDecode.Plan plan) {
			this.contextKey = contextKey;
			this.atlas = atlas;
			this.plan = plan;
		}

		private synchronized boolean addConstructed(SpriteContents contents, SpriteRecord record) {
			if (records.containsKey(contents)) {
				return false;
			}
			records.put(contents, record);
			spriteCount++;
			return true;
		}

		public synchronized SpriteRecord record(SpriteContents contents) {
			return records.get(contents);
		}

		private synchronized boolean replace(SpriteContents original, SpriteContents replacement, SpriteRecord record) {
			if (records.remove(original) == null) {
				return false;
			}
			records.put(replacement, record);
			replacementCount++;
			return true;
		}

		synchronized void recordAttempt() {
			stitchAttempts++;
		}

		public synchronized int spriteCount() {
			return spriteCount;
		}

		public synchronized int replacementCount() {
			return replacementCount;
		}

		public synchronized int stitchAttempts() {
			return stitchAttempts;
		}

		public BoundedSpriteDecode.Plan plan() {
			return plan;
		}

		public Identifier atlas() {
			return atlas;
		}

		private synchronized boolean matches(List<SpriteContents> contents) {
			if (contents == null || contents.isEmpty()) {
				return false;
			}
			for (SpriteContents content : contents) {
				if (records.containsKey(content)) {
					return true;
				}
			}
			return false;
		}

		private synchronized void removeAndClose(SpriteContents content) {
			records.remove(content);
		closeOnce(content);
		}

		void closeReplaced(SpriteContents content) {
			removeAndClose(content);
		}

		private synchronized void closeAll(List<SpriteContents> additional) {
			Set<SpriteContents> toClose = Collections.newSetFromMap(new IdentityHashMap<>());
			toClose.addAll(records.keySet());
			if (additional != null) {
				toClose.addAll(additional);
			}
			records.clear();
			for (SpriteContents content : toClose) {
				closeOnce(content);
			}
		}

		private void closeOnce(SpriteContents content) {
			if (content == null || !closed.add(content)) {
				return;
			}
			try {
				content.close();
			} catch (Throwable ignored) {
				// Cleanup must not replace the original reload failure.
			}
		}
	}

	public static final class SpriteRecord {
		private final Identifier sprite;
		private final NativeImage image;
		private final FrameSize frameSize;
		private final Optional<AnimationMetadataSection> animation;
		private final List<MetadataSectionType.WithValue<?>> additional;
		private final Optional<TextureMetadataSection> texture;

		SpriteRecord(
			Identifier sprite,
			NativeImage image,
			FrameSize frameSize,
			Optional<AnimationMetadataSection> animation,
			List<MetadataSectionType.WithValue<?>> additional,
			Optional<TextureMetadataSection> texture
		) {
			this.sprite = sprite;
			this.image = image;
			this.frameSize = frameSize;
			this.animation = animation;
			this.additional = List.copyOf(additional);
			this.texture = texture;
		}

		public Identifier sprite() {
			return sprite;
		}

		public NativeImage image() {
			return image;
		}

		public FrameSize frameSize() {
			return frameSize;
		}

		public Optional<AnimationMetadataSection> animation() {
			return animation;
		}

		public List<MetadataSectionType.WithValue<?>> additional() {
			return additional;
		}

		public Optional<TextureMetadataSection> texture() {
			return texture;
		}
	}

	static SpriteRecord recordFor(AtlasState state, SpriteContents contents) {
		return state == null ? null : state.record(contents);
	}

	static boolean replace(AtlasState state, SpriteContents original, SpriteContents replacement, SpriteRecord record) {
		return state != null && state.replace(original, replacement, record);
	}

	private SpriteMetadataCache() {}
}
