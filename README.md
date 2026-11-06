# Modern Gallery

A fresh Material 3 gallery app (replaces the old Gallery2 code, which can't be built with current tools).

## Build
1. Create a GitHub repo and push this folder (the `.github/workflows/build-apk.yml` file must be included).
2. Open the **Actions** tab -> **Build APK** -> **Run workflow** (it also runs on every push).
3. Download the `ModernGallery-debug-apk` artifact and install `app-debug.apk`.

Local build: open in Android Studio (JDK 17) and run the `app` configuration.

## Features
- Photos timeline grouped by day (Today, Yesterday, dates), pinch-free fast grid with Glide thumbnails
- Albums (folders) with cover and count
- Viewer: swipe between items, pinch / double-tap zoom, video playback, share, favorite, info, edit, delete
- Favorites tab, Videos list, search by name / album / date
- Recycle bin using the system MediaStore trash (restore or delete forever)
- Private space: password protected (PBKDF2-hashed), files moved into app-private storage,
  own recycle bin (30 days), auto-locks when you leave, screenshots blocked
- Editor: rotate, flip, crop ratios, brightness / contrast / saturation, 7 filters, saves a copy to Pictures/Edited
- Dynamic color (Material You), light and dark themes
- Opens images/videos from other apps (ACTION_VIEW)

## Notes
- Requires Android 11+ (API 30) for the MediaStore trash / delete-request APIs.
- Private-space files are stored in the app's sandbox (hidden from other apps) but not additionally encrypted.
