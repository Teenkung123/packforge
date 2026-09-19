package com.teenkung.packforge.loader;

import com.teenkung.packforge.config.ReloadFeatureSnapshot;
import com.teenkung.packforge.config.OptimizationPlan;
import com.teenkung.packforge.config.PackForgeConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ZipResourceReadReuseTest {
	@TempDir Path directory;
	private ReloadExecutionContext context;

	@AfterEach void retire() { ReloadExecutionContext.finish(context); }

	@Test void positiveCertificateAllowsRepeatsAndSeparatesOverlayPaths() throws Exception {
		context = ReloadExecutionContext.startForTesting(snapshot(true));
		try (ZipFile zip = new ZipFile(archive("pack.zip", "base", "overlay").toFile())) {
			ZipResourceReadReuse.register(context, PackIndex.build(zip));
			AtomicInteger opens = new AtomicInteger();
			for (int i = 0; i < 5; i++) {
				ZipEntry resolved = zip.getEntry("assets/example/font/a.json");
				assertEquals("base", read(ZipResourceReadReuse.open(context, zip, resolved, () -> {
					opens.incrementAndGet(); return zip.getInputStream(resolved);
				})));
			}
			assertEquals(2, opens.get());
			ZipEntry overlay = zip.getEntry("overlay/assets/example/font/a.json");
			assertEquals("overlay", read(ZipResourceReadReuse.open(context, zip, overlay, () -> zip.getInputStream(overlay))));
		}
	}

	@Test void unknownArchiveAndUnknownPathAreNotCertifiedByAbsenceOfDuplicates() throws Exception {
		context = ReloadExecutionContext.startForTesting(snapshot(true));
		try (ZipFile zip = new ZipFile(archive("pack.zip", "base", "overlay").toFile())) {
			AtomicInteger opens = new AtomicInteger();
			ZipEntry existing = zip.getEntry("assets/example/font/a.json");
			for (int i = 0; i < 3; i++) read(ZipResourceReadReuse.open(context, zip, existing, () -> {
				opens.incrementAndGet(); return zip.getInputStream(existing);
			}));
			assertEquals(3, opens.get(), "An unregistered handle has no uniqueness proof");
			assertEquals(0, context.preparationBudget().used());
			ZipResourceReadReuse.register(context, PackIndex.build(zip));
			ZipEntry unknown = new ZipEntry("not-in-index.json"); unknown.setSize(3);
			for (int i = 0; i < 3; i++) assertOriginal(zip, unknown);
		}
	}

	@Test void certificatesRemainSpecificToHandleAndReloadAndInvalidation() throws Exception {
		context = ReloadExecutionContext.startForTesting(snapshot(true));
		Path path = archive("pack.zip", "base", "overlay");
		try (ZipFile first = new ZipFile(path.toFile()); ZipFile second = new ZipFile(path.toFile())) {
			PackIndex index = PackIndex.build(first);
			ZipResourceReadReuse.register(context, index);
			assertOriginal(second, second.getEntry("assets/example/font/a.json"));
			index.invalidateCaches();
			assertOriginal(first, first.getEntry("assets/example/font/a.json"));
			ZipResourceReadReuse.register(context, PackIndex.build(first));
			ReloadExecutionContext previous = context;
			context = ReloadExecutionContext.startForTesting(snapshot(true));
			assertEquals(0, previous.preparationBudget().used());
			assertOriginal(first, first.getEntry("assets/example/font/a.json"));
		}
	}

	@Test void disabledUnknownSizeOversizeBinaryAndCustomEntriesBypass() throws Exception {
		context = ReloadExecutionContext.startForTesting(snapshot(true));
		Path path = archive("pack.zip", "base", "overlay");
		try (ZipFile zip = new ZipFile(path.toFile())) {
			ZipResourceReadReuse.register(context, PackIndex.build(zip));
			for (long length : new long[]{-1, ReloadReadCache.MAX_PAYLOAD + 1L}) {
				ZipEntry entry = new ZipEntry("assets/example/font/a.json");
				if (length >= 0) entry.setSize(length);
				assertOriginal(zip, entry);
			}
			ZipEntry binary = new ZipEntry("image.png"); binary.setSize(3); assertOriginal(zip, binary);
			ZipEntry custom = new ZipEntry("assets/example/font/a.json") {}; custom.setSize(4);
			assertOriginal(zip, custom);
			context = ReloadExecutionContext.startForTesting(snapshot(false));
			ZipResourceReadReuse.register(context, PackIndex.build(zip));
			assertOriginal(zip, zip.getEntry("assets/example/font/a.json"));
		}
		context = ReloadExecutionContext.startForTesting(snapshot(true));
		try (ZipFile custom = new CustomZip(path)) {
			ZipResourceReadReuse.register(context, PackIndex.build(custom));
			assertOriginal(custom, custom.getEntry("assets/example/font/a.json"));
			assertEquals(0, context.preparationBudget().used());
		}
	}

	@Test void boundedRegistryAdmissionNeverTurnsUnknownIntoCertified() throws Exception {
		context = ReloadExecutionContext.startForTesting(snapshot(true));
		var scope = context.preparationBudget();
		var occupied = scope.tryReserve(128L * 1024 * 1024 - 700);
		assertNotNull(occupied);
		try (ZipFile zip = new ZipFile(archive("pack.zip", "base", "overlay").toFile())) {
			long before = scope.used();
			ZipResourceReadReuse.register(context, PackIndex.build(zip));
			assertTrue(scope.used() <= before + 700);
			assertOriginal(zip, zip.getEntry("assets/example/font/a.json"));
			ReloadExecutionContext.finish(context);
			assertEquals(before, scope.used(), "Retirement clears admitted registry bookkeeping");
		} finally { occupied.close(); }
		assertEquals(0, scope.used());
	}

	@Test void cachedBytesDoNotReopenClosedZipAndBorrowedStreamsOutliveRetirement() throws Exception {
		context = ReloadExecutionContext.startForTesting(snapshot(true));
		ZipFile zip = new ZipFile(archive("pack.zip", "base", "overlay").toFile());
		try {
			ZipResourceReadReuse.register(context, PackIndex.build(zip));
			ZipEntry entry = zip.getEntry("assets/example/font/a.json");
			assertEquals("base", read(ZipResourceReadReuse.open(context, zip, entry, () -> zip.getInputStream(entry))));
			InputStream borrowed = ZipResourceReadReuse.open(context, zip, entry, () -> zip.getInputStream(entry));
			zip.close();
			assertThrows(IllegalStateException.class, () -> ZipResourceReadReuse.open(context, zip, entry, () -> zip.getInputStream(entry)));
			ReloadExecutionContext.finish(context);
			assertTrue(context.preparationBudget().used() > 0);
			assertEquals("base", read(borrowed));
			assertEquals(0, context.preparationBudget().used());
		} finally { zip.close(); }
	}

	@Test void duplicateReadOfSameEntryStillFollowsInterleavedJavaLookupState() throws Exception {
		context = ReloadExecutionContext.startForTesting(snapshot(true));
		try (ZipFile zip = new ZipFile(duplicateArchive().toFile())) {
			PackIndex index = PackIndex.build(zip);
			assertTrue(index.hasDuplicatePath("aaaa.json"));
			ZipResourceReadReuse.register(context, index);
			ZipEntry sameEntry = zip.getEntry("aaaa.json");
			AtomicInteger opens = new AtomicInteger();
			for (int ordinal : new int[]{0, 1, 0, 1, 0}) {
				Enumeration<? extends ZipEntry> entries = zip.entries();
				for (int i = 0; i <= ordinal; i++) entries.nextElement();
				String expected = read(zip.getInputStream(sameEntry));
				assertEquals(ordinal == 0 ? "aaaa.json" : "bbbb.json", expected,
					"The fixture demonstrates why ZipEntry identity cannot prove immutable bytes");
				assertEquals(expected, read(ZipResourceReadReuse.open(context, zip, sameEntry, () -> {
					opens.incrementAndGet(); return zip.getInputStream(sameEntry);
				})));
			}
			assertEquals(5, opens.get(), "Every duplicate read must preserve the original stream operation");
		}
	}

	private void assertOriginal(ZipFile zip, ZipEntry entry) throws IOException {
		for (int i = 0; i < 3; i++) {
			InputStream original = InputStream.nullInputStream();
			assertSame(original, ZipResourceReadReuse.open(context, zip, entry, () -> original));
			original.close();
		}
	}

	private Path archive(String name, String base, String overlay) throws IOException {
		Path path = directory.resolve(name);
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
			out.putNextEntry(new ZipEntry("assets/example/font/a.json"));
			out.write(base.getBytes(StandardCharsets.UTF_8)); out.closeEntry();
			out.putNextEntry(new ZipEntry("overlay/assets/example/font/a.json"));
			out.write(overlay.getBytes(StandardCharsets.UTF_8)); out.closeEntry();
		}
		return path;
	}

	private Path duplicateArchive() throws IOException {
		Path path = directory.resolve("duplicates.zip");
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
			for (String name : new String[]{"aaaa.json", "bbbb.json"}) {
				out.putNextEntry(new ZipEntry(name));
				out.write(name.getBytes(StandardCharsets.UTF_8)); out.closeEntry();
			}
		}
		byte[] fixture = Files.readAllBytes(path);
		byte[] from = "bbbb.json".getBytes(StandardCharsets.UTF_8);
		byte[] to = "aaaa.json".getBytes(StandardCharsets.UTF_8);
		// Equal-length replacement constructs duplicate local/central names in a synthetic fixture.
		for (int offset = 0; offset <= fixture.length - from.length; offset++) {
			boolean match = true;
			for (int i = 0; i < from.length; i++) if (fixture[offset + i] != from[i]) { match = false; break; }
			if (match) System.arraycopy(to, 0, fixture, offset, to.length);
		}
		Files.write(path, fixture);
		return path;
	}

	private static String read(InputStream input) throws IOException {
		try (input) { return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
	}

	private static ReloadFeatureSnapshot snapshot(boolean enabled) {
		return new ReloadFeatureSnapshot(false, false, false, false, false, false, false, false, false,
			false, false, false, false, 1, false, false, false, false, false, false, false, false, 1,
			false, 1, false, 1, Set.of(), false, 1, false, false, false, false, false, false, false,
			0, Thread.NORM_PRIORITY, false, false, false, false, false, 1, enabled, 128, OptimizationPlan.capture(PackForgeConfig.get()));
	}

	private static final class CustomZip extends ZipFile {
		private CustomZip(Path path) throws IOException { super(path.toFile()); }
	}
}
