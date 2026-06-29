package com.jlxc.scrcpyclient.scrcpy;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;

import java.io.Closeable;
import java.nio.ByteBuffer;

public final class H264Decoder implements Closeable {
    private MediaCodec codec;
    private int width;
    private int height;
    private final Surface surface;

    public H264Decoder(Surface surface) {
        this.surface = surface;
    }

    public synchronized void configure(int width, int height) throws Exception {
        if (codec != null && this.width == width && this.height == height) return;
        close();
        this.width = width;
        this.height = height;
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        if (Build.VERSION.SDK_INT >= 30) {
            format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
        }
        codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        codec.configure(format, surface, null, 0);
        codec.start();
    }

    public synchronized void queue(byte[] data, long ptsUs, boolean keyFrame) throws Exception {
        if (codec == null) return;
        int input = codec.dequeueInputBuffer(10_000);
        if (input >= 0) {
            ByteBuffer b = codec.getInputBuffer(input);
            if (b != null) {
                b.clear();
                b.put(data);
                int flags = keyFrame ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                codec.queueInputBuffer(input, 0, data.length, Math.max(0, ptsUs), flags);
            }
        }
        drain();
    }

    private void drain() {
        if (codec == null) return;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        for (;;) {
            int out = codec.dequeueOutputBuffer(info, 0);
            if (out >= 0) {
                codec.releaseOutputBuffer(out, true);
            } else if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // ignore
            } else {
                break;
            }
        }
    }

    public int width() { return width; }
    public int height() { return height; }

    @Override public synchronized void close() {
        if (codec != null) {
            try { codec.stop(); } catch (Exception ignored) {}
            try { codec.release(); } catch (Exception ignored) {}
            codec = null;
        }
    }
}
