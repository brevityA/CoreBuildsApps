# Core EQ — Smart-Feature Candidates: Prioritized List

**Date:** 2026-11-01  
**Branch:** `arena/0287482a-corebuildsapps`  
**Status:** Features 1–4 implemented and committed; PR #419 open.

This document lists every smart-feature candidate identified from repo analysis of
`dsp/`, `apply/`, `mode/`, `measure/`, and `companion/` plus the research docs under
`docs/research/`. It distinguishes **already-built** from **open** work, and ranks
open candidates by impact on the user's main job (correct the sound on the TV they
own) and by how well-supported the need is.

---

## Already-built (committed)

| # | Feature | File(s) | What it does | Notes |
|---|---------|---------|--------------|-------|
| B1 | Volume-aware smart preset suggestions | `dsp/SmartSuggestion.kt`, `SmartSuggestionTest.kt` | Suggests Flat for gaming/music, speech-clarity for quiet dialogue, low-volume loudness for quiet non-dialogue. Pure Kotlin, no Android deps. | Content-type + volume-driven. Wired into `EqService.extrasContentType()` and `ContentTypeRegistry`. |
| B2 | Room-aware EQ guidance | `dsp/RoomAwareGuidance.kt`, `RoomAwareGuidanceTest.kt` | From a measured curve: modal-band warnings, cut/boost suggestions above the transition frequency, transition-frequency context. Skips manually filtered bands. | Unit-tested for empty curve, strong cut/boost, small corrections, already-manual bands, sort by importance. |
| B3 | Full ISO 226 loudness compensation | `dsp/LoudnessCompensation.kt`, `LoudnessCompensationTest.kt` | Maps the full ISO 226:2003 contour difference (bass + treble) to the 10 manual EQ band centres via log interpolation, normalised to 0 dB at 1 kHz. | Replaces the old bass-only shelf at 63 Hz (`ToneLayers.lowVolumeBassDb` capped at +3 dB). Full-band curve capped at ±6 dB per band. |
| B4 | Auto-apply per device | `apply/DeviceKeyStore.kt`, `dsp/AutoApplyPolicy.kt`, `apply/EqService.kt` | First correction on an output auto-applies; later enables only suggest. Persisted per output (`kind|name`) in SharedPreferences. | `EqService.onOutputsChanged()` consults `AutoApplyPolicy.decide()`; `recordAutoApplied()` called after first successful apply. User toggles always work. |
| B5 | Profile auto-switching learning | `export/ProfileRouteStore.kt`, `apply/EqService.kt`, `ProfilesActivity.kt` | Remembers the profile the user last chose on each output; prefers it when no explicit choice exists. | `ProfilesActivity.onProfileSelected()` writes the preference; `EqService.resolve()` reads it before falling back to `OutputRoute.pick()`. |
| B6 | Time-based intelligence | `dsp/TimeBasedSuggestions.kt`, `TimeBasedSuggestionsTest.kt` | Detects evening (19:00–22:00) and night (22:00–07:00) local hours; returns a `Suggestion` the UI can use to prompt night mode. Clock injectable; handles midnight wrap. | Boundaries unit-tested; custom windows supported. |
| B7 | ISO 226 contours | `dsp/EqualLoudness.kt`, `EqualLoudnessTest.kt` | Full ISO 226:2003 contour table, compensation curve between reference and playback phon levels. | Foundation for B3; previously only the 63 Hz bass shelf was wired. |
| B8 | Dialogue boost / low-volume bass / stereo downmix | `apply/ToneLayers.kt`, `ToneLayersTest.kt` | Runtime tone layers summed before headroom in `BandMapping`. Low-volume bass measures per-output reference + lift. | `loudnessBandGains` field added for B3; backward-compatible default. |
| B9 | Smart preset expansion | `dsp/ManualEq.kt` (14 built-in incl. "smooth-mids", "air") | Manual EQ presets with sanitize (fc 40–8000 Hz, gain ±6 dB, q 0.4–4.0, dedupe by centre). | Preset names ≤ 32 chars; unique ids/names across all presets; no image-change promises. |
| B10 | Hardware presets | `dsp/HardwarePresets.kt` (12 models: SONOS_ARC_ULTRA, BOSE_SMART_SOUNDBAR_900, SAMSUNG_HW_Q990C, …) | Per-model measured-profile presets, 4 each, with `MODELS` entries. | Labels say "reviewer-informed, not measured"; each model's presets distinct from built-ins. |
| B11 | Closed-loop measure/correct/re-measure | `dsp/CorrectionRefinement.kt` | Measure → correct → re-measure loop with quality scoring. | |
| B12 | Correction-file import | `dsp/CorrectionImport.kt` | Parses ParametricEQ, Equalizer APO, GraphicEQ text; fits to 10 bands. | Manual EQ "Import correction file" button. |
| B13 | Night mode (limiter) | `dsp/NightMode.kt`, `ui/EnhancedAudioPrefs.kt` | Protection-only vs night-mode tighter limiter on DynamicsProcessing; toggle on Extra effects. | Wired to `EqService.limiter()`. |
| B14 | Measurement quality score | `dsp/MeasurementQuality.kt` | Composite quality score from SNR, RT60, nulls, gated bands, transition frequency. | |
| B15 | Content modes + content types | `mode/ContentMode.kt`, `mode/ContentType.kt` | Content modes + resolver; 10 content types + package registry. | App-mode rules select overlays. |
| B16 | Output-aware profile routing | `apply/OutputRoute.kt`, `apply/EqService.kt` | Correction follows the measured output chain; steps aside on unmeasured outputs. | Passthrough warning for HDMI bitstream. |
| B17 | Device identity / brand detection | `dsp/DeviceIdentity.kt` | Brand detection from `Build.MANUFACTURER`; `Build.MODEL` matching deferred. | |

