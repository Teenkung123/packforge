package dev.packbench.correctness;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Diagnostic-only CPU snapshots. Never retains images, opens resources, or invokes GPU APIs. */
public final class TextureCapture {
    public static final boolean ENABLED = Boolean.getBoolean("packforge.correctness.resourceHashingEnabled");
    private static final String SESSION = System.getProperty("packforge.benchmark.sessionId", "");
    private static final ArrayBlockingQueue<String> RECORDS = ENABLED ? new ArrayBlockingQueue<>(8192) : null;
    private static final Semaphore HASHERS = new Semaphore(64);
    private static final AtomicReference<String> FAILURE = new AtomicReference<>();
    private static final AtomicLong GENERATIONS = new AtomicLong();
    private static final AtomicLong ATLASES = new AtomicLong();
    private static final AtomicLong SPRITES = new AtomicLong();
    private static final AtomicLong CAPTURES = new AtomicLong();
    private static final AtomicLong WRITTEN = new AtomicLong();
    private static final Set<Generation> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    private static volatile boolean closing;
    private static Thread writer;

    public static synchronized Generation beginGeneration() {
        if (!ENABLED) return null;
        Generation generation = new Generation(GENERATIONS.incrementAndGet());
        if (!ACTIVE.isEmpty()) invalidate("overlapping resource generations");
        ACTIVE.add(generation);
        emit("generation_started", new Context(generation, null), "");
        return generation;
    }

    public static synchronized void completeGeneration(Generation generation, Throwable failure) {
        if (generation == null) return;
        if (!ACTIVE.remove(generation)) invalidate("resource generation completed twice");
        if (failure != null) invalidate("resource generation failed: " + failure.getClass().getName());
        generation.complete.set(true);
        emit("generation_complete", new Context(generation, null), ",\"successful\":" + (failure == null));
    }

    public static Atlas beginAtlas(String id) {
        if (!ENABLED) return null;
        Context parent = CURRENT.get();
        if (parent == null || parent.generation.complete.get()) {
            invalidate("atlas load has no active resource generation");
            return null;
        }
        Atlas atlas = new Atlas(ATLASES.incrementAndGet(), id, parent.generation);
        emit("atlas_started", atlas.context(), "");
        return atlas;
    }

    public static void completeAtlas(Atlas atlas, Throwable failure) {
        if (atlas == null) return;
        if (!atlas.complete.compareAndSet(false, true)) invalidate("atlas completed twice");
        if (failure != null) invalidate("atlas preparation failed: " + failure.getClass().getName());
        emit("atlas_complete", atlas.context(), ",\"successful\":" + (failure == null));
    }

    public static Sprite created(String id, int frameWidth, int frameHeight, boolean animated, NativeImage image) {
        if (!ENABLED) return null;
        Context context = CURRENT.get();
        // Out-of-scope temporary sprites are ignored. An unowned sprite reaching an atlas upload fails validation.
        if (context == null || context.atlas == null) return null;
        Sprite sprite = new Sprite(SPRITES.incrementAndGet(), id, frameWidth, frameHeight, animated, context);
        capture(sprite, "decoded", id, 0, new NativeImage[]{image});
        return sprite;
    }

    public static void capture(Sprite sprite, String stage, String resourceId, int requestedLevel, NativeImage[] images) {
        if (!ENABLED) return;
        if (sprite == null) { invalidate("mip/upload work has no captured decoded sprite ownership"); return; }
        synchronized (TextureCapture.class) {
            start();
            if (closing) { invalidate("capture after closure"); return; }
            if (FAILURE.get() != null) return;
            if (!HASHERS.tryAcquire()) { invalidate("concurrent hashing limit exceeded"); return; }
        }
        long capture = CAPTURES.getAndIncrement();
        try {
            if (sprite.context.generation.complete.get()) throw new IllegalStateException("capture after generation completion");
            if (images == null || images.length == 0 || requestedLevel < 0 || images.length <= requestedLevel) {
                throw new IllegalStateException("missing required mip level");
            }
            for (int level = 0; level < images.length; level++) {
                NativeImage image = images[level];
                if (image == null || image.format() != NativeImage.Format.RGBA) {
                    throw new IllegalStateException("unsupported or missing native image");
                }
                long started = System.nanoTime();
                String hash = PixelDigest.hash(image.getWidth(), image.getHeight(), image::getPixel);
                emit("pixels", sprite.context, ",\"captureIndex\":" + capture + ",\"spriteIndex\":" + sprite.id
                    + ",\"stage\":" + quote(stage) + ",\"resourceId\":" + quote(resourceId)
                    + ",\"spriteId\":" + quote(sprite.resourceId) + ",\"frameWidth\":" + sprite.frameWidth
                    + ",\"frameHeight\":" + sprite.frameHeight + ",\"animated\":" + sprite.animated
                    + ",\"requestedLevel\":" + requestedLevel + ",\"level\":" + level
                    + ",\"width\":" + image.getWidth() + ",\"height\":" + image.getHeight()
                    + ",\"sha256\":" + quote(hash) + ",\"hashNanos\":" + (System.nanoTime() - started));
            }
            if (stage.equals("mip_ready")) sprite.mipsCaptured.set(true);
        } catch (RuntimeException | Error failure) {
            invalidate("pixel capture failed: " + failure.getClass().getName() + ": " + failure.getMessage());
            if (failure instanceof Error error) throw error;
        } finally {
            HASHERS.release();
        }
    }

