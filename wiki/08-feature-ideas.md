# Feature ideas (research, 2026-10-19)

What other assistants do (Google Assistant/Gemini, Alexa, Siri) compared with what Elite can do,
mapped to the Android API each idea needs. Sources: Android platform APIs Elite already targets
(API 26–35) and the Claude API's built-in tools. Nothing here has been built yet.

**Value:** how much it changes daily use. **Effort:** S = a few hours, M = a day or two, L = several days.

## Ranked list

| # | Feature | Example | How (Android / Claude) | Value | Effort |
|---|---|---|---|---|---|
| 1 | **Live web answers** | "Today's cricket score?", "Petrol price in Delhi?" | Claude `web_search` server tool on the existing call; no Android work | High | S |
| 2 | **Open any app by name** | "Open YouTube", "Open PhonePe" | `PackageManager` launcher list + `<queries>`; tag `OPEN_APP:<name>` | High | S |
| 3 | **Alarms, timers, reminders** (Phase 3) | "Wake me at 6:30", "Timer 10 minutes" | `AlarmClock.ACTION_SET_ALARM` / `ACTION_SET_TIMER` intents | High | S |
| 4 | **Hindi / Hinglish voice + faster speech** | Talk in Hindi, Elite answers in Hindi | Recognizer `EXTRA_LANGUAGE` hi-IN / en-IN, TTS voice picker; stream Claude's reply and speak sentence by sentence | High | S–M |
| 5 | **Call and message people** | "Call Mom", "WhatsApp Rahul I'm running late" | Contacts lookup (`READ_CONTACTS`), `ACTION_CALL`/`ACTION_DIAL`, WhatsApp `wa.me` intent; always confirm before sending | High | M |
| 6 | **Read and summarise notifications** (Phase 3) | "What did I miss?" | `NotificationListenerService` (user enables in Settings); Claude summarises | High | M |
| 7 | **"What am I looking at?" (vision)** | Point camera at a bill, menu, medicine strip | CameraX photo → Claude image input; read text, translate, explain | High | M |
| 8 | **Location and navigation** (Phase 3) | "Where am I?", "Navigate to the airport" | Fused location + `Geocoder`; Google Maps `google.navigation:` intent | Med–High | S–M |
| 9 | **Hands-free "Elite" wake word** | Say "Elite" without touching the phone | On-device recogniser loop while app is open; later a foreground service with a visible notification | High | M–L |
| 10 | **Phone controls** | "Flashlight on", "Volume 50%", "Silent mode" | `CameraManager.setTorchMode`, `AudioManager`, DND access; Wi-Fi/Bluetooth open settings panels (apps can't toggle them on Android 10+) | Medium | S |
| 11 | **Long-term memory** | "Remember my car is parked on level 2" | Small local store of facts, sent with each request | Medium | S–M |
| 12 | **Calendar** | "What's on today?", "Add meeting Friday 3 pm" | `CalendarContract` read + insert intent | Medium | M |
| 13 | **Coach upgrades** | Meeting summary when coach stops; live translate; 3 reply styles | Same transcript → one more Claude call | Medium | S |
| 14 | **Quick launch** | Start Elite from a Quick Settings tile or home widget | `TileService`, Glance widget | Medium | S–M |
| 15 | **Music control** | "Pause", "Next song" | Media key events via `AudioManager.dispatchMediaKeyEvent` | Medium | S |
| 16 | **Become the default assistant** | Long-press power/home opens Elite | `VoiceInteractionService` + `RoleManager.ROLE_ASSISTANT` | High | L |
| 17 | **Emergency SOS** | "Elite, emergency" sends location to 2 contacts | SMS with location, explicit setup and confirmation | Medium | M |
| 18 | **Control other apps' buttons** | "Book an Uber home" | `AccessibilityService` | High | L, plus Play Store policy risk; not recommended now |

## Recommended next batch
1–4 together are about a day of work and make Elite feel much smarter: it can answer
anything current, open apps, set alarms, and speak Hindi/Hinglish faster.
Then 5–7 (calls/WhatsApp, notifications, vision) as the second batch.

## Notes and cautions
- Anything that sends a message, places a call or shares location asks "Shall I send it, Sir?" first.
- Notification reading and wake word need visible user consent in Android settings; keep the same
  fairness rules as Coach mode (visible indicator, nothing stored).
- Web search and vision add Claude API cost per use; web search is billed per search.
- All of these still need the APK build path sorted ([Building the APK](04-building-the-apk.md)).
