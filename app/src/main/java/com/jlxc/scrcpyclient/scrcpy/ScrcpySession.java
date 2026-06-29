package com.jlxc.scrcpyclient.scrcpy;

import android.content.Context;
import android.view.Surface;

import com.jlxc.scrcpyclient.adb.AdbConnection;
import com.jlxc.scrcpyclient.adb.AdbStream;
import com.jlxc.scrcpyclient.adb.SyncService;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

public final class ScrcpySession implements AutoCloseable {
    public interface Listener extends ScrcpyVideoDemuxer.Listener {
        void onConnected(ScrcpyControl control);
    }

    private static final String VERSION = "4.0";
    private static final String ASSET_SERVER = "scrcpy-server-v4.0.jar";
    private static final String REMOTE_SERVER = "/data/local/tmp/scrcpy-server-v4.0.jar";

    private final Context context;
    private final String host;
    private final int port;
    private final int maxSize;
    private final int bitRate;
    private final int maxFps;
    private final Surface surface;
    private final Listener listener;

    private AdbConnection adb;
    private AdbStream shellStream;
    private AdbStream videoStream;
    private AdbStream controlStream;
    private ScrcpyVideoDemuxer demuxer;
    private Thread demuxThread;
    private H264Decoder decoder;

    public ScrcpySession(Context context, String host, int port, int maxSize, int bitRate, int maxFps, Surface surface, Listener listener) {
        this.context = context.getApplicationContext();
        this.host = host;
        this.port = port;
        this.maxSize = maxSize;
        this.bitRate = bitRate;
        this.maxFps = maxFps;
        this.surface = surface;
        this.listener = listener;
    }

    public void startAsync() {
        new Thread(() -> {
            try { startBlocking(); }
            catch (Exception e) { listener.onLog("start failed: " + e.getMessage()); listener.onStopped(e); }
        }, "scrcpy-session-start").start();
    }

    private void startBlocking() throws Exception {
        File key = new File(context.getFilesDir(), "adbkey.pk8");
        listener.onLog("connecting ADB " + host + ":" + port);
        adb = AdbConnection.connect(host, port, key, listener::onLog);

        byte[] server = readAsset(ASSET_SERVER);
        listener.onLog("pushing scrcpy-server " + server.length + " bytes");
        SyncService.push(adb, server, REMOTE_SERVER, 0100755);

        String scid = String.format("%08x", new SecureRandom().nextInt() & 0x7fffffff);
        String socketName = "scrcpy_" + scid;
        String cmd = buildServerCommand(scid);
        listener.onLog("starting scrcpy-server scid=" + scid);
        shellStream = adb.open("shell:" + cmd);
        drainShell(shellStream);

        // Give app_process a short moment to bind localabstract:scrcpy_<scid>.
        Thread.sleep(350);

        listener.onLog("opening video socket localabstract:" + socketName);
        videoStream = adb.open("localabstract:" + socketName);
        listener.onLog("opening control socket localabstract:" + socketName);
        controlStream = adb.open("localabstract:" + socketName);

        ScrcpyControl control = new ScrcpyControl(controlStream.outputStream());
        listener.onConnected(control);

        decoder = new H264Decoder(surface);
        demuxer = new ScrcpyVideoDemuxer(videoStream.inputStream(), decoder, listener);
        demuxThread = new Thread(demuxer, "scrcpy-video-demuxer");
        demuxThread.start();
    }

    private String buildServerCommand(String scid) {
        // v4.0 server options. Audio is off for the first Android-client build to keep decoder latency low.
        return "CLASSPATH=" + REMOTE_SERVER + " app_process / com.genymobile.scrcpy.Server " + VERSION
                + " scid=" + scid
                + " log_level=info"
                + " video=true audio=false control=true cleanup=false"
                + " tunnel_forward=true send_dummy_byte=false"
                + " send_device_meta=true send_stream_meta=true send_frame_meta=true"
                + " video_codec=h264 max_size=" + maxSize
                + " video_bit_rate=" + bitRate
                + " max_fps=" + maxFps;
    }

    private byte[] readAsset(String name) throws Exception {
        try (InputStream in = context.getAssets().open(name)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) >= 0) bos.write(buf, 0, r);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("assets/" + name + " not found. Put the official scrcpy 4.0 server there first.", e);
        }
    }

    private void drainShell(AdbStream stream) {
        new Thread(() -> {
            try {
                InputStream in = stream.inputStream();
                byte[] buf = new byte[4096];
                int r;
                while ((r = in.read(buf)) >= 0) {
                    String s = new String(buf, 0, r, StandardCharsets.UTF_8).trim();
                    if (!s.isEmpty()) listener.onLog("server: " + s);
                }
            } catch (Exception e) {
                listener.onLog("server shell ended: " + e.getMessage());
            }
        }, "scrcpy-server-log").start();
    }

    public int videoWidth() { return decoder == null ? 0 : decoder.width(); }
    public int videoHeight() { return decoder == null ? 0 : decoder.height(); }

    @Override public void close() {
        if (demuxer != null) demuxer.close();
        if (controlStream != null) controlStream.close();
        if (videoStream != null) videoStream.close();
        if (shellStream != null) shellStream.close();
        if (adb != null) adb.close();
    }
}
