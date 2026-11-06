# On-device transcription test (6 Nov 2026)

Phone: Galaxy S20 FE 5G (SM-G781B, Snapdragon 865, Android 13). Library: sherpa-onnx 2.0.1,
compressed (int8/quantised) English models, 4 threads, audio fed in fixed 28-second chunks.
Battery use couldn't be measured because the phone was charging over USB throughout.

## Accuracy: 2 min 7 s of clear speech

Darren reading a 266-word news article (figures, currencies, names like Nvidia, Infineon, ASML,
Ken Paxton). Word error rate = share of words wrong, missing or extra, after treating "$2.5tn"
and "2.5 trillion dollars" as the same.

| Model | Download | Word error rate | Time for 2 min 7 s | Speed vs recording |
|---|---|---|---|---|
| Whisper small | 375 MB | **5.3%** (about 95% right) | 61.8 s | 2× faster |
| Moonshine base | 141 MB | 7.9% | **8.2 s** | 15× faster |
| Whisper base | 160 MB | 10.2% | 15.3 s | 8× faster |

What went wrong:
- Some "errors" are the reading itself (e.g. all three heard "having more and shaving more").
- Several errors sit at the 28-second chunk joins, where a word is cut in half ("sell-off"
  became "off"). The real feature should split at pauses (voice activity detection), not fixed
  times; that should lower every model's error rate.
- Whisper base: "Democratic Tsun army", "Chitmaker, Infini and", "turned below".
- Moonshine: one chunk came out without capitals or punctuation; "in finium" for Infineon.
- Whisper small: got Infineon, tsunami and the figures right; main slips were "share of thought"
  for "share falls" and "express" for "expressed".

## Speed and heat: 10 min 47 s, mostly background noise

| Model | Time | Temperature rise |
|---|---|---|
| Whisper base | 38 s | +3.2 °C |
| Moonshine base | 41 s | +1.3 °C |
| Whisper small | 87 s | +2.4 °C |

On near-silence Whisper invents labels and repeats itself ("(buzzing) (buzzing)…"), another
reason to cut out silent stretches before transcribing.

## What this means

- On-device transcription is practical on this phone. For a 1-hour meeting of continuous
  speech, roughly 4 minutes with Moonshine or about 30 minutes with Whisper small. Expect
  budget phones to be several times slower.
- Whisper small is accurate enough to be useful (about 95% on clear speech); Moonshine is
  close and far faster.
- A sensible offer: **Quick** (Moonshine, 141 MB) and **Accurate** (Whisper small, 375 MB),
  downloaded on first use through Play Asset Delivery. Both MIT licensed for English.
- Still to measure: battery use off the charger, a budget phone, and a real meeting with
  several speakers and background noise.
