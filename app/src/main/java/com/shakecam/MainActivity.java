package com.shakecam;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener {

    private SharedPreferences prefs;
    private SensorManager sm;
    private final Handler h = new Handler(Looper.getMainLooper());

    private LinearLayout root;
    private TextView status, thrLabel, peaksLabel, live;
    private Button toggle, pNotif, pFsi, pOverlay, pBatt;

    private float peak = 0f;
    private long lastUi = 0;

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(Cfg.NAME, MODE_PRIVATE);
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);

        ScrollView sv = new ScrollView(this);
        sv.setFitsSystemWindows(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(32));
        sv.addView(root);
        setContentView(sv);

        text("Shake Cam", 26, true);
        status = text("", 16, true);
        toggle = button("", v -> toggleService());

        header("Sensitivity");
        thrLabel = text("", 15, false);
        SeekBar thr = new SeekBar(this);
        int thrSteps = Math.round((Cfg.THR_MAX - Cfg.THR_MIN) / Cfg.THR_STEP);
        thr.setMax(thrSteps);
        thr.setProgress(Math.round((Cfg.threshold(prefs) - Cfg.THR_MIN) / Cfg.THR_STEP));
        thr.setOnSeekBarChangeListener(new Seek(p -> {
            prefs.edit().putFloat("thr", Cfg.THR_MIN + p * Cfg.THR_STEP).apply();
            refreshLabels();
        }));
        root.addView(thr);

        peaksLabel = text("", 15, false);
        SeekBar pk = new SeekBar(this);
        pk.setMax(Cfg.PEAKS_MAX - Cfg.PEAKS_MIN);
        pk.setProgress(Cfg.peaks(prefs) - Cfg.PEAKS_MIN);
        pk.setOnSeekBarChangeListener(new Seek(p -> {
            prefs.edit().putInt("peaks", Cfg.PEAKS_MIN + p).apply();
            refreshLabels();
        }));
        root.addView(pk);

        Switch video = new Switch(this);
        video.setText("Open in video mode (only with Swipe/None lock)");
        video.setTextSize(15);
        video.setChecked(Cfg.video(prefs));
        video.setPadding(0, dp(12), 0, dp(4));
        video.setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean("video", on).apply());
        root.addView(video);

        header("Live reading");
        text("Shake the phone (in the housing, ideally in water) and watch the peak. "
                + "Set the sensitivity a little below your typical peak.", 13, false);
        live = text("", 18, true);
        live.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        button("Reset peak", v -> peak = 0f);

        header("Test");
        button("Test wake in 8 s", v -> {
            startForegroundService(new Intent(this, ShakeService.class).setAction(ShakeService.ACTION_TEST));
            Toast.makeText(this, "Lock the phone now. Camera should open in 8 s.", Toast.LENGTH_LONG).show();
            h.postDelayed(this::refresh, 400);
        });

        header("Permissions");
        text("All four are needed for reliable wake with the screen off.", 13, false);
        pNotif = button("", v -> {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        });
        pFsi = button("", v -> {
            if (Build.VERSION.SDK_INT >= 34) {
                open(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri()));
            }
        });
        pOverlay = button("", v ->
                open(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri())));
        pBatt = button("", v ->
                open(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri())));

        refreshLabels();
    }

    // ---- service ----

    private void toggleService() {
        Intent i = new Intent(this, ShakeService.class);
        if (ShakeService.running) {
            stopService(i);
        } else {
            startForegroundService(i);
        }
        h.postDelayed(this::refresh, 400);
    }

    private void notifyService() {
        if (ShakeService.running) {
            startForegroundService(new Intent(this, ShakeService.class).setAction(ShakeService.ACTION_RELOAD));
        }
    }

    // ---- UI state ----

    private void refreshLabels() {
        thrLabel.setText(String.format(Locale.US, "Trigger above %.1f m/s²  (lower = more sensitive)",
                Cfg.threshold(prefs)));
        peaksLabel.setText(String.format(Locale.US, "Swings needed within 1.5 s: %d", Cfg.peaks(prefs)));
        notifyService();
    }

    private void refresh() {
        boolean on = ShakeService.running;
        status.setText(on ? "● ARMED: shake to wake" : "○ Off");
        status.setTextColor(on ? 0xFF2E9E4F : 0xFF999999);
        toggle.setText(on ? "Stop" : "Start");

        boolean notif = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        boolean fsi = Build.VERSION.SDK_INT < 34
                || getSystemService(NotificationManager.class).canUseFullScreenIntent();
        boolean overlay = Settings.canDrawOverlays(this);
        boolean batt = getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());

        perm(pNotif, notif, "Notifications");
        perm(pFsi, fsi, "Full-screen alerts");
        perm(pOverlay, overlay, "Display over other apps");
        perm(pBatt, batt, "Unrestricted battery");
    }

    private void perm(Button b, boolean ok, String name) {
        b.setText((ok ? "✓  " : "Grant:  ") + name);
        b.setEnabled(!ok);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        Sensor acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (acc != null) sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sm.unregisterListener(this);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        refresh();
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float x = e.values[0], y = e.values[1], z = e.values[2];
        float dyn = Math.abs((float) Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH);
        if (dyn > peak) peak = dyn;
        long now = System.currentTimeMillis();
        if (now - lastUi > 100) {
            lastUi = now;
            float t = Cfg.threshold(prefs);
            live.setText(String.format(Locale.US, "now %5.1f   peak %5.1f", dyn, peak));
            live.setTextColor(dyn > t ? 0xFF2E9E4F : 0xFF888888);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // ---- helpers ----

    private Uri pkgUri() {
        return Uri.parse("package:" + getPackageName());
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (Exception ex) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri()));
        }
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(6), 0, dp(6));
        root.addView(t);
        return t;
    }

    private void header(String s) {
        TextView t = text(s, 18, true);
        t.setPadding(0, dp(22), 0, dp(4));
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        root.addView(b);
        return b;
    }

    private interface IntCb { void on(int p); }

    private static final class Seek implements SeekBar.OnSeekBarChangeListener {
        private final IntCb cb;
        Seek(IntCb cb) { this.cb = cb; }
        @Override public void onProgressChanged(SeekBar s, int p, boolean user) { if (user) cb.on(p); }
        @Override public void onStartTrackingTouch(SeekBar s) {}
        @Override public void onStopTrackingTouch(SeekBar s) {}
    }
}
