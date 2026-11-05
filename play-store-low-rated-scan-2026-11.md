# Play Store scan: heavily downloaded, low-rated apps (3 Nov 2026)

Method: 47 high-install candidate apps checked on the Google Play UK storefront (no tool ranks the whole store by rating, so this is a sample, not a census). Top 10 = lowest rating among 100M+ installs. 60 most-relevant 1-2 star reviews read per app (600 total). Reviews returned are global English, mostly US/UK.

| # | App | Installs | Rating | 1-star share |
|---|---|---|---|---|
| 1 | Digital Wellbeing (Google) | 10bn+ | 2.9 | 47% |
| 2 | Google Fit | 100m+ | 2.9 | 38% |
| 3 | Samsung Wallet | 100m+ | 3.2 | 37% |
| 4 | Wear OS companion (Google) | 100m+ | 3.3 | 35% |
| 5 | Google Assistant | 5bn+ | 3.4 | 32% |
| 6 | Fitbit / Google Health | 100m+ | 3.4 | 29% |
| 7 | Galaxy Wearable (Samsung) | 1bn+ | 3.4 | 34% |
| 8 | Google Home | 1bn+ | 3.5 | 28% |
| 9 | Google Authenticator | 100m+ | 3.5 | 28% |
| 10 | Google One | 1bn+ | 3.7 | 23% |
Near misses: Find Hub 3.8, Smart Switch 3.8, Sonos 3.9 (10m+).

## Findings
- All ten are owned by Google or Samsung; most are preinstalled. Huge installs are forced, not chosen, and forced install is itself a top complaint (Wellbeing, Assistant, Wallet, One).
- Dominant root cause is owner policy/backend decisions (feature removal, migrations to Gemini / Google Health / Samsung Wallet, billing bundles, dropped device support), then reliability bugs. Pure UI faults are a minority. Confirms the parked note: "failures usually in the service behind the app".
- Orphaned hardware is a recurring theme: Gear S3, older Galaxy Buds, 1st-gen Chromecast and Home Mini, older Nest devices, Pixel/Galaxy watches needing repeated resets. Supports the orphaned-device-revival candidate.
- Third-party openings found: local smart-home control (Home Assistant-style) for Google Home refugees; authenticator export/migration; Google storage analyser/cleanup; Wear OS scripted watch faces after the WatchMaker block; Fitbit leavers moving to Garmin. Most are digital and crowded.

## Implication
Rebuilding these apps is not available: the owner holds the platform, accounts and device firmware. Stays parked. The physical angle (orphaned devices) gains evidence. Next check for it remains s.50B CDPA scope.
