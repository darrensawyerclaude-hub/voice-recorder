# Play Store scan 2: standalone apps, heavy installs, poor ratings (3 Nov 2026)

Brief (Darren): high download / poor rating apps that don't rely on a third party.
Method: 30 utility-category searches on Google Play UK (~770 apps seen). Kept 10m+ installs, rating under 4.0, excluding preinstalled system apps and apps whose core needs a cloud service. 12 candidates; 702 one- and two-star reviews read (60 per app, 18 for one).

| App | Installs | Rating | Main cause of poor rating | Rebuild verdict |
|---|---|---|---|---|
| Dolby On (recorder) | 10m+ | 3.6 | Recordings locked in a hidden folder, slow one-by-one export, lost recordings, missing basics (pause, WAV, mic choice) | Strong: users love the sound, name no replacement |
| Muzio (music player) | 100m+ | 3.0 | Lifetime purchases revoked for a ~£5.49/week subscription (early 2025); call-log permission forced for after-call ads (mid 2025); unskippable ads | Strong demand, crowded alternatives (Poweramp, Musicolet) |
| Moon+ Reader (ebooks) | 10m+ | 3.7 | Aggressive video ads; subscription replacing one-off Pro; lost reading position | Strong: readers say they'd pay once |
| Nova Launcher | 100m+ | 3.2 | After ownership change: audio video ads in the app drawer (spike Jul-Sep 2026), paid users pushed to subscription, forced AI search | Users leaving now; launcher is heavy engineering; wedge = import Nova backup |
| VLC | 500m+ | 3.6 | Regression spike v3.7 (2026): clunky UI, library rescans, lost resume | Not as a general player (free, open source); niche: audiobook/driving player |
| FX File Explorer | 10m+ | 3.3 | Abandoned since Aug 2023; copy/move file loss; SD card failures | Crowded category |
| ASTRO File Manager | 50m+ | 3.8 | Forced data-collection consent; features removed; total failure in v8.15.3 (Sep 2026) | Crowded category |
| Garage Ringtones | 10m+ | 3.6 | Hard paywall with no close button; wrong playback speed | Easy build, young users who pay little |
| Colliz DIY wallpaper | 10m+ | 3.0 | Ad farm: an ad on every click; store ads show features it lacks | Easy build, young users who pay little |
| SD Maid 1 | 10m+ | 3.7 | Android 11+ limits what cleaners can do; replaced by owner's SD Maid 2 | No: platform limits |
| ScanLife | 10m+ | 2.6 | Abandoned; product lookup relies on a dead server | No |
| Phone Cleaner | 10m+ | 3.7 | Scareware/adware category | No |

## Findings
- In standalone apps the cause is the owner, not the code: six of twelve went bad through a monetisation pivot (revoked lifetime licences, weekly subscriptions, intrusive ads, data harvesting), usually after a change of ownership or strategy. Two decayed through abandonment. Only Dolby On and VLC fail mainly on UX.
- The same promise recurs across categories: pay once, no ads, files stay yours, licence honoured forever.
- Users are actively leaving and naming alternatives, so the barrier is distribution and switching, not building. A one-tap importer from the failing app (Nova layout, Muzio playlists, Moon+ library and positions) is the wedge.
- Distinct from the thesis: this is a digital lane with low barriers to entry, and every category already has free open-source options.

## Decision: monetisation (Darren, 3 Nov 2026)
Cheap subscription, chosen over a one-off unlock for recurring revenue. Rules, drawn from what the reviews punished:
- Core job free forever (for the recorder: recording and clean WAV export). No paywall on the core task.
- Subscription pays for features with a real ongoing cost (transcription, summaries, search across recordings, cloud backup).
- Monthly and annual plans only; no weekly pricing. Annual is the default. Price to be set after checking comparable recorder apps.
- Two-tap cancellation, no silent trial conversion, reminder before renewal.
- Never move a free feature behind the paywall; state this on the store page.
- No ads, ever. No lifetime option at launch; add only if reviews ask for it.

## Recorder: what good looks like (positive-review study, 3 Nov 2026)
Method: 410 four- and five-star reviews across 8 apps (Dolby On, Splend, Easy Voice Recorder, "quality apps" Voice Recorder, Smart Voice Recorder, Hi-Q MP3, Otter, Summary AI Note Taker). About 15% of reviews discounted as low-signal; Summary's 4.7 looks inflated (about 40% low-signal, prompted right after first use).

Two distinct buyer groups:
- Classic recorder users (music and song ideas are the largest use, then lectures, meetings, interviews, journalling). Value, in order: never losing a recording (screen off, background, interrupted-file recovery); instant start (widget, one tap); sound clarity and gain control; open files and export (visible folder, cloud upload, batch export); few or no ads; naming, folders, search; pause/resume. They pay small one-off sums (£1.29-£6.49 on Play) to remove ads or support the developer. No reviewer praised a subscription.
- AI transcription/summary users (meetings, lectures, interviews, plus a strong accessibility and neurodivergent group: hard of hearing, ADHD, dysgraphia, memory loss). Value: accuracy, being present instead of writing notes, an actionable summary, speaker names. They do pay subscriptions (Otter £11.99-£96.99, Summary £7.99-£70.99 on Play) when it removes a job or disability barrier; occasional users say they are priced out and ask for a cheaper tier or pay-per-use.

Most requested features in positive reviews: pause button; batch export and choice of save folder; auto-backup to Drive (people lost recordings changing phone); pause on incoming call; trim and cut a middle section; mic choice and gain; skip silence; bookmarks; better speaker identification; British spelling.

Implication: the subscription works for the transcription group, not the classic group. Product shape = excellent free recorder (reliability, files yours, no ads) as the acquisition engine, with a cheap monthly subscription for transcription, summaries, search and backup aimed at occasional meeting and lecture users priced out of Otter. Risks: Samsung and Google build transcription into their own free recorders; on-device transcription weakens the "ongoing cost" justification even though it keeps our cost low.

## First test
Dolby On replacement recorder. Kill if fewer than 100 paying subscribers within 60 days of launch. Open: Darren's yes on opening the digital lane alongside the fit library; Google Play developer account needed before launch (fee, identity verification, current tester rule to check).
