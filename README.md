# Shake Cam

Shake the phone in its housing → screen wakes → camera opens. You get a double buzz when it triggers.

## Build

**Option A: GitHub (no tools needed)**
1. Create a new private repo on github.com.
2. Upload everything in this folder, including the hidden `.github` folder. Easiest from a PC: drag the folder contents into "Add file → Upload files".
3. The **Actions** tab builds automatically (about 3 min).
4. On the phone, open the repo's **Releases** and tap `ShakeCam.apk`. Allow your browser to install unknown apps when asked.

**Option B: Android Studio**
Open this folder and run it on the phone, or use Build → Build APK.

The signing key (`app/shakecam.jks`) is fixed, so new builds install over old ones.

## First-time setup (in the app)
1. Tap each **Grant** button until all four show ✓:
   - Notifications
   - Full-screen alerts
   - Display over other apps
   - Unrestricted battery
2. **Calibrate.** With the phone in the housing, do your "wake" shake and read the peak. A bucket or bath is ideal, because water drag cuts the peak a lot. Set the sensitivity slider a bit below that peak.
3. Tap **Start**. The notification "Shake Cam armed" means it's running.
4. Tap **Test wake in 8 s** and lock the phone. The camera should open on its own.

## How it behaves
- **PIN/pattern lock:** the camera opens in secure mode over the lock screen. You can shoot normally and swipe to video. You can't browse old photos without unlocking.
- **Swipe/None lock:** the full camera opens. The video-mode switch only applies here.
- **Screen already on and unlocked:** a shake gives one buzz and does nothing else.
- **After a trigger:** shakes are ignored for 4 s.

## Battery
While armed, the app holds the CPU awake to read the accelerometer. Expect a few % per hour. Arm it before the dive and tap **Stop** after.

## Tuning
- **Too many false triggers when finning:** raise the sensitivity value, or require 4 swings.
- **Won't trigger underwater:** lower the sensitivity value. Short, sharp wrist flicks work better than big slow waves.
