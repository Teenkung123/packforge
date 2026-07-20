package com.teenkung.packforge.client.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class SpriteMetadataCache {
	public record CapRecord(Identifier sprite, Identifier atlas, int scale, int originalFrameW, int originalFrameH, int newFrameW, int newFrameH) {}

	public static final class RetainedSprite {
		public final Identifier sprite;
		public final Identifier atlas;
		public NativeImage image;
		public FrameSize frameSize;
		public final Optional<AnimationMetadataSection> animation;
		public final List<MetadataSectionType.WithValue<?>> additional;
		public final Optional<TextureMetadataSection> texture;
		public int appliedScale;

		public RetainedSprite(Identifier sprite, Identifier atlas, NativeImage image, FrameSize frameSize,
		                      Optional<AnimationMetadataSection> animation,
		                      List<MetadataSectionType.WithValue<?>> additional,
		                      Optional<TextureMetadataSection> texture, int appliedScale) {
			this.sprite = sprite;
			this.atlas = atlas;
			this.image = image;
			this.frameSize = frameSize;
			this.animation = animation;
			this.additional = additional;
			this.texture = texture;
			this.appliedScale = appliedScale;
		}

		public SpriteContents toSpriteContents() {
			return new SpriteContents(sprite, frameSize, image, animation, additional, texture);
		}
	}

	private static final Map<Identifier, AtomicInteger> downscaledCountByAtlas = new ConcurrentHashMap<>();
	private static final Map<Identifier, AtomicInteger> spriteCountByAtlas = new ConcurrentHashMap<>();
	private static final Map<Identifier, CapRecord> records = new ConcurrentHashMap<>();
	private static final Map<Identifier, RetainedSprite> retained = new ConcurrentHashMap<>();

	public static void recordSprite(Identifier atlas) {
		spriteCountByAtlas.computeIfAbsent(atlas, k -> new AtomicInteger()).incrementAndGet();
	}

	public static void recordCap(Identifier sprite, Identifier atlas, int scale, int origFW, int origFH, int newFW, int newFH) {
		records.put(sprite, new CapRecord(sprite, atlas, scale, origFW, origFH, newFW, newFH));
		downscaledCountByAtlas.computeIfAbsent(atlas, k -> new AtomicInteger()).incrementAndGet();
	}

	public static void retain(RetainedSprite r) {
		RetainedSprite prev = retained.put(r.sprite, r);
		if (prev != null && prev.image != r.image) {
			try { prev.image.close(); } catch (Throwable ignored) {}
		}
	}

	public static RetainedSprite getRetained(Identifier sprite) {
		return retained.get(sprite);
	}

	public static void resetForReload() {
		downscaledCountByAtlas.clear();
		spriteCountByAtlas.clear();
		records.clear();
		for (RetainedSprite r : retained.values()) {
			try { r.image.close(); } catch (Throwable ignored) {}
		}
		retained.clear();
	}

	public static Map<Identifier, AtomicInteger> downscaledByAtlas() { return downscaledCountByAtlas; }
	public static Map<Identifier, AtomicInteger> spriteCountByAtlas() { return spriteCountByAtlas; }
	public static Map<Identifier, CapRecord> records() { return records; }

	private SpriteMetadataCache() {}
}
