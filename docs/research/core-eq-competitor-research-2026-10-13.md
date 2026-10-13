# Core EQ — competitor research and ranked roadmap (2026-10-13)

Research date: 2026-10-13. This pass updates the 2026-09-28 market survey and the
2026-10-10 gap review against what is shipping now. It is a sixth pass, not a
replacement for them. Competitor claims come from vendor pages, store listings
and reviews, and are marked where they are only listing copy. Core EQ claims
come from this repository's code and changelog.

No code changed in this pass. Section 5 lists the decisions needed before any
of the roadmap items are built.

---

## 1. Summary

Core EQ's distinctive position is still the one the earlier passes found: it is
the only Android TV app that measures the room with the remote's microphone and
builds a correction from that measurement. That is not the same as being the
best room-correction product. Commercial systems lead on correction depth:
multi-seat averaging, user-set correction limits, target-curve editing, and
phase/time correction. Core EQ is behind on all four, and it should not claim
parity on any of them until a device run shows it.

Core EQ is already ahead of the TV-native equalisers on measurement, safety
limits and import. It is roughly level with the phone-side tools on import and
loudness, and behind them on per-device profile polish.

The ranked gap list in section 4 is the proposed route. The first two items are
the cheapest way to close the gap without new hardware.

---

## 2. Competitors, as of this research

### 2.1 Android TV / Google TV equalisers (direct category)

