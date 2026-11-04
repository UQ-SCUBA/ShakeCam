package com.shakecam;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.WindowManager;

/** Invisible: turns the screen on over the lock screen and opens the camera. */
public class WakeActivity extends Activity {

    private static long lastLaunch = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        getSystemService(NotificationManager.class).cancel(ShakeService.ID_WAKE);

        // The service fires two launch paths; only act on the first.
        long now = SystemClock.elapsedRealtime();
        if (now - lastLaunch > 2500) {
            lastLaunch = now;
            start();
        }
        new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1500);
    }

    private void start() {
        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (km.isKeyguardLocked() && !km.isKeyguardSecure()) {
            // Swipe / no-PIN lock screen: dismiss it, then open the full camera.
            km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override public void onDismissSucceeded() { openCamera(false); }
                @Override public void onDismissError() { openCamera(true); }
                @Override public void onDismissCancelled() { openCamera(true); }
            });
        } else {
            // PIN-locked: use the secure camera, which runs over the lock screen.
            openCamera(km.isKeyguardLocked());
        }
    }

    private void openCamera(boolean secure) {
        boolean video = Cfg.video(getSharedPreferences(Cfg.NAME, MODE_PRIVATE));
        String action = secure
                ? MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE
                : (video ? MediaStore.INTENT_ACTION_VIDEO_CAMERA : MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException ignored) {
            }
        }
    }
}
