package com.teenkung.packforge.verification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static com.teenkung.packforge.verification.ArtifactVerifier.string;

/** Packaging and bootstrap contracts for the official loader-specific slim distributions. */
final class MixinExtrasContract {
    private static final String PREFIX = "com/llamalad7/mixinextras/";
    private static final String JARJAR = "META-INF/jarjar/metadata.json";
    private static final String INIT = "mixinextras.init.mixins.json";
    private static final Map<String, String> NEO_MINIMUMS = Map.of(
            "mc1_21_1", "21.1.250", "mc1_21_4", "21.4.157", "mc1_21_8", "21.8.54",
            "mc1_21_11", "21.11.45", "mc26_1_to_26_3", "26.1.0.1-beta");

    private MixinExtrasContract() {}

    static JsonObject policy(JsonObject registry, JsonObject target, String platform) {
        JsonObject loader = target.getAsJsonObject("platforms").getAsJsonObject(platform);
        return loader.has("mixinExtras") ? loader.getAsJsonObject("mixinExtras")
                : registry.getAsJsonObject("mixinExtrasPolicies").getAsJsonObject(platform);
    }

    static void validatePolicy(JsonObject registry, JsonObject target, String platform) {
        JsonObject loader = target.getAsJsonObject("platforms").getAsJsonObject(platform);
        JsonObject policy = policy(registry, target, platform);
        String provider = string(policy, "provider");
        require(provider.equals("loader") || provider.equals("bundled"), "Unknown MixinExtras provider");
        boolean bundled = provider.equals("bundled");
        require(!platform.equals("forge") || bundled, "Forge must bundle MixinExtras");
        require(string(policy, "version").equals(verifiedVersion(string(target, "key"), platform, bundled)),
                "Unverified MixinExtras version");
        require(bundled ? policy.has("classifier") && string(policy, "classifier").equals("slim")
                : !policy.has("classifier"), "MixinExtras bundle must use slim classifier");
        require(loader.has("maxArtifactBytes") && loader.get("maxArtifactBytes").getAsLong() > 0,
                "Missing positive artifact size ceiling");
        if (platform.equals("neoforge") && !bundled) {
            String minimum = NEO_MINIMUMS.get(string(target, "key"));
            require(minimum != null && atLeast(string(loader, "version"), minimum), "NeoForge below verified MixinExtras minimum");
        }
    }

    private static String verifiedVersion(String targetKey, String platform, boolean bundled) {
        if (bundled) return "0.5.4";
        if (targetKey.equals("mc26_1_to_26_3") && platform.equals("fabric")) return "0.5.5";
        return platform.equals("neoforge") ? "0.5.3" : "0.5.4";
    }

