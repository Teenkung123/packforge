package com.teenkung.packforge.verification;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/** Build-only release contract verifier. No Minecraft or Gradle runtime dependencies. */
public final class ArtifactVerifier {
    static final String BASE = "com/teenkung/packforge/";
    static final String FILE_PACK = BASE + "mixin/loader/FilePackResourcesMixin.class";
    static final String WRAP = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static final Pattern INTERMEDIARY = Pattern.compile("\\b(?:method|field)_[0-9]+\\b");
    private static final List<String> BUILD_PREFIXES = List.of("org/gradle/", "groovy/", "org/codehaus/groovy/",
            "org/objectweb/asm/", "com/google/gson/", "org/junit/", "org/opentest4j/", "org/apiguardian/",
            "com/teenkung/packforge/verification/", "dev/kikugie/stonecutter/", "net/fabricmc/loom/",
            "net/neoforged/moddevgradle/", "net/minecraftforge/gradle/");

    private ArtifactVerifier() {}

    public static void main(String[] args) throws IOException {
        require(args.length == 3 || args.length == 4,
                "Usage: ArtifactVerifier <registry.json> <artifact-directory> <mod-version> [target-key]");
        verify(Path.of(args[0]), Path.of(args[1]), args[2], args.length == 4 ? args[3] : null);
        System.out.println("PackForge artifact contracts passed.");
    }

    public static void verify(Path registryPath, Path directory, String version, String targetKey) throws IOException {
        verify(registryPath, directory, version, targetKey, ClassInventory::expected);
    }

    static void verify(Path registryPath, Path directory, String version, String targetKey,
                       Function<String, ClassInventory> inventories) throws IOException {
        JsonObject registry = JsonParser.parseString(Files.readString(registryPath)).getAsJsonObject();
        validateRegistry(registry);
        List<JsonObject> targets = registry.getAsJsonArray("targets").asList().stream().map(JsonElement::getAsJsonObject).toList();
        require(targetKey == null || targets.stream().anyMatch(t -> string(t, "key").equals(targetKey)), "Unknown target: " + targetKey);
        List<String> expected = new ArrayList<>();
        for (JsonObject target : targets) {
            for (String platform : target.getAsJsonObject("platforms").keySet()) {
                String name = artifactName(target, platform, version);
                expected.add(name);
                if (targetKey == null || string(target, "key").equals(targetKey)) {
                    try {
                        verifyArtifact(registry, target, platform, directory.resolve(name),
                                inventories.apply(string(target, "key") + "/" + platform));
                    } catch (RuntimeException | IOException failure) {
                        throw new IllegalStateException(name + ": " + failure.getMessage(), failure);
                    }
                }
            }
        }
        // Target-only mode mirrors root target verification, permitting other targets in the collection.
        if (targetKey == null) {
            List<String> actual;
            try (var paths = Files.walk(directory)) {
                actual = paths.filter(Files::isRegularFile).map(p -> p.getFileName().toString())
                        .filter(n -> n.startsWith("packforge-") && n.endsWith(".jar")).sorted().toList();
            }
            expected.sort(String::compareTo);
            require(actual.equals(expected), "Expected exactly " + expected.size() + " current release artifacts; expected=" + expected + ", actual=" + actual);
        }
    }

    static String artifactName(JsonObject target, String platform, String version) {
        return "packforge-" + platform + "-" + version + string(target.getAsJsonObject("platforms").getAsJsonObject(platform), "versionSuffix")
                + "-mc" + string(target, "artifactMinecraft") + ".jar";
    }