    public static Context uploadContext(String atlasId, Sprite sprite) {
        if (!ENABLED) return null;
        if (sprite == null || !sprite.context.atlas.resourceId.equals(atlasId)) {
            invalidate("uploaded sprite lacks matching decoded atlas ownership");
            return null;
        }
        if (!sprite.mipsCaptured.get()) invalidate("uploaded sprite lacks completed mip capture");
        return sprite.context;
    }

    public static void uploaded(Context context, int sprites) {
        if (ENABLED && context != null) emit("atlas_uploaded", context, ",\"sprites\":" + sprites);
    }

    public static <T> T within(Context context, Supplier<T> action) {
        if (!ENABLED || context == null) return action.get();
        Context previous = CURRENT.get();
        CURRENT.set(context);
        try { return action.get(); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }

    public static Executor bind(Context context, Executor executor) {
        if (!ENABLED || context == null) return executor;
        return command -> executor.execute(() -> within(context, () -> { command.run(); return null; }));
    }

    public static void invalidate(String reason) { if (ENABLED) FAILURE.compareAndSet(null, reason); }

    private static synchronized void emit(String event, Context context, String fields) {
        start();
        if (closing && HASHERS.availablePermits() == 64) { invalidate("event after closure"); return; }
        String atlas = context.atlas == null ? ",\"atlasIndex\":0,\"atlasId\":null"
            : ",\"atlasIndex\":" + context.atlas.id + ",\"atlasId\":" + quote(context.atlas.resourceId);
        String record = "{\"schema\":2,\"event\":" + quote(event) + ",\"sessionId\":" + quote(SESSION)
            + ",\"resourceGeneration\":" + context.generation.id + atlas
            + ",\"nanoTime\":" + System.nanoTime() + fields + "}";
        if (!RECORDS.offer(record)) invalidate("bounded output queue overflow");
    }

    private static synchronized void start() {
        if (writer != null) return;
        String destination = System.getProperty("packforge.correctness.output", "");
        if (SESSION.isBlank() || SESSION.length() > 256 || destination.isBlank()) throw new IllegalArgumentException("Correctness capture requires bounded sessionId and correctness.output");
        Path output = Path.of(destination).toAbsolutePath();
        writer = new Thread(() -> write(output), "texture-correctness-output");
        writer.setDaemon(true);
        writer.start();
    }

    private static void write(Path destination) {
        try {
            Files.createDirectories(destination.getParent());
            try (BufferedWriter output = Files.newBufferedWriter(destination, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                while (!closing || !RECORDS.isEmpty() || HASHERS.availablePermits() != 64) {
                    String record = RECORDS.poll(100, TimeUnit.MILLISECONDS);
                    if (record != null) { output.write(record); output.newLine(); WRITTEN.incrementAndGet(); }
                }
                String failure = FAILURE.get();
                output.write("{\"schema\":2,\"event\":\"terminal\",\"sessionId\":" + quote(SESSION)
                    + ",\"valid\":" + (failure == null) + ",\"captures\":" + CAPTURES.get()
                    + ",\"generations\":" + GENERATIONS.get() + ",\"atlases\":" + ATLASES.get()
                    + ",\"records\":" + WRITTEN.get() + ",\"reason\":" + (failure == null ? "null" : quote(failure)) + "}");
                output.newLine();
            }
        } catch (IOException | InterruptedException | RuntimeException failure) {
            invalidate("capture output failed: " + failure.getClass().getName());
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }

    public static void close() {
        if (!ENABLED) return;
        start();
        synchronized (TextureCapture.class) {
            if (!ACTIVE.isEmpty()) invalidate("shutdown with unfinished resource generation");
            if (CAPTURES.get() == 0) invalidate("zero texture capture coverage");
            closing = true;
        }
        try {
            writer.join(30_000);
            if (writer.isAlive()) invalidate("capture output did not close within 30 seconds");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            invalidate("interrupted while closing capture output");
        }
        if (FAILURE.get() != null) throw new IllegalStateException("Invalid texture correctness run: " + FAILURE.get());
    }

    static String quote(String value) {
        if (value.length() > 2048) throw new IllegalArgumentException("Diagnostic record string exceeds bound");
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '"' || character == '\\') result.append('\\').append(character);
            else if (character < 32) result.append(String.format("\\u%04x", (int) character));
            else result.append(character);
        }
        return result.append('"').toString();
    }

    public record Context(Generation generation, Atlas atlas) {}
    public static final class Generation {
        final long id;
        final AtomicBoolean complete = new AtomicBoolean();
        Generation(long id) { this.id = id; }
        public Context context() { return new Context(this, null); }
    }
    public static final class Atlas {
        final long id;
        final String resourceId;
        final Generation generation;
        final AtomicBoolean complete = new AtomicBoolean();
        Atlas(long id, String resourceId, Generation generation) { this.id = id; this.resourceId = resourceId; this.generation = generation; }
        public Context context() { return new Context(generation, this); }
    }
    public static final class Sprite {
        final long id;
        final String resourceId;
        final int frameWidth, frameHeight;
        final boolean animated;
        final Context context;
        final AtomicBoolean mipsCaptured = new AtomicBoolean();
        Sprite(long id, String resourceId, int frameWidth, int frameHeight, boolean animated, Context context) {
            this.id = id; this.resourceId = resourceId; this.frameWidth = frameWidth; this.frameHeight = frameHeight;
            this.animated = animated; this.context = context;
        }
    }
    private TextureCapture() {}
}