- **SoundWave TV: Equalizer** (Nexech, Google Play). 5- to 10-band graphic EQ
  using the platform `Equalizer` API, bass boost, virtualiser, reverb and genre
  presets. Still manual sliders with no measurement. Its listing shows version
  12.18.1 dated 2026-09-11, with a NextUI migration and "AI features" in the
  notes ([listing copy](https://soundwave-eq-tv.en.aptoide.com/app)).
  The 2026-09-28 pass recorded a 3.8/5 rating and reports of audio dropouts
  after sleep; those reports have not been re-checked in this pass.
- **TV DSP Center** (Play Store). Manual EQ with TV-targeted presets. No
  measurement (see the 2026-09-28 survey, §2).

Core EQ position: ahead on measurement, correction limits and import. Behind on
polish for a 10-foot remote user.

### 2.2 Phone-side system equalisers (ported ideas)

- **Wavelet: headphone equalizer** (Pittvandewitt, Google Play, open source).
  Its listing claims AutoEq for 5,000+ headphone profiles, import from
  autoeq.app and squig.link, a limiter with automatic preamp, a 9-band graphic
  EQ, channel balance, a virtualiser, reverberation, and ISO 226 equal-loudness
  compensation that follows the listening volume. Profiles load automatically
  when a headphone changes ([listing](https://play.google.com/store/apps/details?id=com.pittvandewitt.wavelet&hl=en_US),
  [feature docs](https://pittvandewitt.github.io/Wavelet/Features/)).
  Its Troubleshooting page notes that some features disappear in AIDL mode,
  which is a useful reminder that system-wide effects depend on the device's
  effect-session behaviour.
- **Equalizer314** (F-Droid). Pairs `DynamicsProcessing` with `Visualizer` to
  keep permissions minimal ([F-Droid](https://f-droid.org/en/packages/com.bearinmind.equalizer314/)).
- **Poweramp Equalizer.** Parametric EQ with AutoEq import. Core EQ already
  exports to it (companion export; see the 2026-10-11 Poweramp roadmap).

Core EQ position: level on import (AutoEq, Equalizer APO, Poweramp, REW) and on
ISO 226 loudness. Behind on automatic per-device profile switching for TV
outputs and on the breadth of pre-built AutoEq-style data. Core EQ should not
ship a 5,000-entry database of TV or soundbar curves; there is no equivalent
public dataset for those devices.

### 2.3 Room-correction systems (the depth benchmark)

- **Audyssey MultEQ XT32 with the MultEQ Editor app** (Denon, Marantz). Up to
  eight measurement positions. The Editor app, about $20, exposes before/after
  curves, a custom target curve, an upper frequency limit for correction, and
  per-speaker disable ([Home Theater](https://hometheater.us.com/audio/room-correction/),
  [Wirecutter](https://www.nytimes.com/wirecutter/blog/automatic-room-correction/)).
  Reviewers recommend limiting correction to a band (for example up to 500 Hz)
  when a full-range correction sounds dull; the Editor's upper limit is the
  control they point to.
- **Dirac Live** (NAD, Arcam, miniDSP, Denon and Marantz add-on). Mixed-phase
  correction, so it corrects time as well as magnitude. It uses an external
  USB measurement microphone, and its full version has a curve editor. Bass
  Control is a paid add-on, listed at about $299 ([HomeCineSolutions](https://en.homecinesolutions.fr/blog/posts/2174-dirac-live-audyssey-or-ypao-comparison-guide-to-choose-the-best-room-acoustic-calibration)).
- **Yamaha YPAO, Onkyo / Pioneer AccuEQ.** Receiver-built systems, with
  partial reflection handling in YPAO.
- **REW (Room EQ Wizard)** with a calibrated UMIK-1 or UMIK-2. Free. It takes a
  calibration file per microphone, averages several positions, has a modal
  filter setting for parametric EQ, and supports multi-input capture in the
  Pro version ([REW features](https://www.roomeqwizard.com/features.html)).
- **miniDSP AutoEQ in Device Console.** Paid, per device. Measures with a
  calibrated mic and applies in one step. It does **not** average several mic
  positions; full REW does ([miniDSP](https://www.minidsp.com/applications/67-room-eq-wizard-rew)).

Core EQ position: the only one of these that runs from the TV remote with no
computer, no USB mic, and no receiver. Behind on everything else: multi-seat
averaging, correction limits set by the user, a target editor, phase/time
correction and calibrated microphones. Core EQ's own limits (40 Hz to 8 kHz,
+6 dB / −12 dB) are documented in the code; this pass did not check the limits
of the systems above, so no comparison is claimed. The limits are a safety
choice and should be presented as one.

### 2.4 Vendor-specific apps

- **Room EQ (Onkyo, com.hansong.onkyo).** Calibration for the vendor's own
  receivers. Not a cross-device competitor, but a sign that vendors are
  shipping companion measurement apps.

---

## 3. Where Core EQ is ahead, level, and behind

| Capability | Core EQ (repo) | TV-native EQs | Phone system EQs | Room-correction systems |
|---|---|---|---|---|
| Measures the room, no computer | **Yes** (remote mic) | No | No | Some (receivers, mic + app) |
| Calibrated external mic input | No | No | No | Yes |
| Multi-position / seat averaging | **No** (same-position repeat only) | No | No | Yes (up to 8 in XT32) |
| User-set correction upper limit | **No** (fixed 8 kHz) | No | Limiter only | Yes |
| Target-curve editor | Presets only | No | Graphic EQ | Yes |
| Import AutoEq / Equalizer APO | **Yes** | Rare | Yes | Varies |
| Import REW / Poweramp files | **Yes** | No | Some | Varies |
| Hard correction limits (boost/cut) | **Yes**, documented | Not documented | Limiter | Varies |
| ISO 226 loudness | **Yes** | No | Yes | Dynamic EQ / volume |
| Phase / time correction | No | No | No | Yes (Dirac, YPAO partial) |
| Companion phone control | **Yes** (LAN, encrypted) | No | n/a | Varies |
| Per-device profiles | Partial (device identity) | No | Yes | Per-input |

---

## 4. Ranked roadmap

Ranked by how much each item closes the gap to the depth benchmark, weighed
against cost and risk. None of these is approved.

1. **Multi-position seat averaging on the remote.** Measure at up to 3–4
   positions around the main seat, keep each trace, and average them into one
   correction. Competitors do this (Audyssey, REW, Dirac); the old review lists
   it as item 4. Software only. Risk: the seat envelope must be explained, and
   the averaging must reject outlier positions. Verify on a device before any
   claim.
2. **User-set correction ceiling, narrow-only.** Let the user lower the
   correction's upper limit (for example 500 Hz, 1 kHz, 2 kHz, or the default
   8 kHz). Narrowing is safe; widening past 8 kHz is not, so the control must
   never go above the current limit. This is the Audyssey Editor's most-used
   control. Cost: a change to the Kotlin limits and the Python parity reference
   (`tools/core_eq_dsp.py`), plus updating `tests/test_core_eq_parity.py`.
3. **Custom target curve import.** Accept a GraphicEQ target file and treat it
   as the target, with the existing limits still applied. This matches the
   Editor's custom target and reuses the import parser.
4. **Calibrated external microphone (UMIK class) through a USB audio input,
   with calibration-file import.** This is the step that moves correction below
   40 Hz and adds real measurement accuracy. It is the largest item: USB audio
   routing on Android TV varies by device, and a USB mic needs its own
   calibration file. Needs a hardware decision (section 5).
5. **Phase or time correction.** Not proposed. It needs a full FIR or mixed-phase
   engine on the TV, which Android's effect APIs do not provide. Say so rather
   than imply it.
6. **Per-output profile switching polish.** Wavelet-style automatic profile
   load when a TV output changes. Partly there through device identity. Needs a
   device list to test against (2026-10-10 review, item 3).

## 5. Decisions needed from you

1. **Item 2 (narrow correction ceiling):** approve changing the Python reference
   and Kotlin limits together? It is the one change in this list that touches
   the safety contract.
2. **Item 4 (external mic):** is a USB measurement microphone in scope for the
   product, and which one? Without a decision on hardware, it stays unbuilt.
3. **Device list:** which TVs, soundbars and phones do you test on? Needed for
   item 1 validation and for item 6.
4. **Positioning:** should the public copy claim "the only remote-mic room
   measurement app", which the research supports, or "best room correction",
   which it does not?

---

## 6. Verification status

- Code state checked against this repository: import formats, ISO 226, limiter,
  Spatializer status, and the fixed 40 Hz to 8 kHz band all confirmed in the
  source.
- Competitor features taken from store listings, vendor pages and reviews. None
  was tested on a device in this pass. Store-listing claims are marked as such.
- No Android build was run in this pass, and nothing in this document has been
  shipped.

## Sources

- SoundWave TV listing: https://soundwave-eq-tv.en.aptoide.com/app
- Wavelet features: https://pittvandewitt.github.io/Wavelet/Features/
- Wavelet on Google Play: https://play.google.com/store/apps/details?id=com.pittvandewitt.wavelet&hl=en_US
- Equalizer314 on F-Droid: https://f-droid.org/en/packages/com.bearinmind.equalizer314/
- Room correction systems compared: https://hometheater.us.com/audio/room-correction/
- Wirecutter on automatic room correction: https://www.nytimes.com/wirecutter/blog/automatic-room-correction/
- Dirac Live, Audyssey and YPAO comparison: https://en.homecinesolutions.fr/blog/posts/2174-dirac-live-audyssey-or-ypao-comparison-guide-to-choose-the-best-room-acoustic-calibration
- REW features: https://www.roomeqwizard.com/features.html
- miniDSP AutoEQ and REW comparison: https://www.minidsp.com/applications/67-room-eq-wizard-rew
- Prior passes: `docs/research/core-eq-android-tv-market-and-competitors-2026-09-28.md`,
  `docs/research/core-eq-gap-and-improvement-review-2026-10-10.md`,
  `docs/research/core-eq-poweramp-import-and-accuracy-roadmap-2026-10-11.md`
