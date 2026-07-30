package com.teenkung.packforge.client.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AsyncDiagnosticCsvTest {
	@TempDir Path directory;

	@Test
	void writesSnapshotWithOneHeader() throws Exception {
		Path file = directory.resolve("diagnostics.csv");
		AsyncDiagnosticCsv.write(file, "header", List.of("first"));
		AsyncDiagnosticCsv.write(file, "header", List.of("second"));
		assertEquals(List.of("header", "first", "second"), Files.readAllLines(file));
	}

	@Test
	void ioFailureIsContained() {
		Path directoryAsFile = directory.resolve("not-a-file");
		assertDoesNotThrow(() -> AsyncDiagnosticCsv.write(directoryAsFile, "header", List.of("row")));
		assertDoesNotThrow(() -> AsyncDiagnosticCsv.write(directoryAsFile.resolve("child.csv"), "header", List.of("row")));
	}
}
