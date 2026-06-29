package com.jlxc.scrcpyclient.adb;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

final class AdbPacket {
    static final int A_SYNC = 0x434e5953;
    static final int A_CNXN = 0x4e584e43;
    static final int A_OPEN = 0x4e45504f;
    static final int A_OKAY = 0x59414b4f;
    static final int A_CLSE = 0x45534c43;
    static final int A_WRTE = 0x45545257;
    static final int A_AUTH = 0x48545541;

    static final int ADB_VERSION = 0x01000000;
    static final int MAX_DATA = 256 * 1024;

    static final int AUTH_TOKEN = 1;
    static final int AUTH_SIGNATURE = 2;
    static final int AUTH_RSAPUBLICKEY = 3;

    final int command;
    final int arg0;
    final int arg1;
    final byte[] payload;

    AdbPacket(int command, int arg0, int arg1, byte[] payload) {
        this.command = command;
        this.arg0 = arg0;
        this.arg1 = arg1;
        this.payload = payload == null ? new byte[0] : payload;
    }

    static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    static String commandToString(int command) {
        byte[] b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(command).array();
        return new String(b, StandardCharsets.US_ASCII);
    }

    void writeTo(OutputStream out) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(command);
        header.putInt(arg0);
        header.putInt(arg1);
        header.putInt(payload.length);
        header.putInt(checksum(payload));
        header.putInt(command ^ 0xffffffff);
        out.write(header.array());
        if (payload.length > 0) out.write(payload);
        out.flush();
    }

    static AdbPacket readFrom(InputStream in) throws IOException {
        byte[] headerBytes = readExactly(in, 24);
        ByteBuffer header = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN);
        int command = header.getInt();
        int arg0 = header.getInt();
        int arg1 = header.getInt();
        int len = header.getInt();
        int checksum = header.getInt();
        int magic = header.getInt();
        if ((command ^ 0xffffffff) != magic) {
            throw new IOException("Bad ADB packet magic for " + commandToString(command));
        }
        if (len < 0 || len > MAX_DATA * 4) {
            throw new IOException("Bad ADB payload length: " + len);
        }
        byte[] payload = readExactly(in, len);
        if (checksum(payload) != checksum) {
            throw new IOException("Bad ADB payload checksum");
        }
        return new AdbPacket(command, arg0, arg1, payload);
    }

    static byte[] readExactly(InputStream in, int len) throws IOException {
        byte[] data = new byte[len];
        int off = 0;
        while (off < len) {
            int r = in.read(data, off, len - off);
            if (r < 0) throw new EOFException("Unexpected EOF");
            off += r;
        }
        return data;
    }

    private static int checksum(byte[] payload) {
        int sum = 0;
        for (byte b : payload) sum += (b & 0xff);
        return sum;
    }
}
