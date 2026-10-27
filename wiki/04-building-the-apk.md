# Building the APK

## Why it can't be built in the Claude cloud workspace
Android builds need three things from Google: the Android Gradle Plugin, AndroidX/Compose
libraries (both from `maven.google.com`, which redirects to `dl.google.com`), and the SDK
platform. This project's cloud workspace uses a built-in network policy that **blocks
`dl.google.com`** (checked 2026-10-16: HTTP 403 on CONNECT). Maven Central works (via
Google's mirror), which is why the code could be compile-checked but not packaged.

What was verified here instead: all Kotlin sources compile against the Android 15 API
(Robolectric `android-all`) plus Compose Multiplatform, with small stubs for
`AndroidViewModel`, `LocalView` and `BuildConfig`; `MainActivity` uses AndroidX Activity APIs
and was not compiled. Parser and wake-phrase unit tests pass.

## Three ways to get an APK
1. **Android Studio (simplest):** open the `elite` folder → Build → Build App Bundle(s) / APK(s)
   → Build APK(s) → "locate" → copy `app-debug.apk` to the phone and install
   (allow "install unknown apps").
2. **GitHub Actions (set up):** the code lives at https://github.com/rahulcs754/my-elite. `.github/workflows/build-apk.yml`
   builds on every push; download from the repo's Actions tab → run → Artifacts → `elite-apk`.
3. **Open the network:** in Project settings, add a cloud environment that allows
   `dl.google.com` and `maven.google.com`; a new Claude thread can then build the APK itself.
   Docs: https://code.claude.com/docs/en/cloud-environments#network-access

## Versions
AGP 8.9.1 · Gradle 8.11.1 (wrapper) · Kotlin 2.1.20 · compileSdk/targetSdk 35 · minSdk 26 ·
Compose BOM 2025.05.00 · anthropic-java 2.34.0 · JDK 17.


## CI notes
- The workflow uses the Android SDK preinstalled on `ubuntu-24.04` runners. The first run used `android-actions/setup-android@v3`, whose `sdkmanager` call failed on the runner ("Failed to find package 'tools'"), so it was removed.
- Build logs can be read with the GitHub connector (`get_job_logs`); direct log downloads are blocked from the Claude workspace.