---

## Open candidates (prioritized)

Ranked by impact on the user's main job and how well-supported the need is. None is approved; each needs a maintainer decision.

### P1 — Model-level presets for more brands (needs device list)

**Source:** `docs/research/core-eq-gap-and-improvement-review-2026-10-10.md` §2 item 4; `core-eq-device-models-research-2026-10-10.md`.

Three brands are covered (Sonos, Bose, Samsung). The research note exists; no source gives a measured response for additional models.

**Implementation notes:**
- Add `HARDWARE_PRESETS` entries for requested models in `HardwarePresets.kt` (4 presets each).
- Labels must say "reviewer-informed, not measured"; no image-change promises; names ≤ 32 chars; unique ids/names.
- Needs the maintainer's device list (brand + model per TV, soundbar, speaker) plus a sourced preset basis.
- `DeviceIdentity.kt` already has brand detection; `Build.MODEL` matching is deferred (gap item 5).

**Blocker:** maintainer device list.

### P2 — Correction-file import formats (already built, format decision pending)

**Source:** `docs/research/core-eq-gap-and-improvement-review-2026-10-10.md` §2 item 1.

`CorrectionImport.kt` already parses ParametricEQ, Equalizer APO, and GraphicEQ text and fits to the 10 bands. The open question is which formats to officially support and the import flow UX.

**Implementation notes:**
- Define Core EQ's own versioned text/JSON exchange format; validate bounds/NaNs/filter counts; preview/confirm import flow.
- Evaluate AutoEQ text and Poweramp's public preset JSON as additional sources only when each schema is documented and tests cover version drift.
- Do not parse `.poweramp-backup`/`.peq-settings` containers without format-owner permission/documentation and a security review.
- Disclose conversion/approximation when parametric filters convert to a graphic EQ.

**Blocker:** maintainer format decision.

### P3 — Real passthrough detection

**Source:** `docs/research/core-eq-gap-and-improvement-review-2026-10-10.md` §2 item 2.

