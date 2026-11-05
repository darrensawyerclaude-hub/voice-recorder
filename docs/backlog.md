# Voice Recorder backlog

Ordered by priority. The evidence comes from the review studies in
[play-store-standalone-scan-2026-11.md](../play-store-standalone-scan-2026-11.md).
Free means it's part of the core that stays free forever. Paid means it goes in the subscription.

## Next

| # | Item | Why | Tier |
|---|---|---|---|
| 1 | Run background and force-stop tests (B1, B5) | "Never losing a recording" is the top thing users value, and it's still unproven | — |
| 2 | Pause on incoming call, resume after | One of the most-requested features; test B8 currently has no handling | Free |
| 3 | WAV export | Dolby On complaint; promised as free core in the monetisation rules | Free |
| 4 | Export several recordings at once | Dolby On's "slow one-by-one export" complaint | Free |
| 5 | Lock-screen and instant start: quick settings "Record" button, a cut-down quick record screen that shows over the lock screen (Pause and Stop only, no access to the list, so the Samsung corner shortcut works without unlocking), and a home-screen widget | "Instant start" is second in what users value. Check on Android 14+ whether a quick settings button can start the mic while locked | Free |

## Later

| Item | Why | Tier |
|---|---|---|
| Trim, and cut out a middle section | Requested in positive reviews | Free |
| Mic choice (built-in, headset, Bluetooth, USB) and manual gain | Requested, and a Dolby On complaint | Free |
| Folders or tags | "Naming, folders, search" is in the value list; naming and search are done | Free |
| Bookmarks during recording | Requested | Free |
| Skip silence on playback | Requested | Free |
| Choice of save folder | Requested | Free |
| Auto-backup to Google Drive | People lost recordings when changing phone | Paid |
| Transcription (British spelling) | AI group; accessibility users | Paid |
| Summaries, speaker names | AI group | Paid |
| Search inside transcripts | Ongoing-cost feature | Paid |

## Before launch

- Final app name and ID (currently the placeholders "Recorder" and `com.vrecorder.app`; the ID can't change after the first upload)
- Proper app icon
- Google Play developer account, and an organisation account if going the limited-company route
- Privacy policy and Play data-safety form
- Test on a Pixel, a Xiaomi and an older phone (test sheet section 6)

## Done

- Record, pause, resume and stop, from the app and the notification
- Background recording (foreground service)
- Crash-safe format (AAC in an ADTS stream)
- Visible folder (Recordings/VoiceRecorder), Open folder button
- Automatic gain and limiter (speech at about -16 dBFS)
- Level meter
- Name on save, rename
- Search, sort (newest, oldest, name, longest)
- Collapsed list rows, delete confirmation
- Share, Open with
- Help screen
