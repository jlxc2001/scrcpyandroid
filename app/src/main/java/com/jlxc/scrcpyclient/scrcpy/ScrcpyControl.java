package com.jlxc.scrcpyclient.scrcpy;

import android.view.KeyEvent;
import android.view.MotionEvent;

import java.io.IOException;
import java.io.OutputStream;

public final class ScrcpyControl {
    private static final int TYPE_INJECT_KEYCODE = 0;
    private static final int TYPE_INJECT_TOUCH_EVENT = 2;
    private static final int TYPE_ROTATE_DEVICE = 11;
    private static final long POINTER_ID_GENERIC_FINGER = -2L; // UINT64(-2)

    private final OutputStream out;

    public ScrcpyControl(OutputStream out) {
        this.out = out;
    }

    public synchronized void sendBack() throws IOException {
        sendKey(KeyEvent.KEYCODE_BACK);
    }

    public synchronized void sendHome() throws IOException {
        sendKey(KeyEvent.KEYCODE_HOME);
    }

    public synchronized void sendRotateDevice() throws IOException {
        out.write(new byte[]{(byte) TYPE_ROTATE_DEVICE});
        out.flush();
    }

    public synchronized void sendKey(int keyCode) throws IOException {
        writeKey(MotionEvent.ACTION_DOWN, keyCode);
        writeKey(MotionEvent.ACTION_UP, keyCode);
    }

    private void writeKey(int action, int keyCode) throws IOException {
        byte[] b = new byte[14];
        b[0] = TYPE_INJECT_KEYCODE;
        b[1] = (byte) action;
        Binary.put32be(b, 2, keyCode);
        Binary.put32be(b, 6, 0); // repeat
        Binary.put32be(b, 10, 0); // metastate
        out.write(b);
        out.flush();
    }

    public synchronized void sendTouch(int action, int x, int y, int screenW, int screenH, float pressure) throws IOException {
        byte[] b = new byte[32];
        b[0] = TYPE_INJECT_TOUCH_EVENT;
        b[1] = (byte) action;
        Binary.put64be(b, 2, POINTER_ID_GENERIC_FINGER);
        Binary.put32be(b, 10, x);
        Binary.put32be(b, 14, y);
        Binary.put16be(b, 18, screenW);
        Binary.put16be(b, 20, screenH);
        int p = Math.max(0, Math.min(0xffff, Math.round(pressure * 65535f)));
        Binary.put16be(b, 22, p);
        Binary.put32be(b, 24, 0); // action_button
        Binary.put32be(b, 28, 0); // buttons
        out.write(b);
        out.flush();
    }
}
