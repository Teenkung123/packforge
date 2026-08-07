package com.teenkung.packforge.client.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.ReportedException;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.StitcherException;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Original-stitch atlas recovery with bounded, identity-owned replacements. */
public final class AtlasRetry {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final AtomicBoolean CAP_UNAVAILABLE_LOGGED = new AtomicBoolean();
	private static final AtomicBoolean RETRY_UNAVAILABLE_LOGGED = new AtomicBoolean();

	@FunctionalInterface
	public interface OriginalStitch {
		SpriteLoader.Preparations stitch(List<SpriteContents> sprites, int mipLevel, Executor executor);
	}

	public static SpriteLoader.Preparations stitch(
		Identifier atlas,
		List<SpriteContents> initial,
		int mipLevel,
		Executor executor,
		OriginalStitch original,
		SpriteMetadataCache.AtlasState state
	) {
		List<SpriteContents> current = initial;
		int recoveryAttempts = 0;
		try {
			while (true) {
				state.recordAttempt();
				try {
					SpriteLoader.Preparations result = original.stitch(current, mipLevel, executor);
					return result;
				} catch (RuntimeException failure) {
					if (!isExactStitchFailure(failure)) {
						throw failure;
					}
					if (recoveryAttempts >= state.plan().atlasRetryMaxAttempts()) {
						throw failure;
					}
					List<SpriteContents> recovered = halveAll(current, state);
					if (recovered == null) {
						throw failure;
					}
					recoveryAttempts++;
					current = recovered;
				}
			}
		} catch (RuntimeException | Error failure) {
			SpriteMetadataCache.fail(state, current);
			throw failure;
		}
	}

	public static boolean isExactStitchFailure(Throwable failure) {
		if (failure instanceof StitcherException) {
			return failure.getClass() == StitcherException.class;
		}
		if (!(failure instanceof ReportedException reported)) {
			return false;
		}
		Throwable cause = reported.getCause();
		return cause != null && cause.getClass() == StitcherException.class;
	}

	static boolean mayAttemptRecovery(int recoveryAttempts, int maxAttempts, boolean replacementCreated) {
		return replacementCreated && recoveryAttempts < Math.max(1, maxAttempts);
	}

	private static List<SpriteContents> halveAll(
		List<SpriteContents> input,
		SpriteMetadataCache.AtlasState state
	) {
		List<SpriteContents> out = new ArrayList<>(input.size());
		IdentityHashMap<SpriteContents, SpriteContents> replacements = new IdentityHashMap<>();
		int changed = 0;
		for (SpriteContents original : input) {
			SpriteContents replacement = replacements.get(original);
			if (replacement == null) {
				replacement = halveOne(original, state);
				if (replacement != original) {
					replacements.put(original, replacement);
					changed++;
				}
			}
			out.add(replacement);
		}
		if (changed == 0) {
			return null;
		}

		for (var entry : replacements.entrySet()) {
			closeReplaced(state, entry.getKey());
		}
		LOGGER.warn("PackForge atlas {} retry: replaced {}/{} sprites", state.atlas(), changed, input.size());
		return out;
	}

	private static SpriteContents halveOne(SpriteContents original, SpriteMetadataCache.AtlasState state) {
		SpriteMetadataCache.SpriteRecord record = SpriteMetadataCache.recordFor(state, original);
		if (record == null) {
			return original;
		}
		NativeImage source = record.image();
		FrameSize frame = record.frameSize();
		int width;
		int height;
		try {
			width = source.getWidth();
			height = source.getHeight();
		} catch (Throwable failure) {
			return original;
		}
		if (width < 2 || height < 2 || (width & 1) != 0 || (height & 1) != 0
			|| frame.width() < 2 || frame.height() < 2
			|| (frame.width() & 1) != 0 || (frame.height() & 1) != 0) {
			return original;
		}

		NativeImage scaled;
		try {
			scaled = SpriteResize.resize(source, width / 2, height / 2);
		} catch (Throwable failure) {
			LOGGER.warn("PackForge atlas {} retry resize failed for {}", state.atlas(), record.sprite(), failure);
			return original;
		}

		FrameSize newFrame = new FrameSize(frame.width() / 2, frame.height() / 2);
		SpriteContents replacement;
		try {
			replacement = new SpriteContents(
				record.sprite(),
				newFrame,
				scaled,
				record.animation(),
				record.additional(),
				record.texture()
			);
		} catch (RuntimeException | Error failure) {
			try {
				scaled.close();
			} catch (Throwable ignored) {
				// Preserve the constructor failure.
			}
			LOGGER.warn("PackForge atlas {} retry construction failed for {}", state.atlas(), record.sprite(), failure);
			return original;
		}

		SpriteMetadataCache.SpriteRecord replacementRecord = new SpriteMetadataCache.SpriteRecord(
			record.sprite(),
			scaled,
			newFrame,
			record.animation(),
			record.additional(),
			record.texture()
		);
		if (!SpriteMetadataCache.replace(state, original, replacement, replacementRecord)) {
			try {
				replacement.close();
			} catch (Throwable ignored) {
				// Preserve the original ownership decision.
			}
			return original;
		}
		return replacement;
	}

	private static void closeReplaced(SpriteMetadataCache.AtlasState state, SpriteContents original) {
		state.closeReplaced(original);
	}

	public static void logCapUnavailable(Identifier atlas) {
		if (CAP_UNAVAILABLE_LOGGED.compareAndSet(false, true)) {
			LOGGER.warn(
				"PackForge mc26 atlas cap remains on the ORIGINAL path for {}; "
					+ "the loader and third-party sprite hooks stay authoritative. "
					+ "Stitch retry is available only when explicitly enabled and uses original contents",
				atlas
			);
		}
	}

	public static void logRetryUnavailable(Identifier atlas) {
		if (RETRY_UNAVAILABLE_LOGGED.compareAndSet(false, true)) {
			LOGGER.warn(
				"PackForge mc26 atlas retry remains on the ORIGINAL path for {}; "
					+ "the call-scoped state association was unavailable, so no cap or retry wrapper was installed",
				atlas
			);
		}
	}

	private AtlasRetry() {}
}
