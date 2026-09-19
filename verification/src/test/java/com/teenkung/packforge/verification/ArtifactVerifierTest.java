package com.teenkung.packforge.verification;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.teenkung.packforge.verification.ArtifactVerifier.*;
import static org.junit.jupiter.api.Assertions.*;

class ArtifactVerifierTest {
    @TempDir Path directory;
    private static final String MODERN_TARGET = "mc26_1_to_26_3";

    private JsonObject registry() throws IOException {
        try (var input = getClass().getResourceAsStream("/registry.json")) {
            assertNotNull(input);
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static JsonObject target(JsonObject registry, String key) {
        return registry.getAsJsonArray("targets").asList().stream().map(e -> e.getAsJsonObject())
                .filter(t -> string(t, "key").equals(key)).findFirst().orElseThrow();
    }

    private static void verifyArtifact(JsonObject registry, JsonObject target, String platform, Path jar) throws IOException {
        ArtifactVerifier.verifyArtifact(registry, target, platform, jar,
                ClassInventory.of(fixture(registry, target, platform).keySet()));
    }

    private static void verify(Path registryPath, Path directory, String version, String key) throws IOException {
        JsonObject registry = JsonParser.parseString(Files.readString(registryPath)).getAsJsonObject();
        ArtifactVerifier.verify(registryPath, directory, version, key, node -> {
            String[] parts = node.split("/");
            return ClassInventory.of(fixture(registry, target(registry, parts[0]), parts[1]).keySet());
        });
    }

    @Test void completeRegistryArtifactSetAndCli() throws Exception {
        JsonObject registry = registry();
        int expectedCount = registry.getAsJsonArray("targets").asList().stream()
                .map(JsonElement::getAsJsonObject)
                .mapToInt(target -> target.getAsJsonObject("platforms").size())
                .sum();
        int count = 0;
        for (var element : registry.getAsJsonArray("targets")) {
            JsonObject target = element.getAsJsonObject();
            for (String platform : target.getAsJsonObject("platforms").keySet()) {
                write(directory.resolve(artifactName(target, platform, "1.4")), fixture(registry, target, platform));
                count++;
            }
        }
        assertEquals(expectedCount, count);
        Path path = directory.resolve("registry.json");
        Files.writeString(path, registry.toString());
        verify(path, directory, "1.4", null);
        var cliFailure = assertThrows(IllegalStateException.class,
                () -> ArtifactVerifier.main(new String[]{path.toString(), directory.toString(), "1.4"}));
        assertTrue(cliFailure.getMessage().contains("compiler addition") || cliFailure.getMessage().contains("inventory mismatch"));
        Files.write(directory.resolve("packforge-stale.jar"), new byte[0]);
        assertThrows(IllegalStateException.class, () -> verify(path, directory, "1.4", null));
        assertDoesNotThrow(() -> verify(path, directory, "1.4", "mc1_20_1"));
        assertThrows(IllegalStateException.class, () -> verify(path, directory, "1.4", "unknown"));
        Files.delete(directory.resolve(artifactName(target(registry, "mc1_20_1"), "forge", "1.4")));
        assertThrows(IllegalStateException.class, () -> verify(path, directory, "1.4", "mc1_20_1"));
    }

    @TestFactory Stream<DynamicTest> rejectsBenchmarkEntriesEvenWithMatchingInventory() {
        return Stream.of("dev/packbench/observer/BenchmarkObserver.class",
                "dev/packbench/fixture.json", "META-INF/versions/25/dev/packbench/observer/Observer.class",
                "benchmark-observer.mixins.json", "META-INF/benchmark-observer.json", "capture.jfr",
                "org/openjdk/jmh/Runner.class", "one/profiler/AsyncProfiler.class",
                "org/asyncprofiler/AsyncProfiler.class", "jdk/jfr/Event.class", "org/openjdk/jmc/Recorder.class",
                "org/junit/Test.class").map(path -> DynamicTest.dynamicTest(path, () -> {
            JsonObject registry = registry();
            JsonObject target = target(registry, MODERN_TARGET);
            Map<String, byte[]> files = fixture(registry, target, "fabric");
            files.put(path, path.endsWith(".class") ? clazz(path, 61) : new byte[0]);
            Path jar = directory.resolve("contaminated.jar");
            write(jar, files);
            var failure = assertThrows(IllegalStateException.class, () -> ArtifactVerifier.verifyArtifact(
                    registry, target, "fabric", jar, ClassInventory.of(files.keySet())));
            assertTrue(failure.getMessage().startsWith("Forbidden"), failure.getMessage());
        }));
    }

    @Test void mc26RegistryDoesNotAdvertiseUnadmittedAtlasCap() throws Exception {
        JsonObject registry = registry();
        for (String key : List.of("mc26_1_to_26_2", MODERN_TARGET)) {
            JsonObject target = target(registry, key);
            List<String> capabilities = target.getAsJsonArray("capabilities").asList().stream()
                    .map(JsonElement::getAsString).toList();
            assertFalse(capabilities.contains("ATLAS_CAP"), key);
            assertTrue(capabilities.contains("ATLAS_RETRY"), key);
        }
    }

    @Test void readReuseRequiresItsImplementationAndAdapter() throws Exception {
        JsonObject registry = registry();
        JsonObject target = target(registry, MODERN_TARGET);
        target.getAsJsonArray("capabilities").add("RESOURCE_READ_REUSE");
        Map<String, byte[]> complete = fixture(registry, target, "fabric");
        Path jar = directory.resolve("reuse.jar");
        write(jar, complete);
        verifyArtifact(registry, target, "fabric", jar);
        for (String name : List.of("loader/ReloadReadCache", "loader/ZipResourceReadReuse",
                "concurrent/PreparationBudget", "mixin/loader/ZipIoSupplierReadReuseMixin")) {
            Map<String, byte[]> missing = new LinkedHashMap<>(complete);
            missing.remove(BASE + name + ".class");
            write(jar, missing);
            assertThrows(IllegalStateException.class, () -> ArtifactVerifier.verifyArtifact(
                    registry, target, "fabric", jar, ClassInventory.of(missing.keySet())), name);
        }
    }

    @TestFactory Stream<DynamicTest> rejectsArtifactMutations() {
        Map<String, Consumer<Map<String, byte[]>>> mutations = new LinkedHashMap<>();
        mutations.put("missing core", f -> f.remove(BASE + "PackForgeCore.class"));
        mutations.put("missing unchecked class", f -> f.remove(BASE + "platform/PackForgeServices.class"));
        mutations.put("hidden class", f -> f.put("hidden/Helper.class", clazz("hidden/Helper.class", 61)));
        mutations.put("same count substitution", f -> {
            f.remove(BASE + "platform/PackForgeServices.class");
            f.put("hidden/Helper.class", clazz("hidden/Helper.class", 61));
        });
        mutations.put("too-new common", f -> f.put(BASE + "PackForgeCore.class", clazz(BASE + "PackForgeCore.class", 70)));
        mutations.put("too-old node", f -> f.put(FILE_PACK, operations(61, Map.of(), false)));
        mutations.put("invalid header", f -> f.put(BASE + "PackForgeCore.class", new byte[8]));
        mutations.put("missing config screen", f -> f.remove(BASE + "client/config/PackForgeConfigScreen.class"));
        mutations.put("missing config image", f -> f.remove("assets/packforge/textures/gui/sprites/config_cog.png"));
        mutations.put("stale capability", f -> put(f, "packforge-capabilities.properties", "target=wrong"));
        mutations.put("wrong pack range", f -> put(f, "pack.mcmeta", "{\"pack\":{\"min_format\":[1,0],\"max_format\":[1,0]}}"));
        mutations.put("wrong compatibility", f -> edit(f, "packforge.fabric.mixins.json", j -> j.addProperty("compatibilityLevel", "JAVA_17")));
        mutations.put("missing registered class", f -> f.remove(BASE + "client/mixin/font/FontManagerMixin.class"));
        mutations.put("missing bridge", f -> f.remove(BASE + "internal/loader/SharedZipFileAccessBridge.class"));
        mutations.put("forbidden bridge", f -> f.put(BASE + "loader/SharedZipFileAccessBridge.class", clazz(BASE + "loader/SharedZipFileAccessBridge.class", 61)));
        mutations.put("forbidden mixin bridge", f -> f.put(BASE + "mixin/loader/SharedZipFileAccessBridge.class", clazz(BASE + "mixin/loader/SharedZipFileAccessBridge.class", 61)));
        mutations.put("removed composite", f -> f.put(BASE + "mixin/loader/CompositePackResourcesMixin.class", clazz(BASE + "mixin/loader/CompositePackResourcesMixin.class", 61)));
        mutations.put("nested atlas class", f -> f.put(BASE + "client/mixin/atlas/SpriteLoaderMixin$State.class", clazz(BASE + "client/mixin/atlas/SpriteLoaderMixin$State.class", 61)));
        mutations.put("missing atlas state", f -> f.remove(BASE + "client/atlas/AtlasLoadInvocation.class"));
        mutations.put("unexpected extras", f -> f.put("META-INF/jars/mixinextras-fabric-0.5.4.jar", new byte[0]));
        mutations.put("nested common", f -> f.put("META-INF/jars/packforge-common.jar", new byte[0]));
        mutations.put("build tool", f -> f.put("org/gradle/api/Plugin.class", clazz("org/gradle/api/Plugin.class", 61)));
        mutations.put("multi-release build tool", f -> f.put("META-INF/versions/17/org/objectweb/asm/ClassReader.class", clazz("org/objectweb/asm/ClassReader.class", 61)));
        mutations.put("wrong Fabric dependency", f -> edit(f, "fabric.mod.json", j -> j.getAsJsonObject("depends").addProperty("fabric-api", "*")));
        mutations.put("missing Mod Menu", f -> f.remove(BASE + "client/config/PackForgeModMenuApi.class"));
        mutations.put("wrong widener", f -> put(f, "packforge.accesswidener", "accessWidener v2 intermediary"));
        mutations.put("wrong operations", f -> f.put(FILE_PACK, operations(69, Map.of("wrong", 5), false)));
        return mutations.entrySet().stream().map(e -> DynamicTest.dynamicTest(e.getKey(), () -> {
            JsonObject registry = registry();
            JsonObject target = target(registry, MODERN_TARGET);
            Map<String, byte[]> fixture = fixture(registry, target, "fabric");
            e.getValue().accept(fixture);
            Path jar = directory.resolve("mutated.jar");
            write(jar, fixture);
            assertThrows(IllegalStateException.class, () -> verifyArtifact(registry, target, "fabric", jar));
        }));
    }

    @TestFactory Stream<DynamicTest> everyCapabilityRequirementIsEnforced() {
        return CapabilityContracts.ALL.entrySet().stream().flatMap(e -> {
            List<DynamicTest> tests = new ArrayList<>();
            var c = CapabilityContracts.forAdapter(e.getKey(), "mc26");
            for (String path : c.classes()) tests.add(capabilityFailure(e.getKey(), path, f -> f.remove(path)));
            if (!c.anyClasses().isEmpty()) tests.add(capabilityFailure(e.getKey(), "alternatives", f -> c.anyClasses().forEach(f::remove)));
            for (String name : c.mainMixins()) tests.add(capabilityFailure(e.getKey(), name, f -> removeMixin(f, "packforge.fabric.mixins.json", "mixins", Set.of(name))));
            for (String name : c.clientMixins()) tests.add(capabilityFailure(e.getKey(), name, f -> removeMixin(f, "packforge.fabric.client.mixins.json", "client", Set.of(name))));
            if (!c.anyClientMixins().isEmpty()) tests.add(capabilityFailure(e.getKey(), "mixin alternatives", f -> removeMixin(f, "packforge.fabric.client.mixins.json", "client", Set.copyOf(c.anyClientMixins()))));
            return tests.stream();
        });
    }

    @TestFactory Stream<DynamicTest> modelContractsUseTheActiveAdapterImplementation() {
        return Stream.of(MODERN_TARGET, "mc1_21_1").map(key -> DynamicTest.dynamicTest(key, () -> {
            JsonObject registry = registry();
            JsonObject target = target(registry, key);
            JsonArray capabilities = new JsonArray();
            capabilities.add("MODEL_PARSE_BATCHING");
            capabilities.add("MODEL_PARSE_TIMINGS");
            target.add("capabilities", capabilities);
            Map<String, byte[]> files = fixture(registry, target, "fabric");
            boolean modern = key.equals(MODERN_TARGET);
            assertEquals(!modern, files.containsKey(BASE + "client/model/ModelParseOptimizer.class"));
            Path jar = directory.resolve("model-contract.jar");
            write(jar, files);
            verifyArtifact(registry, target, "fabric", jar);
            List<String> required = modern
                    ? List.of("concurrent/ModelSchedulingPlan", "concurrent/CoalescingExecutor", "client/model/ModelSourceDiagnostics")
                    : List.of("client/model/ModelBatchPlan", "client/model/ModelParseOptimizer", "client/model/ModelParseTimings");
            for (String name : required) {
                Map<String, byte[]> missing = new LinkedHashMap<>(files);
                missing.remove(BASE + name + ".class");
                write(jar, missing);
                assertThrows(IllegalStateException.class, () -> ArtifactVerifier.verifyArtifact(
                        registry, target, "fabric", jar, ClassInventory.of(missing.keySet())), name);
            }
        }));
    }

    @Test
    void mc120RawFontPreselectionContractRejectsMissingImplementation() throws Exception {
        JsonObject registry = registry();
        JsonObject target = target(registry, "mc1_20_1");
        JsonArray capabilities = new JsonArray();
        capabilities.add("FONT_PROVIDER_PRESELECTION");
        target.add("capabilities", capabilities);
        Map<String, byte[]> fixture = fixture(registry, target, "fabric");
        Path jar = directory.resolve("mc120-raw-font-contract.jar");
        write(jar, fixture);
        assertDoesNotThrow(() -> verifyArtifact(registry, target, "fabric", jar));

        fixture.remove(BASE + "client/font/RawFontSelectionRegistry.class");
        write(jar, fixture);
        assertThrows(IllegalStateException.class, () -> verifyArtifact(registry, target, "fabric", jar));
    }

    private DynamicTest capabilityFailure(String capability, String label, Consumer<Map<String, byte[]>> mutate) {
        return DynamicTest.dynamicTest(capability + ": " + label, () -> {
            JsonObject registry = registry();
            JsonObject target = target(registry, MODERN_TARGET);
            JsonArray capabilities = new JsonArray();
            capabilities.add(capability);
            target.add("capabilities", capabilities);
            Map<String, byte[]> fixture = fixture(registry, target, "fabric");
            Path jar = directory.resolve("capability.jar");
            write(jar, fixture);
            assertDoesNotThrow(() -> verifyArtifact(registry, target, "fabric", jar));
            mutate.accept(fixture);
            write(jar, fixture);
            assertThrows(IllegalStateException.class, () -> verifyArtifact(registry, target, "fabric", jar));
        });
    }

    @Test void forgeRefmapsRangesAndIntermediarySelectors() throws Exception {
        JsonObject registry = registry();
        JsonObject target = target(registry, "mc1_20_1");
        List<Consumer<Map<String, byte[]>>> mutations = List.of(
                f -> f.remove("main.refmap.json"),
                f -> put(f, "main.refmap.json", "{\"mappings\":{}}"),
                f -> put(f, "client.refmap.json", "{\"mappings\":{}}"),
                f -> put(f, "main.refmap.json", "{\"method_123\":1}"),
                f -> put(f, "META-INF/mods.toml", "versionRange=\"[wrong]\""),
                f -> f.put(FILE_PACK, operations(61, operationTargets(target, "fabric"), false)),
                f -> put(f, "pack.mcmeta", "{\"pack\":{\"pack_format\":99}}"),
                f -> edit(f, "main.refmap.json", j -> j.getAsJsonObject("mappings").getAsJsonObject(BASE + "mixin/observe/SimpleReloadInstanceMixin").remove("<init>")),
                f -> f.put(BASE + "client/mixin/config/PackSelectionScreenMixin.class", withConstant("method_123")));
        for (var mutation : mutations) {
            Map<String, byte[]> fixture = fixture(registry, target, "forge");
            mutation.accept(fixture);
            Path jar = directory.resolve("forge.jar");
            write(jar, fixture);
            assertThrows(IllegalStateException.class, () -> verifyArtifact(registry, target, "forge", jar));
        }
    }

    @Test void duplicateZipEntriesAreRejected() throws Exception {
        JsonObject registry = registry();
        JsonObject target = target(registry, "mc1_20_1");
        Map<String, byte[]> fixture = fixture(registry, target, "fabric");
        put(fixture, "duplicate-a", "a");
        put(fixture, "duplicate-b", "b");
        Path jar = directory.resolve("duplicates.jar");
        write(jar, fixture);
        byte[] bytes = Files.readAllBytes(jar);
        byte[] search = "duplicate-b".getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i <= bytes.length - search.length; i++) {
            boolean matches = true;
            for (int j = 0; j < search.length; j++) matches &= bytes[i + j] == search[j];
            if (matches) bytes[i + search.length - 1] = 'a';
        }
        Files.write(jar, bytes);
        var error = assertThrows(IllegalStateException.class, () -> verifyArtifact(registry, target, "fabric", jar));
        assertTrue(error.getMessage().contains("Duplicate entry"));
    }

    @Test void annotationScopeAndCounts() throws Exception {
        var target = target(registry(), "mc1_20_1");
        var expected = operationTargets(target, "fabric");
        for (boolean visible : List.of(true, false)) {
            var audit = inspectOperations(operations(61, expected, visible));
            assertEquals(5, audit.count);
            assertEquals(expected, audit.targets);
        }
        assertEquals(0, inspectOperations(clazz(FILE_PACK, 61)).count);
    }

    @Test void registryRejectsUnknownCapabilityAndDuplicateKeys() throws Exception {
        JsonObject registry = registry();
        registry.getAsJsonArray("targets").add(registry.getAsJsonArray("targets").get(0).deepCopy());
        assertThrows(IllegalStateException.class, () -> validateRegistry(registry));
        JsonObject unknown = registry();
        unknown.getAsJsonArray("targets").get(0).getAsJsonObject().getAsJsonArray("capabilities").add("UNKNOWN");
        assertThrows(IllegalStateException.class, () -> validateRegistry(unknown));
    }

    @Test void inventoryAdditionIsExactAndMandatory() {
        var baseline = ClassInventory.of(Set.of("Original.class"));
        var expected = new ClassInventory(baseline.count(), baseline.sha256(), "Synthetic$1.class");
        assertDoesNotThrow(() -> expected.verify(Set.of("Original.class", "Synthetic$1.class")));
        assertThrows(IllegalStateException.class, () -> expected.verify(Set.of("Original.class")));
        assertThrows(IllegalStateException.class, () -> expected.verify(Set.of("Synthetic$1.class")));
        assertThrows(IllegalStateException.class, () -> expected.verify(Set.of("Original.class", "Synthetic$1.class", "Synthetic$2.class")));
        assertThrows(IllegalStateException.class, () -> ClassInventory.expected("unknown/fabric"));
    }

    @Test void legacyModDevExactPinIsAccepted() throws Exception {
        JsonObject registry = registry();
        JsonObject target = target(registry, "mc1_20_1");
        var files = fixture(registry, target, "forge");
        edit(files, "META-INF/jarjar/metadata.json", j -> j.getAsJsonArray("jars").get(0).getAsJsonObject()
                .getAsJsonObject("version").addProperty("range", "[" + string(MixinExtrasContract.policy(registry, target, "forge"), "version") + "]"));
        Path jar = directory.resolve("legacy-mdg.jar");
        write(jar, files);
        assertDoesNotThrow(() -> verifyArtifact(registry, target, "forge", jar));
    }

    @TestFactory Stream<DynamicTest> nestedJarRegistrationMutations() {
        return Stream.of("forge").flatMap(platform -> {
            String metadata = platform.equals("fabric") ? "fabric.mod.json" : "META-INF/jarjar/metadata.json";
            String pathKey = platform.equals("fabric") ? "file" : "path";
            Map<String, Consumer<Map<String, byte[]>>> mutations = new LinkedHashMap<>();
            mutations.put("arbitrary jar", f -> f.put("hidden/helper.jar", new byte[0]));
            mutations.put("uppercase jar", f -> f.put("hidden/HELPER.JAR", new byte[0]));
            mutations.put("missing registration", f -> edit(f, metadata, j -> j.remove("jars")));
            mutations.put("duplicate registration", f -> edit(f, metadata, j -> j.getAsJsonArray("jars").add(j.getAsJsonArray("jars").get(0).deepCopy())));
            mutations.put("wrong path", f -> edit(f, metadata, j -> j.getAsJsonArray("jars").get(0).getAsJsonObject().addProperty(pathKey, "hidden/helper.jar")));
            if (!platform.equals("fabric")) {
                mutations.put("wrong coordinates", f -> edit(f, metadata, j -> j.getAsJsonArray("jars").get(0).getAsJsonObject().getAsJsonObject("identifier").addProperty("artifact", "helper")));
                mutations.put("wrong version", f -> edit(f, metadata, j -> j.getAsJsonArray("jars").get(0).getAsJsonObject().getAsJsonObject("version").addProperty("artifactVersion", "0.0.0")));
                mutations.put("wrong range", f -> edit(f, metadata, j -> j.getAsJsonArray("jars").get(0).getAsJsonObject().getAsJsonObject("version").addProperty("range", "[0,)")));
            }
            return mutations.entrySet().stream().map(e -> DynamicTest.dynamicTest(platform + ": " + e.getKey(), () -> {
                JsonObject registry = registry();
                JsonObject target = target(registry, "mc26_1_to_26_2");
                var files = fixture(registry, target, platform);
                Path jar = directory.resolve("nested.jar");
                write(jar, files);
                assertDoesNotThrow(() -> verifyArtifact(registry, target, platform, jar));
                e.getValue().accept(files);
                write(jar, files);
                assertThrows(IllegalStateException.class, () -> verifyArtifact(registry, target, platform, jar));
            }));
        });
    }

    @TestFactory Stream<DynamicTest> loaderProvidedRejectsLibrariesAndRegistrations() {
        return Stream.of("fabric", "neoforge").flatMap(platform -> Stream.of("library", "registration", "flattened").map(mutation ->
                DynamicTest.dynamicTest(platform + ": " + mutation, () -> {
                    JsonObject registry = registry();
                    JsonObject target = target(registry, MODERN_TARGET);
                    var files = fixture(registry, target, platform);
                    switch (mutation) {
                        case "library" -> files.put("META-INF/jars/unexpected.jar", new byte[0]);
                        case "flattened" -> files.put("com/llamalad7/mixinextras/MixinExtrasBootstrap.class", clazz("Bootstrap.class", 61));
                        case "registration" -> {
                            JsonArray jars = new JsonArray();
                            jars.add(new JsonObject());
                            if (platform.equals("fabric")) edit(files, "fabric.mod.json", j -> j.add("jars", jars));
                            else put(files, "META-INF/jarjar/metadata.json", "{\"jars\":[{}]}");
                        }
                    }
                    Path jar = directory.resolve("provided.jar");
                    write(jar, files);
                    var failure = assertThrows(IllegalStateException.class, () -> ArtifactVerifier.verifyArtifact(
                            registry, target, platform, jar, ClassInventory.of(files.keySet())));
                    assertTrue(failure.getMessage().contains("Loader-provided"), failure.getMessage());
                })));
    }

    @Test void sizeCeilingIsEnforced() throws Exception {
        JsonObject registry = registry();
        JsonObject target = target(registry, "mc1_20_1");
        Path jar = directory.resolve("oversize.jar");
        write(jar, fixture(registry, target, "fabric"));
        target.getAsJsonObject("platforms").getAsJsonObject("fabric").addProperty("maxArtifactBytes", Files.size(jar) - 1);
        var failure = assertThrows(IllegalStateException.class, () -> verifyArtifact(registry, target, "fabric", jar));
        assertTrue(failure.getMessage().contains("size ceiling"));
    }

    @Test void fallbackPoliciesAndMinimumLoaders() throws Exception {
        for (String platform : List.of("fabric", "neoforge")) {
            JsonObject registry = registry();
            JsonObject target = target(registry, MODERN_TARGET);
            JsonObject loader = target.getAsJsonObject("platforms").getAsJsonObject(platform);
            loader.add("mixinExtras", registry.getAsJsonObject("mixinExtrasPolicies").getAsJsonObject("forge").deepCopy());
            loader.addProperty("maxArtifactBytes", 800000);
            validateRegistry(registry);
            Path jar = directory.resolve("fallback.jar");
            write(jar, fixture(registry, target, platform));
            verifyArtifact(registry, target, platform, jar);
        }
        JsonObject oldFabric = registry();
        JsonObject fabric = target(oldFabric, "mc1_20_1").getAsJsonObject("platforms").getAsJsonObject("fabric");
        fabric.addProperty("loaderVersion", "0.19.1");
        fabric.addProperty("loaderDependency", ">=0.19.1");
        assertThrows(IllegalStateException.class, () -> validateRegistry(oldFabric));
        JsonObject oldNeo = registry();
        JsonObject neo = target(oldNeo, "mc1_21_1").getAsJsonObject("platforms").getAsJsonObject("neoforge");
        neo.addProperty("version", "21.1.249");
        neo.addProperty("versionRange", "[21.1.249,)");
        assertThrows(IllegalStateException.class, () -> validateRegistry(oldNeo));
    }

    @Test void combinedMc26RequiresFabricMixinExtras055() throws Exception {
        JsonObject registry = registry();
        JsonObject target = target(registry, MODERN_TARGET);
        assertDoesNotThrow(() -> validateRegistry(registry));
        target.getAsJsonObject("platforms").getAsJsonObject("fabric").remove("mixinExtras");
        assertThrows(IllegalStateException.class, () -> validateRegistry(registry));
    }

    @Test void combinedMc26RetainsItsBaselineModMenuIntegration() throws Exception {
        JsonObject registry = registry();
        JsonObject target = target(registry, MODERN_TARGET);
        Map<String, byte[]> files = fixture(registry, target, "fabric");
        assertTrue(files.containsKey(BASE + "client/config/PackForgeModMenuApi.class"));
        Path jar = directory.resolve("combined-mc26-fabric.jar");
        write(jar, files);
        assertDoesNotThrow(() -> verifyArtifact(registry, target, "fabric", jar));
        files.remove(BASE + "client/config/PackForgeModMenuApi.class");
        write(jar, files);
        assertThrows(IllegalStateException.class, () -> verifyArtifact(registry, target, "fabric", jar));
    }

    @TestFactory Stream<DynamicTest> rejectsBrokenSlimBootstrap() {
        return Stream.of("missing plugin", "wrong plugin", "wrong version", "missing internal registration", "missing service", "missing operation", "full library", "invalid inner jar")
                .map(mutation -> DynamicTest.dynamicTest(mutation, () -> {
                    Map<String, byte[]> bundle = MixinExtrasContract.unzip(slimFixture("forge", "0.5.4"));
                    String innerPath = "META-INF/jars/MixinExtras-0.5.4.jar";
                    switch (mutation) {
                        case "missing plugin" -> bundle.remove("com/llamalad7/mixinextras/platform/forge/MixinExtrasConfigPlugin.class");
                        case "wrong plugin" -> edit(bundle, "mixinextras.init.mixins.json", j -> j.addProperty("plugin", "Wrong"));
                        case "wrong version" -> put(bundle, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMixinConfigs: mixinextras.init.mixins.json\nFMLModType: GAMELIBRARY\nImplementation-Version: 0.0.0\n\n");
                        case "missing internal registration" -> bundle.remove("META-INF/jarjar/metadata.json");
                        case "invalid inner jar" -> bundle.put(innerPath, new byte[0]);
                        default -> {
                            var core = MixinExtrasContract.unzip(bundle.get(innerPath));
                            if (mutation.equals("missing service")) core.remove("META-INF/services/javax.annotation.processing.Processor");
                            else if (mutation.equals("missing operation")) core.remove("com/llamalad7/mixinextras/injector/wrapoperation/Operation.class");
                            else core.put("com/llamalad7/mixinextras/lib/antlr/runtime/Parser.class", new byte[0]);
                            bundle.put(innerPath, zipBytes(core));
                        }
                    }
                    assertThrows(IllegalStateException.class, () -> MixinExtrasContract.verifyBundle(bundle, "forge", "0.5.4"));
                }));
    }

    private static byte[] slimFixture(String platform, String version) {
        Map<String, byte[]> core = new LinkedHashMap<>();
        for (String name : List.of("MixinExtrasBootstrap", "injector/wrapoperation/WrapOperation", "injector/wrapoperation/Operation", "sugar/Local", "ap/MixinExtrasAP")) {
            String path = "com/llamalad7/mixinextras/" + name + ".class";
            core.put(path, clazz(path, 52));
        }
        put(core, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nImplementation-Version: " + version + "\n\n");
        put(core, "META-INF/services/javax.annotation.processing.Processor", "com.llamalad7.mixinextras.ap.MixinExtrasAP\n");
        Map<String, byte[]> bundle = platform.equals("forge") ? new LinkedHashMap<>() : core;
        String plugin = "com.llamalad7.mixinextras.platform." + platform + ".MixinExtrasConfigPlugin";
        bundle.put(plugin.replace('.', '/') + ".class", clazz(plugin.replace('.', '/') + ".class", 52));
        put(bundle, "mixinextras.init.mixins.json", "{\"plugin\":\"" + plugin + "\"}");
        if (platform.equals("fabric")) {
            put(bundle, "fabric.mod.json", "{\"id\":\"mixinextras\",\"version\":\"" + version + "\",\"mixins\":[\"mixinextras.init.mixins.json\"]}");
        } else {
            put(bundle, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMixinConfigs: mixinextras.init.mixins.json\nFMLModType: GAMELIBRARY\nImplementation-Version: " + version + "\n\n");
            if (platform.equals("forge")) {
                bundle.put("META-INF/jars/MixinExtras-" + version + ".jar", zipBytes(core));
                put(bundle, "META-INF/jarjar/metadata.json", "{\"jars\":[{\"identifier\":{\"group\":\"com.github.LlamaLad7\",\"artifact\":\"MixinExtras\"},\"version\":{\"artifactVersion\":\"" + version + "\",\"range\":\"[" + version + ",)\"},\"path\":\"META-INF/jars/MixinExtras-" + version + ".jar\"}]}");
            }
        }
        return zipBytes(bundle);
    }

    private static byte[] zipBytes(Map<String, byte[]> files) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                for (var entry : files.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            return bytes.toByteArray();
        } catch (IOException failure) { throw new IllegalStateException(failure); }
    }

    private static Map<String, byte[]> fixture(JsonObject registry, JsonObject target, String platform) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        String unchecked = BASE + "platform/PackForgeServices.class";
        files.put(unchecked, clazz(unchecked, 61));
        Set<String> main = new LinkedHashSet<>(List.of("loader.FilePackResourcesMixin"));
        Set<String> client = new LinkedHashSet<>(List.of("config.PackSelectionScreenMixin"));
        for (String path : List.of(BASE + "PackForgeCore.class", BASE + "client/config/PackForgeConfigScreen.class")) files.put(path, clazz(path, 61));
        if (!string(target, "key").equals("mc1_20_1")) {
            main.add("loader.SharedZipFileAccessMixin");
            String bridge = BASE + "internal/loader/SharedZipFileAccessBridge.class";
            files.put(bridge, clazz(bridge, 61));
        }
        if (string(target, "apiAdapter").equals("mc26")) {
            String atlas = BASE + "client/atlas/AtlasLoadInvocation.class";
            files.put(atlas, clazz(atlas, 61));
        }
        for (String name : strings(target, "capabilities")) {
            var c = CapabilityContracts.forAdapter(name, string(target, "apiAdapter"));
            Stream.concat(c.classes().stream(), c.anyClasses().stream()).forEach(p -> files.put(p, clazz(p, 61)));
            main.addAll(c.mainMixins());
            client.addAll(c.clientMixins());
            client.addAll(c.anyClientMixins());
        }
        int major = target.get("javaVersion").getAsInt() + 44;
        JsonObject mainConfig = config(files, main, "mixins", BASE + "mixin", major, target);
        JsonObject clientConfig = config(files, client, "client", BASE + "client/mixin", major, target);
        mainConfig.addProperty("refmap", "main.refmap.json");
        clientConfig.addProperty("refmap", "client.refmap.json");
        JsonObject configs = target.getAsJsonObject("mixinConfigs");
        put(files, string(configs, "main"), mainConfig.toString());
        put(files, string(configs, "client"), clientConfig.toString());
        files.put(FILE_PACK, operations(major, operationTargets(target, platform), false));
        put(files, "main.refmap.json", "{\"mappings\":{\"com/teenkung/packforge/mixin/observe/SimpleReloadInstanceMixin\":{\"<init>\":\"<init>\",\"Lnet/minecraft/server/packs/resources/SimpleReloadInstance$StateFactory;create()V\":\"Lowner;m_1()V\"}}}");
        put(files, "client.refmap.json", "{\"mappings\":{\"com/teenkung/packforge/client/mixin/atlas/SpriteLoaderMixin\":{\"loadAndStitch\":\"Lowner;m_2()V\"}}}");
        put(files, "assets/packforge/lang/en_us.json", "{}");
        files.put("assets/packforge/textures/gui/sprites/config_cog.png", new byte[0]);
        JsonObject extrasPolicy = MixinExtrasContract.policy(registry, target, platform);
        String extrasVersion = string(extrasPolicy, "version");
        String extrasPath = "META-INF/" + (platform.equals("fabric") ? "jars/" : "jarjar/") + "mixinextras-" + platform + "-" + extrasVersion + "-slim.jar";
        boolean bundled = string(extrasPolicy, "provider").equals("bundled");
        if (bundled) files.put(extrasPath, slimFixture(platform, extrasVersion));
        JsonObject pack = new JsonObject();
        JsonObject packSpec = target.getAsJsonObject("packMetadata");
        if (string(packSpec, "schema").equals("single")) pack.add("pack_format", packSpec.get("packFormat"));
        else {
            pack.add("min_format", packSpec.get("minFormat"));
            pack.add("max_format", packSpec.get("maxFormat"));
        }
        JsonObject metadata = new JsonObject();
        metadata.add("pack", pack);
        put(files, "pack.mcmeta", metadata.toString());
        JsonObject loader = target.getAsJsonObject("platforms").getAsJsonObject(platform);
        put(files, "packforge-capabilities.properties", "target=" + string(target, "key") + "\nminecraft=" + string(target, "artifactMinecraft")
                + "\nmaturity=" + string(loader, "maturity") + "\nbeta=" + string(loader, "maturity").equals("beta") + "\ncapabilities=" + String.join(",", strings(target, "capabilities")));
        if (platform.equals("fabric")) {
            JsonObject fabric = JsonParser.parseString("{\"entrypoints\":{}}").getAsJsonObject();
            if (loader.get("modMenuEntrypoint").getAsBoolean()) {
                files.put(BASE + "client/config/PackForgeModMenuApi.class", clazz(BASE + "client/config/PackForgeModMenuApi.class", 61));
                JsonArray modmenu = new JsonArray();
                modmenu.add("com.teenkung.packforge.client.config.PackForgeModMenuApi");
                fabric.getAsJsonObject("entrypoints").add("modmenu", modmenu);
                JsonObject suggests = new JsonObject();
                suggests.addProperty("modmenu", "*");
                fabric.add("suggests", suggests);
            }
            JsonObject depends = new JsonObject();
            depends.addProperty("fabricloader", string(loader, "loaderDependency"));
            depends.addProperty("minecraft", string(loader, "minecraftDependency"));
            depends.addProperty("java", ">=" + target.get("javaVersion").getAsInt());
            fabric.add("depends", depends);
            put(files, "fabric.mod.json", fabric.toString());
            put(files, "packforge.accesswidener", "accessWidener v2 " + (target.get("legacyApi").getAsBoolean() ? "intermediary" : "official"));
        } else put(files, platform.equals("forge") ? "META-INF/mods.toml" : "META-INF/neoforge.mods.toml",
                "versionRange=\"" + string(loader, "versionRange") + "\"\nversionRange=\"" + string(loader, "minecraftVersionRange") + "\"");
        if (!bundled) return files;
        JsonObject registration = new JsonObject();
        registration.addProperty(platform.equals("fabric") ? "file" : "path", extrasPath);
        if (!platform.equals("fabric")) {
            JsonObject identifier = new JsonObject();
            identifier.addProperty("group", "io.github.llamalad7");
            identifier.addProperty("artifact", "mixinextras-" + platform);
            registration.add("identifier", identifier);
            JsonObject pin = new JsonObject();
            pin.addProperty("artifactVersion", extrasVersion);
            pin.addProperty("range", "[" + extrasVersion + (platform.equals("forge") ? ",)" : "]"));
            registration.add("version", pin);
        }
        JsonArray jars = new JsonArray();
        jars.add(registration);
        if (platform.equals("fabric")) edit(files, "fabric.mod.json", j -> j.add("jars", jars));
        else {
            JsonObject jarjar = new JsonObject();
            jarjar.add("jars", jars);
            put(files, "META-INF/jarjar/metadata.json", jarjar.toString());
        }
        return files;
    }

    private static JsonObject config(Map<String, byte[]> files, Set<String> names, String key, String pkg, int major, JsonObject target) {
        JsonObject config = new JsonObject();
        config.addProperty("package", pkg.replace('/', '.'));
        config.addProperty("compatibilityLevel", string(target, "mixinCompatibility"));
        JsonArray mixins = new JsonArray();
        for (String name : names) {
            mixins.add(name);
            String path = pkg + "/" + name.replace('.', '/') + ".class";
            files.put(path, clazz(path, major));
        }
        config.add(key, mixins);
        return config;
    }

    private static byte[] clazz(String path, int major) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(major, Opcodes.ACC_PUBLIC, path.replace(".class", ""), null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] withConstant(String constant) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(61, Opcodes.ACC_PUBLIC, "Example", null, "java/lang/Object", null);
        writer.newUTF8(constant);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] operations(int major, Map<String, Integer> targets, boolean visible) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(major, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, FILE_PACK.replace(".class", ""), null, "java/lang/Object", null);
        int index = 0;
        for (var target : targets.entrySet()) for (int i = 0; i < target.getValue(); i++) {
            var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "hook" + index++, "()V", null, null);
            var wrap = method.visitAnnotation(WRAP, visible);
            var array = wrap.visitArray("at");
            var at = array.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
            at.visit("target", target.getKey());
            at.visitEnd();
            array.visitEnd();
            wrap.visitEnd();
            var unrelated = method.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/At;", visible);
            unrelated.visit("target", "ignored-outside-wrap");
            unrelated.visitEnd();
            method.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void put(Map<String, byte[]> files, String path, String text) { files.put(path, text.getBytes(StandardCharsets.UTF_8)); }
    private static void edit(Map<String, byte[]> files, String path, Consumer<JsonObject> change) {
        JsonObject json = JsonParser.parseString(new String(files.get(path), StandardCharsets.UTF_8)).getAsJsonObject();
        change.accept(json);
        put(files, path, json.toString());
    }
    private static void removeMixin(Map<String, byte[]> files, String path, String key, Set<String> names) {
        edit(files, path, json -> {
            JsonArray array = new JsonArray();
            strings(json, key).stream().filter(n -> !names.contains(n)).forEach(array::add);
            json.add(key, array);
        });
    }
    private static void write(Path path, Map<String, byte[]> files) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
    }
}
