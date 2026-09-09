package com.teenkung.packforge.verification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Properties;
import java.util.TreeSet;

/** Frozen class-name parity, independent of mod version and class-file bytecode. */
record ClassInventory(int count, String sha256, String addition) {
    private static final Properties ORACLE = load();

    static ClassInventory expected(String key) {
        String value = ORACLE.getProperty(key);
        if (value == null) throw new IllegalStateException("Missing class inventory oracle: " + key);
        String[] fields = value.split(",", -1);
        return new ClassInventory(Integer.parseInt(fields[0]), fields[1], fields[2]);
    }

    static ClassInventory of(Collection<String> entries) {
        var classes = new TreeSet<String>();
        entries.stream().filter(n -> n.endsWith(".class")).forEach(classes::add);
        try {
            byte[] bytes = (String.join("\n", classes) + "\n").getBytes(StandardCharsets.UTF_8);
            return new ClassInventory(classes.size(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), "");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    void verify(Collection<String> entries) {
        var normalized = new TreeSet<>(entries);
        if (!addition.isEmpty() && !normalized.remove(addition)) {
            throw new IllegalStateException("Missing required compiler addition: " + addition);
        }
        ClassInventory actual = of(normalized);
        if (count != actual.count || !sha256.equals(actual.sha256)) {
            throw new IllegalStateException("Class inventory mismatch: expected " + count + "/" + sha256
                    + ", actual " + actual.count + "/" + actual.sha256);
        }
    }

    private static Properties load() {
        Properties result = new Properties();
        try (var input = ClassInventory.class.getResourceAsStream("/class-inventories.properties")) {
            if (input == null) throw new IllegalStateException("Missing class inventory oracle resource");
            result.load(input);
            return result;
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read class inventory oracle", failure);
        }
    }
}
