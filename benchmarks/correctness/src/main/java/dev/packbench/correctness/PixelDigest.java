package dev.packbench.correctness;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Canonical row-major ARGB bytes, independent of native memory endianness. */
public final class PixelDigest {
    public static String hash(int width, int height, PixelReader pixels) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Non-positive image dimensions");
        final MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 unavailable", failure); }
        byte[] chunk = new byte[4096];
        int offset = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels.get(x, y);
                chunk[offset++] = (byte) (pixel >>> 24);
                chunk[offset++] = (byte) (pixel >>> 16);
                chunk[offset++] = (byte) (pixel >>> 8);
                chunk[offset++] = (byte) pixel;
                if (offset == chunk.length) {
                    digest.update(chunk);
                    offset = 0;
                }
            }
        }
        digest.update(chunk, 0, offset);
        return HexFormat.of().formatHex(digest.digest());
    }

    @FunctionalInterface public interface PixelReader { int get(int x, int y); }
    private PixelDigest() {}
}