    private static boolean atLeast(String actual, String minimum) {
        String[] a = actual.split("[.-]");
        String[] b = minimum.split("[.-]");
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            if (!a[i].matches("[0-9]+") || !b[i].matches("[0-9]+")) return actual.equals(minimum);
            int comparison = Integer.compare(Integer.parseInt(a[i]), Integer.parseInt(b[i]));
            if (comparison != 0) return comparison > 0;
        }
        return a.length >= b.length;
    }

    static void verify(ZipFile zip, Set<String> names, String platform, JsonObject policy) throws IOException {
        Set<String> jars = jars(names);
        String metadataPath = platform.equals("fabric") ? "fabric.mod.json" : JARJAR;
        JsonObject metadata = names.contains(metadataPath) ? json(read(zip, metadataPath)) : new JsonObject();
        if (string(policy, "provider").equals("loader")) {
            require(jars.isEmpty(), "Loader-provided MixinExtras forbids nested libraries: " + jars);
            require(!metadata.has("jars") || metadata.getAsJsonArray("jars").isEmpty(), "Loader-provided MixinExtras forbids nested registrations");
            require(names.stream().noneMatch(n -> n.startsWith(PREFIX)), "Loader-provided MixinExtras must not be flattened");
            return;
        }
        String version = string(policy, "version");
        String path = "META-INF/" + (platform.equals("fabric") ? "jars/" : "jarjar/")
                + "mixinextras-" + platform + "-" + version + "-slim.jar";
        require(jars.equals(Set.of(path)), "Only the approved slim MixinExtras jar may be nested: " + jars);
        registration(metadata, platform, path, "io.github.llamalad7", "mixinextras-" + platform, version);
        verifyBundle(unzip(read(zip, path)), platform, version);
    }

    static void verifyBundle(Map<String, byte[]> bundle, String platform, String version) throws IOException {
        String plugin = PREFIX + "platform/" + platform + "/MixinExtrasConfigPlugin";
        require(bundle.containsKey(plugin + ".class"), "Missing MixinExtras bootstrap plugin");
        require(string(json(required(bundle, INIT)), "plugin").equals(plugin.replace('/', '.')), "Wrong MixinExtras bootstrap plugin registration");
        if (platform.equals("fabric")) {
            JsonObject metadata = json(required(bundle, "fabric.mod.json"));
            require(string(metadata, "id").equals("mixinextras") && string(metadata, "version").equals(version)
                    && ArtifactVerifier.strings(metadata, "mixins").contains(INIT), "Incorrect MixinExtras Fabric metadata");
        } else {
            Manifest manifest = new Manifest(new ByteArrayInputStream(required(bundle, "META-INF/MANIFEST.MF")));
            require(INIT.equals(manifest.getMainAttributes().getValue("MixinConfigs"))
                    && "GAMELIBRARY".equals(manifest.getMainAttributes().getValue("FMLModType")), "Missing MixinExtras manifest bootstrap metadata");
            if (platform.equals("forge")) {
                require(version.equals(manifest.getMainAttributes().getValue("Implementation-Version")), "Incorrect MixinExtras manifest version");
                String inner = "META-INF/jars/MixinExtras-" + version + ".jar";
                require(jars(bundle.keySet()).equals(Set.of(inner)), "Incorrect Forge MixinExtras internal library");
                registration(json(required(bundle, JARJAR)), "forge", inner, "com.github.LlamaLad7", "MixinExtras", version);
                Map<String, byte[]> core = unzip(required(bundle, inner));
                Manifest coreManifest = new Manifest(new ByteArrayInputStream(required(core, "META-INF/MANIFEST.MF")));
                require(version.equals(coreManifest.getMainAttributes().getValue("Implementation-Version")), "Incorrect MixinExtras core version");
                verifyCore(core);
                return;
            }
        }
        verifyCore(bundle);
    }

    private static void verifyCore(Map<String, byte[]> core) {
        require(jars(core.keySet()).isEmpty(), "Unexpected library inside MixinExtras core");
        for (String clazz : Set.of("MixinExtrasBootstrap", "injector/wrapoperation/WrapOperation", "injector/wrapoperation/Operation", "sugar/Local", "ap/MixinExtrasAP")) {
            required(core, PREFIX + clazz + ".class");
        }
        require(new String(required(core, "META-INF/services/javax.annotation.processing.Processor"), StandardCharsets.UTF_8)
                .trim().equals("com.llamalad7.mixinextras.ap.MixinExtrasAP"), "Incorrect MixinExtras processor service");
        require(core.keySet().stream().noneMatch(n -> n.startsWith(PREFIX + "lib/antlr/runtime/")), "Full MixinExtras distribution bundled instead of slim");
    }

    private static void registration(JsonObject metadata, String platform, String path, String group, String artifact, String version) {
        JsonArray registrations = metadata.getAsJsonArray("jars");
        require(registrations != null && registrations.size() == 1, "Expected exactly one nested jar registration");
        JsonObject entry = registrations.get(0).getAsJsonObject();
        String pathKey = platform.equals("fabric") ? "file" : "path";
        require(entry.has(pathKey) && path.equals(string(entry, pathKey)), "Incorrect nested jar registration path");
        if (!platform.equals("fabric")) {
            JsonObject identifier = entry.getAsJsonObject("identifier");
            JsonObject pin = entry.getAsJsonObject("version");
            require(identifier != null && group.equals(string(identifier, "group")) && artifact.equals(string(identifier, "artifact")), "Incorrect nested jar coordinates");
            require(pin != null && version.equals(string(pin, "artifactVersion"))
                    && Set.of("[" + version + "]", "[" + version + ",)").contains(string(pin, "range")), "Incorrect nested jar version registration");
        }
    }

    static Map<String, byte[]> unzip(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                require(entries.put(entry.getName(), zip.readAllBytes()) == null, "Duplicate nested entry: " + entry.getName());
            }
        }
        require(!entries.isEmpty(), "Empty or invalid nested jar");
        return entries;
    }

    private static Set<String> jars(Set<String> names) { return names.stream().filter(n -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")).collect(Collectors.toSet()); }
    private static JsonObject json(byte[] bytes) { return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject(); }
    private static byte[] required(Map<String, byte[]> entries, String path) { require(entries.containsKey(path), "Missing MixinExtras entry: " + path); return entries.get(path); }
    private static byte[] read(ZipFile zip, String path) throws IOException { try (var input = zip.getInputStream(zip.getEntry(path))) { return input.readAllBytes(); } }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
