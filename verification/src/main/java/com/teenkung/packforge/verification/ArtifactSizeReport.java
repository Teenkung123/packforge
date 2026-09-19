package com.teenkung.packforge.verification;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipFile;

import static com.teenkung.packforge.verification.ArtifactVerifier.string;

/** Build-only accounting of the actual compressed bytes in every declared release artifact. */
public final class ArtifactSizeReport {
    private ArtifactSizeReport() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 3 || args.length > 4) throw new IllegalArgumentException(
                "Usage: ArtifactSizeReport <registry.json> <artifact-directory> <mod-version> [baseline.json]");
        Path registryPath = Path.of(args[0]).toAbsolutePath();
        Path output = registryPath.getParent().getParent().resolve("build/size-optimization");
        report(registryPath, Path.of(args[1]), args[2], args.length == 4 ? Path.of(args[3]) : null, output);
    }

    static void report(Path registryPath, Path directory, String version, Path baselinePath, Path output) throws IOException {
        JsonObject registry = JsonParser.parseString(Files.readString(registryPath)).getAsJsonObject();
        ArtifactVerifier.validateRegistry(registry);
        Map<String, JsonObject> baseline = new HashMap<>();
        if (baselinePath != null) {
            for (JsonElement entry : JsonParser.parseString(Files.readString(baselinePath)).getAsJsonArray()) {
                JsonObject artifact = entry.getAsJsonObject();
                baseline.put(string(artifact, "name"), artifact);
            }
        }
        JsonArray artifacts = new JsonArray();
        StringBuilder markdown = new StringBuilder("# PackForge release artifact sizes\n\nCompressed entry bytes; ZIP overhead includes headers, directory records and descriptors.\n\n")
                .append("| Artifact | Before bytes | Final bytes | Reduction | Classes | Images | Libraries | Other | ZIP overhead |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        StringBuilder hashes = new StringBuilder();
        for (JsonElement element : registry.getAsJsonArray("targets")) {
            JsonObject target = element.getAsJsonObject();
            for (String platform : target.getAsJsonObject("platforms").keySet()) {
                String name = ArtifactVerifier.artifactName(target, platform, version);
                JsonObject artifact = measure(directory.resolve(name));
                artifact.addProperty("target", string(target, "key"));
                artifact.addProperty("platform", platform);
                artifact.addProperty("maxArtifactBytes", target.getAsJsonObject("platforms").getAsJsonObject(platform).get("maxArtifactBytes").getAsLong());
                JsonObject before = baseline.get(name);
                String reduction = "—";
                if (before != null) {
                    long previous = before.get("bytes").getAsLong();
                    long saved = previous - artifact.get("bytes").getAsLong();
                    artifact.addProperty("baselineBytes", previous);
                    artifact.addProperty("savedBytes", saved);
                    if (previous > 0) {
                        artifact.addProperty("reductionPercent", 100.0 * saved / previous);
                        reduction = String.format(Locale.ROOT, "%.2f%%", 100.0 * saved / previous);
                    }
                }
                artifacts.add(artifact);
                markdown.append('|').append(name).append('|').append(before == null ? "—" : before.get("bytes").getAsLong())
                        .append('|').append(artifact.get("bytes").getAsLong()).append('|').append(reduction);
                for (String category : new String[]{"classes", "images", "libraries", "other", "overhead"}) markdown.append('|').append(artifact.get(category).getAsLong());
                markdown.append("|\n");
                hashes.append(string(artifact, "sha256")).append("  ").append(name).append('\n');
            }
        }
        Files.createDirectories(output);
        Files.writeString(output.resolve("final.json"), new GsonBuilder().setPrettyPrinting().create().toJson(artifacts) + "\n");
        Files.writeString(output.resolve("size-report.md"), markdown.toString());
        Files.writeString(output.resolve("final-sha256.txt"), hashes.toString());
        System.out.println("Wrote size accounting for " + artifacts.size() + " artifacts to " + output);
    }

    static JsonObject measure(Path path) throws IOException {
        Map<String, Long> categories = new LinkedHashMap<>();
        for (String category : new String[]{"classes", "images", "libraries", "other"}) categories.put(category, 0L);
        try (ZipFile zip = new ZipFile(path.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName().toLowerCase(Locale.ROOT);
                String category = name.endsWith(".jar") ? "libraries" : name.endsWith(".class") ? "classes"
                        : name.matches(".*\\.(png|jpg|jpeg|gif|webp|svg)$") ? "images" : "other";
                if (entry.getCompressedSize() < 0) throw new IOException("Missing compressed size: " + entry.getName());
                categories.merge(category, entry.getCompressedSize(), Long::sum);
            }
        }
        JsonObject artifact = new JsonObject();
        artifact.addProperty("name", path.getFileName().toString());
        long size = Files.size(path);
        artifact.addProperty("bytes", size);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) != -1;) digest.update(buffer, 0, read);
            }
            artifact.addProperty("sha256", HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        categories.forEach(artifact::addProperty);
        artifact.addProperty("overhead", size - categories.values().stream().mapToLong(Long::longValue).sum());
        return artifact;
    }
}