    static void validateRegistry(JsonObject registry) {
        require(registry.get("schemaVersion").getAsInt() == 1, "Unsupported registry schema");
        for (String field : List.of("mixinExtrasVersion", "mixinExtrasFabricLoaderMinimum", "fabricAsmVersion", "fabricMixinVersion")) string(registry, field);
        Set<String> keys = new HashSet<>();
        Set<String> suffixes = new HashSet<>();
        for (JsonElement element : registry.getAsJsonArray("targets")) {
            JsonObject target = element.getAsJsonObject();
            for (String field : List.of("key", "taskSuffix", "minecraftVersion", "artifactMinecraft", "apiAdapter", "javaVersion", "legacyApi", "packMetadata", "mixinConfigs", "capabilities", "platforms")) {
                require(target.has(field) && !target.get(field).isJsonNull(), "Missing target field: " + field);
            }
            require(keys.add(string(target, "key")), "Duplicate target key");
            require(suffixes.add(string(target, "taskSuffix")), "Duplicate task suffix");
            require(CapabilityContracts.ALL.keySet().containsAll(strings(target, "capabilities")), "Capabilities without artifact contracts");
            require(Set.of("single", "range").contains(string(target.getAsJsonObject("packMetadata"), "schema")), "Unsupported pack metadata schema");
            require(Set.of(17, 21, 25).contains(target.get("javaVersion").getAsInt()), "Unsupported target Java");
            JsonObject platforms = target.getAsJsonObject("platforms");
            require(!platforms.isEmpty(), "Target must enable a platform");
            for (String platform : platforms.keySet()) {
                require(Set.of("fabric", "forge", "neoforge").contains(platform), "Unknown platform: " + platform);
                JsonObject loader = platforms.getAsJsonObject(platform);
                String maturity = string(loader, "maturity");
                String suffix = string(loader, "versionSuffix");
                require(maturity.equals("stable") ? suffix.isEmpty() : maturity.equals("beta") && suffix.matches("-beta\\.\\d+"), "Invalid maturity/version suffix");
                if (target.get("legacyApi").getAsBoolean()) {
                    String minecraft = string(target, "minecraftVersion");
                    require(platform.equals("fabric") ? string(loader, "minecraftDependency").equals("=" + minecraft)
                            : string(loader, "minecraftVersionRange").equals("[" + minecraft + "]"), "Legacy compatibility must be exact");
                }
                if (platform.equals("fabric")) {
                    require(string(loader, "loaderDependency").equals(">=" + string(loader, "loaderVersion")), "Fabric minimum differs from compile version");
                    require(compareVersions(string(loader, "loaderVersion"), string(registry, "mixinExtrasFabricLoaderMinimum")) >= 0, "Fabric Loader below MixinExtras minimum");
                    require(!string(loader, "modMenuVersion").isBlank() && !string(loader, "fabricApiVersion").isBlank()
                            && loader.get("modMenuEntrypoint").getAsBoolean(), "Missing optional Mod Menu development integration");
                } else {
                    String lower = string(loader, "version");
                    if (platform.equals("forge")) lower = lower.substring(lower.lastIndexOf('-') + 1);
                    require(string(loader, "versionRange").startsWith("[" + lower + ","), "Loader compile version differs from range lower bound");
                }
            }
        }
        require(keys.contains(string(registry, "defaultTarget")), "Unknown defaultTarget");
    }

