package com.teenkung.packforge.client;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.loader.ReloadExecutionContext;
import com.teenkung.packforge.loader.ReloadHooks;
import net.minecraft.client.renderer.texture.SpriteContents;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test-only evidence counters for compatibility fixtures.
 *
 * <p>The counters are completely inert unless the runtime harness explicitly sets
 * {@code PACKFORGE_RUNTIME_HEAVY_EVIDENCE=true}.  They prove that a heavy fixture
 * reached the corresponding client loading stages; a resolved-resource hash alone
 * cannot provide that proof.</p>
 */
public final class HeavyFixtureEvidence {
	private static final String ENABLE_ENV = "PACKFORGE_RUNTIME_HEAVY_EVIDENCE";
	private static final boolean ENABLED = readEnabled();
	private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
	private static final AtomicInteger FONT_PROVIDER_ATTEMPTS = new AtomicInteger();
	private static final AtomicInteger FONT_PROVIDER_SUCCESSES = new AtomicInteger();
	private static final AtomicInteger FIXTURE_MODEL_LOADS = new AtomicInteger();
	private static final AtomicInteger SPRITE_DECODE_COUNT = new AtomicInteger();
	private static final AtomicInteger HIGH_RESOLUTION_SPRITES = new AtomicInteger();
	private static final AtomicInteger MIPMAP_STAGE_COUNT = new AtomicInteger();
	private static final AtomicInteger MIPMAP_OWNED_COUNT = new AtomicInteger();

	private HeavyFixtureEvidence() {
	}

	/** Registers reload lifecycle hooks when the runtime harness explicitly enables evidence. */
	public static void initialize() {
		if (!enabled() || !INITIALIZED.compareAndSet(false, true)) {
			return;
		}
		ReloadHooks.registerStartHook(HeavyFixtureEvidence::resetForReload);
		ReloadHooks.registerCompletionHook(HeavyFixtureEvidence::reportCompletion);
		PackForge.LOGGER.info("PackForge heavy fixture evidence enabled");
	}

	public static boolean enabled() {
		return ENABLED;
	}

	private static boolean readEnabled() {
		try {
			return Boolean.parseBoolean(System.getenv().getOrDefault(ENABLE_ENV, "false"));
		} catch (SecurityException exception) {
			return false;
		}
	}

	public static void recordFontProviderAttempt() {
		if (enabled()) {
			FONT_PROVIDER_ATTEMPTS.incrementAndGet();
		}
	}

	public static void recordFontProviderResult(boolean success) {
		if (enabled() && success) {
			FONT_PROVIDER_SUCCESSES.incrementAndGet();
		}
	}

	public static void recordModelLoad(String id) {
		if (enabled() && id != null && id.contains("fixture_")) {
			FIXTURE_MODEL_LOADS.incrementAndGet();
		}
	}

	public static void recordSprites(Iterable<?> sprites) {
		if (!enabled() || sprites == null) {
			return;
		}
		for (Object sprite : sprites) {
			if (sprite instanceof SpriteContents contents) {
				recordSprite(contents.width(), contents.height());
			}
		}
	}

	public static void recordSprite(int width, int height) {
		if (!enabled()) {
			return;
		}
		SPRITE_DECODE_COUNT.incrementAndGet();
		if (width >= 256 || height >= 256) {
			HIGH_RESOLUTION_SPRITES.incrementAndGet();
		}
	}

	public static void recordMipmapStage(boolean owned) {
		if (!enabled()) {
			return;
		}
		MIPMAP_STAGE_COUNT.incrementAndGet();
		if (owned) {
			MIPMAP_OWNED_COUNT.incrementAndGet();
		}
	}

	private static void resetForReload() {
		FONT_PROVIDER_ATTEMPTS.set(0);
		FONT_PROVIDER_SUCCESSES.set(0);
		FIXTURE_MODEL_LOADS.set(0);
		SPRITE_DECODE_COUNT.set(0);
		HIGH_RESOLUTION_SPRITES.set(0);
		MIPMAP_STAGE_COUNT.set(0);
		MIPMAP_OWNED_COUNT.set(0);
	}

	private static void reportCompletion(ReloadExecutionContext context, Throwable error) {
		if (!enabled() || context == null) {
			return;
		}
		String status = error == null ? "PASS" : "FAILURE";
		PackForge.LOGGER.info(
			"PackForge heavy fixture evidence: id={} status={} fontProviderAttempts={} fontProviderSuccesses={} fixtureModelLoads={} spriteDecodeCount={} highResolutionSprites={} mipmapStageCount={} mipmapOwnedCount={}",
			context.reloadId(),
			status,
			FONT_PROVIDER_ATTEMPTS.get(),
			FONT_PROVIDER_SUCCESSES.get(),
			FIXTURE_MODEL_LOADS.get(),
			SPRITE_DECODE_COUNT.get(),
			HIGH_RESOLUTION_SPRITES.get(),
			MIPMAP_STAGE_COUNT.get(),
			MIPMAP_OWNED_COUNT.get());
	}
}
