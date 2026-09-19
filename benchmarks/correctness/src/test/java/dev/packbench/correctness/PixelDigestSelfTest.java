package dev.packbench.correctness;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

/** Standalone reference tests; does not require Minecraft or a graphics context. */
public final class PixelDigestSelfTest {
    public static void main(String[] args) throws Exception {
        int[] pixels = {0x00abcdef, 0xff123456, 0x7f000001, 0x01020304};
        byte[] reference = {0, (byte) 0xab, (byte) 0xcd, (byte) 0xef,
            (byte) 0xff, 0x12, 0x34, 0x56, 0x7f, 0, 0, 1, 1, 2, 3, 4};
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(reference));
        if (!expected.equals(PixelDigest.hash(2, 2, (x, y) -> pixels[y * 2 + x]))) {
            throw new AssertionError("ARGB order/transparency differs");
        }
        int width = 1025;
        AtomicInteger reads = new AtomicInteger();
        String chunked = PixelDigest.hash(width, 2, (x, y) -> { reads.incrementAndGet(); return 0; });
        expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[width * 2 * 4]));
        if (!expected.equals(chunked) || reads.get() != width * 2) throw new AssertionError("Chunk boundary/read count differs");
        try {
            PixelDigest.hash(0, 1, (x, y) -> 0);
            throw new AssertionError("Invalid dimensions accepted");
        } catch (IllegalArgumentException expectedFailure) {
            // Expected failure, before attempting any pixel access.
        }
        System.out.println("PixelDigestSelfTest passed");
    }
}
