package com.teenkung.packforge.loader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReloadListenerTelemetryTest {
	@BeforeEach
	void resetReloadStatus() {
		ReloadStatus.resetForTesting();
	}

	@Test
	void applyMarksReadinessOnlyAfterSuccessfulWork() {
		ReloadStatus.start();
		ReloadListenerTelemetry.apply("Shader Loader", () -> {}, false, true).run();
		assertFalse(ReloadStatus.isStatusTextReady());
		ReloadListenerTelemetry.apply("FontManager", () -> {}, false, true).run();
		assertTrue(ReloadStatus.isStatusTextReady());
	}

	@Test
	void applyDoesNotMarkReadinessForResourceKeysAlone() {
		ReloadStatus.start();
		ReloadListenerTelemetry.apply("minecraft:shaders", () -> {}, false, true).run();
		assertFalse(ReloadStatus.isStatusTextReady());
		ReloadListenerTelemetry.apply("minecraft:fonts", () -> {}, false, true).run();

		assertFalse(ReloadStatus.isStatusTextReady());
	}

	@Test
	void applyMarksReadinessForIntermediaryFontListenerName() {
		ReloadStatus.start();
		ReloadListenerTelemetry.apply("Shader Loader", () -> {}, false, true).run();
		assertFalse(ReloadStatus.isStatusTextReady());
		ReloadListenerTelemetry.apply("class_378", () -> {}, false, true).run();

		assertTrue(ReloadStatus.isStatusTextReady());
	}

	@Test
	void readableLabelsCoverIntermediaryVanillaListenerNames() {
		assertEquals("fonts", ReloadStatus.readableListener("class_378"));
		assertEquals("languages", ReloadStatus.readableListener("class_1076"));
		assertEquals("textures", ReloadStatus.readableListener("class_1060"));
		assertEquals("sounds", ReloadStatus.readableListener("class_1144"));
		assertEquals("models", ReloadStatus.readableListener("class_1092"));
		assertEquals("entity models", ReloadStatus.readableListener("class_5599"));
		assertEquals("block renderer", ReloadStatus.readableListener("class_776"));
		assertEquals("GUI sprites", ReloadStatus.readableListener("class_8658"));
		assertEquals("map decorations", ReloadStatus.readableListener("class_9443"));
		assertEquals("clouds", ReloadStatus.readableListener("class_9955"));
		assertEquals("equipment assets", ReloadStatus.readableListener("class_10201"));
		assertEquals("dry foliage colors", ReloadStatus.readableListener("class_10831"));
		assertEquals("waypoint styles", ReloadStatus.readableListener("class_11327"));
		assertEquals("painting textures", ReloadStatus.readableListener("class_4044"));
		assertEquals("shader loader", ReloadStatus.readableListener("class_757"));
		assertEquals(
			"shader loader",
			ReloadStatus.readableListener("net.minecraft.client.renderer.ShaderManager Reload Listener")
		);
	}

	@Test
	void successfulCurrentReloadProvidesSafeReadinessFallback() {
		ReloadStatus.start();
		ReloadListenerTelemetry.apply("loader-specific-font-listener", () -> {}, false, true).run();
		ReloadListenerTelemetry.apply("loader-specific-shader-listener", () -> {}, false, true).run();
		assertFalse(ReloadStatus.isStatusTextReady());

		ReloadStatus.finish(null, false);

		assertTrue(ReloadStatus.isStatusTextReady());
	}

	@Test
	void failedCurrentReloadDoesNotProvideReadinessFallback() {
		ReloadStatus.start();

		ReloadStatus.finish(new IllegalStateException("expected"), false);

		assertFalse(ReloadStatus.isStatusTextReady());
	}

	@Test
	void failedApplyAlwaysClosesTaskWithoutMarkingItApplied() {
		ReloadStatus.start();
		AtomicInteger calls = new AtomicInteger();
		Runnable wrapped = ReloadListenerTelemetry.apply("Shader Loader", () -> {
			calls.incrementAndGet();
			throw new IllegalStateException("expected");
		}, false, true);
		assertThrows(IllegalStateException.class, wrapped::run);
		assertEquals(1, calls.get());
		assertFalse(ReloadStatus.isStatusTextReady());
		assertFalse(ReloadStatus.detailLine().contains("tasks"));
	}

	@Test
	void disabledTelemetryReturnsOriginalRunnable() {
		Runnable command = () -> {};
		assertEquals(command, ReloadListenerTelemetry.prepare("FontManager", command, false, false));
		assertEquals(command, ReloadListenerTelemetry.apply("FontManager", command, false, false));
	}

	@Test
	void finishingClearsTheActiveReloadState() {
		ReloadStatus.start();
		assertTrue(ReloadStatus.isActive());

		ReloadStatus.finish(null);

		assertFalse(ReloadStatus.isActive());
		assertTrue(ReloadStatus.isComplete());
		ReloadStatus.consumeSummaryToast();
	}

	@Test
	void laterReloadsReuseEstablishedUiReadiness() {
		ReloadStatus.start();
		ReloadListenerTelemetry.apply("Shader Loader", () -> {}, false, true).run();
		ReloadListenerTelemetry.apply("FontManager", () -> {}, false, true).run();
		assertTrue(ReloadStatus.isStatusTextReady());
		ReloadStatus.finish(null);

		ReloadStatus.start();

		assertTrue(ReloadStatus.isStatusTextReady());
	}

	@Test
	void activeReloadDoesNotClaimOneHundredPercentBeforeCompletion() {
		ReloadStatus.start();

		assertEquals(0.99F, ReloadStatus.displayProgress(1.0F));

		ReloadStatus.finish(null, false);
		assertEquals(1.0F, ReloadStatus.displayProgress(1.0F));
	}

	@Test
	void failedReloadToastIsConsumableOnceWithoutReadiness() {
		ReloadStatus.start();
		ReloadStatus.finish(new IllegalStateException("expected"), true);

		ReloadStatus.ReloadSummary summary = ReloadStatus.consumeSummaryToast();

		assertNotNull(summary);
		assertFalse(summary.success());
		assertNull(ReloadStatus.consumeSummaryToast());
	}
}
