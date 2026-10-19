# Core EQ: Smart Features & Display Calibration Research

**Date:** 2026-10-06  
**Author:** Research agent for brevityA/CoreBuildsApps  
**Scope:** Two parallel investigations — (1) making Core EQ smarter and more
intelligent, and (2) designing a display calibration companion to ship
alongside the EQ within the same application.

---

## Part 1 — Making Core EQ Smarter

### 1.1 What Core EQ already does well

Core EQ is already the most technically sophisticated Android TV EQ app on
the market. It ships with:

- Real swept-sine measurement via the remote microphone (`SweepAnalysis`)
- Two-regime correction with Schroeder-frequency transition
- Minimum-phase gating that refuses to invert non-minimum-phase regions
- Null detection that never boosts into cancellations
- RT60 estimation via noise-compensated T20
- Content-mode overlays (Movie/TV, Everyday, Gaming)
- Output-aware profile routing
- REW magnitude-only import
- DynamicsProcessing engine with Equalizer fallback
- DUMP-discovery for per-session correction
- Manual 10-band parametric EQ with presets

### 1.2 Smart features to add

#### 1.2.1 ISO 226 Equal-Loudness Compensation (Adaptive Loudness)

**Problem.** Human hearing is non-linear: at lower volumes, bass and treble
perception drops disproportionately compared to midrange (the Fletcher-Munson
effect, standardized as ISO 226:2003). When the viewer turns the TV down for
late-night watching, the room correction still reads flat, but the perceived
tonal balance shifts — bass disappears and dialogue loses body.

**Precedent.** Wavelet (Android headphone EQ) ships ISO 226-based equal
loudness compensation as a headline feature [1]. Audyssey MultEQ ships
"Dynamic EQ" which optimises sound quality at lower volumes by boosting bass
and treble in real time, accounting for the way humans perceive audio at
varying levels [6]. TV DSP Center has "Dynamic Loudness" that automatically
adjusts bass at different volume levels [1]. alsaloudness is an open-source
implementation for ALSA [4]. The academic paper "Adaptive Loudness
Compensation in Music Listening" derives a first-order shelving filter from
ISO 226 contours that tracks within ±1 dB [9].

**Implementation plan.**
- Add an `EqualLoudness` object in `dsp/` that holds the ISO 226:2003
  contour table (phon curves from 20 to 100 phon at the standard
  frequencies) and computes a compensation curve for a given reference
  SPL and playback SPL delta.
- The compensation is a level-dependent EQ overlay: at reference volume
  (e.g. 80 dB SPL) it is flat; as volume drops, bass and treble boost
  increases following the difference between the reference contour and
  the current contour.
- Hook into `EqService`: listen to `AudioManager.getStreamVolume()` on
  `STREAM_MUSIC` via a `ContentObserver` or periodic poll, compute the
  delta from the calibration reference, and compose the loudness overlay
  with the room correction before the band-mapping step.
- The loudness overlay uses the same peaking-filter model as ManualEq so
  it passes through the existing headroom and limiter pipeline.
- Expose a toggle and reference-volume setting on a new "Loudness" card
  on the Home screen.
- `EqualLoudnessTest` pins the contour table and the compensation curve
  at known phon deltas.

**Why it matters.** The changelog's sixth research pass already identified
the ISO 226 evening TV volume trap: taming room modes relieves upward
masking on consonants. Loudness compensation is the complementary half —
when the viewer turns down for those same reasons, the correction
automatically keeps the bass and presence they'd otherwise lose.

#### 1.2.2 Night Mode (Dynamic Range Compression)

**Problem.** Late-night TV at low volume exposes a different problem: the
dynamic range of movies and shows. Dialogue whispers are inaudible; action
scenes are too loud for the neighbours. The viewer rides the volume remote.

**Precedent.** Audyssey ships "Dynamic Volume" which tracks the volume in
real time and optimises the dynamic range for the situation [6]. TV DSP
Center's Smart DSP includes an "Anti-Clipping Limiter" and a "Dialogue
Enhancer" [1].

**Implementation plan.**
- Add a `NightMode` toggle on the Home screen (or in modes: Gaming never,
  Movie/TV always available, Everyday optional).
- When enabled, `EqService` configures the DynamicsProcessing limiter with
  more aggressive settings (lower threshold, higher ratio) instead of the
  protection-only limiter. This is the DynamicsProcessing API's built-in
  compressor/limiter, which is already configured but set to
  protection-only today (`LimiterSettings`).
