# Voice Recorder test sheet

Run this on a real phone for every build that will go to testers or the Play Store.
Copy the "Run log" table for each new run.

## Run log

| Date | Build (versionName) | Phone / Android | Tester | Passed | Failed | Notes |
|---|---|---|---|---|---|---|
| 2026-11-05 | 0.1.0 (1st build) | Galaxy S20 FE / 13 | Darren | P1, P2 | R3, R4 | Timer frozen at 00:00; playback too quiet even at full volume |
| 2026-11-05 | 0.1.0 (3rd build: recording gain, naming) | Galaxy S20 FE / 13 | Darren | R3, R4, P1, N1, N3, F3 | | Speech measured at -16 dBFS (was -25), noise floor -65 dBFS, peak -1.3 dBFS |
| 2026-11-05 | 0.1.0 (6th build: list, open folder, Help) | Galaxy S20 FE / 13 | Darren | F4, H1, H2, B1, B5 | | B1: 10 min 47 s locked, no gaps. B5: force stop kept all 30 s |

Result codes: **Pass**, **Fail** (add a note and the time it happened, so it can be matched to the log), **Skip**.

## 1. Recording basics

| ID | Steps | Expected | Result |
|---|---|---|---|
| R1 | Fresh install. Tap Record. | Asked for microphone and notification permission. After allowing, recording starts. | |
| R2 | Deny microphone permission, then tap Record again. | Asked again or nothing records. No crash. | |
| R3 | Record for 30 s. | Timer counts up every second. | Pass (3rd build) |
| R4 | Record speech from about 50 cm away, then play it back with media volume at half. | Clearly audible. Speech level is similar to the phone's own Samsung Voice Recorder. | Pass (3rd build) |
| R5 | Watch the level bar while speaking, then while silent. | Bar moves with your voice and drops when you're silent. | |
| R6 | Shout or clap close to the mic. | Playback is loud but not crackly or distorted. | |
| R7 | Record in a quiet room and stay silent for 10 s. | Background hiss isn't pumped up to an obvious level. | |

## 2. Pause and stop

| ID | Steps | Expected | Result |
|---|---|---|---|
| P1 | Record, tap Pause, wait 10 s, tap Resume, then Stop. | Timer freezes while paused. The file has no 10 s gap. | Pass (1st and 3rd builds) |
| P2 | Pause and resume from the notification. | Same as P1. The notification buttons change between Pause and Resume. | Pass (1st build) |
| P3 | Pause and resume 5 times in one recording. | Plays back smoothly with no clicks or glitches at the joins. | |
| P4 | Stop from the notification while the app is closed. | Notification disappears. When you open the app, it asks you to name the recording. | |

## 3. Naming

| ID | Steps | Expected | Result |
|---|---|---|---|
| N1 | Stop a recording. | The "Name this recording" box appears with the default name selected. | Pass (3rd build) |
| N2 | Tap "Keep default". | Saved as Recording_date_time. | |
| N3 | Type "Meeting with Sam" and tap Save. | The list and My Files both show "Meeting with Sam.aac". | Pass (3rd build) |
| N4 | Rename an older recording from the list. | The name changes in the list and in My Files. | |
| N5 | Rename a recording to a name that's already used. | Shows "Couldn't use that name" or picks a unique name. Nothing is lost. | |
| N6 | Use characters like / : ? * in the name. | Replaced with _ or rejected. No crash. | |
| N7 | Clear the name entirely and tap Save. | Shows "Couldn't use that name". The original name is kept. | |

## 4. Background and reliability