`OutputRoute.mayPassThrough()` already exists (HDMI may bypass EQ from Android's encoded-surround setting). Full automatic detection is probably impossible on stock builds — Android's public API cannot show the current stream format.

**Implementation notes:**
- The existing check is the honest ceiling; do not claim more.
- Report passthrough as a conditional caveat in the status line, not a promise.

**Blocker:** platform limitation; no code change needed beyond honest wording.

### P4 — Virtual surround / Spatializer

**Source:** `docs/research/core-eq-gap-and-improvement-review-2026-10-10.md` §2 item 3.

`SpatialStatus.kt` exists as a read-only status row (API 32+). Android has no on/off call for the spatializer, so a settings switch could not work.

**Implementation notes:**
- Scope to Core EQ's own output; gate to API 32.
- A Spatializer or effect on one track is not proof it reaches other apps — do not overclaim.

**Blocker:** platform limitation; read-only status is the ceiling.

### P5 — Multi-position spatial averaging

**Source:** `docs/research/core-eq-smart-features-and-display-calibration-2026-10-06.md` §1.2.3.

`dsp/SeatAverage.kt` exists but is not integrated into `MeasureActivity`. A single measurement position only corrects one seat; other seats have different modal responses.

**Implementation notes:**
- Extend `MeasureActivity` with a position counter ("Add position" per sweep).
- Power-average positions in the energy domain after 300–3000 Hz broadband alignment (avoids artificial comb nulls from complex vector averaging).
- Profile carries a `positions` count; UI shows "Position 1 of 3" and "Done averaging".
- `SpatialAveragingTest` for two synthetic positions with different modal peaks.

**Blocker:** UI integration work in `MeasureActivity`.

### P6 — Display calibration companion

**Source:** `docs/research/core-eq-smart-features-and-display-calibration-2026-10-06.md` Part 2.

A new screen within Core EQ (or sibling activity) with native test patterns (black level, white level, greyscale, colour bars, sharpness, overscan, uniformity, gamma, HDR PQ patterns), a guided calibration wizard, and before/after comparison.

**Implementation notes:**
- `DisplayCalibrationActivity` + `TestPatternView` (SurfaceView, pixel-accurate) + `PatternLibrary`.
- Shares design language with Core EQ (night chrome, Outfit type, `cb_*` colour tokens).
- Separate feature area from audio EQ; larger scope.

**Blocker:** large scope; separate feature area.

### P7 — On-TV ADB execution

**Source:** `docs/research/core-eq-gap-and-improvement-review-2026-10-10.md` §2 item 7.

Setup shows copy-to-computer ADB commands instead of executing ADB on the TV.

**Implementation notes:**
- Needs an ADB protocol library and TV pairing; security review for client certificates.
- Low priority without a user report.

**Blocker:** ADB library + pairing + security review.

### P8 — Bluetooth latency and USB DAC handling

**Source:** `docs/research/core-eq-gap-and-improvement-review-2026-10-10.md` §2 item 8.

Listed as "Future" in `V1.2.0_FEATURES.md`; not in code. Latency needs a loopback measurement the app does not have.

**Implementation notes:**
- Low priority without a user report.

**Blocker:** loopback measurement infrastructure; no user demand evidenced.

### P9 — Localisation

**Source:** `docs/research/core-eq-gap-and-improvement-review-2026-10-10.md` §2 item 9.

`res/values/` only (English). One `contentDescription` across 12 layouts.

**Implementation notes:**
- Decide whether the audience needs translations before investing.
- TalkBack pass on a real TV still needed.

**Blocker:** audience decision.

### P10 — JBL direct integration (out of scope)

**Source:** `docs/research/core-eq-ecosystem-expansion-2026-10-25.md` §P3.

Community Home Assistant `jbl-bar` integration requires a third-party client certificate; not an official JBL SDK or stable contract.

**Implementation notes:**
- Do not build now. No Core EQ roadmap slot recommended until a supported public API or carefully reviewed community bridge exists.
- Keep soundbar EQ/device configuration separate from Core EQ's room correction and content modes.

**Blocker:** no official API; license/security concerns.

### P11 — Poweramp Equalizer bridge (optional, user-requested only)

**Source:** `docs/research/core-eq-ecosystem-expansion-2026-10-25.md` §P2.

Poweramp publishes `PeqAPI` with `SET_EQU_PRESET` and `ACTION_IMPORT_PRESETS`. App-specific, not a generic Android EQ contract.

**Implementation notes:**
- Isolate behind package/API detection and manual confirmation; never claim it affects arbitrary players.
- Only if users request it.

**Blocker:** user demand; app-specific scope.

---

## Implementation notes common to all new features

- **Repo conventions:** filters must pass `ManualEq.sanitize` (fc 40–8000 Hz, gain ±6 dB, q 0.4–4.0, dedupe by centre); preset names ≤ 32 chars; unique preset ids/names across all presets; no preset name promising an image change; each model's presets distinct from built-ins.
- **Pure Kotlin where possible:** DSP objects (`LoudnessCompensation`, `TimeBasedSuggestions`, `SmartSuggestion`, `RoomAwareGuidance`) have no Android deps and are unit-testable without a device.
- **CI gate:** `.github/workflows/core-eq-apk.yml` runs `:app:testDebugUnitTest` and `:app:lintDebug`, then builds. No Java/Gradle/Android SDK in this sandbox, so compilation and unit tests are deferred to CI.
- **Privacy guardrails:** Notification access reads only media-session tokens/package/state; DUMP is exceptional and opt-in; no `QUERY_ALL_PACKAGES` or `PACKAGE_USAGE_STATS`; no claim of "works with all apps/devices."

---

## Open questions for the maintainer

1. Add more hardware presets (P1) — needs your device list.
2. Officially support which correction-file import formats (P2)?
3. Is multi-position spatial averaging (P5) worth the `MeasureActivity` UI work?
4. Is the display calibration companion (P6) in scope as a separate feature area?
5. Is localisation (P9) in scope for the next round?