- The dialogue enhancer component boosts the 1–4 kHz SII band by a modest
  2–3 dB, using the same presence overlay the "dialogue" target already
  defines, but as a runtime overlay rather than a measurement target.
- Night mode is composed with room correction and loudness compensation in
  order: room → loudness → night → manual.
- `NightModeTest` pins the limiter thresholds and the presence overlay.

#### 1.2.3 Multi-Position Spatial Averaging

**Problem.** A single measurement position only corrects one seat. Other
seats in the room have different modal responses, and a correction optimised
for one position can make another worse. The fourth research pass identified
that spatial seat averaging must use power (energy) averaging preceded by
300–3000 Hz broadband alignment, because complex vector averaging creates
artificial comb nulls.

**Implementation plan.**
- Extend `MeasureActivity` with a "position counter" — after each sweep,
  the user can tap "Add position" to take another measurement at a
  different seat. The positions are power-averaged in the energy domain
  after 300–3000 Hz alignment (the same `alignTo` the single-position
  path uses).
- The averaged response becomes the measured curve for correction. The
  profile carries a `positions` count.
- The UI shows "Position 1 of 3" and a "Done averaging" button.
- `SpatialAveragingTest` proves that two synthetic positions with different
  modal peaks produce a correction that addresses the shared modes while
  leaving position-specific ones alone.

#### 1.2.4 Volume-Aware Smart Preset Suggestions

**Problem.** Users often don't know what EQ preset to use. The app has
Manual Eq presets (Flat, Bass Lift, Speech Clarity, Less Bass) but no
intelligence about which is appropriate for the current content.

**Implementation plan.**
- The content-mode system already detects what app is playing (Movie,
  Gaming, Everyday). Extend it with volume-aware preset suggestions:
  - Low volume + Movie → suggest "Speech clarity" overlay
  - Low volume + Everyday → suggest "Bass lift" overlay (combined with
    loudness compensation)
  - Gaming → suggest "Flat" (low-latency, unprocessed)
- Show a non-intrusive suggestion chip on the Home screen that the user
  can tap to apply or dismiss.
- `SmartSuggestionTest` pins the suggestion rules.

#### 1.2.5 Measurement Quality Score

**Problem.** Users don't always know if their measurement was good enough.
The SNR is reported but not contextualised.

**Implementation plan.**
- Add a composite "Measurement Quality" score (0–100) that considers:
  - SNR (20 dB minimum, 40+ dB excellent)
  - RT60 plausibility (0.1–1.5 s for residential rooms)
  - Number of nulls (fewer is better, >5 suggests a difficult room)
  - Number of non-minimum-phase bands gated (fewer is better)
  - Transition frequency (lower is better: more of the range is corrected)
- Display it on the Home screen and in Profiles as a colour-coded badge
  (green > 75, amber 50–75, red < 50).
- `MeasurementQualityTest` pins the scoring weights.

---

## Part 2 — Display Calibration Companion

### 2.1 Market Gap

The Android TV display calibration landscape is remarkably thin:

- **TV Calibration (VMApps)** — Shows test patterns for brightness, contrast,
  colour and overscan. No HDR support. No guided instructions. Users
  complain about the lack of in-app help and target values [2].
- **Samsung Smart Calibration** — Uses the phone's camera to measure the TV
  output and auto-adjust white balance, gamma, and colour. Achieves
  DeltaE 2000 < 1 in Basic mode, < 0.5 in Professional mode. Only works
  with Samsung TVs and Galaxy/iPhone cameras [3][7].
- **CalMAN / DisplayCAL** — Professional tools requiring external
  colorimeters (i1Display Pro, etc). Not available on Android TV.

No existing Android TV app combines:
1. Reference-quality test patterns rendered natively on the TV
2. Guided step-by-step calibration instructions with target values
3. HDR test patterns
4. Before/after comparison

### 2.2 Proposed Feature: Core Display Calibration Screen

A new screen within Core EQ (or a sibling activity) that provides:

#### 2.2.1 Test Pattern Generator

Full-screen, pixel-accurate test patterns rendered via `SurfaceView`:

- **Black level** — Near-black bars from IRE 0 to IRE 10 in 1-step
  increments. The user adjusts TV brightness until the lowest visible bar
  is distinguishable from absolute black.