| ID | Steps | Expected | Result |
|---|---|---|---|
| B1 | Start recording, lock the screen for 10 min, then unlock and stop. | Timer shows about 10 min. The file is the full length. | Pass (6th build): 10 min 47 s, no gaps, no background kill in the log |
| B2 | Same as B1 but 60 min. | Full length, no gaps. | |
| B3 | Record, switch to 3 other apps (camera, browser, a game), then come back. | Still recording. Timer is correct. | |
| B4 | Record, then swipe the app away from Recent apps. | Recording carries on (notification stays). Stopping it works. | |
| B5 | Record for 30 s, then Settings → Apps → Recorder → Force stop. | The file up to that point still plays. | Pass (6th build): 30 s kept, plays to the end, no gaps |
| B6 | Record, then let the battery saver turn on (or turn it on manually). | Recording carries on. | |
| B7 | Restart the phone during a recording. | The file up to the restart still plays. | |
| B8 | Recording while a call comes in. Answer it, talk for 20 s, then hang up. | Pauses as soon as it rings ("Paused for call" in the app and notification, timer frozen). About 2 s after hanging up it resumes by itself. Neither the ringtone nor the call is in the file. | |
| B8a | Recording while a call comes in. Decline it. | Pauses while ringing, resumes about 2 s after you decline. | |
| B8b | Recording, then a WhatsApp (or other internet) call comes in. Answer, then hang up. | Same as B8. | |
| B8c | Tap Pause yourself, then take a call and hang up. | Stays paused after the call (it only auto-resumes if the call paused it). | |
| B8d | During a call that paused the recording, tap Resume. | Recording resumes and doesn't pause again during that call. Android may record silence until the call ends. | |
| B8e | After B8, look at the app and the notification while still recording. | Both say "Paused for a [length] call at [time] · resumed". No pop-up or buzz appeared during the call. | |
| B8f | Stop the recording from B8. | The naming box says "Paused for a [length] call at [time]. The call isn't in the recording." | |
| B8g | Take two calls during one recording, then stop. | It says "Paused for 2 calls, [total] in total". | |
| B9 | Another app uses the mic (e.g. a WhatsApp voice note) during a recording. | Note what happens. | |

## 5. Files and list

| ID | Steps | Expected | Result |
|---|---|---|---|
| F1 | Open My Files → Recordings → VoiceRecorder. | All recordings are there with the right names. | |
| F2 | Play a recording in another app (Samsung Music, VLC). | Plays at the same volume as in this app. | |
| F3 | Share a recording to WhatsApp or email. | Arrives and plays. | Pass (3rd build, WhatsApp) |
| F4 | Tap a recording, then Delete. | Asks "Delete recording?" first. Cancel keeps it. Delete removes it from the list and from My Files. | Pass (4th build) |
| F5 | Uninstall and reinstall the app. | Old recordings are still in My Files. | |
| F6 | Make 30+ recordings. | The list scrolls smoothly, newest first. | |
| F7 | Look at the list without tapping anything. | Each row shows only name, date, length and size. No buttons. | |
| F8 | Tap a recording, then tap it again. Then tap a different one. | The first tap shows Play / Rename / Share / Delete, the second hides them. Only one row is open at a time. | |
| F9 | While recording, look at the list. | The file being recorded shows "Recording…" and can't be opened. | |
| F10 | Check the length shown on a row against the timer when you stopped. | Within a second or two. | |
| F11 | Type part of a name in Search. | The list narrows to matching names, ignoring upper/lower case. Clear restores the full list. | |
| F12 | Search for something that doesn't exist. | Shows "No recordings match …". | |
| F13 | Try each Sort option. | Newest, Oldest, Name A–Z and Longest each reorder the list correctly. | |
| F14 | Pick a sort, close the app fully, reopen. | The same sort is still selected. | |
| F15 | Tap Open folder (next to "Saved to …"). | The phone's file manager opens at Recordings/VoiceRecorder (My Files on Samsung), showing your recordings. | |
| F16 | Same as F15 on a phone without that file manager (Pixel, Xiaomi). | The system Files app opens at the folder, or a message gives the folder path. Never nothing. | |
| F17 | Tap a recording, then Open with. | A list of apps that can play audio appears. Picking one plays the recording. | |
| F18 | Tap a recording on a narrow phone or with large text. | All six buttons are visible, wrapping onto a second line if needed. | |

## 6. Selecting and exporting

