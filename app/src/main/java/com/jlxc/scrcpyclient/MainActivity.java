package com.jlxc.scrcpyclient;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jlxc.scrcpyclient.scrcpy.ScrcpyControl;
import com.jlxc.scrcpyclient.scrcpy.ScrcpySession;

public class MainActivity extends Activity implements SurfaceHolder.Callback, ScrcpySession.Listener {
    private SurfaceView surfaceView;
    private TextView logView;
    private TextView titleView;
    private EditText hostEdit, portEdit, maxSizeEdit, bitRateEdit, fpsEdit;
    private ScrcpySession session;
    private ScrcpyControl control;
    private int videoW = 0, videoH = 0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(buildUi());
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(this);
        surfaceView.setOnTouchListener((v, event) -> {
            ScrcpyControl c = control;
            if (c == null || videoW <= 0 || videoH <= 0) return true;
            int action;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: action = MotionEvent.ACTION_DOWN; break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: action = MotionEvent.ACTION_UP; break;
                case MotionEvent.ACTION_MOVE: action = MotionEvent.ACTION_MOVE; break;
                default: return true;
            }
            int x = Math.max(0, Math.min(videoW - 1, Math.round(event.getX() * videoW / Math.max(1, v.getWidth()))));
            int y = Math.max(0, Math.min(videoH - 1, Math.round(event.getY() * videoH / Math.max(1, v.getHeight()))));
            try { c.sendTouch(action, x, y, videoW, videoH, action == MotionEvent.ACTION_UP ? 0f : 1f); }
            catch (Exception e) { appendLog("touch failed: " + e.getMessage()); }
            return true;
        });
        root.addView(surfaceView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(8), dp(10), dp(8));
        panel.setBackgroundColor(0xaa000000);

        titleView = new TextView(this);
        titleView.setText("JLXC Scrcpy Android Client · v4.0 / v0.2");
        titleView.setTextColor(0xff39c5bb);
        titleView.setTextSize(16);
        panel.addView(titleView);

        LinearLayout row = row();
        hostEdit = edit("192.168.1.100"); row.addView(label("ADB IP")); row.addView(hostEdit, weight());
        portEdit = edit("5555"); row.addView(label("Port")); row.addView(portEdit, small());
        maxSizeEdit = edit("1280"); row.addView(label("Max")); row.addView(maxSizeEdit, small());
        bitRateEdit = edit("8000000"); row.addView(label("Bit")); row.addView(bitRateEdit, medium());
        fpsEdit = edit("60"); row.addView(label("FPS")); row.addView(fpsEdit, small());
        panel.addView(row);

        LinearLayout buttons = row();
        Button connect = button("连接 / 启动");
        connect.setOnClickListener(v -> startSession());
        buttons.addView(connect);
        Button stop = button("停止");
        stop.setOnClickListener(v -> stopSession());
        buttons.addView(stop);
        Button back = button("BACK");
        back.setOnClickListener(v -> { try { if (control != null) control.sendBack(); } catch (Exception e) { appendLog(e.getMessage()); } });
        buttons.addView(back);
        Button home = button("HOME");
        home.setOnClickListener(v -> { try { if (control != null) control.sendHome(); } catch (Exception e) { appendLog(e.getMessage()); } });
        buttons.addView(home);
        Button hide = button("隐藏面板");
        hide.setOnClickListener(v -> panel.setVisibility(View.GONE));
        buttons.addView(hide);
        panel.addView(buttons);

        ScrollView scroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextColor(0xffdddddd);
        logView.setTextSize(11);
        logView.setText("提示：目标机需要开启无线 ADB；首次连接要在目标机上同意 RSA 授权。\n");
        scroll.addView(logView);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, dp(95)));

        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(panel, p);
        root.setOnLongClickListener(v -> { panel.setVisibility(View.VISIBLE); return true; });
        return root;
    }

    private void startSession() {
        if (surfaceView.getHolder().getSurface() == null || !surfaceView.getHolder().getSurface().isValid()) {
            appendLog("surface not ready"); return;
        }
        stopSession();
        String host = hostEdit.getText().toString().trim();
        int port = parse(portEdit, 5555);
        int maxSize = parse(maxSizeEdit, 1280);
        int bitrate = parse(bitRateEdit, 8_000_000);
        int fps = parse(fpsEdit, 60);
        session = new ScrcpySession(this, host, port, maxSize, bitrate, fps, surfaceView.getHolder().getSurface(), this);
        appendLog("starting session...");
        session.startAsync();
    }

    private void stopSession() {
        control = null;
        if (session != null) {
            try { session.close(); } catch (Exception ignored) {}
            session = null;
            appendLog("stopped");
        }
    }

    private int parse(EditText e, int fallback) {
        try { return Integer.parseInt(e.getText().toString().trim()); } catch (Exception ex) { return fallback; }
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {}
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) { stopSession(); }

    @Override public void onConnected(ScrcpyControl control) {
        this.control = control;
        appendLog("control connected");
    }

    @Override public void onDeviceName(String name) {
        runOnUiThread(() -> titleView.setText("JLXC Scrcpy Client · " + name));
        appendLog("device: " + name);
    }

    @Override public void onVideoSize(int width, int height) {
        videoW = width; videoH = height;
        appendLog("video: " + width + "x" + height);
    }

    @Override public void onLog(String line) { appendLog(line); }

    @Override public void onStopped(Exception e) {
        if (e != null) appendLog("session ended: " + e.getMessage());
    }

    private void appendLog(String s) {
        runOnUiThread(() -> logView.append(s + "\n"));
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private TextView label(String s) {
        TextView v = new TextView(this);
        v.setText(s + " "); v.setTextColor(Color.WHITE); v.setTextSize(12);
        return v;
    }

    private EditText edit(String s) {
        EditText e = new EditText(this);
        e.setSingleLine(true); e.setText(s); e.setTextColor(Color.WHITE); e.setTextSize(12);
        e.setSelectAllOnFocus(true); e.setPadding(dp(4), 0, dp(4), 0);
        e.setBackgroundColor(0x5539c5bb);
        return e;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s); b.setTextSize(12); b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, dp(40), 1); }
    private LinearLayout.LayoutParams small() { return new LinearLayout.LayoutParams(dp(70), dp(40)); }
    private LinearLayout.LayoutParams medium() { return new LinearLayout.LayoutParams(dp(105), dp(40)); }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
