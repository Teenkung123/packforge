package com.teenkung.packforge.verification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactSizeReportTest {
    @TempDir Path directory;

    @Test void accountingReconcilesExactBytesAndHash() throws Exception {
        Path artifact = directory.resolve("sample.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(artifact))) {
            for (String name : new String[]{"Example.class", "icon.png", "META-INF/library.jar", "config.json"}) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(name.repeat(100).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        var report = ArtifactSizeReport.measure(artifact);
        long total = 0;
        for (String key : new String[]{"classes", "images", "libraries", "other", "overhead"}) {
            long bytes = report.get(key).getAsLong();
            assertTrue(bytes > 0, key);
            total += bytes;
        }
        assertEquals(Files.size(artifact), total);
        assertEquals(total, report.get("bytes").getAsLong());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact))), report.get("sha256").getAsString());
    }
}
