package com.jlxc.scrcpyclient.adb;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class AdbStream {
    private final AdbConnection connection;
    private final int localId;
    private volatile int remoteId;
    private volatile boolean closed;
    private final CountDownLatch ready = new CountDownLatch(1);
    private final BlockingQueue<byte[]> incoming = new ArrayBlockingQueue<>(256);

    private byte[] current;
    private int currentOffset;

    AdbStream(AdbConnection connection, int localId) {
        this.connection = connection;
        this.localId = localId;
    }

    int localId() { return localId; }
    int remoteId() { return remoteId; }

    void onOkay(int remoteId) {
        this.remoteId = remoteId;
        ready.countDown();
    }

    void onWrite(byte[] data) {
        if (!closed) incoming.offer(data);
    }

    void onClose() {
        closed = true;
        ready.countDown();
        incoming.offer(new byte[0]);
    }

    void awaitReady(long timeoutMs) throws IOException {
        try {
            if (!ready.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new IOException("ADB stream open timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        if (closed) throw new IOException("ADB stream closed while opening");
    }

    public InputStream inputStream() {
        return new InputStream() {
            @Override public int read() throws IOException {
                byte[] one = new byte[1];
                int r = read(one, 0, 1);
                return r <= 0 ? -1 : (one[0] & 0xff);
            }

            @Override public int read(byte[] b, int off, int len) throws IOException {
                if (len == 0) return 0;
                while (current == null || currentOffset >= current.length) {
                    if (closed && incoming.isEmpty()) return -1;
                    try {
                        current = incoming.take();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException(e);
                    }
                    currentOffset = 0;
                    if (current.length == 0 && closed) return -1;
                }
                int n = Math.min(len, current.length - currentOffset);
                System.arraycopy(current, currentOffset, b, off, n);
                currentOffset += n;
                return n;
            }
        };
    }

    public OutputStream outputStream() {
        return new OutputStream() {
            @Override public void write(int b) throws IOException {
                write(new byte[]{(byte) b});
            }

            @Override public void write(byte[] b, int off, int len) throws IOException {
                if (closed) throw new IOException("ADB stream is closed");
                int pos = off;
                while (len > 0) {
                    int chunk = Math.min(len, AdbPacket.MAX_DATA);
                    byte[] payload = Arrays.copyOfRange(b, pos, pos + chunk);
                    connection.sendWrite(localId, remoteId, payload);
                    pos += chunk;
                    len -= chunk;
                }
            }
        };
    }

    public void close() {
        closed = true;
        connection.closeStream(localId, remoteId);
    }
}
