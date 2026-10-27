# Claude API notes

| Setting | Value | Why |
|---|---|---|
| SDK | `com.anthropic:anthropic-java` 2.34.0 | Official SDK; Kotlin uses the Java SDK |
| Model | `claude-opus-5-5` | Current default Opus model |
| Effort (assistant) | `LOW` | Fast spoken replies; thinking can't be disabled on this model, effort is the control |
| Effort (coach) | `MEDIUM` | Wording matters more than speed |
| Refusal fallback | `fallbacks: "default"` + header `server-side-fallback-2026-07-01` | If the safety filter wrongly declines a harmless request, the API retries on another model |
| max_tokens | 16000 | Non-streaming default; thinking tokens count toward it |

## Conversation memory
- Assistant mode keeps history and appends the **full** assistant message (`response.toParam()`),
  including thinking blocks, so earlier turns go back unchanged.
- History is **cleared**, not trimmed, at 40 messages: editing earlier turns can make the API
  reject the request on newer accounts ("preserved thinking").
- A failed or refused request removes the unanswered user turn so roles keep alternating.
- Coach suggestions are stateless (no history).

## Errors spoken to the user
401 → key rejected (tap KEY) · 429 → rate limited · other HTTP → status code · network → check internet.

## Cost/latency levers
Switch `MODEL` to `claude-sonnet-5-5` (cheaper) or `claude-haiku-5-5` (cheapest/fastest) if speed or cost matters more than quality.
