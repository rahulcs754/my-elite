# Architecture

```
 tap core ──► SpeechInput (STT) ──► EliteViewModel ──► EliteBrain (Claude API)
                                         │                    │
                                         │◄── reply text ─────┘
                                         ▼
                                   ActionParser ──► ActionExecutor (Intents, battery)
                                         │
                                         ▼
                                   SpeechOutput (TTS) ──► back to idle
```

| File | Role |
|---|---|
| `MainActivity.kt` | Hosts the Compose screen, asks for mic permission, stops coach mode in `onStop` |
| `EliteViewModel.kt` | State machine: IDLE → LISTENING → THINKING → SPEAKING; also runs Coach mode |
| `ai/EliteBrain.kt` | Claude calls: assistant chat (with memory) and coach suggestions (stateless) |
| `ai/ApiKeyStore.kt` | Stores the API key typed into the app (app-private storage) |
| `actions/*` | Tag list, parser, executor |
| `voice/SpeechInput.kt` | One-shot recognition for assistant mode |
| `voice/ContinuousListener.kt` | Back-to-back recognition for Coach mode |
| `voice/SpeechOutput.kt` | TTS with a done-callback and volume control |
| `coach/*` | Rolling transcript buffer and "Elite Elite" detection |
| `ui/*` | Arc reactor, HUD screen, coach banner/panel, key dialog, theme |

## Key decisions
- **One tap control.** The core's tap meaning depends on phase (start / finish / stop), and in Coach mode it asks for a suggestion.
- **Launch actions run after speaking.** Opening the camera mid-sentence would cut Elite off; battery is read first so its value is spoken with the reply.
- **API key typed in the app**, with `local.properties` as a fallback, so a prebuilt APK works for anyone.
- **Minify off.** The Anthropic SDK uses Jackson reflection that R8 would strip.
