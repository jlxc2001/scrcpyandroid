package com.jlxc.scrcpyclient.scrcpy;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class ScrcpyVideoDemuxer implements Closeable, Runnable {
    public interface Listener {
        void onDeviceName(String name);
        void onVideoSize(int width, int height);
        void onLog(String line);
        void onStopped(Exception e);
    }

    private static final int CODEC_H264 = 0x68323634;
    private static final long FLAG_CONFIG = 1L << 62;
    private static final long FLAG_KEY = 1L << 61;
    private static final long PTS_MASK = FLAG_KEY - 1;

    private final InputStream in;
    private final H264Decoder decoder;
    private final Listener listener;
    private volatile boolean closed;
    private byte[] pendingConfig;

    public ScrcpyVideoDemuxer(InputStream in, H264Decoder decoder, Listener listener) {
        this.in = in;
        this.decoder = decoder;
        this.listener = listener;
    }

    @Override public void run() {
        Exception error = null;
        try {
            // scrcpy v4.0 currently sends the device name as fixed 64-byte metadata on the first socket.
            byte[] deviceNameBytes = Binary.readExactly(in, 64);
            int end = 0;
            while (end < deviceNameBytes.length && deviceNameBytes[end] != 0) end++;
            listener.onDeviceName(new String(deviceNameBytes, 0, end, StandardCharsets.UTF_8));

            int codecId = Binary.i32be(Binary.readExactly(in, 4), 0);
            if (codecId != CODEC_H264) {
                throw new IllegalStateException("Only H.264 is implemented in this Android client, codecId=0x" + Integer.toHexString(codecId));
            }

            for (;;) {
                if (closed) return;
                byte[] header = Binary.readExactly(in, 12);
                if ((header[0] & 0x80) != 0) {
                    int width = Binary.i32be(header, 4);
                    int height = Binary.i32be(header, 8);
                    listener.onVideoSize(width, height);
                    decoder.configure(width, height);
                    continue;
                }
                long ptsFlags = Binary.i64be(header, 0);
                int len = Binary.i32be(header, 8);
                if (len <= 0 || len > 4 * 1024 * 1024) {
                    throw new IllegalStateException("Bad scrcpy packet length: " + len);
                }
                byte[] payload = Binary.readExactly(in, len);
                boolean config = (ptsFlags & FLAG_CONFIG) != 0;
                boolean key = (ptsFlags & FLAG_KEY) != 0;
                long pts = ptsFlags & PTS_MASK;
                if (config) {
                    pendingConfig = concat(pendingConfig, payload);
                } else {
                    if (pendingConfig != null) {
                        payload = concat(pendingConfig, payload);
                        pendingConfig = null;
                    }
                    decoder.queue(payload, pts / 1000L, key);
                }
            }
        } catch (Exception e) {
            error = e;
            listener.onLog("video stopped: " + e.getMessage());
        } finally {
            listener.onStopped(error);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        if (a == null || a.length == 0) return b;
        ByteArrayOutputStream bos = new ByteArrayOutputStream(a.length + b.length);
        bos.write(a, 0, a.length);
        bos.write(b, 0, b.length);
        return bos.toByteArray();
    }

    @Override public void close() {
        closed = true;
        try { in.close(); } catch (Exception ignored) {}
        decoder.close();
    }
}
