package com.shakecam;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

import java.util.ArrayDeque;

/**
 * Foreground service that watches the accelerometer (screen on or off)
 * and, on a deliberate shake, wakes the screen and opens the camera.
 */
public class ShakeService extends Service implements SensorEventListener {

    static final String ACTION_RELOAD = "com.shakecam.RELOAD";
    static final String ACTION_TEST = "com.shakecam.TEST";

    static volatile boolean running = false;

    static final int ID_RUN = 1;
    static final int ID_WAKE = 2;
    private static final String CH_RUN = "running";
    private static final String CH_WAKE = "wake";

    /** All swings must land inside this window. */
    private static final long WINDOW_MS = 1500;
    /** Ignore crossings closer together than this (one swing ringing). */
    private static final long MIN_GAP_MS = 120;
    /** Quiet time after a trigger. */
    private static final long COOLDOWN_MS = 4000;

    private SensorManager sm;
    private PowerManager pm;
    private KeyguardManager km;
    private NotificationManager nm;
    private PowerManager.WakeLock cpuLock;
    private final Handler h = new Handler(Looper.getMainLooper());

    private float thr = Cfg.THR_DEFAULT;
    private int need = Cfg.PEAKS_DEFAULT;

    private final ArrayDeque<Long> peaks = new ArrayDeque<>();
    private boolean above = false;
    private long lastPeak = 0;
    private long lastTrigger = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        pm = (PowerManager) getSystemService(POWER_SERVICE);
        km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        nm = getSystemService(NotificationManager.class);

        NotificationChannel run = new NotificationChannel(CH_RUN, "Shake Cam armed",
                NotificationManager.IMPORTANCE_LOW);
        NotificationChannel wake = new NotificationChannel(CH_WAKE, "Wake camera",
                NotificationManager.IMPORTANCE_HIGH);
        wake.setSound(null, null);
        wake.enableVibration(false);
        nm.createNotificationChannel(run);
        nm.createNotificationChannel(wake);

        goForeground();
        load();

        // Keep the CPU running with the screen off so sensor events keep arriving.
        cpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "shakecam:cpu");
        cpuLock.setReferenceCounted(false);
        cpuLock.acquire();

        Sensor acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (acc != null) {
            sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    private void goForeground() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CH_RUN)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Shake Cam armed")
                .setContentText("Shake to wake the camera")
                .setOngoing(true)
                .setContentIntent(open)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_RUN, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(ID_RUN, n);
        }
    }

    private void load() {
        SharedPreferences p = getSharedPreferences(Cfg.NAME, MODE_PRIVATE);
        thr = Cfg.threshold(p);
        need = Cfg.peaks(p);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        goForeground();
        load();
        if (intent != null && ACTION_TEST.equals(intent.getAction())) {
            h.postDelayed(this::trigger, 8000);
        }
        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float x = e.values[0], y = e.values[1], z = e.values[2];
        float dyn = Math.abs((float) Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH);
        long now = SystemClock.elapsedRealtime();

        if (!above && dyn > thr) {
            above = true;
            if (now - lastPeak < MIN_GAP_MS) return;
            lastPeak = now;
            while (!peaks.isEmpty() && now - peaks.peekFirst() > WINDOW_MS) peaks.pollFirst();
            peaks.addLast(now);
            if (peaks.size() >= need) {
                peaks.clear();
                if (now - lastTrigger > COOLDOWN_MS) {
                    lastTrigger = now;
                    trigger();
                }
            }
        } else if (above && dyn < thr * 0.5f) {
            above = false;
        }
    }

    @SuppressWarnings("deprecation")
    private void trigger() {
        boolean screenOn = pm.isInteractive();
        boolean locked = km.isKeyguardLocked();
        if (screenOn && !locked) {
            buzz(1); // already awake and usable
            return;
        }
        buzz(2);

        // Belt and braces: ask for the screen directly as well.
        PowerManager.WakeLock screen = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "shakecam:screen");
        screen.acquire(3000);

        Intent wi = new Intent(this, WakeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        // Full-screen intent: the standard way to show an activity over a locked, off screen.
        PendingIntent pi = PendingIntent.getActivity(this, 1, wi,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CH_WAKE)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Opening camera")
                .setCategory(Notification.CATEGORY_ALARM)
                .setFullScreenIntent(pi, true)
                .setAutoCancel(true)
                .setTimeoutAfter(10000)
                .build();
        nm.notify(ID_WAKE, n);

        // Direct launch too, allowed from the background with "Display over other apps".
        if (Settings.canDrawOverlays(this)) {
            try {
                startActivity(wi);
            } catch (Exception ignored) {
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void buzz(int pulses) {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        long[] pattern = pulses <= 1 ? new long[]{0, 150} : new long[]{0, 150, 120, 150};
        v.vibrate(VibrationEffect.createWaveform(pattern, -1));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onDestroy() {
        running = false;
        h.removeCallbacksAndMessages(null);
        if (sm != null) sm.unregisterListener(this);
        if (cpuLock != null && cpuLock.isHeld()) cpuLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
