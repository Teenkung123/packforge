package com.teenkung.packforge.loader;

import com.teenkung.packforge.PackForge;
import com.teenkung.packforge.concurrent.PreparationBudget;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiPredicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** One preparation generation's directory walks, in the filesystem's encounter order. */
public final class ReloadDirectoryIndex implements AutoCloseable {
    private static final int BUILD_AFTER_LISTINGS = 3;
    private final PreparationBudget.Scope budget;
    private final long reloadId;
    private final Map<Path, Namespace> namespaces = new HashMap<>();
    private final LongAdder listings = new LongAdder();
    private final LongAdder builds = new LongAdder();
    private final LongAdder hits = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private boolean retired;

    public ReloadDirectoryIndex(PreparationBudget.Scope budget) {
        this(budget, -1L);
    }

    ReloadDirectoryIndex(PreparationBudget.Scope budget, long reloadId) {
        this.budget = budget;
        this.reloadId = reloadId;
        budget.onRetire(this::close);
    }

    @FunctionalInterface
    public interface Finder {
        Stream<Path> find(Path start, int depth, BiPredicate<Path, BasicFileAttributes> predicate,
                          FileVisitOption... options) throws IOException;
    }

    /** Called by the narrow Files.find adapter; validation and suppliers remain in vanilla. */
    public static Stream<Path> list(Path namespaceRoot, Path start, int depth,
                                   BiPredicate<Path, BasicFileAttributes> predicate,
                                   FileVisitOption[] options, Finder original) throws IOException {
        ReloadExecutionContext context = ReloadExecutionContext.preparationContext();
        ReloadDirectoryIndex index = context == null ? null : context.directoryIndex();
        return index == null ? original.find(start, depth, predicate, options)
            : index.find(namespaceRoot, start, depth, predicate, options, original);
    }

    Stream<Path> find(Path namespaceRoot, Path start, int depth,
                      BiPredicate<Path, BasicFileAttributes> predicate,
                      FileVisitOption[] options, Finder original) throws IOException {
        // Only the verified vanilla, unbounded, non-following walk is indexed.
        if (depth != Integer.MAX_VALUE || options.length != 0 || !start.startsWith(namespaceRoot)) {
            return original.find(start, depth, predicate, options);
        }
        listings.increment();
        Namespace namespace = namespace(namespaceRoot);
        if (namespace != null) {
            Stream<Path> cached = namespace.list(namespaceRoot, start, predicate, original);
            if (cached != null) {
                hits.increment();
                return cached;
            }
        }
        return original.find(start, depth, predicate, options);
    }

    private synchronized Namespace namespace(Path root) {
        if (retired || budget.isRetired()) return null;
        Namespace found = namespaces.get(root);
        if (found != null) return found;
        PreparationBudget.Reservation memory = budget.tryReserve(512L + 4L * root.toString().length());
        if (memory == null) return null;
        Namespace created = new Namespace(memory);
        namespaces.put(root, created);
        return created;
    }

    public Statistics statistics() {
        return new Statistics(listings.sum(), builds.sum(), hits.sum(), failures.sum());
    }

    public record Statistics(long listings, long builds, long hits, long failures) {}

    @Override
    public void close() {
        List<Namespace> owners;
        synchronized (this) {
            if (retired) return;
            retired = true;
            owners = new ArrayList<>(namespaces.values());
            namespaces.clear();
        }
        owners.forEach(Namespace::close);
    }

    private final class Namespace {
        private final PreparationBudget.Reservation memory;
        private int calls;
        private boolean building;
        private boolean closed;
        private boolean failed;
        private Snapshot snapshot;

        private Namespace(PreparationBudget.Reservation memory) { this.memory = memory; }

