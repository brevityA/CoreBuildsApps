# Core EQ: what room correction does, what to build next, and an everyday curve for a JBL Bar 2.0 All-in-One

**Research date:** 25 October 2026 (Australia/Sydney)
**Scope:** the Core EQ app in this checkout (`coreeq/`, commit `7084ec4`, version 1.6.1 /
code 15), the numeric reference in `tools/core_eq_dsp.py`, and public JBL material for
the Bar 2.0 All-in-One.
**Basis and limits:** this is a code-and-document review plus a numeric model run against
the repository's own DSP reference. **No TV, phone, soundbar or calibrated microphone was
used, and no Gradle/unit-test run happened** (this sandbox has no JDK). Every curve below
is a model output, not a measurement of your bar in your room.

---

## Executive summary

**What room correction actually does.** It plays a 10 s sweep through your TV's audio
path, records it on the remote's microphone, and works out your room's noise floor,
reverberation time and Schroeder frequency. Then it does two different things either side
of that transition frequency (capped at 400 Hz): below it, it inverts the measured bass
error, boosting at most **+2 dB** from one seat and cutting up to **−12 dB**, while
refusing to touch nulls, non-minimum-phase bands, noisy bands and everything under the
speaker's own roll-off. Above it, it applies **smooth 1-octave shaping capped at ±3 dB**.
The whole correction lives between **40 Hz and 8 kHz** because that is all a TV remote's
microphone can honestly see. In short: **it is mostly a bass-peak remover with a gentle
tonal tilt above it** — not a system that can add sub-bass, fix imaging, or process a
Dolby bitstream.

**Three things worth building.** A narrow-only user correction ceiling (the highest-value
missing control); a loudness-matched A/B bypass (the cheapest trust feature); and a
per-band "why this was left alone" list (the data already exists in `PostCorrectionCheck`).

**Two things worth fixing first.** `UseCaseTargets.boxShape()` has a hard 1.0 → 0.0 step
where the other two implementations of the same curve interpolate over half an octave —
and none of `UseCaseTargets`' ten curves is actually applied at runtime, although the
Content-type screen says they are.

**Your everyday curve**, for a Bar 2.0 All-in-One at Q = 1.0:
`125 Hz +2.0 · 250 Hz −1.0 · 2 kHz +1.0 · 4 kHz +1.0 · 6.3 kHz −1.0 · 8 kHz −1.5`, all
other bands 0 dB. Peak boost +1.7 dB, peak cut −2.0 dB, and nothing asked of the bar
below its published 70 Hz (−6 dB) limit. It is a starting point to be measured, not a
correction of a measured error.


---

## Part 0 — Which JBL you have (do this first)

"JBL 2.0 Compact" is not a JBL model name. JBL describes the **Bar 2.0 All-in-One** and
its MK2 revision as a "Compact 2.0 channel soundbar", so the family is right but the
revision is ambiguous:

| | Original Bar 2.0 All-in-One | Bar 2.0 All-in-One MK2 |
|---|---|---|
| Channels / power | 2.0, 80 W max (2 × 40 W) | 2.0, 80 W max (2 × 40 W) |
| Drivers | 2 × racetrack | 2 × racetrack |
| Published response | 70 Hz – 20 kHz | 70 Hz – 20 kHz (−6 dB) |
| Inputs | HDMI ARC/out, optical, Bluetooth 4.2, USB (US) | HDMI ARC/out, optical, Bluetooth 4.2, USB (US) |
| Size | 614 × 58 × 90 mm, 1.61 kg | 614 × 56 × 90 mm, 1.6 kg |
| Model number | `JBLBAR20AIOBLK…` | `JBLBAR20AIOM2BLK…` (AU: …AS) |

The headline specs are identical, so **the label on the bar decides, not the name in
Bluetooth settings** — both revisions pair as "JBL BAR 2.0", which is why Core EQ's
matcher (`HardwarePresets.kt`, `JBL_BAR_2_0.nameExclude`) deliberately refuses that bare
name and only accepts an explicit non-MK2 All-in-One string. Read the model number off
the bar's label before trusting a model-specific preset.

