# Core EQ — build plan

A standalone, Core Builds-branded **room measurement and equaliser app for
Android TV**: play pink noise, listen with the remote microphone, and hand back
an equaliser setting for *this* room — with the limits of the device it is
running on stated out loud.

**Research first.** The plan in `docs/research/core-eq-measurement-and-capability-2026-09.md`
is what this document is built on, and it changed the shape of the product in
two places:

- The **remote mic is supported** (the TV hardware guide says the controller
  microphone is "fully supported"), but it is not a measurement microphone.
  Core EQ corrects **40 Hz–8 kHz** and refuses to invent a curve outside it.
- Android has **no global equaliser API**. Session 0 is deprecated and never
  removed; the supported path is an opt-in broadcast most players do not send.
  So the product is built around a **capability probe** whose verdict is shown,
  not assumed.

The DSP is written and tested today: `tools/core_eq_dsp.py` is the reference
implementation of the whole measurement → correction → export chain, and
`tests/test_core_eq_dsp.py` holds it there. The Kotlin is a translation, not a
re-derivation.

---

## 1. What it is

| | |
|---|---|
| **Name** | Core EQ |
| **Package** | `tv.corebuilds.eq` (`AGENTS.md`: new apps use `tv.corebuilds.<name>`) |
| **Path** | `coreeq/` — its own Gradle root, like `doctor/` and `shift/` |
| **Tag prefix** | `coreeq-v*`, floating tag `coreeq`, stable APK `coreeq-release.apk` |
| **Audience** | Android TV / Google TV / Fire TV owners who want their set to sound better and do not own a measurement microphone |
| **Promise** | *Measure the room you are sitting in, with the remote you already hold.* |

