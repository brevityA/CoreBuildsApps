# Core EQ — Poweramp import and accuracy roadmap

Research date: 2026-10-11. This is a code-and-literature review, not a claim
that the current phone/TV audio paths have passed physical qualification.

## Executive summary

- Core EQ now accepts one exported Poweramp / Poweramp Equalizer JSON preset,
  in addition to the AutoEq / Equalizer APO correction text it already imported.
  It converts the supported frequency response into the existing ten fixed
  manual bands. It does **not** retain Poweramp's original filters, per-channel
  separation, preamp or all filter shapes.
- The import UI now presents a review dialog before saving/applying, with fit
  error, destination band count, a best-effort/versionless mapping warning and
  each skipped active filter. The JSON format has no published versioned schema;
  dated fixtures test real-world shapes but do not validate filter responses.
- Room measurement already has substantive safeguards: capture clipping and
  global SNR gates, per-band SNR, marker-based clock-drift/dropout detection,
  capture-bandwidth detection, null handling, a minimum-phase gate, roll-off
  detection, and safe boost/cut limits. Synthetic tests are strong; device and
  microphone qualification are not complete.
- Highest-value accuracy sequence: qualify known capture chains; support a
  traceable external microphone calibration file and verified input selection;
  add same-seat repeatability; then investigate a small seat-envelope average
  and post-correction verification. Stereo-separated correction is valuable
  for some sources but is not the same problem as single-output TV room EQ.
- Keep Android application claims honest. Android effects are session-based;
  session-0/global application is deprecated and device-specific. A profile
  or successful effect construction is not proof that another app's stream
  traversed the effect.

## Poweramp import findings and current behavior

Poweramp's developer forum describes preset JSON import/export, `.pa-eq-preset`
imports, and AutoEq `.txt` import. A community-provided JSON example has a
root array containing a preset object with `name`, `preamp`, `parametric`, and
`bands`; each band carries integer `type`, `channels`, `frequency`, `q`,
`gain`, and `color` fields. The current native importer accepts either that
single-object array or a single preset object. Multi-preset JSON arrays are
refused rather than silently importing the first item.

| Source shape | Core EQ conversion | Loss or caveat |
|---|---|---|
| Poweramp parametric JSON with codes 3–5 | Best-effort interpretation as peaking (3), low shelf (4) and high shelf (5); evaluate on a frequency grid and fit the ten fixed Core EQ bands. | **Provisional numeric mapping:** Poweramp JSON has no schema/build version. A review warning is shown before anything is saved or applied. RBJ shelf response may differ from Poweramp. |
| Active Poweramp parametric codes 0–2 and unknown codes | Skip each active band and list its position and numeric type. Reject the file if no supported band remains. | Public samples and third-party parsers do not agree on these codes; Core EQ does not guess or silently create a flat preset. |
| Poweramp left-only/right-only bands | Average the two channel magnitude responses in dB, then fit one shared curve. | Not channel-preserving; Core EQ applies one shared curve. The conversion is explained in the import preview. |
| Poweramp graphic JSON (`parametric=false`, code 2 points) | Treat fixed-frequency gains as points and interpolate in log-frequency/dB before fitting. | This is an observed compatibility path, not a guarantee for every export generation; interpolation and ten-band fitting are approximate. |
| Poweramp preamp | Preserve and report its value in result metadata. | Not applied. Core EQ recomputes headroom for the fitted bands; source loudness is not preserved. |
| AutoEq / Equalizer APO text | Continue using the existing parser, including GraphicEQ interpolation and PK fitting. | Shelves/non-PK shapes and preamp are not copied by the text path. |

The numeric type map remains explicitly provisional. The JSON does not include a
Poweramp build/schema version, and public examples show why a global mapping
cannot safely be inferred from one forum post: June 2023 and October 2023
public samples show different uses of codes 0–2; a July 2026 user-posted preset
contains active codes 0 through 5; and a separate community importer documents
a different, narrower 0/1/2 interpretation. The Poweramp developer confirms
that tone/pass cutoff and Q settings can be app-configured and are not reliably
portable. These dated examples have no trustworthy app-build tag, so they are
recorded as fixtures, not as proof of each code's acoustic response.

