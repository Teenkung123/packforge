package com.teenkung.packforge.loader;

import com.teenkung.packforge.concurrent.PreparationBudget;
import com.teenkung.packforge.config.OptimizationPlan;
import com.teenkung.packforge.config.PackForgeConfig;
import com.teenkung.packforge.config.ReloadFeatureSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ReloadDirectoryIndexTest {
    private static final FileVisitOption[] NO_OPTIONS = new FileVisitOption[0];
    private static final BiPredicate<Path, BasicFileAttributes> FILES = (path, attributes) -> attributes.isRegularFile();
    @TempDir Path temporary;

    @AfterEach void reset() { ReloadExecutionContext.resetForTesting(); }

    @Test void buildsOnThirdCallAndPreservesOrderPrefixesAndOriginalPredicate() throws Exception {
        Path root = fixture();
        PreparationBudget budget = new PreparationBudget();
        try (var scope = budget.openScope(); var index = new ReloadDirectoryIndex(scope)) {
            AtomicInteger scans = new AtomicInteger();
            ReloadDirectoryIndex.Finder finder = counting(scans);
            Path models = root.resolve("models");
            for (int i = 0; i < 2; i++) assertEquals(vanilla(models), listed(index, root, models, finder));
            assertEquals(0, index.statistics().builds());
            assertEquals(vanilla(models), listed(index, root, models, finder));
            assertEquals(1, index.statistics().builds());
            assertEquals(3, scans.get());
            for (String prefix : List.of("", "models", "models/nested", "models2", "empty", "models/z.json")) {
                Path start = root.resolve(prefix);
                assertEquals(vanilla(start), listed(index, root, start, finder));
            }
            // Invalid resource names must still reach vanilla's validation after the Files.find hook.
            assertTrue(listed(index, root, models, finder).contains(models.resolve("has space.json")));
            assertTrue(listed(index, root, models, finder).contains(models.resolve("Upper.json")));
            try (var stream = index.find(root, root, Integer.MAX_VALUE,
                    (path, attributes) -> attributes.isDirectory(), NO_OPTIONS, finder);
                 var expected = Files.find(root, Integer.MAX_VALUE, (path, attributes) -> attributes.isDirectory())) {
                assertEquals(expected.toList(), stream.toList());
            }
            assertEquals(3, scans.get(), "cached listings must not rescan");
        }
        assertEquals(0, budget.used());
    }

    @Test void missingPathsAndUnsupportedWalksKeepVanillaErrorsAndOptions() throws Exception {
        Path root = fixture();
        try (var scope = new PreparationBudget().openScope(); var index = new ReloadDirectoryIndex(scope)) {
            warm(index, root);
            Path missing = root.resolve("missing");
            assertThrows(NoSuchFileException.class, () -> listed(index, root, missing, Files::find));
            Path notDirectory = root.resolve("models/z.json/child");
            assertThrows(IOException.class, () -> listed(index, root, notDirectory, Files::find));
            for (int depth : List.of(0, 1, 2)) {
                try (var expected = Files.find(root, depth, FILES);
                     var actual = index.find(root, root, depth, FILES, NO_OPTIONS, Files::find)) {
                    assertEquals(expected.toList(), actual.toList());
                }
            }
            try (var expected = Files.find(root, Integer.MAX_VALUE, FILES, FileVisitOption.FOLLOW_LINKS);
                 var actual = index.find(root, root, Integer.MAX_VALUE, FILES,
                     new FileVisitOption[]{FileVisitOption.FOLLOW_LINKS}, Files::find)) {
                assertEquals(expected.toList(), actual.toList());
            }
            assertThrows(NoSuchFileException.class, () -> listed(index, missing, missing, Files::find));
            assertThrows(NoSuchFileException.class, () -> listed(index, missing, missing, Files::find));
            assertThrows(NoSuchFileException.class, () -> listed(index, missing, missing, Files::find));
        }
    }

    @Test void symlinksKeepNonFollowingAndDirectPrefixSemantics() throws Exception {
        Path root = fixture();
        Path outside = Files.createDirectories(temporary.resolve("outside"));
        Files.writeString(outside.resolve("external.json"), "external");
        try {
            Files.createSymbolicLink(root.resolve("linked-directory"), outside);
            Files.createSymbolicLink(root.resolve("linked-file.json"), root.resolve("models/z.json"));
            Files.createSymbolicLink(root.resolve("broken.json"), root.resolve("absent"));
        } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            assumeTrue(false, "Host cannot create symlinks: " + unavailable);
        }
        try (var scope = new PreparationBudget().openScope(); var index = new ReloadDirectoryIndex(scope)) {
            warm(index, root);
            for (String prefix : List.of("", "linked-directory", "linked-directory/external.json", "linked-file.json", "broken.json")) {
                Path start = root.resolve(prefix);
                assertEquals(vanilla(start), listed(index, root, start, Files::find));
            }
        }
    }

    @Test void budgetFailureDiscardsPartialScanAndDoesNotRetry() throws Exception {
        Path root = fixture();
        // Allow a partial scan even when the temporary directory has a long absolute path.
        PreparationBudget budget = new PreparationBudget(2500L + 8L * root.toString().length());
        try (var scope = budget.openScope(); var index = new ReloadDirectoryIndex(scope)) {
            AtomicInteger scans = new AtomicInteger();
            var finder = counting(scans);
            for (int i = 0; i < 2; i++) assertEquals(vanilla(root), listed(index, root, root, finder));
            long namespaceMemory = budget.used();
            for (int i = 0; i < 3; i++) assertEquals(vanilla(root), listed(index, root, root, finder));
            assertEquals(6, scans.get(), "one failed whole-namespace scan plus five vanilla calls");
            assertEquals(1, index.statistics().failures());
            assertEquals(0, index.statistics().builds());
            assertTrue(budget.peak() > namespaceMemory, "the scan must admit paths before exhausting its budget");
            assertEquals(namespaceMemory, budget.used(), "partial paths must release their memory");
        }
        assertEquals(0, budget.used());
    }

    @Test void failedWholeNamespaceScanFallsBackToReadableSubtree() throws Exception {
        Path root = fixture();
        Path models = root.resolve("models");
        try (var scope = new PreparationBudget().openScope(); var index = new ReloadDirectoryIndex(scope)) {
            AtomicInteger failures = new AtomicInteger();
            ReloadDirectoryIndex.Finder finder = (start, depth, predicate, options) -> {
                if (start.equals(root)) {
                    failures.incrementAndGet();
                    // Files.find can fail lazily while consuming a directory stream.
                    return Stream.of(root).peek(ignored -> { throw new UncheckedIOException(new IOException("unreadable sibling")); });
                }
                return Files.find(start, depth, predicate, options);
            };
            for (int i = 0; i < 5; i++) assertEquals(vanilla(models), listed(index, root, models, finder));
            assertEquals(1, failures.get());
            assertEquals(1, index.statistics().failures());
        }
    }

    @Test void concurrentCallersDoNotWaitForBuildAndOnlyOneSnapshotIsPublished() throws Exception {
        Path root = fixture();
        try (var scope = new PreparationBudget().openScope(); var index = new ReloadDirectoryIndex(scope)) {
            listed(index, root, root.resolve("models"), Files::find);
            listed(index, root, root.resolve("models2"), Files::find);
            CountDownLatch scanning = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            ReloadDirectoryIndex.Finder slow = (start, depth, predicate, options) -> {
                scanning.countDown();
                await(release);
                return Files.find(start, depth, predicate, options);
            };
            CompletableFuture<List<Path>> builder = CompletableFuture.supplyAsync(() -> uncheckedList(index, root, slow));
            try {
                assertTrue(scanning.await(5, TimeUnit.SECONDS));
                CompletableFuture<List<Path>> concurrent = CompletableFuture.supplyAsync(() -> uncheckedList(index, root, Files::find));
                assertEquals(vanilla(root), concurrent.get(5, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
            assertEquals(vanilla(root), builder.get(5, TimeUnit.SECONDS));
            assertEquals(1, index.statistics().builds());
            assertEquals(vanilla(root), listed(index, root, root, Files::find));
        }
    }

    @Test void retirementKeepsBorrowedPathsChargedUntilStreamCloses() throws Exception {
        Path root = fixture();
        PreparationBudget budget = new PreparationBudget();
        try (var scope = budget.openScope(); var index = new ReloadDirectoryIndex(scope)) {
            warm(index, root);
            try (var borrowed = index.find(root, root, Integer.MAX_VALUE, FILES, NO_OPTIONS, Files::find)) {
                scope.retire();
                assertTrue(budget.used() > 0);
                assertEquals(vanilla(root), borrowed.toList());
            }
            assertEquals(0, budget.used());
            Files.writeString(root.resolve("later.json"), "later");
            assertEquals(vanilla(root), listed(index, root, root, Files::find));
        }
    }

    @Test void retirementDuringScanCannotPublishOrLeakSnapshot() throws Exception {
        Path root = fixture();
        PreparationBudget budget = new PreparationBudget();
        try (var scope = budget.openScope(); var index = new ReloadDirectoryIndex(scope)) {
            listed(index, root, root, Files::find);
            listed(index, root, root, Files::find);
            CountDownLatch scanning = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            ReloadDirectoryIndex.Finder slow = (start, depth, predicate, options) -> {
                scanning.countDown();
                await(release);
                return Files.find(start, depth, predicate, options);
            };
            CompletableFuture<List<Path>> builder = CompletableFuture.supplyAsync(() -> uncheckedList(index, root, slow));
            try {
                assertTrue(scanning.await(5, TimeUnit.SECONDS));
                scope.retire();
            } finally { release.countDown(); }
            assertEquals(vanilla(root), builder.get(5, TimeUnit.SECONDS));
            assertEquals(0, budget.used());
        }
    }

    @Test void onlyPreparationWorkersIndexAndLaterReloadSeesEdits() throws Exception {
        Path root = fixture();
        AtomicInteger scans = new AtomicInteger();
        ReloadDirectoryIndex.Finder finder = counting(scans);
        assertEquals(vanilla(root), currentList(root, finder));
        ReloadExecutionContext older = ReloadExecutionContext.startForTesting(features(true));
        Runnable listing = () -> {
            for (int i = 0; i < 5; i++) assertEquals(uncheckedVanilla(root), uncheckedCurrentList(root, finder));
        };
        // Even a direct preparation executor on the initiating/render thread must use vanilla.
        ReloadListenerTelemetry.prepareExecutor(older, "test", Runnable::run).execute(listing);
        assertEquals(6, scans.get());
        CompletableFuture.runAsync(ReloadExecutionContext.bindRunnable(older, listing)).get(5, TimeUnit.SECONDS);
        assertEquals(11, scans.get());
        CompletableFuture.runAsync(ReloadExecutionContext.bindPreparationRunnable(older, listing)).get(5, TimeUnit.SECONDS);
        assertEquals(14, scans.get());
        assertEquals(1, older.directoryIndex().statistics().builds());
        ReloadExecutionContext newer = ReloadExecutionContext.startForTesting(features(true));
        Files.writeString(root.resolve("later.json"), "edit visible next reload");
        assertNull(older.directoryIndex());
        CompletableFuture.runAsync(ReloadExecutionContext.bindPreparationRunnable(older, listing)).get(5, TimeUnit.SECONDS);
        assertEquals(19, scans.get(), "old queued tasks cannot borrow the newer reload's index");
        CompletableFuture.runAsync(ReloadExecutionContext.bindPreparationRunnable(newer, listing)).get(5, TimeUnit.SECONDS);
        assertEquals(22, scans.get());
        ReloadExecutionContext.finish(newer);
        assertEquals(0, newer.preparationBudget().used());
        ReloadExecutionContext disabled = ReloadExecutionContext.startForTesting(features(false));
        CompletableFuture.runAsync(ReloadExecutionContext.bindPreparationRunnable(disabled, listing)).get(5, TimeUnit.SECONDS);
        assertEquals(27, scans.get());
    }

    @Test void nestedApplyTasksClearAndRestorePreparationEligibilityEvenOnFailure() throws Exception {
        ReloadExecutionContext context = ReloadExecutionContext.startForTesting(features(true));
        CompletableFuture.runAsync(ReloadExecutionContext.bindPreparationRunnable(context, () -> {
            assertSame(context, ReloadExecutionContext.preparationContext());
            assertThrows(IllegalStateException.class, () -> ReloadListenerTelemetry.applyExecutor(context, "test", Runnable::run).execute(() -> {
                assertNull(ReloadExecutionContext.preparationContext());
                throw new IllegalStateException("expected");
            }));
            assertSame(context, ReloadExecutionContext.preparationContext());
        })).get(5, TimeUnit.SECONDS);
        assertNull(ReloadExecutionContext.preparationContext());
    }

    @Test void capturedOuterExecutorCarriesPreparationWithoutListenerWrappingOrExtraTelemetry() throws Exception {
        Path root = fixture();
        ReloadExecutionContext context = ReloadExecutionContext.startForTesting(features(true));
        AtomicInteger scans = new AtomicInteger();
        // The vanilla factories through 1.21.4 submit directly to their captured outer executor.
        // No ReloadListenerTelemetry.prepareExecutor participates in this path.
        CompletableFuture.runAsync(() -> {
            assertSame(context, ReloadExecutionContext.current());
            assertSame(context, ReloadExecutionContext.preparationContext());
            for (int i = 0; i < 5; i++) {
                assertEquals(uncheckedVanilla(root), uncheckedCurrentList(root, counting(scans)));
            }
            ReloadExecutionContext.bindRunnable(context, () -> assertNull(ReloadExecutionContext.preparationContext())).run();
            assertSame(context, ReloadExecutionContext.preparationContext());
        }, ReloadExecutionContext.bindPreparationExecutor(context, CompletableFuture.delayedExecutor(0, TimeUnit.MILLISECONDS)))
            .get(5, TimeUnit.SECONDS);
        assertEquals(3, scans.get());
        assertEquals(1, context.directoryIndex().statistics().builds());
        assertTrue(context.metrics().listenerSnapshots().isEmpty(), "phase binding must not duplicate listener telemetry");
        assertNull(ReloadExecutionContext.preparationContext());
    }

    private Path fixture() throws IOException {
        Path root = Files.createDirectories(temporary.resolve("pack/assets/test"));
        for (String name : List.of("models/z.json", "models/a.json", "models/nested/c.json", "models2/b.json",
                "models/has space.json", "models/Upper.json", "textures/image.png")) {
            Path path = root.resolve(name);
            Files.createDirectories(path.getParent());
            Files.writeString(path, name);
        }
        Files.createDirectory(root.resolve("empty"));
        return root;
    }

    private static void warm(ReloadDirectoryIndex index, Path root) throws IOException {
        for (int i = 0; i < 3; i++) listed(index, root, root, Files::find);
    }

    private static List<Path> listed(ReloadDirectoryIndex index, Path root, Path start, ReloadDirectoryIndex.Finder finder) throws IOException {
        try (var paths = index.find(root, start, Integer.MAX_VALUE, FILES, NO_OPTIONS, finder)) { return paths.toList(); }
    }

    private static List<Path> vanilla(Path start) throws IOException {
        try (var paths = Files.find(start, Integer.MAX_VALUE, FILES)) { return paths.toList(); }
    }

    private static List<Path> currentList(Path root, ReloadDirectoryIndex.Finder finder) throws IOException {
        try (var paths = ReloadDirectoryIndex.list(root, root, Integer.MAX_VALUE, FILES, NO_OPTIONS, finder)) { return paths.toList(); }
    }

    private static List<Path> uncheckedList(ReloadDirectoryIndex index, Path root, ReloadDirectoryIndex.Finder finder) {
        try { return listed(index, root, root, finder); } catch (IOException error) { throw new UncheckedIOException(error); }
    }

    private static List<Path> uncheckedCurrentList(Path root, ReloadDirectoryIndex.Finder finder) {
        try { return currentList(root, finder); } catch (IOException error) { throw new UncheckedIOException(error); }
    }

    private static List<Path> uncheckedVanilla(Path root) {
        try { return vanilla(root); } catch (IOException error) { throw new UncheckedIOException(error); }
    }

    private static ReloadDirectoryIndex.Finder counting(AtomicInteger scans) {
        return (start, depth, predicate, options) -> {
            scans.incrementAndGet();
            return Files.find(start, depth, predicate, options);
        };
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }

    private static ReloadFeatureSnapshot features(boolean indexEnabled) {
        return new ReloadFeatureSnapshot(
            true, true, indexEnabled, false, false, false, false, false, false, false,
            false, false, false, 64, false, false, false, false, false, false,
            false, false, 128, false, 128, false, 256, Set.of(), false, 2,
            false, false, false, false, false, false, false, 1, 4, true,
            false, false, false, false, 1, false, 128, OptimizationPlan.capture(PackForgeConfig.get()));
    }
}
