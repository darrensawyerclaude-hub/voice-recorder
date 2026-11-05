# Voice Recorder test sheet

Run this on a real phone for every build that will go to testers or the Play Store.
Copy the "Run log" table for each new run.

## Run log

| Date | Build (versionName) | Phone / Android | Tester | Passed | Failed | Notes |
|---|---|---|---|---|---|---|
| 2026-11-05 | 0.1.0 (1st build) | Galaxy S20 FE / 13 | Darren | P1, P2 | R3, R4 | Timer frozen at 00:00; playback too quiet even at full volume |
| 2026-11-05 | 0.1.0 (3rd build: recording gain, naming) | Galaxy S20 FE / 13 | Darren | R3, R4, P1, N1, N3, F3 | | Speech measured at -16 dBFS (was -25), noise floor -65 dBFS, peak -1.3 dBFS |

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
| B1 | Start recording, lock the screen for 10 min, then unlock and stop. | Timer shows about 10 min. The file is the full length. | |
| B2 | Same as B1 but 60 min. | Full length, no gaps. | |
| B3 | Record, switch to 3 other apps (camera, browser, a game), then come back. | Still recording. Timer is correct. | |
| B4 | Record, then swipe the app away from Recent apps. | Recording carries on (notification stays). Stopping it works. | |
| B5 | Record for 30 s, then Settings → Apps → Recorder → Force stop. | The file up to that point still plays. | |
| B6 | Record, then let the battery saver turn on (or turn it on manually). | Recording carries on. | |
| B7 | Restart the phone during a recording. | The file up to the restart still plays. | |
| B8 | Recording while a call comes in. Answer it, then hang up. | Note what happens. Calls aren't handled yet, so this records current behaviour. | |
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
| F18 | Tap a recording on a narrow phone or with large text. | All five buttons are visible, wrapping onto a second line if needed. | |

## 6. Help

| ID | Steps | Expected | Result |
|---|---|---|---|
| H1 | Tap Help at the top right. | The Help screen opens and scrolls. Every section is readable. | Pass (6th build) |
| H2 | Tap Close, then open Help again and press the phone's Back button. | Both return to the recorder screen, not out of the app. | Pass (6th build) |
| H3 | Follow each Settings path in Help on this phone. | Each path exists as written. Note any that differ. | |
| H4 | Tap "Open Recorder's settings". | The phone's settings page for Recorder opens. | |
| H5 | Open Help while recording. | Recording carries on. Closing Help shows the timer still running. | |

## 7. Phones to cover before launch

Background recording behaves differently by brand, so tick off at least one phone from each:

- [x] Samsung (Galaxy S20 FE, Android 13)
- [ ] Google Pixel
- [ ] Xiaomi / Redmi / POCO
- [ ] One older or budget phone (Android 10 or 11)
