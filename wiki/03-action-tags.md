# Action tags

Claude can't touch the phone. Instead, the system prompt tells it to append tags, and
the app turns tags into real actions.

```
User:   "Open the camera"
Claude: "Opening the camera, Sir. [ACTION: OPEN_CAMERA]"
App:    speaks "Opening the camera, Sir." then fires the camera Intent
```

| Tag | What the app does | Permission |
|---|---|---|
| `OPEN_CAMERA` | `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA`, fallback `ACTION_IMAGE_CAPTURE` | none (camera app takes the photo) |
| `OPEN_GALLERY` | `CATEGORY_APP_GALLERY`, fallback `ACTION_VIEW` on the images library | none |
| `BATTERY_LEVEL` | Reads `BatteryManager` and speaks "Battery is at N percent" | none |

## Parser rules
- Regex `[ ACTION : NAME ]`, case-insensitive, whitespace-tolerant.
- Unknown tags are stripped from speech and ignored, so typos are never read aloud.
- Duplicate tags run once.

## Adding an action (Phase 3 pattern)
1. Add the tag and a one-line description to `SYSTEM_PROMPT` in `ai/EliteBrain.kt`.
2. Add an `EliteAction` entry (`launchesApp = true` if it opens another screen).
3. Implement it in `ActionExecutor.execute`, and add any manifest permission.

Example for alarms: tag `SET_ALARM:07:30`, executor fires `AlarmClock.ACTION_SET_ALARM`
with `EXTRA_HOUR`/`EXTRA_MINUTES` (needs `com.android.alarm.permission.SET_ALARM`).
Tags with arguments need the parser regex extended to capture `:value`.