**Neither revision has a user parametric EQ or any room calibration.** JBL gives you
Smart Mode (EQ preset plus JBL Surround) and Standard Mode (preset EQ). SoundGuys lists
Room Calibration: No, Dialogue Enhancement: No, Night Mode: No for this model. That
matters twice over: Core EQ is the only EQ you can give this bar, and there is no second
DSP in the chain whose settings you must hold still during a measurement — a genuine
advantage over the Bar 800 case.

---

## Part 1 — What your room correction is actually doing

### 1.1 The pipeline, with the real constants

Measured from `SweepAnalysis.analyze()`, `Correction.calculateCorrectionCurve()`,
`Peaking.fitPeakingFilters()` and `DspConstants`:

**1. Play and record a stimulus.** A 10 s exponential sine sweep (`ESS_SECONDS`) plays
through the TV's own audio path and is recorded by the remote's microphone. Two timing
beeps bracket it so a dropped stretch of Bluetooth audio is caught (`markerDriftPpm`;
anything over 300 ppm is refused as "part of the recording went missing").

**2. Deconvolve to an impulse response, then gate on noise.** The inverse sweep is
convolved with the recording, and the noise floor is taken from a quiet window after the
sweep. Below **20 dB SNR** the measurement is rejected outright rather than corrected —
"turn the TV up, hold the remote still, keep the room quiet".

**3. Turn the IR into a spectrum with per-band confidence.** 1/6-octave band magnitudes
plus a **per-band SNR**. Bands under 10 dB band SNR, or more than 20 dB below the
400 Hz–1.6 kHz level, are treated as the end of usable bandwidth (`bandwidthEdge`). This
is how the app notices that a TV remote's 16 kHz ADPCM link stops at 8 kHz: the capture
reads 60–92 dB down above 5 kHz, so the correction ceiling drops to wherever the
microphone actually still hears.

**4. Characterise the room.** RT60 is computed broadband and per octave (125, 250, 500,
1 k, 2 k Hz). From your room volume the app computes the **Schroeder frequency**
(2000·√(RT60/V)) and sets the **transition frequency** to `min(2 × Schroeder, 400 Hz)`, or
300 Hz when the volume is unknown. This is the single most important number in the
correction, and it is measured, not guessed.

**5. Find what must not be touched.**
- **Nulls**: any band more than 6 dB below its own 2-octave trend is a cancellation, not a
  peak, and is never boosted (`detectNulls`). Filling a null spends amplifier power to
  make the null deeper.
- **Roll-off**: walking down from 250 Hz, the first band that falls 6 dB under the
  mid-bass plateau is where the speaker stops working. Below that the correction is
  exactly zero (`detectLowRolloff`). It steps over up to two null bands so a room mode
  doesn't fake a roll-off — a fix that exists because a simulated soundbar rolling off at
  63 Hz used to read 40 Hz and get bass the driver could not make.
- **Non-minimum-phase bass**: excess group delay over 5 ms below the transition switches
  correction off for that band.

**6. Correct in two regimes.** Below the transition the room dominates, so the app
inverts the smoothed measured-vs-target error directly — but with a **+2 dB boost cap for
a single-seat measurement** (`SINGLE_SEAT_BOOST_DB`) and allowed cuts to −12 dB. Above the
transition the field is diffuse and the microphone is not a trustworthy arbiter, so the
correction collapses to **1-octave shaping capped at ±3 dB** (`MAX_SHAPING_DB`).

**7. Hold the shape to a budget.** +6 dB / −12 dB global, a 6 dB-per-octave slope limit, a
one-octave raised-cosine taper at both ends, and finally a recentring so the mean correction
from 100 Hz to the ceiling is 0 dB (the correction changes shape, not loudness).

