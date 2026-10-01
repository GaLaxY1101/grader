package ua.kpi.grader.testgen.sandbox;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Builds a minimal uncompressed POSIX (ustar) tar archive in memory.
 *
 * <p>Used to stream source files into sandbox containers over stdin, so no host
 * directory has to be shared with the container. This keeps the sandbox working when
 * the backend itself runs inside a container that talks to the host Docker daemon.
 */
final class TarArchive {

    private static final int BLOCK = 512;

    private TarArchive() {
    }

    /**
     * @param files file name (ASCII, max 100 chars, no directories) → UTF-8 content
     * @return tar archive bytes
     */
    static byte[] of(Map<String, String> files) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long mtime = System.currentTimeMillis() / 1000;
        files.forEach((name, content) -> {
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            out.writeBytes(header(name, data.length, mtime));
            out.writeBytes(data);
            out.writeBytes(new byte[padding(data.length)]);
        });
        out.writeBytes(new byte[BLOCK * 2]);
        return out.toByteArray();
    }

    private static byte[] header(String name, long size, long mtime) {
        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        if (nameBytes.length > 100) {
            throw new IllegalArgumentException("Tar entry name too long: " + name);
        }
        byte[] header = new byte[BLOCK];
        put(header, 0, nameBytes);
        put(header, 100, octal(0644, 8));
        put(header, 108, octal(0, 8));
        put(header, 116, octal(0, 8));
        put(header, 124, octal(size, 12));
        put(header, 136, octal(mtime, 12));
        put(header, 148, "        ".getBytes(StandardCharsets.US_ASCII));
        header[156] = '0';
        put(header, 257, "ustar\0".getBytes(StandardCharsets.US_ASCII));
        put(header, 263, "00".getBytes(StandardCharsets.US_ASCII));

        long checksum = 0;
        for (byte b : header) {
            checksum += b & 0xFF;
        }
        byte[] checksumField = String.format("%06o", checksum).getBytes(StandardCharsets.US_ASCII);
        put(header, 148, checksumField);
        header[154] = 0;
        header[155] = ' ';
        return header;
    }

    /** Zero-padded octal number followed by a NUL, filling {@code width} bytes. */
    private static byte[] octal(long value, int width) {
        String digits = String.format("%0" + (width - 1) + "o", value);
        return (digits + "\0").getBytes(StandardCharsets.US_ASCII);
    }

    private static void put(byte[] target, int offset, byte[] source) {
        System.arraycopy(source, 0, target, offset, source.length);
    }

    private static int padding(int length) {
        int remainder = length % BLOCK;
        return remainder == 0 ? 0 : BLOCK - remainder;
    }
}