    private static int compareVersions(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int comparison = Integer.compare(i < a.length ? Integer.parseInt(a[i]) : 0, i < b.length ? Integer.parseInt(b[i]) : 0);
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    static void verifyArtifact(JsonObject registry, JsonObject target, String platform, Path artifact) throws IOException {
        verifyArtifact(registry, target, platform, artifact, ClassInventory.expected(string(target, "key") + "/" + platform));
    }

    static void verifyArtifact(JsonObject registry, JsonObject target, String platform, Path artifact,
                               ClassInventory inventory) throws IOException {
        require(Files.isRegularFile(artifact), "Expected artifact was not produced");
        JsonObject loader = target.getAsJsonObject("platforms").getAsJsonObject(platform);
        try (ZipFile zip = new ZipFile(artifact.toFile())) {
            Set<String> names = new HashSet<>();
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                require(names.add(name), "Duplicate entry: " + name);
                String normalized = name.replaceFirst("^META-INF/versions/[0-9]+/", "");
                require(!name.endsWith(".class") || BUILD_PREFIXES.stream().noneMatch(normalized::startsWith), "Forbidden build-tool class: " + name);
                String leaf = name.substring(name.lastIndexOf('/') + 1).toLowerCase(java.util.Locale.ROOT);
                require(!(leaf.endsWith(".jar") && (leaf.contains("common") || leaf.startsWith("packforge-"))), "Forbidden nested common/runtime jar: " + name);
                if (name.endsWith(".class")) {
                    int major = classMajor(bytes(zip, name), name);
                    require(major <= target.get("javaVersion").getAsInt() + 44, "Class exceeds target Java: " + name + " major=" + major);
                }
            }
            for (String forbidden : List.of(BASE + "mixin/loader/SharedZipFileAccessBridge.class", BASE + "loader/SharedZipFileAccessBridge.class",
                    BASE + "mixin/loader/CompositePackResourcesMixin.class")) require(!names.contains(forbidden), "Forbidden class: " + forbidden);
            if (!string(target, "key").equals("mc1_20_1")) required(names, BASE + "internal/loader/SharedZipFileAccessBridge.class");
            for (String path : List.of(BASE + "PackForgeCore.class", BASE + "client/config/PackForgeConfigScreen.class",
                    BASE + "client/mixin/config/PackSelectionScreenMixin.class", "assets/packforge/lang/en_us.json", "assets/packforge/textures/gui/sprites/config_cog.png")) required(names, path);
            String extras = "/mixinextras-" + platform + "-" + string(registry, "mixinExtrasVersion") + ".jar";
            require(names.stream().anyMatch(n -> n.endsWith(extras)), "Missing pinned MixinExtras " + extras);
            JsonObject pack = json(zip, "pack.mcmeta").getAsJsonObject("pack");
            JsonObject expectedPack = target.getAsJsonObject("packMetadata");
            if (string(expectedPack, "schema").equals("single")) {
                require(expectedPack.get("packFormat").equals(pack.get("pack_format")), "Wrong resource-pack format");
            } else {
                require(expectedPack.get("minFormat").equals(pack.get("min_format")) && expectedPack.get("maxFormat").equals(pack.get("max_format")), "Wrong resource-pack format range");
            }
            Properties capabilities = new Properties();
            capabilities.load(new ByteArrayInputStream(bytes(zip, "packforge-capabilities.properties")));
            Map<String, String> expectedCapabilities = Map.of("target", string(target, "key"), "minecraft", string(target, "artifactMinecraft"),
                    "maturity", string(loader, "maturity"), "beta", Boolean.toString(string(loader, "maturity").equals("beta")), "capabilities", String.join(",", strings(target, "capabilities")));
            expectedCapabilities.forEach((key, value) -> require(value.equals(capabilities.getProperty(key)), "Stale capability metadata: " + key));
            JsonObject configs = target.getAsJsonObject("mixinConfigs");
            JsonObject main = json(zip, string(configs, "main"));
            JsonObject client = json(zip, string(configs, "client"));
            for (JsonObject config : List.of(main, client)) require(string(config, "compatibilityLevel").equals(string(target, "mixinCompatibility")), "Incorrect mixin compatibility");
            List<String> mainMixins = strings(main, "mixins");
            List<String> clientMixins = strings(client, "client");
            require(!mainMixins.contains("loader.CompositePackResourcesMixin"), "Removed CompositePackResources mixin registered");
            if (!string(target, "key").equals("mc1_20_1")) require(mainMixins.contains("loader.SharedZipFileAccessMixin"), "Missing SharedZipFileAccess mixin");
            require(clientMixins.contains("config.PackSelectionScreenMixin"), "Missing configuration button mixin");
            checkMixins(zip, main, mainMixins, platform);
            checkMixins(zip, client, clientMixins, platform);
            byte[] filePack = bytes(zip, FILE_PACK);
            // This adapter-owned mixin, unlike flattened Java 17 PackForgeCore, proves node compilation.
            require(classMajor(filePack, FILE_PACK) == target.get("javaVersion").getAsInt() + 44, "Node-owned FilePackResourcesMixin must use exact target Java");
            OperationAudit audit = inspectOperations(filePack);
            require(audit.count == 5 && audit.targets.equals(operationTargets(target, platform)), "Incorrect @WrapOperation targets: " + audit.targets);
            String constants = new String(filePack, StandardCharsets.ISO_8859_1);
            require(constants.contains(WRAP), "Missing @WrapOperation marker");
            require(!constants.contains("CallbackInfoReturnable") && !constants.contains("Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;"), "Broad public-method replacement hook");
            if (string(target, "apiAdapter").equals("mc26")) {
                required(names, BASE + "client/atlas/AtlasLoadInvocation.class");
                require(names.stream().noneMatch(n -> n.startsWith(BASE + "client/mixin/atlas/SpriteLoaderMixin$") && n.endsWith(".class")), "Nested mc26 SpriteLoader mixin class");
            }
            for (String capability : strings(target, "capabilities")) {
                var contract = CapabilityContracts.ALL.get(capability);
                require(contract != null, "Capability without contract: " + capability);
                contract.classes().forEach(path -> required(names, path));
                require(contract.anyClasses().isEmpty() || contract.anyClasses().stream().anyMatch(names::contains), "Missing implementation alternatives for " + capability);
                require(mainMixins.containsAll(contract.mainMixins()), "Missing main mixin for " + capability);
                require(clientMixins.containsAll(contract.clientMixins()), "Missing client mixin for " + capability);
                require(contract.anyClientMixins().isEmpty() || contract.anyClientMixins().stream().anyMatch(clientMixins::contains), "Missing client mixin alternatives for " + capability);
            }
            checkLoader(zip, names, target, platform, loader, main, client);
            checkNestedJars(zip, names, platform, string(registry, "mixinExtrasVersion"));
            inventory.verify(names);
        }
    }

