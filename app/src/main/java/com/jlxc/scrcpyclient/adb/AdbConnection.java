package com.jlxc.scrcpyclient.adb;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class AdbConnection implements Closeable {
    public interface LogSink { void log(String line); }

    private final Socket socket;
    private final java.io.InputStream in;
    private final java.io.OutputStream out;
    private final Map<Integer, AdbStream> streams = new ConcurrentHashMap<>();
    private final AtomicInteger nextLocalId = new AtomicInteger(1);
    private volatile boolean closed;
    private Thread readerThread;
    private final LogSink log;

    private AdbConnection(Socket socket, LogSink log) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
        this.log = log;
    }

    public static AdbConnection connect(String host, int port, File keyFile, LogSink log) throws Exception {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 8000);
        socket.setTcpNoDelay(true);
        AdbConnection c = new AdbConnection(socket, log);
        AdbKey key = AdbKey.loadOrCreate(keyFile);
        c.handshake(key);
        c.startReader();
        return c;
    }

    private void handshake(AdbKey key) throws Exception {
        byte[] cnxn = "host::features=shell_v2,cmd,stat_v2,fixed_push_mkdir".getBytes(StandardCharsets.UTF_8);
        send(new AdbPacket(AdbPacket.A_CNXN, AdbPacket.ADB_VERSION, AdbPacket.MAX_DATA, cnxn));
        boolean sentPublicKey = false;
        boolean signedOnce = false;
        long deadline = System.currentTimeMillis() + 90_000;
        for (;;) {
            if (System.currentTimeMillis() > deadline) {
                throw new IOException("ADB auth timeout; check the target device authorization dialog");
            }
            AdbPacket p = AdbPacket.readFrom(in);
            if (p.command == AdbPacket.A_CNXN) {
                log("ADB connected");
                return;
            }
            if (p.command == AdbPacket.A_AUTH && p.arg0 == AdbPacket.AUTH_TOKEN) {
                if (!signedOnce) {
                    log("ADB auth: signing token");
                    signedOnce = true;
                    send(new AdbPacket(AdbPacket.A_AUTH, AdbPacket.AUTH_SIGNATURE, 0, key.sign(p.payload)));
                } else if (!sentPublicKey) {
                    log("ADB auth: sending public key; accept the RSA prompt on target device");
                    sentPublicKey = true;
                    send(new AdbPacket(AdbPacket.A_AUTH, AdbPacket.AUTH_RSAPUBLICKEY, 0,
                            key.publicKeyPayload("scrcpy_android@jlxc")));
                } else {
                    // After the user accepts the key, adbd often sends a new token.
                    send(new AdbPacket(AdbPacket.A_AUTH, AdbPacket.AUTH_SIGNATURE, 0, key.sign(p.payload)));
                }
            } else {
                log("ADB handshake ignored packet: " + AdbPacket.commandToString(p.command));
            }
        }
    }

    public AdbStream open(String destination) throws IOException {
        int local = nextLocalId.getAndIncrement();
        AdbStream stream = new AdbStream(this, local);
        streams.put(local, stream);
        byte[] payload = (destination + "\0").getBytes(StandardCharsets.UTF_8);
        send(new AdbPacket(AdbPacket.A_OPEN, local, 0, payload));
        stream.awaitReady(10_000);
        return stream;
    }

    void sendWrite(int localId, int remoteId, byte[] payload) throws IOException {
        send(new AdbPacket(AdbPacket.A_WRTE, localId, remoteId, payload));
    }

    void closeStream(int localId, int remoteId) {
        streams.remove(localId);
        try { send(new AdbPacket(AdbPacket.A_CLSE, localId, remoteId, new byte[0])); } catch (IOException ignored) {}
    }

    private synchronized void send(AdbPacket packet) throws IOException {
        if (closed) throw new IOException("ADB connection closed");
        packet.writeTo(out);
    }

    private void startReader() {
        readerThread = new Thread(() -> {
            try {
                while (!closed) {
                    AdbPacket p = AdbPacket.readFrom(in);
                    switch (p.command) {
                        case AdbPacket.A_OKAY: {
                            AdbStream s = streams.get(p.arg1);
                            if (s != null) s.onOkay(p.arg0);
                            break;
                        }
                        case AdbPacket.A_WRTE: {
                            AdbStream s = streams.get(p.arg1);
                            if (s != null) {
                                send(new AdbPacket(AdbPacket.A_OKAY, p.arg1, p.arg0, new byte[0]));
                                s.onWrite(p.payload);
                            }
                            break;
                        }
                        case AdbPacket.A_CLSE: {
                            AdbStream s = streams.remove(p.arg1);
                            if (s != null) {
                                s.onClose();
                                send(new AdbPacket(AdbPacket.A_CLSE, p.arg1, p.arg0, new byte[0]));
                            }
                            break;
                        }
                        default:
                            log("ADB reader ignored: " + AdbPacket.commandToString(p.command));
                    }
                }
            } catch (Exception e) {
                log("ADB reader stopped: " + e.getMessage());
                closed = true;
                for (AdbStream s : streams.values()) s.onClose();
                streams.clear();
            }
        }, "adb-reader");
        readerThread.start();
    }

    private void log(String s) { if (log != null) log.log(s); }

    @Override public void close() {
        closed = true;
        try { socket.close(); } catch (IOException ignored) {}
        for (AdbStream s : streams.values()) s.onClose();
        streams.clear();
    }
}
