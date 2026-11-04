package com.shakecam;

import android.content.SharedPreferences;

/** Shared settings. */
final class Cfg {
    static final String NAME = "cfg";

    /** Shake strength (m/s² above gravity) a swing must exceed to count. */
    static final float THR_MIN = 3f, THR_MAX = 25f, THR_STEP = 0.5f, THR_DEFAULT = 10f;
    /** How many swings within the window are needed to trigger. */
    static final int PEAKS_MIN = 2, PEAKS_MAX = 5, PEAKS_DEFAULT = 3;

    static float threshold(SharedPreferences p) { return p.getFloat("thr", THR_DEFAULT); }
    static int peaks(SharedPreferences p) { return p.getInt("peaks", PEAKS_DEFAULT); }
    static boolean video(SharedPreferences p) { return p.getBoolean("video", false); }

    private Cfg() {}
}