**8. Fit and apply.** Six RBJ peaking filters plus a preamp figure, applied through
`DynamicsProcessing` (or the platform `Equalizer` where that is refused). Then, optionally,
a **check sweep** runs through the correction on its own session and reports room-alone,
predicted-residual and actually-heard numbers side by side.

### 1.2 What that means for your bar, in plain numbers

| Question | Answer from the code |
|---|---|
| How much can it add below 400 Hz? | **+2 dB, at most**, from one seat. It is mostly a *cut* engine in the bass. |
| How much can it change 400 Hz–8 kHz? | **±3 dB**, and only in smooth 1-octave shapes. |
| How deep can a cut go? | −12 dB (real modal peaks are the thing being removed). |
| What is the highest frequency it will touch? | 8 kHz, and often lower if the remote's link or the SNR says so. |
| Does it add sub-bass? | No. The floor starts at 40 Hz and stops wherever your bar rolls off — for a Bar 2.0 that is around 70 Hz, measured from *your* sweep. |
| Does it fix imaging, width or dialogue "focus"? | No. Those are directivity and placement. An EQ cannot widen a stereo image. |
| Does it touch audio that skips Android's mixer? | No. Dolby/DTS sent as a bitstream bypasses on-device EQ — the service already says so (`EqService.kt:398`). |

So the honest description of "room correction" here is: **it removes bass boom and
one-note drones from roughly 40–400 Hz, and applies a gentle ±3 dB overall tilt above
that, within a 40 Hz–8 kHz window that the remote's microphone can actually see.** It
does not "correct your room"; it removes the room's loudest errors where they are
correctable and refuses to guess elsewhere.

### 1.3 Where the correction is genuinely well built

Worth saying plainly, because these are the things most EQ apps get wrong:

- Nulls and roll-off are protected *before* gain is computed, not clamped afterwards.
- The two-regime split is the accepted practice (correct the steady-state response below
  the transition, shape gently above it).
- The boost cap is asymmetric and small, with cuts allowed deep. That is the right way
  round.
- Every refusal is explained on screen, and the post-correction check distinguishes
  "predicted" from "heard".
- The check, the seat averaging and the drift detection all exist and are documented as
  unvalidated on hardware rather than claimed as working.

---

## Part 2 — What I would build next, and what I found while reading

Ranked by value per unit of risk. None of this is a hardware claim.

### 2.1 Findings (defects or misleading surfaces)

**F1 — `UseCaseTargets.boxShape()` has a hard discontinuity. This is a real bug.**

`UseCaseTargets.boxShape()` returns `1.0` inside `[lo, hi]` and `0.0` outside
`[lo/√2, hi·√2]`, with a smooth step only in the outer 0.7-octave shelf. The plateau edge
is a **vertical step**. Measured on a 1/24-octave grid for the 2.2–4.5 kHz presence box:

| | value at 2100 Hz | value at 2200 Hz | largest step between adjacent 1/24-oct bins |
|---|---|---|---|
| `UseCaseTargets.boxShape` | 0.000 | **1.000** | **1.000** |
| `Targets.boxDb` (the used path) | 0.956 | 1.000 | 0.130 |
| `tools/core_eq_dsp.py::_box_db` | 0.956 | 1.000 | 0.130 |

Three implementations of the same concept, and one of them is wrong. Because four of the
ten curves multiply a *negative* gain by a box (`nasalCut`, `harshnessCut`, `mudCut`,
`bedReduction`, `midScoop`), the discontinuity lands as an up-to-3.5 dB cliff in the
computed target at 2 kHz / 4 kHz. Fix: delete `boxShape` and `bellShape` from
`UseCaseTargets` and call the existing `Targets.boxDb` / a shared bell helper. The
parity test `tests/test_core_eq_parity.py` cannot see this because the Python reference
uses the correct `_box_db`.

Impact today is nil — see F2 — which is exactly why it should be fixed now, before
anything is wired to it.

