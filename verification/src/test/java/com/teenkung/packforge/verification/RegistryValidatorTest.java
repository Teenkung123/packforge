package com.teenkung.packforge.verification;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RegistryValidatorTest {
    @TempDir Path root;

    private static final String FILE_PACK = "@WrapOperation\n"
            + "getResource(Ljava/lang/String;)Lnet/minecraft/server/packs/resources/IoSupplier;\n".repeat(2)
            + "ZipFile;getEntry(Ljava/lang/String;)Ljava/util/zip/ZipEntry;\n"
            + "ZipFile;entries()Ljava/util/Enumeration;\n".repeat(2)
            + "IoSupplier;create(Ljava/util/zip/ZipFile;Ljava/util/zip/ZipEntry;)\n".repeat(2);
    private static final String RELOAD = "private ReloadInstance packforge$createReload(Executor preparationExecutor, Executor reloadExecutor, CompletableFuture<Unit> initialStage, List<PackResources> packs, Operation<ReloadInstance> original) { original.call(preparationExecutor, reloadExecutor, initialStage, packs); }";
    private static final String MODERN = "packforge$associateAtlasState(ResourceManager resourceManager, Identifier atlasId, int mipLevel, Executor executor, Set<MetadataSectionType<?>> additional, Operation<CompletableFuture<SpriteLoader.Preparations>> original) { original.call(resourceManager, atlasId, mipLevel, executor, additional); } private static CompletableFuture<List<SpriteContents>> packforge$decodeBounded(";
    private static final String LEGACY = "method = \"loadAndStitch(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/ResourceLocation;ILjava/util/concurrent/Executor;Ljava/util/Collection;)Ljava/util/concurrent/CompletableFuture;\"";
    private static final String MODERN_PATH = "versions/mc26/common/src/client/java/com/teenkung/packforge/client/mixin/atlas/SpriteLoaderMixin.java";
    private static final String LEGACY_PATH = "versions/mc1_21_shared/common/src/client/java/com/teenkung/packforge/client/mixin/atlas/SpriteLoaderMixin.java";

    private Path fixture() throws Exception {
        JsonObject registry;
        try (var input = getClass().getResourceAsStream("/registry.json")) {
            assertNotNull(input);
            registry = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        for (var element : registry.getAsJsonArray("targets")) {
            String adapter = element.getAsJsonObject().get("apiAdapter").getAsString();
            write("versions/" + adapter + "/FilePackResourcesMixin.java", FILE_PACK);
            write("versions/" + adapter + "/ReloadableResourceManagerMixin.java", RELOAD);
        }
        write(MODERN_PATH, MODERN);
        write(LEGACY_PATH, LEGACY);
        write("versions/mc26/packforge.fabric.mixins.json", "{}");
        write("registry.json", registry.toString());
        return root.resolve("registry.json");
    }

    @Test void sourceCliAndAllSourceGuards() throws Exception {
        Path registry = fixture();
        RegistryValidator.main(new String[]{registry.toString(), root.toString()});
        List<String[]> changes = List.of(
                new String[]{"versions/mc26/FilePackResourcesMixin.java", FILE_PACK + " @WrapMethod"},
                new String[]{"versions/mc26/FilePackResourcesMixin.java", FILE_PACK + " @Local String x, Operation<Void> operation"},
                new String[]{"versions/mc26/FilePackResourcesMixin.java", FILE_PACK + " CallbackInfoReturnable"},
                new String[]{"versions/mc26/FilePackResourcesMixin.java", FILE_PACK + " cancellable = true"},
                new String[]{"versions/mc26/FilePackResourcesMixin.java", FILE_PACK.replace("@WrapOperation", "")},
                new String[]{"versions/mc26/FilePackResourcesMixin.java", FILE_PACK.replace("ZipFile;entries()", "ZipFile;other()")},
                new String[]{"versions/mc26/ReloadableResourceManagerMixin.java", RELOAD.replace("original.call", "wrong.call")},
                new String[]{"versions/mc26/ReloadableResourceManagerMixin.java", RELOAD.replace("Executor preparationExecutor", "Object preparationExecutor")},
                new String[]{MODERN_PATH, MODERN.replace("private static", "private")},
                new String[]{MODERN_PATH, MODERN.replace("original.call", "wrong.call")},
                new String[]{LEGACY_PATH, LEGACY.replace("Collection", "List")},
                new String[]{"versions/mc26/packforge.fabric.mixins.json", "CompositePackResourcesMixin"},
                new String[]{"versions/mc26/OtherMixin.java", "field_123"},
                new String[]{"versions/mc26/duplicate/FilePackResourcesMixin.java", FILE_PACK});
        for (String[] change : changes) {
            Path path = root.resolve(change[0]);
            String before = Files.exists(path) ? Files.readString(path) : null;
            write(change[0], change[1]);
            assertThrows(IllegalStateException.class, () -> RegistryValidator.validate(registry, root), change[0]);
            if (before == null) Files.delete(path); else Files.writeString(path, before);
        }
        Files.delete(root.resolve("versions/mc26/ReloadableResourceManagerMixin.java"));
        assertThrows(IllegalStateException.class, () -> RegistryValidator.validate(registry, root));
    }

    private void write(String relative, String text) throws Exception {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
    }
}