- **White level (contrast)** — Near-white bars from IRE 90 to IRE 100.
  The user adjusts contrast until the highest bars are distinguishable.
- **Greyscale ramp** — 11-step greyscale from 0% to 100%. Checks for
  colour casts (tint errors show as non-neutral greys).
- **Colour bars** — Standard SMPTE colour bars (75% amplitude, 75%
  saturation) for colour and tint adjustment.
- **Sharpness** — Alternating single-pixel lines at various resolutions.
  The user adjusts sharpness until lines are distinct without ringing.
- **Overscan** — Border markers at 2.5%, 5%, and 10% inset. Confirms the
  TV is not cropping the image.
- **Uniformity** — Full-screen flat colours (white, grey, red, green,
  blue) for checking panel uniformity, backlight bleed, and dead pixels.
- **Gamma** — Mid-grey patches at known luminance levels for gamma
  estimation.
- **HDR patterns** (when the display reports HDR support) — ST.2084 PQ
  reference levels, specular highlights at 1000/4000/10000 nits.

#### 2.2.2 Guided Calibration Wizard

A step-by-step flow that walks the user through each adjustment:

1. **Picture Mode** — Set TV to Movie/Cinema/Filmmaker mode
2. **Backlight/OLED Light** — Set to maximum (for dark rooms) or a
   comfortable level
3. **Brightness (black level)** — Show the black-level pattern, explain
   what to look for, provide the target ("adjust until IRE 2 is just
   visible above absolute black")
4. **Contrast (white level)** — Show the white-level pattern, explain
   what to look for
5. **Colour** — Show colour bars, explain how to use a blue filter
   (or the "colour only" mode on some TVs)
6. **Sharpness** — Show the sharpness pattern, explain that zero or near-
   zero is usually correct
7. **Gamma** — Show the gamma pattern, explain target (2.2 for bright
   rooms, BT.1886 for dark rooms)
8. **Colour Temperature** — Set to Warm/Warm2 for closest to D65

Each step shows the pattern full-screen with a collapsible instruction
panel at the bottom.

#### 2.2.3 Before/After Comparison

A toggle that alternates between a reference image (a known-good test
image or a photograph) and the current TV output, so the user can see
the effect of their adjustments.

#### 2.2.4 HDR Calibration Check

For HDR-capable displays, patterns that verify:
- PQ tracking (is the TV actually outputting ST.2084 correctly?)
- Peak luminance (does the TV clip at its rated peak?)
- Colour volume (are HDR colours accurate?)

#### 2.2.5 Integration with Core EQ

The display calibration screen lives alongside the EQ screens in the same
app, sharing the same design language (night chrome, Outfit type, the
`cb_*` colour tokens). This is natural because the user who cares about
audio correction also cares about picture accuracy — they are setting up
the same home theatre.

### 2.3 Technical Architecture

```
DisplayCalibrationActivity
├── TestPatternView (SurfaceView, renders patterns pixel-accurately)
├── CalibrationStepAdapter (wizard step management)
├── PatternLibrary (singleton, all pattern renderers)
│   ├── BlackLevelPattern
│   ├── WhiteLevelPattern
│   ├── GreyscalePattern
│   ├── ColourBarsPattern
│   ├── SharpnessPattern
│   ├── OverscanPattern
│   ├── UniformityPattern
│   ├── GammaPattern
│   └── HdrPatterns (conditional on display capability)
└── CalibrationProfile (persists user's TV settings notes)
```

---

## References

[1] TV DSP Center, SoundWave TV, Room AutoEQ, Wavelet, Poweramp Equalizer  
    — Google Play listings, 2026.
[2] TV Calibration (VMApps) — Google Play, reviews and description.
[3] Samsung Smart Calibration — Samsung Support documentation, 2026.
[4] alsaloudness — github.com/dpapavas/alsaloudness
[5] Headphonesty: "Music Sounds Weird at Low Volume? Here's the Fix
    Audiophiles Swear By", August 2025.
[6] What Hi-Fi: "What is Audyssey?", July 2025.
[7] Forbes: "New Samsung DIY TV Calibration App Promises Perfect Pictures
    For All", January 2021.
[8] ResearchGate: "Adaptive Loudness Compensation in Music Listening", 2019.
[9] Samsung EZCal — AV Forums, 2021.
[10] Discover8K: "Using a Phone to Calibrate an 8K TV", June 2022.
