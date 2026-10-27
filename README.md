# Elite: voice assistant for Android (Phase 1 + 2)

**Framework: Kotlin native + Jetpack Compose.** Phase 3 needs deep Android hooks
(NotificationListenerService, AlarmManager, location services, background services).
Native Kotlin reaches all of them directly; in Flutter each one needs a plugin or
custom platform-channel code anyway, so Kotlin is less work for this scope.

## What works now
- Tap the glowing core, speak, Elite answers out loud (Android STT → Claude → Android TTS).
- "Open the camera", "show my photos", "how much battery do I have?" run real phone actions.
- Tap while listening to finish early; tap while Elite talks to stop it.
- **Conversation Coach:** tap CONVERSATION COACH, talk with someone, say "Elite Elite" (or tap the core)
  and Elite shows a good reply to say. A red banner shows it's listening; it stops when you leave the app.

- Ask about anything current (web search), "open YouTube", "wake me at 6:30", "timer 10 minutes",
  "call Mom", "WhatsApp Rahul I'm late", "what did I miss?", "what is this?" (camera). Speak Hindi,
  English or Hinglish; tap **LANG** to set what the recogniser listens for. Details: [wiki/09-new-features.md](wiki/09-new-features.md).

More background and decisions: see the [wiki](wiki/README.md).

## Setup, step by step
1. **Install Android Studio** (Meerkat 2024.3 or newer).
2. **Copy this `elite` folder** to your computer (or unzip `elite-android.zip`).
3. **Get a Claude API key** at https://console.anthropic.com → API Keys.
4. *(Optional)* The app asks for your key on first launch and stores it on the phone; tap **KEY**
   (top right) to change it. To bake a key into your own build instead, copy
   `local.properties.example` to `local.properties` and set `ANTHROPIC_API_KEY=sk-ant-...`.
5. **Open the project**: Android Studio → File → Open → choose the `elite` folder → wait for Gradle sync.
6. **Connect your phone** (Settings → About phone → tap Build number 7 times → Developer options →
   USB debugging on), plug it in, pick it in the device dropdown, press ▶ Run.
7. On first tap, **allow microphone access**. Then say "Elite, open the camera".

**Just want the APK?** In Android Studio: Build → Build App Bundle(s) / APK(s) → Build APK(s), then
click "locate" and copy `app-debug.apk` to your phone. Or push this folder to a GitHub repo: the
included `.github/workflows/build-apk.yml` builds the APK automatically (Actions tab → Artifacts).

## Where everything lives
```
app/src/main/java/com/elite/assistant/
├── MainActivity.kt          Screen + microphone permission
├── EliteViewModel.kt        The voice loop: listen → think → act → speak
├── ai/ApiKeyStore.kt       Saves the key typed into the app
├── ai/EliteBrain.kt         Claude API call, conversation memory, system prompt with action tags
├── actions/EliteAction.kt   List of supported [ACTION: ...] tags
├── actions/ActionParser.kt  Splits Claude's reply into speech + actions
├── actions/ActionExecutor.kt Intents for camera / gallery, battery reading
├── coach/                 Rolling transcript + "Elite Elite" detection
├── voice/ContinuousListener.kt Non-stop listening for Coach mode
├── voice/SpeechInput.kt     Android SpeechRecognizer (speech-to-text)
├── voice/SpeechOutput.kt    Android TextToSpeech
└── ui/                      Arc-reactor button, HUD screen, colours
```

## Adding a new action (how Phase 3 will plug in)
1. Add the tag to the `SYSTEM_PROMPT` list in `ai/EliteBrain.kt`.
2. Add an entry to `EliteAction` (set `launchesApp = true` if it opens another app).
3. Handle it in `ActionExecutor.execute`.

## Notes
- Model: `claude-opus-5-5` at low effort for quick spoken replies. Change `MODEL` or the
  effort in `EliteBrain.kt` if you want it faster/cheaper or more thorough.
- Refusal fallback is switched on (`fallbacks: "default"`), so if Claude's safety filter wrongly
  declines a harmless request, the API retries it on another model automatically.
- The key lives on your phone (or in your own build). A public app should call Claude through
  your own server instead.
- Speech recognition uses Google's recognizer on most phones and may need internet.

## Common errors
| Symptom | Fix |
|---|---|
| "I need a Claude API key first" | Tap KEY and paste your key. |
| "API key was rejected" | Key is wrong or revoked; create a new one in the console and tap KEY. |
| "Speech recognition isn't available" | Install/enable the Google app or "Speech Recognition & Synthesis". |
| Gradle sync fails on `anthropic-java` | Check internet; File → Sync Project with Gradle Files. |
