# Voice Recorder backlog

Ordered by priority. The evidence comes from the review studies in
[play-store-standalone-scan-2026-11.md](../play-store-standalone-scan-2026-11.md).
Positioning (6 Nov 2026): Samsung's free preinstalled recorder already has transcripts, speakers and summaries on Galaxy phones, so lead on what it lacks: works on every Android phone, files that are yours, private by default, call pausing, lossless trim, lock-screen bookmarks. Aim testing and marketing at non-Samsung phones first.
Pricing (decided 6 Nov 2026): one plan, about £1 a month, everything included, no ads.
Nothing may add a running cost for us. "Cost to run" says whether an item needs a server or paid API.

## Next

| # | Item | Why | Cost to run |
|---|---|---|---|
| 1 | Longer and harsher reliability tests (B2 60 min, B6 battery saver, B7 restart) | B1 and B5 passed on the S20 FE; these cover the remaining ways a recording gets cut short | — |
| 2 | Mic choice (built-in, headset, Bluetooth, USB) and manual gain | Requested, and a Dolby On complaint | None |
| 3 | Waveform with playhead, and skip silences on playback | Seen in Samsung's recorder; skip silences was requested in reviews | None |
| 4 | Speaker labels (on-device diarisation via sherpa-onnx) | Biggest visible gap vs Samsung; quality uncertain, test first | None |
| 5 | Before-launch list (name, ID, icon, privacy policy, Play account, billing) | The app ID can't change after the first upload | — |

## Later

| Item | Why | Cost to run |
|---|---|---|
| Translate transcripts (Google ML Kit, on-device, ~30 MB per language) | Seen in Samsung's recorder | None |
| Side-by-side layout on tablets and in landscape | Seen in Samsung's recorder | None |
| Folders or tags | "Naming, folders, search" is in the value list; naming and search are done | None |
| Choice of save folder for new recordings | Requested. Export can already send copies to any folder | None |
| Record straight to WAV (lossless) | Musicians; export to WAV only converts the AAC | None |
| Auto-backup to the user's own Google Drive | People lost recordings when changing phone | None (user's Drive, no server of ours) |
| Summaries, speaker names | AI group. Via the AI-app handoff only; a cloud API would cost us | None if via handoff |
| Search inside transcripts | Needs on-device transcription or a pasted transcript | None |

## Before launch

- Final app name and ID (currently the placeholders "Recorder" and `com.vrecorder.app`; the ID can't change after the first upload)
- Proper app icon
- Google Play developer account, and an organisation account if going the limited-company route
- Privacy policy and Play data-safety form (the app now uses the internet, only to download speech models)
- Move speech-model downloads to Play Asset Delivery (currently Hugging Face and GitHub, third-party hosting)
- Measure transcription battery use unplugged, and speed on a budget phone
- Google Play billing: about £1/month and about £10/year, free trial, renewal reminder, two-tap cancel (needs the Play account to test)
- Lapsed subscribers: play, share and export stay free forever; recording and editing need the subscription
- Store page leads with: "£1 a month. Everything included. No ads. Your files stay yours, even if you cancel."
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
- On-device transcription: Quick (Moonshine) or Accurate (Whisper small), downloaded on first use; split at pauses, silence skipped, British spelling; background service with progress; transcript tab with tap-to-jump, search, copy, share; main search covers transcripts
- Transcribe with an AI app: Gemini, with a British-English transcript prompt incl. bookmarks. ChatGPT and Claude tested and excluded: their apps can't transcribe a shared audio file
- Player: 0.5× to 2× speed, clock with tenths, repeat (all or A–B)
- Edit transcript lines; Summarise sends the transcript text to Gemini
- Instant start: quick settings Record button, home-screen widget, quick record screen over the lock screen (no access to the list)