Core EQ therefore uses only the narrow 3–5 parametric subset in this build,
adds a visible no-version/best-effort warning, lists every skipped active band,
and refuses a parametric import when nothing supported remains. Graphic mode is
handled separately, with its fixed gains treated as points. The import flow
shows fit error, the number of destination bands, skipped codes and other
caveats in a review dialog **before** saving/applying; users can cancel. Do not
expand the mapping without dated genuine exports and validation against the
source app's actual filter responses. For exact or current-build interchange,
Poweramp AutoEq/Equalizer APO text is the safer fallback.

The app does not implement the `.pa-eq-preset` container itself. Poweramp JSON
export is the supported native path; AutoEq text import remains available.
A settings backup (for example, Poweramp's full settings/data export) is not an
EQ preset and should not be fed to this importer.

### Poweramp evidence

- Poweramp developer, [Equalizer builds 899–908](https://forum.powerampapp.com/topic/21506-poweramp-equalizer-builds-899-908/): preset JSON import/export, `.pa-eq-preset`, AutoEq text, and graphic/parametric modes.
- Poweramp user export example, [Trouble importing JSON generated by script](https://forum.powerampapp.com/topic/26543-trouble-importing-json-generated-by-script/), posted 2023-06-27: a root array with tone slots and additional channel-specific type-0/type-1 rows. The original app build is not stated; this is a dated fixture, not a type specification.
- Public user preset, [EV6_REW_Slope1.5 Gist](https://gist.github.com/V1rgul/adb2c3f59fe3692a92bc6d51af28a068), created 2023-10-15: a parametric JSON using active type-2 bands. The app build and type semantics are not specified.
- Public user-posted preset, [maxmpz/powerampapi issue #52](https://github.com/maxmpz/powerampapi/issues/52), opened 2026-07-17: an array preset exercising codes 0–5. Neither app build nor the acoustic meaning of each numeric code is identified.
- Poweramp developer, [tone-control JSON discussion](https://forum.powerampapp.com/topic/31398-how-to-convert-tone-knobs-type-0-type-1-into-standard-lsf-hsf-bands-in-parametric-json/): Bass/Treble cutoff and Q are app-configured and not reliably exported. The user's suggested type-4/type-5 shelf map is not a vendor schema contract.
- Third-party importer, [servitola/eq Poweramp parser and fixture](https://github.com/servitola/eq/blob/main/Sources/eq/Import/Formats/PowerampFormat.swift): documents a narrower 0/1/2 map, illustrating that community implementations disagree; it is a compatibility clue, not an authority.
- Poweramp feature description, [Parametric Equalizer](https://forum.powerampapp.com/files/file/74-powerampequalizer-build-908-equ-uniapk/): identifies filter-shape names (low/high-pass, low/high-shelf, band-pass and peaking) and channel selection, but does not publish a versioned JSON enum.
- AutoEq maintainer discussion, [native AutoEq support](https://github.com/jaakkopasanen/AutoEq/issues/763): Poweramp has native AutoEq preset support; AutoEq text remains the safer interchange path when JSON type compatibility is uncertain.
- The checked-in fixture provenance and limitations are documented in `coreeq/app/src/test/resources/poweramp/README.md`.

## What Core EQ already checks

Code review of `SweepAnalysis`, `MeasureActivity`, `CompanionServer`, and
their tests found these existing safeguards:

- obvious capture clipping is rejected at `-0.5 dBFS` on TV and phone paths;
- the sweep's impulse-response SNR must clear a 20 dB minimum; per-band SNR
  below 10 dB is displayed but not corrected;
- two timing markers estimate playback/capture clock drift; small drift is
  resampled, while drift over 300 ppm is rejected as a likely dropout;
- low input bandwidth is detected rather than extrapolated as speaker roll-off;
- dips/nulls are reported and not boosted without limit; the low-frequency
  correction is bounded to a 2 dB boost for the current single-position path;
- high-frequency and non-minimum-phase regions are gated; speaker roll-off
  limits the correction floor; the curve remains inside its tested gain/slope
  budgets;
- RT60 is fit as a noise-aware T20 only when its decay range is sufficient;
  phone mic response and processing metadata remain provenance, not calibration.

The displayed capture score is based on the recording SNR, while room facts
(nulls, gated bands and transition) are shown separately. This avoids presenting
a room's cancellations as poor microphone quality. The current tests exercise
synthetic ground truth and deterministic DSP; they do not qualify any TV remote,
phone, USB mic, HDMI chain or Android build.

## Ranked research and implementation work

### 1. Qualify capture chains before adding a calibration claim

Create a repeatable device matrix: TV model/output, phone model/OS/input route,
AudioRecord source, actual sample rate, processing state, speaker level, mic
orientation and distance. Compare multiple repeated measurements at the same
position with a calibrated USB reference mic. Define per-band repeatability
and response-error budgets before accepting a phone model. Android-reported
microphone frequency response is useful provenance but is not, by itself, an
individual serial-number calibration or proof that a chosen microphone/route
was used.

**Implementation gate:** save a device/input fingerprint and qualification
status; keep unsupported routes as “unknown”; never label a generic phone mic
as calibrated.

### 2. External microphone calibration + explicit input route

A traceable measurement mic and its matching frequency-response file can
correct a known microphone's frequency response. The calibration must match
the exact device/serial and orientation. It does not correct room reflections,
phone AGC, a hidden resampler, or an output chain that bypasses the intended
EQ. First add a bounded, validated calibration-file parser and store its
provenance; then expose Android input-device discovery/preferred-device hints
and record the route Android actually reports. A preferred device is not a
guaranteed route: reject or downgrade the calibration if the active input does
not match.

Keep absolute SPL separate: a frequency-response calibration file alone does
not provide a reliable microphone sensitivity/level calibration. Do not lower
the correction floor or boost allowance until the calibrated path is validated.

### 3. Same-seat repeatability and “why the capture is weak” diagnostics

The existing global gates catch clipping, low overall SNR, severe clock
mismatch, low-band SNR and capture-bandwidth edges. Add a user-visible repeat
capture at the same position and compare smoothed band magnitudes, marker drift,
SNR and route metadata. A large disagreement should ask the user to remeasure
rather than average incompatible routes or silently save the latest result.

Before choosing thresholds, record repeated sweeps across rooms, levels and
known routes. If the same device changes response with playback level, that
may reveal TV/AVR dynamic processing or compression; it is a different issue
from room correction. A two-level test is a promising detector, but must be
qualified before becoming a setup promise.

### 4. Multi-position averaging inside a small listening-seat envelope

A single point is highly sensitive to small microphone movements, especially
for interference and narrow cancellations. A useful future TV workflow is a
center point plus a small, explicit head/seat envelope (for example three
positions around ±25 cm), not a vague claim to “measure the room.” Preserve
per-position traces and metadata; combine only compatible captures from the
same output, phone/mic, level and room state. Choose and validate the averaging
domain (energy/magnitude vs dB) on synthetic and physical data, reject captures
with poor SNR or a mismatched route, and retain the existing null/boost limits.
Spatial averaging must not turn a cancellation at one position into an unsafe
boost or claim that every seat is corrected.

### 5. Left/right separation: a different correction mode

Poweramp can represent independent left/right headphone or system filters;
Core EQ's current TV measurement is a single mono capture and its applied
curve is shared. A faithful stereo workflow would require reliable left-only
and right-only stimulus routing, channel-preserving capture, route verification,
separate stored profiles/filters, and a user-visible fallback when the TV or
soundbar downmixes. Do not infer channel identity from a stereo file if Android
or the output has already mixed it. For current room correction, the Poweramp
importer's L/R averaging is explicitly lossy; it is not “stereo calibration.”

### 6. After-correction verification and application proof

Add a separate verification action, not an automatic feedback loop: capture
before correction, apply the profile, then measure again with the same output,
position, mic, level and route. Compare only bands whose capture quality passes
the existing gates, and report whether the *measured in-room response* improved.
Do not let a verification sweep overwrite the baseline room profile or
regenerate correction from the already-corrected response without an explicit
workflow.

An acoustic post-measurement can provide evidence that an effect changed the
sound at that seat, but not that every Android app or every output traverses
that effect. A device-independent application claim requires a controlled
physical loopback/test signal and a qualified hardware/audio-path matrix.

### 7. Distortion and nonlinear processing detection

The logarithmic sweep's separated harmonic impulse responses contain potential
evidence of speaker distortion or dynamic processing. Core EQ currently uses
the main linear impulse and discards the pre-arrival harmonic responses. A
future detector could estimate harmonic energy relative to the fundamental and
compare two playback levels for compression. This is promising for issue
detection (damaged/overdriven speaker, loudness management, dynamic DSP), but
requires calibrated stimulus level, a known capture chain and tests against
known nonlinear hardware. Do not report THD or identify a bad speaker until
that work is measured.

## Application limits and distinctive opportunity

Android's public `Equalizer` is attached to an audio session; the API deprecates
session 0 for the global output mix. `DynamicsProcessing` is also instantiated
for an audio session. Core EQ already probes its effect/capability paths, but
cannot promise universal system-wide routing. The distinctive product angle
should be the combination of **honest path qualification, actionable
measurement diagnostics, a transparent fit/verification report, and an
optional Android phone sensor**—not a claim that the app can force every TV
stream through EQ or turn every phone into a laboratory microphone.

## Decision and readiness

| Work item | Status |
|---|---|
| Poweramp native single-preset JSON importer | Implemented as a narrow best-effort subset; dated 2023/2026 public fixtures added. Unsupported-only imports fail closed; a pre-apply review lists skipped bands and warns that numeric types are unversioned. Android compile/runtime and source-app response validation still need verification. |
| AutoEq / Equalizer APO text importer | Existing; retained. |
| Capture clipping/SNR/drift/bandwidth/null/phase/roll-off safeguards | Existing; synthetic tests pass in the repository's Python DSP suite. Physical capture paths are not qualified. |
| Calibrated USB mic and calibration file | Research candidate; not implemented. |
| Same-seat repeatability gate | Research candidate; not implemented. |
| Multi-position average | Research candidate; not implemented. |
| True L/R measurement and correction | Research candidate; not implemented. Poweramp import downmix is explicitly approximate. |
| Post-correction verification | Research candidate; not implemented. |
| Harmonic-distortion / dynamic-processing diagnosis | Research candidate; not implemented. |
| TV/phone audio-path qualification | Android build and physical-device tests remain required. |

## Additional sources

- Android [`Equalizer`](https://developer.android.com/reference/android/media/audiofx/Equalizer) and [`DynamicsProcessing`](https://developer.android.com/reference/android/media/audiofx/DynamicsProcessing): effects attach to audio sessions; session 0/global output use is deprecated for `Equalizer`.
- Android [`MicrophoneInfo`](https://developer.android.com/reference/android/media/MicrophoneInfo) and [`AudioRecord`](https://developer.android.com/reference/android/media/AudioRecord): platform-reported microphone/input information is path metadata, not a guarantee of traceable calibration or routing.
- HouseCurve, [Roon room-correction workflow](https://housecurve.com/docs/appnotes/roon): emphasizes listening-area spatial averaging and headroom management.
- Roon, [Digital Room Correction](https://blog.roonlabs.com/digital-room-correction/): practical constraints for target design and avoiding boosts into deep nulls.
- REW author forum, [mic calibration discussion](https://www.avnirvana.com/threads/how-to-calibrate-mic-with-rew.9883/): a calibration file must match the microphone; a reference-mic comparison is a process, not a generic phone setting.
- Audio Engineering Society / JASA paper, [multi-point room equalization](https://pubs.aip.org/asa/jasa/article-pdf/152/6/3635/16533502/3635_1_online.pdf): multiple positions improve spatial coverage but introduce measurement-effort and averaging-design choices.