It is **not** a headphone equaliser (Wavelet's territory), not a player
companion (Poweramp EQ's), and not hardware-bound room correction (Dirac,
Audyssey). Those remain separate tools; Core EQ is focused on room correction
for Android TV.

**One measured base belongs to one output.** Profiles record the sweep's
reported `AudioTrack` output kind and, where Android supplies it, its device
name; the connected-output estimate is only a fallback when the track route is
unavailable or unrecognized. Core EQ first selects an exact same-kind/name
match, then a generic same-kind profile; it does not borrow a named curve from
another device. At apply time, API 33+ uses Android's anticipated media route
when available; older versions rank connected outputs. Neither proves where
another app's stream is actually routed. If Android supplies no recognized
route or the estimate does not match a measured profile, on-device correction
pauses rather than guessing with another chain's curve; only a legacy
all-output profile or an explicitly unknown-output profile applies while the
route remains unidentified.

## 2. The screens

Two-pane shell, icon-pack furniture throughout: `TvActivity` dp-box
normalisation, `cb_*` tokens, the 3dp ring / 3dp gap focus treatment, Outfit +
DejaVu Sans Mono, a 12sp type floor.

| Screen | Job | D-pad contract |
|---|---|---|
| **Home** | Output-matched room profile and its quality score, with Correction, Mode and Re-measure in one card at the top (1.3.0); measured-vs-total-EQ graph; applied-engine band preview; a menu grouped Sound (Manual EQ, Extra effects, Content type) and Set up (Profiles, Capability, Display calibration) | `Measure` / `Re-measure` is focused at open. Everything fits 960 x 540dp; the menu scrolls only while the update bar is showing. |
| **Manual EQ** | Ten editable peaking bands from 40 Hz to 8 kHz, with separate Movie / TV, Everyday and Gaming overlays, presets, and a graph of the selected layer over room correction | Choose the overlay to edit; left/right selects a band; up/down changes it by 0.5 dB within ±6 dB. A manual-only profile is labelled as not room-measured. |
| **Modes** | Remote-selectable Movie / TV, Everyday and Gaming; optional app rules; sticky or temporary manual override; current mode/reason and detected players | Single-action mode controls and dialogs are D-pad reachable. Automatic detection is best-effort and runs while correction is on. Conflicts choose Everyday. |
| **Measure** | Three numbered steps, live capture graph, progress bar, Stop / Start over | Step 1 focused. OK starts the stimulus. Back cancels and restores Home's focus. |
| **Profiles** | Saved room bases per output or explicitly marked manual-only profiles; per-mode tone overlays; output-matched preview and selected-mode export | Rows first, then export chips, then Export / Delete. |
| **Capability** | Every path the app can use to apply an equaliser, probed, with a verdict and a fallback | Rows focusable; the verdict card is read-only but focusable so it can be narrated. |

The existing mockup set (`docs/core-eq-home.png`, `core-eq-measure.png`,
`core-eq-profiles.png`, `core-eq-capability.png`) predates the Manual EQ
editor; it remains a four-frame snapshot of the original measurement workflow.
The manual screen is implemented in `ManualEqActivity` and will need a matching
fifth frame before a visual release receipt is claimed.

## 3. The measurement → correction chain

```
exponential sine sweep (10 s, Farina) — primary
pink noise (20 s, deterministic) — cross-check and live RTA
  → AudioRecord(VOICE_RECOGNITION, 48 kHz), read ~3 s past the stop
  → deconvolve with the time-reversed inverse filter → impulse response
        ├→ RT60 (Schroeder backward integration)
        │      → Schroeder f_s = 2000·√(RT60/V)   [room dims are asked for]
        ├→ excess group delay → minimum-phase gate
        └→ magnitude response
  → 3 captures within the seat envelope (head height, ±25 cm), energy-averaged
  → measured vs target (Flat / B&K / Room / Olive / Dialogue / House)
  → room model
        transition  = min(2·f_s, 400 Hz), or 300 Hz when the room is unknown
        nulls       = dips deeper than 6 dB below the 2-octave trend
        floor       = wherever the loudspeaker's own roll-off is detected
  → correction, in two regimes
        below the transition   variable smoothing (1/6 oct) → invert
                               → never boost into a null
                               → honour the minimum-phase gate
                               → clamp(+6/−12) → slope limit(6 dB/oct)
                               → recentre → clamp again
        above the transition   shaping only, ±3 dB, one-octave smoothing
        → taper the edges → zero outside floor–8 kHz
  → collapse onto the device's own Equalizer bands (millibel-clamped)
  → fit 8 peaking filters for the parametric export (Q 0.4–4.0, spacing penalty)
  → export: parametric .txt (Preamp + band limit + "Bands Overlap = Cascade")
            / GraphicEQ: / profile .json (transition, nulls, room, capability)
```

The room curve is never overwritten by hand. Each profile has a measured room
base and three independent manual tone overlays (Movie / TV, Everyday and
Gaming), each holding ten fixed-frequency peaking filters (40 Hz–8 kHz, ±6 dB
in 0.5 dB steps), with Flat, Bass lift, Speech clarity and Less bass presets
plus user-saved presets. The same `BandMapping` composes the selected overlay
with room correction for DynamicsProcessing, the platform Equalizer fallback
and TV-settings export, shifting any positive peak down to preserve digital
headroom. Parametric exports include the selected mode's overlay and a
combined filter-based preamp. GraphicEQ exports include the selected overlay;
when tone filters are present, their preamp is calculated from the exact curve
points serialized and preserves any more-conservative room reserve. Base-only
GraphicEQ exports still omit a preamp line (a pre-existing limitation). Profile
JSON backups preserve the room base and all three mode overlays separately,
record the selected mode, and calculate its combined preamp. A manual-only
profile is allowed, but is labelled as not measured.

Mode selection is manual by default. Optional app rules use only DUMP-discovered
media/game session UIDs, and a UID selects a rule only when `PackageManager`
returns one distinct non-Core-EQ package. Session broadcasts and public playback
callbacks are rescan triggers only; their session/package extras are ignored.
Android documents DUMP as not for third-party apps, so an ADB grant may be
refused by a stock or release build. Unmapped or unidentified active players,
and simultaneous conflicting app rules, fall back to Everyday while
automatic switching is on because Core EQ has one shared curve. A manual choice
in automatic mode creates a configurable sticky override or a temporary
override that lasts until the detected player set changes.

Every constant in that pipeline is commented with its reason in
`tools/core_eq_dsp.py`. The order of the clamp / recentre / taper steps is
load-bearing: a first draft re-centred last and silently broke three documented
budget guarantees at once, which is what `tests/test_core_eq_dsp.py` now pins.

### Why the chain is split at a transition frequency

This is the central design decision, and it comes from
`docs/research/core-eq-correction-science-2026-09-27.md` §1–2. Below the
transition the room is a handful of discrete standing waves: correcting them
is real work, and because low-frequency room modes are **minimum phase**,
cutting a modal peak also kills its ringing. Above it, a microphone at one
point is measuring reflections as much as the speaker, and forcing the curve
smooth there is equalising things an upstream filter cannot touch. So the
corrector inverts below the line and only shapes above it.

The line itself is computable rather than guessed: `f_s = 2000·√(RT60/V)`, one
octave up, capped at 400 Hz. The working numbers the field converges on are
300–500 Hz — Toole's transition, Dirac ART's 150 Hz, `audioxpress`'s 20–400 Hz
recommendation, and the 250–500 Hz filter limit serious Audyssey users set.

And a null is never filled. It is destructive interference at the capsule; a
boost raises the direct and reflected arrivals equally and the cancellation
survives, so all a boost buys is excursion and distortion. The corrector marks
such dips, leaves them at zero, and the UI says so out loud.

## 4. Applying the correction — the ladder

The current runtime is **not an ordered capability probe**. `EqService.applyAll()`
tries the output-mix/session-0 path first, configures and verifies the chosen
engine, and releases per-session effects to avoid applying the same curve
twice. Only if that path cannot be created or configured does it attach to
DUMP-discovered sessions. The same DUMP snapshot may supply app identity while
the output-mix path is active. Session-open/close broadcasts and public
playback callbacks only request a debounced DUMP rescan; their extras are not
used as session IDs or package claims. If no on-device path attaches, the user
can export the selected profile and mode.

| Runtime path | What Core EQ does | What the result does **not** prove |
|---|---|---|
| Output mix (`DynamicsProcessing(0)` / `Equalizer(0, 0)`) | Tries this first. API 28+ prefers DynamicsProcessing, then falls back to the platform Equalizer; the service checks control/configuration and reads settings back. | A constructible/configured session-0 effect does not prove that every app, codec or output route traverses it. |
| Session/playback signals | Treats session-open/close broadcasts and public playback callbacks as rescan triggers only; ignores their session/package extras. | Signals do not identify another app's active audio session or route. |
| Optional DUMP discovery | If Android grants `android.permission.DUMP`, parses `dumpsys media.audio_flinger` and `dumpsys audio` for candidate session IDs, UID, usage and activity. It may attach sessions when the output-mix path failed. A UID selects an app rule only when its package mapping is unique. | Android says DUMP is not for third-party apps; an ADB grant may be refused on stock/release builds. Dump text is firmware/version-dependent and fixtures are not TV captures; a discovered session still does not prove effect coverage. |
| TV / companion export | Exports the measured room base plus selected mode overlay for the TV's own sound controls or a compatible companion equalizer. | Export success does not mean the external EQ was entered or is applied on the measured signal chain. |

`EffectLadder.probe()` itself only attempts session-0 effect construction and
checks DynamicsProcessing control; it does not test session announcements,
run DUMP discovery or verify end-to-end coverage. The existing Capability
screen copy still treats some of these probes as a device-level verdict; that
pre-existing overstatement is recorded in the audio-routing research rather
than silently presenting construction as proof.

Engine choice at apply time: `DynamicsProcessing` on API 28+ (own band layout,
own limiter, limiter **on**), `android.media.audiofx.Equalizer` below. Band UI
draws the device's reported bands when available and clamps to the reported
`getBandLevelRange()`.

A foreground service keeps the effect chain alive, and gains are re-applied on
every session open — the two bugs that define this category
[5](https://github.com/nepg82/MotoEQ).

## 5. Honesty rules (these are product features)

1. **Correct only where the measurement can be trusted** — from the detected
   loudspeaker roll-off to 8 kHz, and only below the transition frequency. The
   rest of the measurement is displayed, greyed, uncorrected. A remote capsule
   cannot measure it, and above the transition the microphone is measuring
   reflections as much as the speaker.
2. **A profile travels with its provenance.** Microphone, stimulus, sample
   rate, target, band, gain budget, transition frequency, the room it was
   measured in, the dips that were deliberately left alone, and the capability
   verdict at the time it was made — all in the `.json`. Loading a profile from
   another set shows those limits before it offers to apply.
3. **Cut-only is a first-class toggle,** not a hidden setting, and it says what
   it costs: *lower output, no clipping risk*.
4. **The preamp ships with the filters** and rounds toward more negative. The
   export carries its own correction band and the Poweramp
   "Bands Overlap = Cascade" instruction, because both failures sound like
   "the EQ made it worse" rather than like a missing comment line.
5. **No claim of mixed-phase time-domain correction.** The sweep's impulse
   response is used to gate the correction and to measure decay, not to build
   pre-ringing filters. The docs and the UI both say so.
6. **Dips that are cancellations are named, not hidden.** When the corrector
   refuses to fill a null it says which frequency and why: *this is where the
   room cancels the sound; boosting it cannot work*. Leaving it unsaid would
   make a deliberate decision look like a bug.
7. **The target curves are named for what they are.** These are loudspeaker
   in-a-room curves. The Harman *headphone* target's 3 kHz ear-gain peak is
   never baked in — it would double-count pinna gain and make dialogue sound
   like a telephone.

That is `docs/BRAND-GUIDE.md`'s "Honest utility" pillar applied to audio.

## 6. Module shape

```
coreeq/
  settings.gradle.kts            # own Gradle root — do not merge
  app/build.gradle.kts           # AGP 9.4.0, built-in Kotlin, compileSdk /
                                 # targetSdk 37, minSdk 30 (Android 11), with
                                 # a Core-EQ-only envelope exception
  app/src/main/kotlin/tv/corebuilds/eq/
    TvActivity.kt                # dp-box normaliser, same as the icon pack
    MainActivity.kt              # Home
    measure/  CaptureEngine.kt   # AudioRecord(VOICE_RECOGNITION) + Welch
              StimulusPlayer.kt  # AudioTrack, shipped pink-noise asset
    dsp/      Correction.kt      # port of tools/core_eq_dsp.py
              Targets.kt
              Peaking.kt
    apply/    EffectLadder.kt    # the four rungs + probe results
              SessionReceiver.kt
              EqService.kt       # foreground service, re-apply on session open
    export/   Profile.kt         # corebuilds.core-eq/1
              Formats.kt
```

Core EQ uses its own Gradle root and is the suite's API 37 exception: AGP
9.4.0, Gradle 9.7.0 and AGP built-in Kotlin. The other suite roots remain on
AGP 8.5.2 / Kotlin 1.9.24; `tools/gradle_envelope.json` keeps those ceilings
root-specific. Core EQ dependencies remain `core-ktx` 1.13.1, `appcompat` 1.7.0
and `recyclerview` 1.3.2. No Compose — the icon pack's `Theme.CoreBuilds` note
applies here too.

## 7. Gates

A new app in this suite is not real until the suite's gates see it. Wired in
this order:

| Gate | What it proves |
|---|---|
| `python tools/core_eq_dsp.py --selftest` | The 30 DSP invariants |
| `python tests/test_core_eq_dsp.py` | Stimulus, band grid, target shape, the gain budget, slope limiting, fitting determinism, millibel clamping, export correctness |
| `python tools/build_core_eq_mockups.py --check` | The frames match `res/values` and the DSP output |
| `python tools/check_suite_truth.py` | `suite.json`, README stamp and AGENTS.md table agree |
| `python tools/check_gradle_envelope.py` | Every coordinate inside its ceiling |
| `python tests/test_ci_coverage.py` | The new tests are wired into `suite-ci.yml` |
| `python tests/test_changelog_contract.py` | The Unreleased block is well-formed |
| `python tests/test_core_eq_update_contract.py` | The release feed never leads the build, the test package carries no feed, and the tag build publishes the manifest after the releases |

Plus, once `coreeq/` exists: `check_ui_resources.py`, `test_navigation_graph.py`
and `test_tv_layout_fit.py` extended to the new module, and
`core-eq-apk.yml` modelled on `core-doctor-apk.yml` (tag prefix `coreeq-v`).

## 8. Milestones

| | Ships | Verifiable without hardware |
|---|---|---|
| **M0** | DSP reference, tests, research, frames, plan — **this change** | yes |
| **M1** | `coreeq/` module: Home + Manual EQ + Modes + Measure + Profiles + Capability, output-aware profiles, View-based TV UI, audio effects and exports | yes (lint, tests, hardware apply gates) |
| **M2** | Stimulus playback + capture + live graph; the correction is computed on-device and compared against the reference chain on the same synthetic input | yes, with an emulator mic |
| **M3** | Effect ladder + foreground service + export formats | needs a device |
| **M4** | `suite.json`, README stamp, dependabot, workflow, release tags | CI |

M2 is the milestone that matters: it is where the Kotlin has to agree with
`tools/core_eq_dsp.py` numerically, and it should be proved by feeding both the
same WAV and asserting the corrections match within a stated tolerance.

## 9. Open questions for the maintainer

1. **Downloader code** — *decided: `7946159`*, generated by the maintainer at
   https://go.aftvnews.com/ and recorded in `suite.json`.
2. **Automatic app-based modes** — *decided and implemented best-effort.*
   Manual mode selection always remains available. Optional rules use
   DUMP-discovered UID/package mappings; session broadcasts and public playback
   callbacks only trigger rescans. Android may refuse a DUMP grant to a
   third-party release. Unmapped/unidentified players and conflicting demands
   select Everyday because only one global curve is available. Device/app
   coverage remains a hardware-validation question.
3. **Generic stock Android TV coverage** — *target, not yet proven.* Verify on
   representative stock TV hardware whether session-0 effects reach common
   streaming apps, which outputs are actually routed through the mixer, and
   how PCM/passthrough settings affect HDMI audio. No root, privileged install,
   or system-image change is in scope; a one-time ADB grant is acceptable.
4. **USB measurement microphone support** (UMIK-1 and similar) — later. It
   changes the correction floor from 40 Hz down to ~20 Hz and makes the result
   a real measurement rather than a good estimate; until it lands,
   `CorrectionLimits.MIN_HZ` stays at 40 Hz.
5. **Name** — *Core EQ* keeps the `core-*` tag-prefix family legible next to
   `coreline-v*` and `coreshift-*`. Alternatives considered and rejected:
   Core Tone, Core Room, Core Tune.

---

## Receipt

```
python tools/core_eq_dsp.py --selftest   → all 30 invariants hold
python tests/test_core_eq_dsp.py         → 41 passed
python tools/build_core_eq_mockups.py --check → mockups ok - 4 frames match the sources
```

No Android SDK in this environment, so no APK was built. Named rather than
implied, per `AGENTS.md`.
