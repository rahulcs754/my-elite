# Roadmap

| Phase | Feature | Status |
|---|---|---|
| 1 | Voice loop: tap → STT → Claude → TTS | Done (code), not yet run on a device |
| 2 | Camera, gallery, battery actions | Done (code) |
| – | In-app API key entry | Done |
| – | Conversation Coach ("Elite Elite") | Done (code) |
| – | APK build | Blocked here; see [Building the APK](04-building-the-apk.md) |
| 3 | Read notifications aloud | Next: `NotificationListenerService` + user grant in settings |
| 3 | Set alarms | Next: `AlarmClock.ACTION_SET_ALARM`, tag with time argument |
| 3 | Check location | Next: Fused Location Provider, `ACCESS_FINE_LOCATION`, reverse geocode to speak the place |
