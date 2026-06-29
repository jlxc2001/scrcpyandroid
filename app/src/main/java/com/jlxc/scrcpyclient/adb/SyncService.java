package com.jlxc.scrcpyclient.adb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class SyncService {
    private static final int CHUNK = 64 * 1024;

    private SyncService() {}

    public static void push(AdbConnection adb, byte[] data, String remotePath, int mode) throws IOException {
        AdbStream stream = adb.open("sync:");
        try {
            InputStream in = stream.inputStream();
            OutputStream out = stream.outputStream();
            String spec = remotePath + "," + mode;
            writeRequest(out, "SEND", spec.getBytes(StandardCharsets.UTF_8));
            int off = 0;
            while (off < data.length) {
                int n = Math.min(CHUNK, data.length - off);
                writeRequest(out, "DATA", slice(data, off, n));
                off += n;
            }
            writeIdAndLength(out, "DONE", (int) (System.currentTimeMillis() / 1000));
            readStatus(in);
        } finally {
            stream.close();
        }
    }

    private static byte[] slice(byte[] data, int off, int len) {
        byte[] out = new byte[len];
        System.arraycopy(data, off, out, 0, len);
        return out;
    }

    private static void writeRequest(OutputStream out, String id, byte[] payload) throws IOException {
        writeIdAndLength(out, id, payload.length);
        out.write(payload);
    }

    private static void writeIdAndLength(OutputStream out, String id, int len) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        b.put(id.getBytes(StandardCharsets.US_ASCII));
        b.putInt(len);
        out.write(b.array());
        out.flush();
    }

    private static void readStatus(InputStream in) throws IOException {
        byte[] id = AdbPacket.readExactly(in, 4);
        String status = new String(id, StandardCharsets.US_ASCII);
        if ("OKAY".equals(status)) return;
        if ("FAIL".equals(status)) {
            byte[] l = AdbPacket.readExactly(in, 4);
            int len = ByteBuffer.wrap(l).order(ByteOrder.LITTLE_ENDIAN).getInt();
            byte[] msg = AdbPacket.readExactly(in, len);
            throw new IOException("ADB sync FAIL: " + new String(msg, StandardCharsets.UTF_8));
        }
        throw new IOException("Unexpected ADB sync status: " + status);
    }

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (;;) {
            int r = in.read(buf);
            if (r < 0) break;
            bos.write(buf, 0, r);
        }
        return bos.toByteArray();
    }
}
