package com.teenkung.packforge.client.atlas;

import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class SpriteMetadataCache {
	public record CapRecord(Identifier sprite, Identifier atlas, int scale, int originalFrameW, int originalFrameH, int newFrameW, int newFrameH) {}

	private static final Map<Identifier, AtomicInteger> downscaledCountByAtlas = new ConcurrentHashMap<>();
	private static final Map<Identifier, AtomicInteger> spriteCountByAtlas = new ConcurrentHashMap<>();
	private static final Map<Identifier, CapRecord> records = new ConcurrentHashMap<>();

	public static void recordSprite(Identifier atlas) {
		spriteCountByAtlas.computeIfAbsent(atlas, k -> new AtomicInteger()).incrementAndGet();
	}

	public static void recordCap(Identifier sprite, Identifier atlas, int scale, int origFW, int origFH, int newFW, int newFH) {
		records.put(sprite, new CapRecord(sprite, atlas, scale, origFW, origFH, newFW, newFH));
		downscaledCountByAtlas.computeIfAbsent(atlas, k -> new AtomicInteger()).incrementAndGet();
	}

	public static void resetForReload() {
		downscaledCountByAtlas.clear();
		spriteCountByAtlas.clear();
		records.clear();
	}

	public static Map<Identifier, AtomicInteger> downscaledByAtlas() { return downscaledCountByAtlas; }
	public static Map<Identifier, AtomicInteger> spriteCountByAtlas() { return spriteCountByAtlas; }
	public static Map<Identifier, CapRecord> records() { return records; }

	private SpriteMetadataCache() {}
}
