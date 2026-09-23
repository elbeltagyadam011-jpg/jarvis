# JARVIS v5 Security Hardening

This build keeps the existing feature set while adding local safety controls.

## Changes
- Device-control switches now default to OFF (least privilege) on first run.
- Sensitive local actions require a one-time confirmation dialog before execution.
- AI-initiated device actions also require a one-time confirmation.
- AI tool execution is capped at 3 tool calls per user request.
- More than 3 local chained commands are rejected as a safety measure.
- Android backup is disabled and app data extraction is excluded.
- Cleartext HTTP is explicitly disabled.
- No shell execution, accessibility service, device-admin, SMS, contacts, location, or storage permissions were added.
- Gemini remains restricted to the existing allow-listed structured tools.
- Gemini receives the user's AI prompt plus only the small tool result needed for an approved tool call; it does not receive the stored API key through the prompt or tool result.

## Important limitation
No Android application can honestly be guaranteed to be "100% safe". This hardening reduces the application's own attack surface and accidental actions, but device security also depends on Android/Samsung security updates, the installed apps, the user's permissions, the Gemini account/API configuration, and the device itself.

## Build/test
The source was hardened and structurally checked. An APK was not claimed as built because this project archive does not contain a Gradle wrapper and the current build environment does not have Android SDK/Gradle available.


### v5.1 hardening additions
- All app-opening actions are gated by the Control Center and one-time confirmation.
- Multi-command requests are shown as a single explicit confirmation before execution.
- Gemini no longer receives tool definitions for battery/time/date/network status, reducing device telemetry leaving the phone.
- Every remaining Gemini tool call requires explicit one-time approval.
- API-key screen uses password input and the Activity is FLAG_SECURE to reduce screenshot/screen-recording exposure.
- Gemini model names are restricted to a safe character set before being placed in the URL.
- Gemini HTTP timeouts are shorter, caching is disabled, and oversized responses are rejected.
- A hosted CI workflow is included to build a debug APK with Android SDK 35.
