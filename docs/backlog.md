# Voice Recorder backlog

Ordered by priority. The evidence comes from the review studies in
[play-store-standalone-scan-2026-11.md](../play-store-standalone-scan-2026-11.md).
Free means it's part of the core that stays free forever. Paid means it goes in the subscription.

## Next

| # | Item | Why | Tier |
|---|---|---|---|
| 1 | Longer and harsher reliability tests (B2 60 min, B6 battery saver, B7 restart) | B1 and B5 passed on the S20 FE; these cover the remaining ways a recording gets cut short | — |
| 2 | Mic choice (built-in, headset, Bluetooth, USB) and manual gain | Requested, and a Dolby On complaint | Free |
| 3 | Before-launch list (name, ID, icon, privacy policy, Play account) | The app ID can't change after the first upload | — |

## Later

| Item | Why | Tier |
|---|---|---|
| Folders or tags | "Naming, folders, search" is in the value list; naming and search are done | Free |
| Skip silence on playback | Requested | Free |
| Choice of save folder for new recordings | Requested. Export can already send copies to any folder | Free |
| Record straight to WAV (lossless) | Musicians; export to WAV only converts the AAC | Free |
| Auto-backup to Google Drive | People lost recordings when changing phone | Paid |
| Transcription (British spelling) | AI group; accessibility users | Paid |
| Summaries, speaker names | AI group | Paid |
| Search inside transcripts | Ongoing-cost feature | Paid |

## Before launch

- Final app name and ID (currently the placeholders "Recorder" and `com.vrecorder.app`; the ID can't change after the first upload)
- Proper app icon
- Google Play developer account, and an organisation account if going the limited-company route
- Privacy policy and Play data-safety form
- Test on a Pixel, a Xiaomi and an older phone (test sheet section 10)

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
- After-call summary in the app, notification and naming box
- Multi-select (long press): share, export or delete several at once
- Export as M4A (default), AAC or WAV to any folder (Downloads, SD card, Drive, USB) via the system folder picker
- Share as M4A for compatibility; offers a smaller speech-quality copy over 20 MB (email limits)
- Playback screen: timeline, ±10 s, speed 0.75–2×
- Bookmarks while recording (app, quick screen, notification) and while playing; rename, delete, jump
- Trim and cut out a middle section, frame-exact with no re-encoding; save as copy or replace; bookmarks follow the edit
- Instant start: quick settings Record button, home-screen widget, quick record screen over the lock screen (no access to the list)