        private Stream<Path> list(Path root, Path start, BiPredicate<Path, BasicFileAttributes> predicate,
                                  Finder original) throws IOException {
            synchronized (this) {
                if (closed || failed || budget.isRetired()) return null;
                if (snapshot != null) return snapshot.open(start, predicate);
                // Concurrent callers keep walking normally; they never wait on filesystem I/O.
                if (building || ++calls < BUILD_AFTER_LISTINGS) return null;
                building = true;
            }
            Snapshot built = null;
            try (Builder builder = new Builder()) {
                try (Stream<Path> walk = original.find(root, Integer.MAX_VALUE, builder::visit)) {
                    walk.forEach(ignored -> {});
                }
                built = builder.finish();
                builds.increment();
            } catch (BudgetExhausted ignored) {
                failures.increment();
            } catch (IOException | UncheckedIOException | SecurityException failure) {
                failures.increment();
                PackForge.LOGGER.debug("Directory index unavailable for {}; using vanilla listing", root, failure);
            } finally {
                synchronized (this) {
                    building = false;
                    failed = built == null;
                    if (closed || budget.isRetired()) {
                        if (built != null) built.close();
                        memory.close();
                    } else {
                        snapshot = built;
                        if (built != null && ReloadTrace.isEnabled()) {
                            PackForge.LOGGER.info("PackForge reload trace: id={} directoryNamespace={} indexedPaths={}",
                                reloadId, root, built.entries.size());
                        }
                    }
                }
            }
            synchronized (this) {
                return closed || snapshot == null || budget.isRetired() ? null : snapshot.open(start, predicate);
            }
        }

        private synchronized void close() {
            if (closed) return;
            closed = true;
            if (snapshot != null) {
                snapshot.close();
                snapshot = null;
            }
            if (!building) memory.close();
        }
    }

    private final class Builder implements AutoCloseable {
        private final List<Entry> entries = new ArrayList<>();
        private final Map<Path, Entry> lookup = new HashMap<>();
        private final ArrayDeque<Entry> directories = new ArrayDeque<>();
        private final List<PreparationBudget.Reservation> reservations = new ArrayList<>();
        private boolean transferred;

        private boolean visit(Path path, BasicFileAttributes attributes) {
            // Includes Path/attributes, map nodes, list growth, traversal stack and reservation overhead.
            PreparationBudget.Reservation memory = budget.tryReserve(384L + 4L * path.toString().length());
            if (memory == null) throw new BudgetExhausted();
            reservations.add(memory);
            while (!directories.isEmpty() && !path.startsWith(directories.peek().path)) {
                directories.pop().end = entries.size();
            }
            Entry entry = new Entry(path, attributes, entries.size());
            entries.add(entry);
            lookup.put(path, entry);
            if (attributes.isDirectory()) directories.push(entry);
            return false;
        }

        private Snapshot finish() {
            while (!directories.isEmpty()) directories.pop().end = entries.size();
            Snapshot result = new Snapshot(entries, lookup, reservations);
            transferred = true;
            return result;
        }

        @Override public void close() {
            if (!transferred) reservations.forEach(PreparationBudget.Reservation::close);
        }
    }

    private static final class Entry {
        private final Path path;
        private final BasicFileAttributes attributes;
        private final int ordinal;
        private int end;

        private Entry(Path path, BasicFileAttributes attributes, int ordinal) {
            this.path = path;
            this.attributes = attributes;
            this.ordinal = ordinal;
            this.end = ordinal + 1;
        }
    }

    private static final class Snapshot implements AutoCloseable {
        private final List<Entry> entries;
        private final Map<Path, Entry> lookup;
        private final List<PreparationBudget.Reservation> reservations;
        private int readers;
        private boolean retired;

        private Snapshot(List<Entry> entries, Map<Path, Entry> lookup,
                         List<PreparationBudget.Reservation> reservations) {
            this.entries = entries;
            this.lookup = lookup;
            this.reservations = reservations;
        }

        private synchronized Stream<Path> open(Path start, BiPredicate<Path, BasicFileAttributes> predicate) {
            if (retired) return null;
            Entry first = lookup.get(start);
            // Unknown prefixes (including paths below a directory symlink) must retain vanilla I/O/errors.
            if (first == null) return null;
            readers++;
            return IntStream.range(first.ordinal, first.end).mapToObj(entries::get)
                .filter(entry -> predicate.test(entry.path, entry.attributes)).map(entry -> entry.path)
                .onClose(this::release);
        }

        private synchronized void release() {
            readers--;
            discardIfUnused();
        }

        @Override public synchronized void close() {
            retired = true;
            discardIfUnused();
        }

        private void discardIfUnused() {
            if (!retired || readers != 0) return;
            entries.clear();
            lookup.clear();
            reservations.forEach(PreparationBudget.Reservation::close);
            reservations.clear();
        }
    }

    private static final class BudgetExhausted extends RuntimeException {
        private BudgetExhausted() { super(null, null, false, false); }
    }
}
