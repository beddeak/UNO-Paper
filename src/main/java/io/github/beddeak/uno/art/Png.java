package io.github.beddeak.uno.art;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public final class Png {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private Png() {
    }

    public static byte[] encode(int width, int height, int[] argb) {
        Deflater deflater = new Deflater(9);
        try {
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (DeflaterOutputStream zlib = new DeflaterOutputStream(compressed, deflater)) {
                byte[] row = new byte[1 + width * 4];
                for (int y = 0; y < height; y++) {
                    int i = 1;
                    for (int x = 0; x < width; x++) {
                        int pixel = argb[y * width + x];
                        row[i++] = (byte) (pixel >>> 16);
                        row[i++] = (byte) (pixel >>> 8);
                        row[i++] = (byte) pixel;
                        row[i++] = (byte) (pixel >>> 24);
                    }
                    zlib.write(row);
                }
            }
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            DataOutputStream ihdr = new DataOutputStream(header);
            ihdr.writeInt(width);
            ihdr.writeInt(height);
            ihdr.writeByte(8);
            ihdr.writeByte(6);
            ihdr.writeByte(0);
            ihdr.writeByte(0);
            ihdr.writeByte(0);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(SIGNATURE);
            DataOutputStream data = new DataOutputStream(out);
            chunk(data, "IHDR", header.toByteArray());
            chunk(data, "IDAT", compressed.toByteArray());
            chunk(data, "IEND", new byte[0]);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deflater.end();
        }
    }

    private static void chunk(DataOutputStream out, String type, byte[] body) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(body);
        out.writeInt(body.length);
        out.write(typeBytes);
        out.write(body);
        out.writeInt((int) crc.getValue());
    }
}