    private static void checkNestedJars(ZipFile zip, Set<String> names, String platform, String version) throws IOException {
        String path = "META-INF/" + (platform.equals("fabric") ? "jars/" : "jarjar/")
                + "mixinextras-" + platform + "-" + version + ".jar";
        Set<String> jars = new HashSet<>();
        names.stream().filter(n -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")).forEach(jars::add);
        require(jars.equals(Set.of(path)), "Only the pinned loader MixinExtras jar may be nested: " + jars);
        JsonObject metadata = json(zip, platform.equals("fabric") ? "fabric.mod.json" : "META-INF/jarjar/metadata.json");
        var registrations = metadata.getAsJsonArray("jars");
        require(registrations != null && registrations.size() == 1, "Expected exactly one nested jar registration");
        JsonObject entry = registrations.get(0).getAsJsonObject();
        require(entry.has(platform.equals("fabric") ? "file" : "path")
                && path.equals(string(entry, platform.equals("fabric") ? "file" : "path")), "Incorrect nested jar registration path");
        if (!platform.equals("fabric")) {
            JsonObject identifier = entry.getAsJsonObject("identifier");
            JsonObject pin = entry.getAsJsonObject("version");
            require(identifier != null && "io.github.llamalad7".equals(string(identifier, "group"))
                    && ("mixinextras-" + platform).equals(string(identifier, "artifact")), "Incorrect nested jar coordinates");
            require(pin != null && version.equals(string(pin, "artifactVersion"))
                    && (("[" + version + "]").equals(string(pin, "range"))
                        || platform.equals("forge") && ("[" + version + ",)").equals(string(pin, "range"))),
                    "Incorrect nested jar version registration");
        }
    }

    private static void checkMixins(ZipFile zip, JsonObject config, List<String> mixins, String platform) throws IOException {
        for (String mixin : mixins) {
            String path = string(config, "package").replace('.', '/') + "/" + mixin.replace('.', '/') + ".class";
            String constants = new String(bytes(zip, path), StandardCharsets.ISO_8859_1);
            require(platform.equals("fabric") || !INTERMEDIARY.matcher(constants).find(), "Fabric intermediary selector in configured mixin: " + mixin);
        }
    }

    private static void checkLoader(ZipFile zip, Set<String> names, JsonObject target, String platform, JsonObject loader,
                                    JsonObject main, JsonObject client) throws IOException {
        if (platform.equals("fabric")) {
            JsonObject fabric = json(zip, "fabric.mod.json");
            JsonObject depends = new JsonObject();
            depends.addProperty("fabricloader", string(loader, "loaderDependency"));
            depends.addProperty("minecraft", string(loader, "minecraftDependency"));
            depends.addProperty("java", ">=" + target.get("javaVersion").getAsInt());
            require(depends.equals(fabric.get("depends")), "Incorrect Fabric dependencies");
            if (loader.has("modMenuEntrypoint") && loader.get("modMenuEntrypoint").getAsBoolean()) {
                require(strings(fabric.getAsJsonObject("entrypoints"), "modmenu").equals(List.of("com.teenkung.packforge.client.config.PackForgeModMenuApi"))
                        && string(fabric.getAsJsonObject("suggests"), "modmenu").equals("*"), "Incomplete optional Mod Menu integration");
                required(names, BASE + "client/config/PackForgeModMenuApi.class");
            }
            String namespace = target.get("legacyApi").getAsBoolean() ? "intermediary" : "official";
            String header = text(zip, "packforge.accesswidener").lines().findFirst().orElse("").trim();
            require(List.of(header.split("\\s+")).equals(List.of("accessWidener", "v2", namespace)), "Incorrect access-widener header");
        } else {
            String toml = text(zip, platform.equals("forge") ? "META-INF/mods.toml" : "META-INF/neoforge.mods.toml");
            require(toml.contains("versionRange=\"" + string(loader, "versionRange") + "\"")
                    && toml.contains("versionRange=\"" + string(loader, "minecraftVersionRange") + "\""), "Incorrect " + platform + " dependency ranges");
            if (platform.equals("forge") && string(target, "minecraftVersion").equals("1.20.1")) {
                JsonObject mappings = new JsonObject();
                for (JsonObject mixin : List.of(main, client)) {
                    String refmap = string(mixin, "refmap");
                    require(!refmap.isBlank(), "Missing legacy Forge refmap");
                    String content = text(zip, refmap);
                    require(!INTERMEDIARY.matcher(content).find(), "Unmapped intermediary selector in refmap");
                    JsonObject map = JsonParser.parseString(content).getAsJsonObject().getAsJsonObject("mappings");
                    if (map != null) map.entrySet().forEach(e -> mappings.add(e.getKey(), e.getValue()));
                }
                JsonObject reload = mappings.getAsJsonObject(BASE + "mixin/observe/SimpleReloadInstanceMixin");
                require(reload != null && reload.has("<init>") && reload.entrySet().stream().anyMatch(e ->
                        e.getKey().startsWith("Lnet/minecraft/server/packs/resources/SimpleReloadInstance$StateFactory;create(") && e.getValue().getAsString().contains(";m_")), "Missing remapped constructor/StateFactory hook");
                JsonObject sprite = mappings.getAsJsonObject(BASE + "client/mixin/atlas/SpriteLoaderMixin");
                require(sprite != null && sprite.has("loadAndStitch") && string(sprite, "loadAndStitch").contains(";m_"), "Missing remapped loadAndStitch hook");
            }
        }
    }

    static Map<String, Integer> operationTargets(JsonObject target, String platform) {
        String owner = platform.equals("fabric") && target.get("legacyApi").getAsBoolean()
                ? "Lnet/minecraft/class_7367;" : "Lnet/minecraft/server/packs/resources/IoSupplier;";
        return Map.of("Ljava/util/zip/ZipFile;getEntry(Ljava/lang/String;)Ljava/util/zip/ZipEntry;", 1,
                "Ljava/util/zip/ZipFile;entries()Ljava/util/Enumeration;", 2,
                owner + "create(Ljava/util/zip/ZipFile;Ljava/util/zip/ZipEntry;)" + owner, 2);
    }

    static final class OperationAudit {
        int count;
        final Map<String, Integer> targets = new HashMap<>();
    }

    static OperationAudit inspectOperations(byte[] bytes) {
        OperationAudit result = new OperationAudit();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        return annotation(descriptor, false, result);
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return result;
    }

    private static AnnotationVisitor annotation(String descriptor, boolean inside, OperationAudit result) {
        boolean wrap = WRAP.equals(descriptor);
        if (wrap) result.count++;
        boolean scoped = inside || wrap;
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override public void visit(String name, Object value) {
                if (scoped && descriptor.equals("Lorg/spongepowered/asm/mixin/injection/At;") && "target".equals(name) && value instanceof String target) result.targets.merge(target, 1, Integer::sum);
            }
            @Override public AnnotationVisitor visitAnnotation(String name, String nested) { return annotation(nested, scoped, result); }
            @Override public AnnotationVisitor visitArray(String name) { return annotation("", scoped, result); }
        };
    }

