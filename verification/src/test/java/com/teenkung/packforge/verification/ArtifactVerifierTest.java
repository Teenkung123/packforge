package com.teenkung.packforge.verification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
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

    @Test void completeSeventeenArtifactSetAndCli() throws Exception {
        JsonObject registry = registry();
        int count = 0;
        for (var element : registry.getAsJsonArray("targets")) {
            JsonObject target = element.getAsJsonObject();
            for (String platform : target.getAsJsonObject("platforms").keySet()) {
                write(directory.resolve(artifactName(target, platform, "1.4")), fixture(registry, target, platform));
                count++;
            }
        }
        assertEquals(17, count);
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
        mutations.put("missing extras", f -> f.keySet().removeIf(n -> n.contains("mixinextras-")));
        mutations.put("nested common", f -> f.put("META-INF/jars/packforge-common.jar", new byte[0]));
        mutations.put("build tool", f -> f.put("org/gradle/api/Plugin.class", clazz("org/gradle/api/Plugin.class", 61)));
        mutations.put("multi-release build tool", f -> f.put("META-INF/versions/17/org/objectweb/asm/ClassReader.class", clazz("org/objectweb/asm/ClassReader.class", 61)));
        mutations.put("wrong Fabric dependency", f -> edit(f, "fabric.mod.json", j -> j.getAsJsonObject("depends").addProperty("fabric-api", "*")));
        mutations.put("missing Mod Menu", f -> f.remove(BASE + "client/config/PackForgeModMenuApi.class"));
        mutations.put("wrong widener", f -> put(f, "packforge.accesswidener", "accessWidener v2 intermediary"));
        mutations.put("wrong operations", f -> f.put(FILE_PACK, operations(69, Map.of("wrong", 5), false)));
        return mutations.entrySet().stream().map(e -> DynamicTest.dynamicTest(e.getKey(), () -> {
            JsonObject registry = registry();
            JsonObject target = target(registry, "mc26_1_to_26_2");
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
            var c = e.getValue();
            for (String path : c.classes()) tests.add(capabilityFailure(e.getKey(), path, f -> f.remove(path)));
            if (!c.anyClasses().isEmpty()) tests.add(capabilityFailure(e.getKey(), "alternatives", f -> c.anyClasses().forEach(f::remove)));
            for (String name : c.mainMixins()) tests.add(capabilityFailure(e.getKey(), name, f -> removeMixin(f, "packforge.fabric.mixins.json", "mixins", Set.of(name))));
            for (String name : c.clientMixins()) tests.add(capabilityFailure(e.getKey(), name, f -> removeMixin(f, "packforge.fabric.client.mixins.json", "client", Set.of(name))));
            if (!c.anyClientMixins().isEmpty()) tests.add(capabilityFailure(e.getKey(), "mixin alternatives", f -> removeMixin(f, "packforge.fabric.client.mixins.json", "client", Set.copyOf(c.anyClientMixins()))));
            return tests.stream();
        });
    }

    private DynamicTest capabilityFailure(String capability, String label, Consumer<Map<String, byte[]>> mutate) {
        return DynamicTest.dynamicTest(capability + ": " + label, () -> {
            JsonObject registry = registry();
            JsonObject target = target(registry, "mc26_1_to_26_2");
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
                .getAsJsonObject("version").addProperty("range", "[" + string(registry, "mixinExtrasVersion") + "]"));
        Path jar = directory.resolve("legacy-mdg.jar");
        write(jar, files);
        assertDoesNotThrow(() -> verifyArtifact(registry, target, "forge", jar));
    }

    @TestFactory Stream<DynamicTest> nestedJarRegistrationMutations() {
        return Stream.of("fabric", "forge", "neoforge").flatMap(platform -> {
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

    private static Map<String, byte[]> fixture(JsonObject registry, JsonObject target, String platform) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        String unchecked = BASE + "platform/PackForgeServices.class";
        files.put(unchecked, clazz(unchecked, 61));
        Set<String> main = new LinkedHashSet<>(List.of("loader.FilePackResourcesMixin"));
        Set<String> client = new LinkedHashSet<>(List.of("config.PackSelectionScreenMixin"));
        for (String path : List.of(BASE + "PackForgeCore.class", BASE + "client/config/PackForgeConfigScreen.class", BASE + "client/config/PackForgeModMenuApi.class")) files.put(path, clazz(path, 61));
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
            var c = CapabilityContracts.ALL.get(name);
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
        String extrasVersion = string(registry, "mixinExtrasVersion");
        String extrasPath = "META-INF/" + (platform.equals("fabric") ? "jars/" : "jarjar/") + "mixinextras-" + platform + "-" + extrasVersion + ".jar";
        files.put(extrasPath, new byte[0]);
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
            JsonObject fabric = JsonParser.parseString("{\"entrypoints\":{\"modmenu\":[\"com.teenkung.packforge.client.config.PackForgeModMenuApi\"]},\"suggests\":{\"modmenu\":\"*\"}}").getAsJsonObject();
            JsonObject depends = new JsonObject();
            depends.addProperty("fabricloader", string(loader, "loaderDependency"));
            depends.addProperty("minecraft", string(loader, "minecraftDependency"));
            depends.addProperty("java", ">=" + target.get("javaVersion").getAsInt());
            fabric.add("depends", depends);
            put(files, "fabric.mod.json", fabric.toString());
            put(files, "packforge.accesswidener", "accessWidener v2 " + (target.get("legacyApi").getAsBoolean() ? "intermediary" : "official"));
        } else put(files, platform.equals("forge") ? "META-INF/mods.toml" : "META-INF/neoforge.mods.toml",
                "versionRange=\"" + string(loader, "versionRange") + "\"\nversionRange=\"" + string(loader, "minecraftVersionRange") + "\"");
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
