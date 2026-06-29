package com.jlxc.scrcpyclient.scrcpy;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public final class Binary {
    private Binary() {}

    public static byte[] readExactly(InputStream in, int len) throws IOException {
        byte[] b = new byte[len];
        int off = 0;
        while (off < len) {
            int r = in.read(b, off, len - off);
            if (r < 0) throw new EOFException("stream closed");
            off += r;
        }
        return b;
    }

    public static int u16be(byte[] b, int off) {
        return ((b[off] & 0xff) << 8) | (b[off + 1] & 0xff);
    }

    public static int i32be(byte[] b, int off) {
        return ((b[off] & 0xff) << 24)
                | ((b[off + 1] & 0xff) << 16)
                | ((b[off + 2] & 0xff) << 8)
                | (b[off + 3] & 0xff);
    }

    public static long i64be(byte[] b, int off) {
        long v = 0;
        for (int i = 0; i < 8; i++) v = (v << 8) | (b[off + i] & 0xffL);
        return v;
    }

    public static void put16be(byte[] b, int off, int v) {
        b[off] = (byte) (v >>> 8);
        b[off + 1] = (byte) v;
    }

    public static void put32be(byte[] b, int off, int v) {
        b[off] = (byte) (v >>> 24);
        b[off + 1] = (byte) (v >>> 16);
        b[off + 2] = (byte) (v >>> 8);
        b[off + 3] = (byte) v;
    }

    public static void put64be(byte[] b, int off, long v) {
        for (int i = 7; i >= 0; i--) {
            b[off + i] = (byte) v;
            v >>>= 8;
        }
    }
}