    private static int classMajor(byte[] bytes, String path) {
        require(bytes.length >= 8 && bytes[0] == (byte) 0xca && bytes[1] == (byte) 0xfe && bytes[2] == (byte) 0xba && bytes[3] == (byte) 0xbe, "Invalid class header: " + path);
        return (Byte.toUnsignedInt(bytes[6]) << 8) | Byte.toUnsignedInt(bytes[7]);
    }

    static List<String> strings(JsonObject object, String key) {
        return object.has(key) ? object.getAsJsonArray(key).asList().stream().map(JsonElement::getAsString).toList() : List.of();
    }
    static String string(JsonObject object, String key) { return object.get(key).getAsString(); }
    private static void required(Set<String> names, String path) { require(names.contains(path), "Missing entry: " + path); }
    private static byte[] bytes(ZipFile zip, String path) throws IOException {
        var entry = zip.getEntry(path);
        require(entry != null, "Missing entry: " + path);
        try (var input = zip.getInputStream(entry)) { return input.readAllBytes(); }
    }
    private static String text(ZipFile zip, String path) throws IOException { return new String(bytes(zip, path), StandardCharsets.UTF_8); }
    private static JsonObject json(ZipFile zip, String path) throws IOException { return JsonParser.parseString(text(zip, path)).getAsJsonObject(); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
