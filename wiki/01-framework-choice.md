# Framework choice: Kotlin native + Jetpack Compose

**Decision:** Kotlin with Jetpack Compose (decided 2026-10-16).

## Why not Flutter
Elite's value is phone automation. Each Phase 3 feature is an Android system API:

| Feature | Android API | Native Kotlin | Flutter |
|---|---|---|---|
| Speech-to-text | `SpeechRecognizer` | direct | plugin |
| Text-to-speech | `TextToSpeech` | direct | plugin |
| Open camera / gallery | Intents | direct | plugin or platform channel |
| Read notifications | `NotificationListenerService` | direct | custom Kotlin service anyway |
| Alarms | `AlarmManager` / `AlarmClock` intents | direct | plugin |
| Location | Fused Location Provider | direct | plugin |
| Always-on listening (later) | Foreground service | direct | custom Kotlin service anyway |

The hard parts (services, listeners, background rules) have to be written in Kotlin
even in a Flutter app, so Flutter adds a second language without saving work.
Flutter's main win, iOS support, isn't a goal: these automations are Android-only.

## Why Compose
- Animated arc-reactor UI is easy with `Canvas` and `rememberInfiniteTransition`.
- State flows from one `ViewModel` straight into the UI; no XML layouts.