**F2 — The ten curated content-type curves are dead code, and the UI says otherwise.**

`ContentType` maps to ten description strings ("Dialogue-first optimization · Center
channel clarity · Theatrical presence"), and `ContentTypeActivity`'s KDoc says each type
"applies a curated target curve from `[UseCaseTargets]`". It does not. The only runtime
consumer is `EqService.extrasContentType()`, which feeds `ExtraPlan.of(...)` — a table of
**bass-boost strength in millibels and loudness gain in millibels**. Nothing outside
`UseCaseTargetsTest` calls `UseCaseTargets`. `Targets.targetCurve` (flat / room / B&K /
dialogue / house / olive) is what the Measure screen actually offers.

That is a 604-line file plus a screen promising ten optimised responses while delivering
five bass-boost strengths. Two honest options: wire the curves (fix F1 first, then add a
"target from content type" path), or relabel the screen as "Bass and loudness by content"
and delete `UseCaseTargets`. Shipping it as-is is the worst of the three.

**F3 — No user-set correction ceiling.**

The October competitor research ranked this as item 2 and it is still absent: a TV
performs no measurement above ~2 kHz and no correction above 8 kHz, and many rooms sound
better with correction stopping at 500 Hz or 1 kHz. `SweepResult.fMaxHz` already carries
the measured bandwidth edge, so a *narrow-only* user limit is a small change: clamp
`fMax` to `min(fMax, userCeiling)` and leave everything else alone. It should never be
allowed to widen.

**F4 — No loudness-matched A/B.**

There is no bypass to compare "correction on" against "correction off". Users are asked
to trust a green residual figure instead of their ears, and the correction recentres to
0 dB mean so a naive toggle also changes perceived loudness. A bypass that applies the
same −(max positive gain) preamp on both sides would let someone hear the difference
fairly. This is the single cheapest trust feature available.

**F5 — The accuracy ceiling is the microphone, and the app can only say so.**

The remote's 16 kHz ADPCM path caps at 8 kHz, the remote's own frequency response is
unknown and position-dependent, and a hand-held mic at ear height sees a different
response from a calibrated one at the same point. Everything above is why correction is
capped at ±3 dB there. The step change is a **calibrated USB microphone (UMIK-class) with
a calibration-file import** — already flagged as the largest roadmap item and still the
right one. Note it interacts with F3: a calibrated mic makes a *higher* ceiling defensible,
not a higher default.

### 2.2 Feature proposals

**P1 — Per-band "why" list.** `PostCorrectionCheck` already knows which bands were
excused (null, non-minimum-phase, low band SNR, below floor, above bandwidth edge). A
scrollable list — "63 Hz · null · not boosted", "315 Hz · −4.2 dB cut", "5 kHz · above the
remote's bandwidth edge" — turns an opaque curve into an explainable one and costs almost
nothing in DSP terms.

**P2 — Room advice from the RT60 you already compute.** `rt60ByOctave` is calculated and
used for the transition frequency, then largely thrown away. If 125 Hz decays 3× slower
than 1 kHz, that is a bass-trapping finding no EQ can fix, and the app is uniquely
positioned to say it. Frame it as advice, not as something the next sweep will cure.

**P3 — Preset preview with a fitted response.** The earlier JBL review asked for this on
the Bar 800: when a soundbar preset is picked, draw the *resulting* 10-band response and
its deviation from flat, so a "Movie" preset is not a list of four numbers but a curve you
can see before you commit.

**P4 — Per-output memory polish.** Device identity exists; the remaining gap is switching
profiles automatically on output change without ever auto-applying a *measured* profile to
an output it was not measured on. Keep the "advisory only" rule the research already
states.

**P5 — Content-type curves, once F1 and F2 are settled.** If they are wired, they belong
as target-curve choices on the Measure screen, not as a separate screen with its own
preference store. Ten curves with no measurement provenance and a real discontinuity are
worse than five honest targets.

**Explicitly not recommended:** phase/time correction (needs FIR the Android effect API
does not give you), any JBL One API bridge (community reverse engineering with a
third-party client certificate), and widening the 8 kHz default.

---

## Part 3 — An everyday curve for the JBL Bar 2.0 Compact

### 3.1 What the evidence actually supports

| Claim | Evidence | Strength |
|---|---|---|
| 70 Hz – 20 kHz published response, −6 dB | JBL product page and spec sheets (both revisions); Sweetwater states the −6 dB qualifier | Manufacturer figure, not a measurement |
| 2 × 40 W, two racetrack drivers, rear-firing bass port | JBL spec sheet; Home Cinema Choice review | Good |
| No user parametric EQ, no room calibration | SoundGuys spec table (Room Calibration: No); Home Cinema Choice: "there are no user EQ options" | Good |
| Bass is competent but not deep; integrates well rather than flapping | Home Cinema Choice: "by no means a bass monster, its LF output integrates well" | Reviewer impression |
| Dialogue is solid for everyday TV | Home Cinema Choice: "solid handling of dialogue" | Reviewer impression |
| Top end is crisp and can read as bright/harsh | Home Cinema Choice: "crisp, clean presentation of higher-frequency effects"; existing repo notes: "vocals sometimes harsh" | Reviewer impression |
| No measured frequency response exists publicly | Searched October 2026; only spec-sheet figures and impressions | **Absence of evidence** |

Two consequences. First, **no curve can be derived from this bar's actual response** — any
claim to the contrary would be invented. Second, the published 70 Hz **−6 dB** figure is
the hard constraint: a boost at 60 Hz on this bar buys excursion, port noise and
distortion, not bass. That is standard practice in every correction tool that supports a
target (miniDSP: "setting the target curve to boost the region below the speaker's natural
roll off frequency *may* result in overdriving the speakers"; HouseCurve: "a small
bookshelf speaker may have a low frequency limit of 50 Hz (−6 dB); applying a +10 dB boost
at 30 Hz… will lead to distortion").

### 3.2 The five rules I designed to

1. **Never ask for output below 70 Hz.** Above 70 Hz the bar is within a few dB of its own
   plateau, so that is the only region where a lift is honest.
2. **Lift body, not "bass".** The 90–180 Hz band is where a ~6 cm-tall bar with two
   racetrack drivers and a rear port has usable output. That is what "bass short on the
   lowest demanding material" is asking for.
3. **Undo the boxiness the lift creates.** A 125 Hz lift makes 250 Hz read as cupped on
   speech, and this bar has no centre channel to lean on. Cut 250 Hz slightly.
4. **Presence where consonants live, not where sibilance lives.** Lift 2–4 kHz, neutral by
   5 kHz, and cut above 6 kHz. This mirrors Core EQ's own `dialogue` target's deliberate
   hard stop above 6 kHz.
5. **Stay small.** Every number here is inside ±2 dB, so the curve survives being stacked
   on top of a measured correction without eating headroom or fighting it.

### 3.3 The curve — ten sliders, exactly as the editor stores them

Core EQ's manual editor fixes **Q = 1.0** and **0.5 dB steps** (`ManualEq.DEFAULT_Q`,
`STEP_DB`), so these values reproduce exactly. 40 Hz, 63 Hz, 500 Hz and 1 kHz stay at
0 dB — deliberately, per rule 1.

| Band | Gain | Why |
|---|---|---|
| 40 Hz | 0.0 | Below the bar's −6 dB point. Nothing to gain but distortion. |
| 63 Hz | 0.0 | Same. The 125 Hz bell's skirt already adds ~0.6 dB here. |
| **125 Hz** | **+2.0** | The body band. The largest lift, and the one that addresses "bass short". |
| **250 Hz** | **−1.0** | Keeps that lift from turning into a cupped, boxy voice. |
| 500 Hz | 0.0 | Neutral. Broadcast mixes are already busy here. |
| 1 kHz | 0.0 | Neutral reference region; leave the mastering alone. |
| **2 kHz** | **+1.0** | First consonant/definition lift for TV dialogue. |
| **4 kHz** | **+1.0** | Second half of the presence band; where "distinct dialogue" lives. |
| **6.3 kHz** | **−1.0** | Start of the tame. Removes the glare on S sounds. |
| **8 kHz** | **−1.5** | The reviewed "crisp/bright" top end. Deepest cut, still only 1.5 dB. |

**What it actually produces** (RBJ peaking model at Q = 1.0, computed with
`tools/core_eq_dsp.py`; the editor's bands are wide, so read the in-between values):

| Hz | 40 | 63 | 70 | 80 | 100 | 125 | 160 | 200 | 250 | 315 | 500 | 1k | 1.6k | 2k | 2.5k | 3.15k | 4k | 5k | 6.3k | 8k |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| dB | +0.2 | +0.6 | +0.7 | +1.0 | +1.5 | **+1.7** | +1.1 | +0.2 | −0.4 | −0.4 | −0.1 | +0.3 | +0.9 | **+1.1** | +1.0 | +0.8 | +0.4 | −0.5 | **−1.6** | **−2.0** |

Peak boost **+1.7 dB**, peak cut **−2.0 dB**, six non-zero filters, ~2 dB of headroom
taken. Nothing is asked of the bar below 70 Hz that it cannot deliver.

### 3.4 A small family around it

Same editor, same Q. Pick by ear and by content — not by spec sheet.

| Band | Everyday (above) | More bass | Gentler | TV news / dialogue | Late night |
|---|---|---|---|---|---|
| 40 Hz | 0.0 | 0.0 | 0.0 | 0.0 | 0.0 |
| 63 Hz | 0.0 | +0.5 | 0.0 | −0.5 | −1.0 |
| 125 Hz | +2.0 | +2.5 | +1.5 | −0.5 | −1.5 |
| 250 Hz | −1.0 | −1.0 | −0.5 | −1.5 | −1.0 |
| 500 Hz | 0.0 | 0.0 | 0.0 | 0.0 | 0.0 |
| 1 kHz | 0.0 | 0.0 | 0.0 | 0.0 | 0.0 |
| 2 kHz | +1.0 | +1.0 | +0.5 | +2.0 | +1.5 |
| 4 kHz | +1.0 | +1.0 | +0.5 | +1.5 | +1.0 |
| 6.3 kHz | −1.0 | −1.0 | −0.5 | −0.5 | −0.5 |
| 8 kHz | −1.5 | −1.5 | −1.0 | −1.0 | −1.0 |
| peak boost | +1.7 | +2.4 | +1.4 | +2.3 | +1.7 |

**"Gentler"** is the one to try first if you also run the measured correction — the
correction may already have tamed some of the top end, and two −1.5 dB cuts stacked is a
dull bar. **"Late night"** pairs with Core EQ's existing night-mode limiter and low-volume
bass layer; don't run both the −1.5 dB at 125 Hz here and a positive low-volume bass shelf
at the same time.

### 3.5 How to apply it without wasting it

1. **Fix the route first.** HDMI ARC or optical from the TV to the bar, TV audio output set
   to **PCM** while measuring (a Dolby bitstream bypasses Android's EQ entirely, and then
   you are measuring the bar with no correction in the path and applying a curve to
   nothing). The `EqService` status line already warns about this.
2. **Leave JBL's own mode where you want it.** Smart or Standard, chosen once, unchanged
   across every sweep. It has no numeric EQ, so there is nothing to log beyond the mode —
   but Smart Mode also changes JBL Surround, so a switch between sweeps invalidates them.
3. **Measure, then overlay.** Run Measure with the **Dialogue** or **Room** target — Room is
   the neutral in-room family; Dialogue adds Core EQ's +2 dB 2.2–4.5 kHz presence box, which
   **overlaps the +1.0 dB at 2 kHz and +1.0 dB at 4 kHz in the curve above.** If you use
   Dialogue, pick the "Gentler" column or drop the 2 kHz band to 0. Do not stack two
   presence lifts and conclude the bar is harsh.
4. **Save it as a mode, not as the profile.** The manual overlay is summed with the
   correction before headroom is taken (`BandMapping`), so it is safe, but keeping it in
   the Everyday mode layer means you can drop it in one tap when you measure a different
   room.
5. **Level-match before judging.** The curve takes ~2 dB of headroom, so "correction on"
   is slightly quieter than off. Raise the volume when you switch, or you will prefer
   whatever is louder.
6. **Change one band at a time, then live with it for days.** A 1 dB move is audible in
   the bass and nearly inaudible at 8 kHz; two moves at once tells you nothing.

### 3.6 What I would not do

- **No +4 to +6 dB bass shelf.** It is the most common "soundbar fix" and it is the one
  thing this bar cannot cash. Below 70 Hz you get port noise and compression, and the
  -6 dB point moves up as the limiter engages.
- **No 3 kHz "clarity" spike.** A narrow +4 dB at 3 kHz is the fastest way to make this
  bar sound like a phone call; the presence band above is deliberately two shallow bands
  wide.
- **No boosting above 10 kHz.** The remote cannot see it, the correction does not go
  there, and on this bar the top end is already the reviewer complaint.
- **No "open stage" claims.** The existing `JBL Bar 2.0 · Open stage` preset is a tone
  shape; an EQ cannot widen the front image. Its name promises something the DSP cannot
  do, and should be renamed (e.g. "Air") or its description corrected.

---

## Part 4 — Suggested order of work

| # | Item | Size | Why now |
|---|---|---|---|
| 1 | Fix `UseCaseTargets.boxShape` discontinuity (reuse `Targets.boxDb`) | XS | Real defect, zero blast radius |
| 2 | Decide F2: wire the content-type curves or relabel the screen and delete the dead file | S | 604 lines of untested-by-runtime code plus a misleading screen |
| 3 | Add verified curve tests: sample the fitted response, not the filter list | S | The current tests would pass with the F1 cliff in place |
| 4 | Narrow-only user correction ceiling | S | Highest-value missing correction control |
| 5 | Loudness-matched A/B toggle | S | Cheapest trust feature |
| 6 | Per-band "why" list from the data `PostCorrectionCheck` already has | M | Turns the curve into an explanation |
| 7 | RT60-by-octave advice card | M | Uses data already computed |
| 8 | Calibrated USB mic + calibration-file import | L | The actual accuracy ceiling |

## Part 5 — Verification status

- Code facts: read directly from `SweepAnalysis.kt`, `Correction.kt`, `Targets.kt`,
  `UseCaseTargets.kt`, `ManualEq.kt`, `Peaking.kt`, `DspConstants.kt`,
  `HardwarePresets.kt`, `EqService.kt`, `ExtraPlan.kt`, `ContentType.kt`,
  `ContentTypeActivity.kt` and `coreeq/CHANGELOG.md`.
- Numeric claims (curve values, skirt behaviour, headroom, the `boxShape` step) were
  computed with `tools/core_eq_dsp.py` in the same repo, i.e. the `Peaking.peakingMagnitudeDb`
  / `_box_db` model the Kotlin is held to by `tests/test_core_eq_parity.py`.
- **No Gradle build and no unit tests were run** (no JDK in this environment), and **no
  TV, remote, soundbar or microphone was used**. F1 is a numeric defect visible in source
  and reproduced in a port of that source; it is not a report of an audible symptom,
  because nothing currently consumes the affected curves.
- Soundbar claims are manufacturer specifications and published reviewer impressions, all
  cited below. **No measured response of a Bar 2.0 exists here**, and the everyday curve is
  a reasoned starting point, not a correction of a measured error.

## Sources

1. JBL, [Bar 2.0 All-in-One product page](https://global.jbl.com/soundbars/BAR+2.0+ALL-IN-ONE.html) — 80 W total, 2 × 40 W, 2 racetrack drivers, 70 Hz–20 kHz, HDMI ARC, optical, Bluetooth, USB.
2. JBL, [Bar 2.0 All-in-One spec sheet (PDF)](https://www.jbl.com/on/demandware.static/-/Sites-masterCatalog_Harman/default/dwc46c078a/pdfs/JBL_Bar_2.0_All_in_one_SpecSheet_English.pdf) — dimensions, WAV rates, Bluetooth 4.2, A2DP/AVRCP.
3. JBL, [Bar 2.0 All-in-One MK2 spec sheet (PDF)](https://www.jbl.com/on/demandware.static/-/Sites-masterCatalog_Harman/default/dwfc6b6831/pdfs/JBL%20Bar%202.0_%20All%20in%20one_MK2_%20Specsheet_EN.pdf) — MK2 revision figures.
4. JBL, [Bar 2.0 All-in-One owner's manual (PDF)](https://www.jbl.com/on/demandware.static/-/Sites-masterCatalog_Harman/default/dw68ddec2a/pdfs/JBL%20_Bar%202.0_All%20in%20One%20Owner's%20Manual%20_EN.pdf) — Smart Mode / Standard Mode.
5. Home Cinema Choice, [JBL Bar 2.0 All-in-One review](https://www.homecinemachoice.com/content/jbl-bar-20-all-one-soundbar-review) — "no user EQ options", rear-firing port, "by no means a bass monster", "solid handling of dialogue", "crisp, clean presentation of higher-frequency effects".
6. SoundGuys, [JBL Bar 2.0 specs](https://www.soundguys.com/product/jbl-bar-20/) — "Room Calibration: No", "Dialogue Enhancement: No", "Night Mode: No", 70–20 000 Hz.
7. Sweetwater, [Bar 2.0 All-in-One MK2](https://www.sweetwater.com/store/detail/Bar20Mk2Blk--jbl-lifestyle-bar-2.0-all-in-one-mk2-black) — 70 Hz–20 kHz stated as (−6 dB).
8. B&H Photo, [JBL Bar 2.0 All-in-One](https://www.bhphotovideo.com/c/product/1509313-REG/jbl_jblbar20aioblkam_bar_2_0_all_in_one_2_0.html) — 2× elliptical cone, 80 W, 70 Hz–20 kHz.
9. miniDSP, [Dirac Live filter design — target curve](https://docs.minidsp.com/product-manuals/tide16/dirac-live/filter-design-target-curve.html) — boosting below a speaker's natural roll-off may overdrive it; in-room targets tilt down with frequency.
10. HouseCurve, [Equalization and gain management](https://housecurve.com/docs/tuning/equalization) — "avoid correcting beyond the capabilities of the system"; use cuts and a lowered target rather than boosts.
11. Dirac, [What is the best target curve for room correction?](https://www.dirac.com/resources/target-curve) — most targets roll off −2 to −6 dB by 20 kHz to avoid fatigue.
12. Tantrum Audio, [Target curves explained](https://tantrumaudio.co.uk/learn/target-curves-explained) — "never target below the system's real low-frequency extension… that is a subwoofer decision, not a curve decision"; expect a 0.5–1 dB/octave downward in-room tilt.
13. Olive, Welti & McMullin (AES 2013) and the `room` / `olive` target family, as documented in this repo's own `tools/core_eq_dsp.py` — the 105 Hz bass shelf breakpoint and the +6.6 dB / −2.4 dB published preference curve.
14. Existing repo research: `docs/research/core-eq-correction-science-2026-09-27.md`,
    `docs/research/jbl-bar-800-and-bar-2-0-core-eq-notes-2026-10-25.md`,
    `docs/research/core-eq-jbl-bar-800-review-2026-10-24.md`,
    `docs/research/core-eq-competitor-research-2026-10-13.md`.
