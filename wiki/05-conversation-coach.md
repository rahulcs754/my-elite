# Conversation Coach ("Elite Elite")

**What it does:** while you talk with someone, Elite listens. When you say **"Elite Elite"**
(or tap the core), it shows a good reply you can say next. You can also have it whispered
at low volume.

## Flow
1. Tap **CONVERSATION COACH**. A red blinking **COACH MODE · LISTENING** banner appears and stays.
2. `ContinuousListener` restarts the speech recognizer after every pause, so listening is continuous.
3. Each finished phrase goes into `ConversationBuffer` (last 5 minutes, max 4,000 characters, memory only).
4. "Elite Elite" is detected in live partial results, so it fires without waiting for a pause.
   The words before it are kept; the trigger itself is not sent.
5. `EliteBrain.suggestReply` sends the transcript to Claude with the coach prompt (effort MEDIUM).
6. The suggestion appears under **SAY THIS** with **WHISPER IT** and **DISMISS**.
   While whispering, listening pauses so Elite doesn't transcribe itself.

## What makes a "good response" (coach prompt rules)
- The exact words to say, 1–3 short sentences, no preamble or options list.
- Same language and tone as the conversation (Hindi, English, Hinglish).
- Respectful, warm, honest; answers what was actually said.
- De-escalates tension; acknowledges the other person's point first.
- Never suggests lying, manipulation, pressure, insults or promises the user may not keep.
- Never invents facts about the user (prices, dates, commitments); suggests checking instead.
- If the transcript is unclear, suggests a natural clarifying question.

## Privacy and fairness decisions
- **Off by default, switched on explicitly** each time.
- **Visible indicator** the whole time it listens, so the other person can see it.
- **Foreground only:** stops in `onStop` (app minimised, screen off, switching apps). Screen is kept awake while coaching.
- **No recordings:** only recognised text, kept in memory, cleared when coach mode stops.
- Transcript leaves the phone only when you ask for a suggestion (sent to the Claude API).
- Recording laws differ by place; tell the other person when you use it.

## Known limits
- One microphone can't tell speakers apart; Claude infers who said what from context.
- Some phones beep each time the recognizer restarts (system sound, can't be disabled by the app on all devices).
- Google's recognizer may need internet; transcript quality drops in noisy places.
- "Elite" is a made-up word for recognizers, so common mishearings ("a lead", "e light") also trigger.

## Future options
- Use an offline on-device recognizer (Android 13+ `createOnDeviceSpeechRecognizer`) to avoid beeps and network.
- Show 2–3 alternative replies (formal / friendly / brief).
- Haptic buzz when a suggestion is ready, for silent use.
