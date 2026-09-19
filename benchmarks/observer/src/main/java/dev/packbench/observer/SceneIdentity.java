package dev.packbench.observer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Fixed test-scene identity, independent of Minecraft classes and player saves. */
final class SceneIdentity {
    static final String WORLD_ID = "packbench-scene-v1";
    static final long SEED = 0x5041434B42454E43L;
    static final UUID ARMOR_STAND = UUID.fromString("00000000-0000-0000-0000-000000005001");
    static final UUID PIG = UUID.fromString("00000000-0000-0000-0000-000000005002");
    static final UUID ITEM = UUID.fromString("00000000-0000-0000-0000-000000005003");
    static final List<UUID> ENTITY_UUIDS = List.of(ARMOR_STAND, PIG, ITEM);
    static final String SOURCE_SHA256 = System.getProperty("packforge.benchmark.sceneSourceSha256", "");
    static final String IMPLEMENTATION_SHA256 = implementationHash();

    private SceneIdentity() {}

    static void validateExpected() {
        if (!WORLD_ID.equals(System.getProperty("packforge.benchmark.expectedSceneId"))
            || !Long.toString(SEED).equals(System.getProperty("packforge.benchmark.expectedSceneSeed"))
            || !IMPLEMENTATION_SHA256.equals(System.getProperty("packforge.benchmark.expectedSceneImplementationSha256"))
            || !SOURCE_SHA256.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("in-world benchmark requires the pinned observer scene identity and source manifest");
        }
    }

    static String nbtUuid(UUID uuid) {
        long high = uuid.getMostSignificantBits();
        long low = uuid.getLeastSignificantBits();
        return "[I;" + (int) (high >>> 32) + "," + (int) high + "," + (int) (low >>> 32) + "," + (int) low + "]";
    }

    private static String implementationHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String name : List.of("SceneIdentity.class", "BenchmarkScene.class", "BenchmarkScene$SceneCommandOutput.class")) {
                String resource = "/dev/packbench/observer/" + name;
                digest.update(resource.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                try (InputStream stream = SceneIdentity.class.getResourceAsStream(resource)) {
                    if (stream == null) throw new IllegalStateException("missing scene implementation: " + name);
                    digest.update(stream.readAllBytes());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("could not identify benchmark scene implementation", failure);
        }
    }
}
