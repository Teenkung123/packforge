package com.teenkung.packforge.loader;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RuntimeResourceHashReporterTest {
	@Test
	void hashIsStableAcrossEnumerationOrderAndSensitiveToResolvedContent() throws Exception {
		Map<String, InputStreamSupplier> first = new LinkedHashMap<>();
		first.put("example:z", bytes("last"));
		first.put("example:a", bytes("first"));

		Map<String, InputStreamSupplier> reversed = new LinkedHashMap<>();
		reversed.put("example:a", bytes("first"));
		reversed.put("example:z", bytes("last"));

		var firstHash = RuntimeResourceHashReporter.hash(first);
		var reversedHash = RuntimeResourceHashReporter.hash(reversed);
		assertEquals(firstHash, reversedHash);
		assertEquals(2, firstHash.entries());

		reversed.put("example:z", bytes("changed winner"));
		var changedHash = RuntimeResourceHashReporter.hash(reversed);
		assertNotEquals(firstHash.sha256(), changedHash.sha256());
	}

	private static InputStreamSupplier bytes(String value) {
		byte[] data = value.getBytes(StandardCharsets.UTF_8);
		return () -> new ByteArrayInputStream(data);
	}
}