| ID | Steps | Expected | Result |
|---|---|---|---|
| E1 | Press and hold a recording. | Ticks appear on every row, the held one is ticked, and the top shows "1 selected" with Share, Export, Delete. | |
| E2 | Tap two more rows, then tap one again. | Ticks toggle. The count updates. | |
| E3 | Tap Select all, then Cancel. Then select again and press the phone's Back button. | Select all ticks everything shown (respects Search). Cancel and Back both leave selection mode without leaving the app. | |
| E4 | Select 3, tap Share, pick WhatsApp or email. | All 3 files arrive and play. | |
| E5 | Select 2, tap Delete. | Asks "Delete 2 recordings?". Delete removes both from the list and My Files. | |
| E6 | Select 3, Export → AAC → choose Downloads. | "Exported 3 recordings as AAC". The files are in Downloads with the same names. The originals are still in the app. | |
| E7 | Export one recording as WAV to Downloads. | A .wav about 10× the size of the .aac. It plays in Samsung Music or VLC with the same length and sound. | Pass (build 9): valid 48 kHz mono 16-bit WAV, 39 s, same length and level as the original |
| E8 | Export the same recording again to the same folder. | A second copy is saved with "(1)" in its name. Nothing is overwritten. | |
| E9 | Export to Google Drive (pick Drive in the folder picker). | Files appear in Drive. | |
| E10 | Export a second time. | The folder picker opens in the folder used last time. | |
| E11 | Tap a single recording, then Export. | The same format choice and folder picker, for just that one. | |
| E12 | Export a 10+ minute recording as WAV. | A progress box shows while it works. The WAV plays to the end. | |
| E13 | Export one recording as M4A (the default) to Downloads. | A .m4a about the same size as the .aac. Plays in Samsung Music. | |
| E14 | Share a short recording (under 20 MB) to Gmail and send it to yourself. Open it on a PC, Mac or iPhone. | Arrives as .m4a and plays there. No size question was asked. | Partial (build 9): sent via Gmail OK; opening on a non-Android device not yet confirmed |
| E15 | Share the 10-minute B1 recording plus another long one (over 20 MB together). | Asks "Too big for some email?" with the full size and the smaller copy's size. | |
| E16 | In E15, tap "Send smaller copy" and email it. | Files are named "... (small).m4a", about a quarter of the size estimate's original. Speech is clear on playback. | |
| E17 | In E15, tap "Full quality". | Sends full-size M4A files. | |
| E18 | Share one recording to WhatsApp. | Arrives and plays as an audio file. | |

## 7. Instant start

| ID | Steps | Expected | Result |
|---|---|---|---|
| Q1 | Add the Record button to quick settings (edit buttons, drag Record in). | It's listed with the red mic icon and labelled Record. | |
| Q2 | Unlocked: pull down quick settings, tap Record. | The shade closes, the quick record screen opens and recording starts at once. The button shows as on. | |
| Q3 | Tap the Record button again while recording. | Recording stops. The button goes back to off. Next time you open the app, it asks you to name the recording. | |
| Q4 | Lock the phone. Pull down quick settings on the lock screen and tap Record. | The quick record screen shows over the lock screen without asking you to unlock, and recording starts. | |
| Q5 | On the quick record screen while locked, try to reach the list of recordings. | There's no way to. Only Pause/Resume and Stop. | |
| Q6 | On the quick record screen, tap Pause, then Resume, then Stop. | Each works. Stop closes the screen. | |
| Q7 | Start from quick settings, then press Home or lock the phone. | Recording carries on (notification shows). | |
| Q8 | Add the Recorder widget to the home screen. | It shows "● Record / Tap to record". | |
| Q9 | Tap the widget. | Quick record screen opens and recording starts. Back on the home screen, the widget shows "■ Stop", a running timer and "Recording". | |
| Q10 | Pause from the notification, look at the widget. | Timer stops, says "Paused". | |
| Q11 | Tap the widget while recording. | Recording stops. Widget goes back to "● Record". | |
| Q12 | Samsung: Settings → Lock screen → Shortcuts → set one corner to Recorder. Swipe it from the lock screen. | Note what happens (it may open the main app and ask to unlock; the quick settings button is the reliable lock-screen route). | |
| Q13 | Fresh install, microphone not yet allowed, tap the widget. | The quick screen says to open the app once and allow the microphone. No crash. | |

## 8. Playback, bookmarks, trim and cut

