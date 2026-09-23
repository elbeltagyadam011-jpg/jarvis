# JARVIS v5

Android project for a controlled, voice-first personal assistant.

## Added in v5
- Real Gemini structured function calling with a larger allow-listed tool set.
- More safe Android controls: Calendar, Contacts, Downloads, Display settings, Sound settings, Airplane-mode settings, web search, and a basic device-status report.
- Voice session can be stopped by pressing the main TALK button again.
- Local Control Center remains the final permission gate for every phone action.
- Search requests open a normal Google results page; JARVIS does not silently browse or submit private data.
- Airplane mode is deliberately opened as a settings page rather than being toggled directly.
- Existing encrypted API-key storage, Arabic/English TTS, wake-session behavior, command chaining, and local commands are retained.

## Important
This is source code, not a prebuilt APK. Build it in Android Studio with Android SDK 35 and a compatible Android Gradle Plugin.

The project does not provide hidden access to the phone. Android permissions and the user's Control Center switches remain required.


## APK build
This project is intentionally shipped as source + Gradle configuration. The build environment used for this hardening pass does not contain the Android SDK/Gradle toolchain, so no APK is claimed as built or tested here. Open the project in Android Studio with an Android SDK installed, then use Build > Build APK(s).

Security posture: device-control permissions are opt-in, side-effecting local and Gemini actions require one-time confirmation, Gemini tool access is restricted to the declared allow-listed actions, and device telemetry tools are not exposed to Gemini.
