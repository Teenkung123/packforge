package com.teenkung.packforge.verification;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.teenkung.packforge.verification.ArtifactVerifier.string;

/** Registry and physical adapter source checks; never configures or builds runtime projects. */
public final class RegistryValidator {
    private static final String MODERN_SPRITE = "versions/mc26/common/src/client/java/com/teenkung/packforge/client/mixin/atlas/SpriteLoaderMixin.java";
    private static final String LEGACY_SPRITE = "versions/mc1_21_shared/common/src/client/java/com/teenkung/packforge/client/mixin/atlas/SpriteLoaderMixin.java";
    private static final String RELOAD_WRAPPER = "(?s)private\\s+ReloadInstance\\s+packforge\\$createReload\\(\\s*Executor\\s+preparationExecutor,\\s*Executor\\s+reloadExecutor,\\s*CompletableFuture<Unit>\\s+initialStage,\\s*List<PackResources>\\s+packs,\\s*Operation<ReloadInstance>\\s+original\\s*\\)";
    private static final String ATLAS_WRAPPER = "(?s)packforge\\$associateAtlasState\\(\\s*ResourceManager\\s+resourceManager,\\s*Identifier\\s+atlasId,\\s*int\\s+mipLevel,\\s*Executor\\s+executor,\\s*Set<MetadataSectionType<\\?>>\\s+additional,\\s*Operation<CompletableFuture<SpriteLoader\\.Preparations>>\\s+original\\s*\\)";

    private RegistryValidator() {}

    public static void main(String[] args) throws IOException {
        require(args.length == 2, "Usage: RegistryValidator <registry.json> <repository-root>");
        validate(Path.of(args[0]), Path.of(args[1]));
        System.out.println("PackForge registry and source contracts passed.");
    }

    public static void validate(Path registryPath, Path repository) throws IOException {
        JsonObject registry = JsonParser.parseString(Files.readString(registryPath)).getAsJsonObject();
        ArtifactVerifier.validateRegistry(registry);
        Path versions = repository.resolve("versions");
        List<Path> sources;
        try (var paths = Files.walk(versions)) {
            sources = paths.filter(Files::isRegularFile).toList();
        }
        for (Path source : sources) {
            String name = source.getFileName().toString();
            if (name.endsWith("Mixin.java")) {
                require(!Pattern.compile("\\b(?:method|field)_[0-9]+\\b").matcher(Files.readString(source)).find(), source + " contains raw intermediary selector");
            }
            if (name.equals("packforge.fabric.mixins.json")) {
                require(!Files.readString(source).contains("CompositePackResourcesMixin"), source + " registers removed CompositePackResources mixin");
            }
        }
        for (var element : registry.getAsJsonArray("targets")) {
            JsonObject target = element.getAsJsonObject();
            Path adapter = versions.resolve(string(target, "apiAdapter"));
            Path filePack = ownedSource(sources, adapter, "FilePackResourcesMixin.java");
            validateFilePack(Files.readString(filePack), filePack.toString());
            Path reload = ownedSource(sources, adapter, "ReloadableResourceManagerMixin.java");
            String text = Files.readString(reload);
            require(Pattern.compile(RELOAD_WRAPPER).matcher(text).find()
                    && text.contains("original.call(preparationExecutor, reloadExecutor, initialStage, packs)"), reload + " must mirror and forward four createReload arguments before Operation");
        }
        String modern = Files.readString(repository.resolve(MODERN_SPRITE));
        require(Pattern.compile(ATLAS_WRAPPER).matcher(modern).find()
                && modern.contains("original.call(resourceManager, atlasId, mipLevel, executor, additional)"), MODERN_SPRITE + " must mirror and forward five loadAndStitch arguments before Operation");
        require(modern.contains("private static CompletableFuture<List<SpriteContents>> packforge$decodeBounded("), MODERN_SPRITE + " runSpriteSuppliers handler must be static");
        String legacy = Files.readString(repository.resolve(LEGACY_SPRITE));
        require(legacy.contains("method = \"loadAndStitch(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/ResourceLocation;ILjava/util/concurrent/Executor;Ljava/util/Collection;)Ljava/util/concurrent/CompletableFuture;\""), LEGACY_SPRITE + " must target the five-argument overload explicitly");
    }

    private static Path ownedSource(List<Path> sources, Path adapter, String filename) {
        List<Path> matches = sources.stream().filter(p -> p.startsWith(adapter) && p.getFileName().toString().equals(filename)).toList();
        require(matches.size() == 1, adapter + " must own exactly one " + filename + "; found " + matches.size());
        return matches.get(0);
    }

    static void validateFilePack(String text, String source) {
        require(text.contains("@WrapOperation") && !text.contains("@WrapMethod") && !text.contains("CallbackInfoReturnable")
                && !text.contains("cancellable = true"), source + " must use operation-level hooks without broad replacement");
        require(!Pattern.compile("(?s)@Local(?:\\([^)]*\\))?[^{}]*Operation<").matcher(text).find(), source + " sugar parameters must trail Operation");
        Map<String, Integer> anchors = Map.of(
                "getResource(Ljava/lang/String;)Lnet/minecraft/server/packs/resources/IoSupplier;", 2,
                "ZipFile;getEntry(Ljava/lang/String;)Ljava/util/zip/ZipEntry;", 1,
                "ZipFile;entries()Ljava/util/Enumeration;", 2,
                "IoSupplier;create(Ljava/util/zip/ZipFile;Ljava/util/zip/ZipEntry;)", 2);
        anchors.forEach((anchor, expected) -> {
            int count = 0;
            for (int index = text.indexOf(anchor); index >= 0; index = text.indexOf(anchor, index + anchor.length())) count++;
            require(count == expected, source + " expected " + expected + " operation anchors for " + anchor + "; found " + count);
        });
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