| ID | Steps | Expected | Result |
|---|---|---|---|
| K1 | While recording, tap Bookmark in the app 3 times about 10 s apart. | Shows "3 · last at 00:2x". The notification also says "3 bookmarks · last at …". | |
| K2 | Record with the phone locked; tap Bookmark in the notification. | Bookmark is added without unlocking. | |
| K3 | On the quick record screen, tap "Bookmark this moment". | Count goes up. | |
| K4 | Stop. Look at the row in the list. | It shows "3 bookmarks". | |
| K5 | Tap the recording, then Play. | Player screen opens: timeline with red bookmark marks, elapsed / total time, Play, −10 s, +10 s, 1×. | |
| K6 | Play, drag the timeline, skip ±10 s. | Moves accurately; times update. | |
| K7 | Tap 1× repeatedly while playing. | Cycles 1.25×, 1.5×, 2×, 0.75×, 1×, and the speed changes without the pitch going chipmunk. | |
| K8 | Tap a bookmark in the list. | Jumps to that point (within a fraction of a second of where it was marked). | |
| K9 | Rename a bookmark, delete another. | Both stick after closing and reopening the player. | |
| K10 | Tap "Bookmark here" while playing. | A new bookmark appears at the current time. | |
| K11 | Rename the recording from the list, reopen the player. | Bookmarks are still there. | |
| K12 | Trim / cut → Keep only the selection. Drag the handles to keep 00:05–00:15. Preview. | Plays 00:05 to 00:15 and stops. | |
| K13 | Save as copy. | "(trimmed)" copy appears in the list, about 10 s long. Original unchanged. Bookmarks inside the kept part are on the copy, shifted to match. | |
| K14 | Trim / cut → Cut out the selection, select 00:05–00:15, Preview. | Plays from about 00:02, skips from 00:05 straight to 00:15, stops about 3 s later. | |
| K15 | Save as copy. | "(cut)" copy is 10 s shorter, no click or glitch at the join. Bookmarks after the cut are moved 10 s earlier; ones inside it are gone. | |
| K16 | Tap a bookmark, then Start here; tap another, then End here. | The selection snaps to the two bookmarks. | |
| K17 | Replace original. | Asks to confirm first. Afterwards the recording is shorter and the original audio outside the selection is gone. | |
| K18 | Delete a recording that had bookmarks, then make a new one. | The new one starts with no bookmarks. | |

## 8b. Transcribe

| ID | Steps | Expected | Result |
|---|---|---|---|
| T1 | Player → Transcribe. | Shows "On this phone" (not yet available, with its limits) and the AI apps installed that accept audio, plus "Other app…". Only Gemini is listed by name; others are under "Other app…". | |
| T2 | Pick Gemini. | Gemini opens with the recording attached and the instructions filled in (or ready to paste). | Pass (6 Nov): Gemini worked |
| T3 | Gemini's reply. | British spelling, [mm:ss] timestamps, Speaker 1/2 labels, summary, action points, offered as a .txt file. Bookmarks marked. | |
| T4 | ChatGPT (via Other app…). | Not suitable: rejects M4A; given a valid 16 kHz WAV it runs code over it and says the words can't be transcribed. | Fail (6 Nov), removed from the list |
| T5 | Claude (via Other app…). | Not suitable: tries to install tools rather than listen. | Fail (6 Nov), removed from the list |
| T6 | Transcribe a 30+ minute recording with each app. | Note the length limit each app hits. | |

| T7 | Transcribe → On this phone → Quick, on a 2-minute recording (first time). | Shows download progress (111 MB), then transcribing progress, in the player and a notification. "Transcript ready" notification at the end. | |
| T8 | Same with Accurate. | 375 MB download, then transcribes. Slower but fewer mistakes. | |
| T9 | Start a transcription, then leave the app and lock the phone. | It finishes; the notification opens the recording. | |
| T10 | Cancel during download, and during transcribing. | Stops. A part-download is removed. Nothing half-saved. | |
| T11 | Transcript tab: tap a line. | Jumps to that point; the current line is highlighted while playing. Bookmarks show in place. | |
| T12 | Search inside the transcript, then Copy and Share. | Filters lines; Copy and Share give the whole transcript with timestamps and bookmarks. | |
| T13 | Search the main list for a word that was said but isn't in any name. | The recording appears. Its row says "transcript". | |
| T14 | Replace original after a trim/cut on a transcribed recording. | The old transcript is removed (it no longer lines up). | |
| T15 | Remove downloaded models. | Models are deleted; the next transcription downloads again. | |
| T16 | Transcribe a 30+ minute recording, unplugged. | Note time taken, battery used and how warm the phone gets. | |

## 9. Help

| ID | Steps | Expected | Result |
|---|---|---|---|
| H1 | Tap Help at the top right. | The Help screen opens and scrolls. Every section is readable. | Pass (6th build) |
| H2 | Tap Close, then open Help again and press the phone's Back button. | Both return to the recorder screen, not out of the app. | Pass (6th build) |
| H3 | Follow each Settings path in Help on this phone. | Each path exists as written. Note any that differ. | |
| H4 | Tap "Open Recorder's settings". | The phone's settings page for Recorder opens. | |
| H5 | Open Help while recording. | Recording carries on. Closing Help shows the timer still running. | |

## 10. Phones to cover before launch

Background recording behaves differently by brand, so tick off at least one phone from each:

- [x] Samsung (Galaxy S20 FE, Android 13)
- [ ] Google Pixel
- [ ] Xiaomi / Redmi / POCO
- [ ] One older or budget phone (Android 10 or 11)
