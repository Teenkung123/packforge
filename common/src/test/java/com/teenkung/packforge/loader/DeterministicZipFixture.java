package com.teenkung.packforge.loader;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class DeterministicZipFixture {
	static Path create(Path path, int generatedEntries) throws IOException {
		return create(path, generatedEntries, defaultPackMetadata());
	}

	static Path create(Path path, int generatedEntries, String packMetadata) throws IOException {
		return create(path, generatedEntries, packMetadata, true);
	}

	static Path createRuntimeCompatible(Path path, int generatedEntries, String packMetadata) throws IOException {
		return create(path, generatedEntries, packMetadata, false);
	}

	private static Path create(Path path, int generatedEntries, String packMetadata, boolean includeMalformedEntries) throws IOException {
		try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
			add(output, "pack.mcmeta", packMetadata.getBytes(StandardCharsets.UTF_8), false);
			addDirectory(output, "assets/minecraft/");
			add(output, "assets/minecraft/textures/a.txt", "alpha".getBytes(StandardCharsets.UTF_8), false);
			add(output, "assets/minecraft/models/block/a.json", "{}".getBytes(StandardCharsets.UTF_8), true);
			add(output, "assets/minecraft/font/default.json", "{\"providers\":[]}".getBytes(StandardCharsets.UTF_8), false);
			add(output, "assets/minecraft/shaders/core/fixture.json", "{}".getBytes(StandardCharsets.UTF_8), true);
			add(output, "assets/minecraft/shaders/core/fixture.vsh", "#version 150\nvoid main(){}".getBytes(StandardCharsets.UTF_8), false);
			add(output, "assets/minecraft/shaders/core/fixture.fsh", "#version 150\nvoid main(){}".getBytes(StandardCharsets.UTF_8), true);
			add(output, "assets/example/textures/duplicate.txt", "base".getBytes(StandardCharsets.UTF_8), false);
			add(output, "overlay/assets/example/textures/overlay.txt", "overlay".getBytes(StandardCharsets.UTF_8), false);
			add(output, "overlay/assets/example/textures/duplicate.txt", "overlay-wins-in-overlay-pack".getBytes(StandardCharsets.UTF_8), true);
			if (includeMalformedEntries) {
				addDirectory(output, "assets/empty_namespace/");
				addDirectory(output, "assets/BadNamespace/");
				addDirectory(output, "assets/foo@bar/");
				add(output, "assets/BadNamespace/textures/ignored.txt", new byte[] { 1 }, true);
				add(output, "assets/foo@bar/textures/legacy.txt", new byte[] { 2 }, false);
				add(output, "assets/minecraft/textures/Bad Path.png", new byte[] { 3 }, true);
				add(output, "assets/minecraft/../escape.txt", new byte[] { 4 }, false);
				add(output, "assets//textures/empty-namespace.txt", new byte[] { 5 }, true);
			}
			if (generatedEntries >= 20_000) {
				add(output, "assets/minecraft/textures/large/fixture.png", png(1024, 1024), true);
				add(output, "assets/minecraft/textures/animated/fixture.png", png(32, 128), false);
				add(output, "assets/minecraft/textures/animated/fixture.png.mcmeta", "{\"animation\":{\"frametime\":2}}".getBytes(StandardCharsets.UTF_8), true);
			}
			for (int i = 0; i < generatedEntries; i++) {
				String namespace = "generated" + (i % 8);
				String name = String.format("assets/%s/textures/generated/%05d.bin", namespace, i);
				byte[] content = ("entry-" + i).getBytes(StandardCharsets.UTF_8);
				add(output, name, content, (i & 1) == 0);
			}
		}
		return path;
	}

	static String defaultPackMetadata() {
		return """
			{"pack":{"pack_format":34,"description":"PackForge fixture"},"overlays":{"entries":[{"formats":{"min_inclusive":34,"max_inclusive":34},"directory":"overlay"}]}}
			""".trim();
	}

	static Path createWithDuplicateEntry(Path path) throws IOException {
		try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(path)) {
			addDuplicate(output, "assets/minecraft/textures/duplicate.txt", "first");
			addDuplicate(output, "assets/minecraft/textures/duplicate.txt", "second");
			addDuplicate(output, "assets/minecraft/textures/other.txt", "other");
		}
		return path;
	}

	private static void addDuplicate(ZipArchiveOutputStream output, String name, String content) throws IOException {
		ZipArchiveEntry entry = new ZipArchiveEntry(name);
		entry.setTime(0L);
		output.putArchiveEntry(entry);
		output.write(content.getBytes(StandardCharsets.UTF_8));
		output.closeArchiveEntry();
	}

	private static void addDirectory(ZipOutputStream output, String name) throws IOException {
		ZipEntry entry = new ZipEntry(name);
		entry.setTime(0L);
		output.putNextEntry(entry);
		output.closeEntry();
	}

	private static void add(ZipOutputStream output, String name, byte[] content, boolean stored) throws IOException {
		ZipEntry entry = new ZipEntry(name);
		entry.setTime(0L);
		if (stored) {
			CRC32 crc = new CRC32();
			crc.update(content);
			entry.setMethod(ZipEntry.STORED);
			entry.setSize(content.length);
			entry.setCompressedSize(content.length);
			entry.setCrc(crc.getValue());
		}
		output.putNextEntry(entry);
		output.write(content);
		output.closeEntry();
	}

	private static byte[] png(int width, int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int alpha = 0xff;
				int red = (x * 31 + y * 17) & 0xff;
				int green = (x * 13 + y * 29) & 0xff;
				int blue = (x * 7 + y * 11) & 0xff;
				image.setRGB(x, y, alpha << 24 | red << 16 | green << 8 | blue);
			}
		}
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		if (!ImageIO.write(image, "png", output)) {
			throw new IOException("PNG writer is unavailable");
		}
		return output.toByteArray();
	}

	private DeterministicZipFixture() {}
}
