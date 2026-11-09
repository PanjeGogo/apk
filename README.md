# HUD Overlay

Native Android overlay application built with Kotlin.

## Current foundation
- Separate Portrait and Landscape HUD profiles.
- Basic text HUD editor with position and opacity controls.
- Foreground overlay service.
- Touch-through overlay window so game controls receive taps.
- Persistent notification with Pause, Resume, and Stop actions.
- GitHub Actions debug APK build.

## Build
Push to `main` or run the **Android APK Build** workflow manually. The debug APK is uploaded as the `hud-overlay-debug-apk` artifact.

## Android permissions
The app requires the user to grant **Display over other apps**. Android 13+ also asks for notification permission.

## Roadmap
- Image/JPG/PNG/GIF overlay picker.
- MP3 playlist with repeat and background playback.
- Video overlay and playback controls.
- Web overlay.
- Multi-layer HUD editor and saved presets.
- MediaProjection recording/capture compatibility testing.

## Important
Android touch-through behavior and capture visibility vary by Android version and device vendor. Test on the target device before relying on it during a live stream.
