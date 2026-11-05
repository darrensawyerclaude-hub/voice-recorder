# Voice Recorder backlog

Ordered by priority. The evidence comes from the review studies in
[play-store-standalone-scan-2026-11.md](../play-store-standalone-scan-2026-11.md).
Free means it's part of the core that stays free forever. Paid means it goes in the subscription.

## Next

| # | Item | Why | Tier |
|---|---|---|---|
| 1 | Longer and harsher reliability tests (B2 60 min, B6 battery saver, B7 restart) | B1 and B5 passed on the S20 FE; these cover the remaining ways a recording gets cut short | — |
| 2 | WAV export | Dolby On complaint; promised as free core in the monetisation rules | Free |
| 3 | Export several recordings at once | Dolby On's "slow one-by-one export" complaint | Free |
| 4 | Lock-screen and instant start: quick settings "Record" button, a cut-down quick record screen that shows over the lock screen (Pause and Stop only, no access to the list, so the Samsung corner shortcut works without unlocking), and a home-screen widget | "Instant start" is second in what users value. Check on Android 14+ whether a quick settings button can start the mic while locked | Free |

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
- Auto-pause for phone and internet calls, auto-resume after (no phone permission needed)
