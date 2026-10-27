# New features (built 2026-10-27)

All seven picks from [Feature ideas](08-feature-ideas.md) are in the code. Each was
compile-checked here; none has been tried on a real phone yet.

| Feature | Try saying | How it works | Permission |
|---|---|---|---|
| Live web answers | "Who won the match today?" | Claude's `web_search` tool (max 3 searches per question) | none |
| Faster replies | (any question) | Reply is streamed; each sentence is spoken as soon as it's complete (`SentenceSplitter`) | none |
| Open any app | "Open YouTube" | `[ACTION: OPEN_APP: YouTube]` → `AppFinder` matches launcher labels | none |
| Alarms & timers | "Wake me at 6:30", "Timer 10 minutes" | `SET_ALARM: 06:30 \| label`, `SET_TIMER: 600 \| label` → Clock app intents; the phone's local time is sent with each question | SET_ALARM (no prompt) |
| Hindi / Hinglish | Speak Hindi; tap **LANG** to switch AUTO → EN → HI | Recogniser language from the LANG setting; Claude answers in your language (Hindi in Devanagari); Hindi sentences use a Hindi TTS voice, others Indian English | none |
| Call | "Call Mom" | `CALL: Mom` → contact lookup → dialer opens with the number; **you press call** | Contacts (asked once) |
| WhatsApp | "WhatsApp Rahul I'm running late" | `WHATSAPP: Rahul \| message` → WhatsApp opens with the text typed; **you press send** | Contacts (asked once) |
| Notifications | "What did I miss?" | `READ_NOTIFICATIONS` → `EliteNotificationListener` reads the notification shade → Claude briefs you | Notification access (switch on in Settings; Elite opens the page) |
| Camera vision | "What is this?", "Read this bill" | `LOOK` → camera opens → photo shrunk to 1568 px → Claude answers → photo deleted | none (camera app takes the photo) |

## Design choices
- **Never sends on its own.** Calls use the dialer and WhatsApp opens with the message typed in, so a misheard name can't message the wrong person.
- **WhatsApp numbers** without a country code are assumed Indian (+91). Change `defaultCountryCode` in `ContactFinder.kt` if needed.
- **Interrupting:** tap the core while Elite is thinking or talking to stop it; any pending action (opening an app, etc.) is cancelled too.
- **Readings first, launches last:** battery and notification briefings are spoken with the reply; anything that opens another app waits until Elite stops talking.
- **Conversation memory** still only grows or resets (see [Claude API notes](06-claude-api-notes.md)); vision, notification and coach calls are one-off and don't touch it.

## Code map
`actions/` (EliteAction + ActionCall with arguments, ActionParser, ActionExecutor, ActionResult, AppFinder, ContactFinder) ·
`ai/SentenceSplitter.kt` · `notifications/EliteNotificationListener.kt` · `settings/VoiceLanguage.kt` ·
`voice/SpeechOutput.kt` (sentence queue, Hindi/English voice per sentence) · `MainActivity.kt` (permission + camera hand-offs) ·
`res/xml/file_paths.xml` (private cache folder for photos).

## Cost note
Web search is billed per search, and photos add image tokens. Everyday questions without search cost the same as before.
