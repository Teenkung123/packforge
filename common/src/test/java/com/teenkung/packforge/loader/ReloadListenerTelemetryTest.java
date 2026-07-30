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
