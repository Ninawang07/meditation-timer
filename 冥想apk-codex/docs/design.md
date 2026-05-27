# Meditation APK Design

## Goal

Port the WeChat Mini Program meditation timer into a native Android application project that can be opened in Android Studio and built into an APK.

## Scope

The Android version keeps the core app behavior:

- Countdown and stopwatch modes
- Preset durations and custom seconds input
- Optional breathing guidance with box breathing and 4-7-8 breathing
- Bowl audio at session start and natural countdown finish
- Silent manual stop
- Cue audio during inhale phases
- Local session history and simple stats

All generated project files live under `冥想apk-codex`.

## Architecture

The app is a small native Android Java project. It uses one `MainActivity` with programmatic UI to avoid external AndroidX or Compose dependencies. Timer state, audio playback, breathing state, and history storage are kept in focused helper classes inside the same package.

Audio uses `MediaPlayer` and a `Handler`-driven volume envelope. The bowl audio preserves the requested behavior: start fades from strong to weak, natural finish fades from weak to strong, and the second strike around 6.8-8.8 seconds is attenuated.

## Build Environment

This machine currently has no `java`, `gradle`, `ANDROID_HOME`, or `ANDROID_SDK_ROOT` command available. The project is therefore generated as an Android Studio project. After installing Android Studio or the Android command-line build tools, run Android Studio's Build APK action, or run `gradle assembleDebug` from this folder when Gradle and Android SDK are available.
