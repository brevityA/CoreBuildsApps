# Core EQ sound-editor roadmap and decision log

The plan for Core EQ after the listening, profile and status batch (#474):
dynamics, phone editing, parametric EQ, tone controls, listening presets and
measurement quality. It replaces nothing in `CORE_EQ_PLAN.md`, which records
how the app was built; this file is where the next milestones and the
owner's decisions about them live. Nothing described here as planned is
built.

Measured room correction, TV operation and the phone companion remain Core
EQ's centre. Poweramp Equalizer's publicly documented features are a
benchmark for how deep editing can go, not a source of code, assets or
layout.

## Decision log

Each entry is the owner's decision, dated, with what it rules out.

| # | Date | Decision | Consequence |
|---|---|---|---|
| D1 | 2026-11-11 | **Warmth and Brightness:** −3 to +3 dB in 1 dB steps, default 0, global like Clarity. Android Bass boost unchanged. No automatic migration and no replacement prompt. | Two new stored values; a missing value reads as 0. |
| D2 | 2026-11-11 | **Tone combination:** fill-to-ceiling applies only to the **new positive** Warmth and Brightness contribution. Existing effects are preserved exactly. Every layer is included in the final headroom calculation. | It is not a universal bass ceiling: existing layers can still exceed it on their own, and negative settings are never limited. |
| D3 | 2026-11-11 | **Auto loudness** gets a separate fix with explicit activation for existing users. | No existing user's sound changes without their action. |
| D4 | 2026-11-11 | **Custom dynamics:** the protection analysis comes first. The proposed ranges are **not approved** for implementation. | The analysis must document the complete static comparison, the gain assumptions and the transient limitations. |
| D5 | 2026-11-11 | **Order:** documentation correction → provenance and instrumentation → companion synchronisation → validated Custom dynamics → further editor and tone work. One implementation milestone at a time. | Later milestones wait for earlier ones. |
| D6 | 2026-11-11 | **Knee wording:** a documentation-only correction on #474, pushed only after remote-write approval. No audio change. | Committed locally as `7679ecee`; not pushed at the time of writing. |
| D7 | 2026-11-11 | **Microphone and projector noise:** the full measurement-quality scope (capture-path audit, ambient check, post-sweep noise assessment, calibration and provenance, actionable guidance and recovery). | No always-on microphone. No automatic sound changes driven by noise. |

Standing rules that apply to every milestone: #474 stays a draft; no merge,
version bump, tag or publication without approval; no force-push; existing
users keep their profiles and sound; hardware checks stay NOT RUN until
observed; source-text checks, fake audio and APK installs are not evidence
that playback works.

## What the engine can do

Core EQ applies sound through Android's DynamicsProcessing effect, or through
the platform Equalizer when a TV refuses it.

| Stage | What Android lets an app set | How Core EQ uses it |
|---|---|---|
| Input gain | One gain per channel | `0 dB`, read back |
| Pre-EQ | Bands with an upper cutoff and a gain; **no Q, no filter type** | 25 bands: 24 from 40 Hz to 8 kHz about a third of an octave apart, one from 8 to 20 kHz |
| Multi-band compressor | Per band: threshold, ratio, attack, release, knee, noise gate, expander ratio, pre-gain, post-gain | Not used; verified off |
| Post-EQ | As pre-EQ | Not used |
| Limiter | Per channel: on/off, link group, attack, release, ratio, threshold, post-gain; **no knee, no lookahead** | The one dynamics stage; Night mode retunes it |
| Read-back | Every value above | Read back after each apply; a mismatch fails the apply |
| Telemetry | **None**: no gain reduction, level or clipping readings | — |
| Equalizer fallback | Band levels at the TV's own centres (five in Android's reference build) | **No dynamics at all** |

Consequences carried into every design below:

- Night mode is one stage, Android's limiter, set to compressor-like values.
  While Night is on, there is no separate limiter behind it.
- Lookahead, sidechain, automatic makeup gain and gain-reduction meters do
  not exist on any path. A knee exists only in the unused multi-band stage.
- Any EQ filter is sampled at band centres. A filter narrower than about one
  band (Q above roughly 4) cannot be reproduced.
- Read-back shows a TV accepted values, not that its implementation honours
  them.

## Milestones, in order (D5)

Each milestone is one branch and one draft PR, stacked on the one before it
and retargeted to `main` as its parent merges. Only one branch at a time
changes the profile file format, and only one changes the phone protocol.

```
main 19b8834f (1.13.2)
 └─ #474   listening, profiles, status (draft) + 7679ecee knee correction (local, D6)
     └─ C   provenance + instrumentation       profile format: provenance object
         └─ S   companion synchronisation      phone protocol: revision, capabilities
             └─ K   validated Custom dynamics  protection analysis first (D4)
                 └─ T   Warmth + Brightness    (D1, D2)
                     └─ P   parametric bands, trim, headroom preview
                         └─ L   listening presets, per-output, A/B, guidance
Separate, each one at a time when scheduled:
  AL  Auto loudness fix with explicit activation (D3)
  N   measurement quality: microphone and projector noise (D7)
  K2  compressor stage with a fixed protection limiter behind it (needs TV evidence)
  E   preset imports (after P)
```

### C: provenance and instrumentation (in progress)

**Provenance.**
- Two records per profile: **created by** (the app build that produced the
  profile's correction: a measurement, a REW import or a manual profile;
  written once) and **last saved by** (the app build that last saved that
  profile).
- Never invented. A profile without them shows "not recorded".
- Opening, listing or viewing a profile writes nothing. Saving one profile
  never stamps another.
- Rename keeps "created by" and updates "last saved by". Duplicate copies
  "created by" and stamps its own "last saved by".
- Export carries both. Import keeps the file's "created by" when present and
  sets "last saved by" to this build.
- A build older than C reads such a file and ignores the new keys; if it
  saves the profile again, it drops them. That loss on downgrade is accepted
  and documented.

**Instrumentation.** Each timing keeps a count, the last, shortest and longest
value, and the most recent 32 values for a median. Memory only, cleared when
the process ends, nothing per sample in the log.

| Timing | Starts | Ends |
|---|---|---|
| Apply | The service begins applying to a session or the output mix | The effect is configured and read back, or refused |
| Engine configure | Band and limiter values are handed to Android | The read-back check completes |
| Player discovery | The discovery request is issued | Its output is parsed |
| Route probe | A probe track is opened | Android reports a route, or the probe gives up |
| Measurement analysis | A captured sweep is handed to the analysis worker | The result is ready for the screen |
| First draw | Home or Profiles is created | Its first frame is drawn |

**Device benchmark.** An `adb` procedure run from a computer: a fixed number
of cold and warm starts, frame statistics for Home and Profiles, and the
timings above read from the diagnostics summary, written to one file naming
the TV model, Android build and app build. No performance claim is made
until such a file exists.

### S: companion synchronisation

- The TV is authoritative. It keeps a revision number that rises on every
  sound change, from either device.
- `get_sound_state` returns every editable value, the revision, and a
  capabilities list giving the reason for each unsupported control.
- `set_sound` carries the changes, the revision they were based on and a
  request number. The TV answers with the values it actually applied (after
  clamping), the new revision, and whether the engine accepted them.
- A write based on an old revision is refused and returns the current state.
- Each control on the phone shows requested, pending, acknowledged or failed.
- Rapid edits are merged; only the latest value per control is sent; answers
  older than the newest acknowledged request are ignored.
- Reconnect refetches the state. Unsent edits are offered for resending,
  never sent silently.
- A phone talking to an older TV hides editing: "Update Core EQ on the TV".
- Staged editing on this protocol: Night and Clarity first; Custom dynamics
  and tone after their milestones; manual and parametric EQ and trim; profile
  rename, duplicate and details; presets and A/B.

### K: validated Custom dynamics (D4)

**Prerequisite: a protection analysis document**, written and reviewed before
any range is implemented. It must contain:

- **The complete static comparison.** For every candidate preset and for the
  edges of any proposed range: the input/output curve from −60 to +12 dBFS
  against the protection limiter (−1 dBFS, 10:1), the margin at every point,
  and the worst case. What happens above +12 dBFS, where nothing is
  guaranteed.
- **Gain assumptions.** Input gain 0 dB; post-gain 0 dB; no makeup gain; the
  EQ's headroom shift keeps Core EQ's largest band at 0 dB; Android's
  BassBoost and LoudnessEnhancer may add gain outside this chain and in an
  unknown order; a floating-point pipeline can carry samples above 0 dBFS
  into the limiter.
- **Transient limitations.** No lookahead: a transient shorter than the
  attack passes partly unreduced. Release shapes recovery and pumping. The
  knee is the TV maker's (hard-knee model only). Channel linking is the one
  link group Core EQ sets. None of this is measurable on the device: Android
  reports no gain reduction.

Design (subject to the analysis):
- Simple stays Off, Light, Strong.
- Advanced edits threshold, ratio, attack and release on the same single
  stage. Any edit makes the preset Custom, which is stored separately.
- One stage only, so two listening compressors never run in series.
- Makeup gain stays 0 and is not shown.
- On the Equalizer fallback the panel is unavailable, with the reason.
- It is compression of loud passages, never "dialogue isolation" or
  "normalisation".

Tests: static curves and the protection rule; preset and bypass transitions;
storage and the 1.13.2 reference values; restoration after a measurement, a
cancelled measurement and lost audio focus; the unsupported-engine path.
Stereo linking, attack and release audibility, and whether a TV honours the
values need hardware.

### T: Warmth and Brightness (D1, D2)

- **Warmth:** low shelf centred on 200 Hz; full effect below about 130 Hz,
  none above about 320 Hz; steepest slope 0.83 dB per octave per dB of
  setting. **Brightness:** high shelf centred on 6.3 kHz; none below about
  4.3 kHz, full above about 9.2 kHz; 1 dB per octave per dB of setting. Both
  use the curve family of the existing Low-volume bass shelf, evaluated at
  each engine band.
- **Combination (D2).** Existing layers (Clarity, Stereo speakers, Low-volume
  bass, Auto loudness) are computed exactly as today. At each band, a
  positive Warmth or Brightness contribution is reduced so that the listening
  layers' combined lift there does not exceed +3 dB; where existing layers
  already exceed it, the new contribution there is 0 and the existing layers
  are untouched. Negative settings are never limited. This is a limit on the
  new controls only, not a ceiling on bass.
- **Headroom.** The automatic shift is computed from the sum of every layer,
  correction and manual EQ included, after that limit.
- **Fallback.** On a five-band Equalizer, Warmth +3 becomes about +3.0 dB at
  60 Hz and +1.0 dB at 230 Hz; Brightness +3 about +3.0 dB at 14 kHz. The
  screen says "approximate on this TV".
- **Measurement.** Never stored in a profile or export. The service is
  suspended during sweeps; the check sweep must also play without them
  (confirmed in code and pinned by a test in T).
- With both at 0, every band is identical to today; the 1.13.2 reference
  tests enforce that.

### P: parametric bands, trim and headroom preview

- Peaking, low shelf and high shelf, each with frequency, gain, and Q or
  slope; bypass and reset per band; ten bands.
- Labelled as an approximation. Tests measure the largest gap between the
  ideal curve and the 25-band result; the Q limit (provisionally 0.5 to 3.0)
  is set from that number. The graph draws both curves.
- No high-pass or low-pass filters; no per-channel bands.
- Five layers stay separate: measured correction, manual listening EQ, tone,
  dynamics, headroom. Editing manual EQ never rewrites a measurement.
- Automatic headroom is shown as a number. User trim is 0 to −12 dB, cut
  only. BassBoost and LoudnessEnhancer are labelled as outside the accounted
  chain. No live clipping display and no true-peak claim.

### L: listening presets, A/B and guidance

- Named sets of listening settings, separate from room profiles.
- Explicit per-output assignment. Precedence: manual choice, then the
  output's preset, then the global default. Unknown or ambiguous outputs get
  the global default.
- On upgrade, today's settings become the default preset: no sound change.
- No time-based or per-app switching until output identification is reliable
  and the user enables it.
- A/B switches between current settings and a snapshot without changing what
  is stored; it states the calculated level difference and makes no
  loudness-matching claim.
- Guidance names its trigger (overlapping boosts, Night on an engine that
  cannot run it, low measurement quality, an unknown output, no profile for
  the output) and offers an explicit action. Nothing changes automatically.

### AL: Auto loudness fix with explicit activation (D3)

The defect: since 2026-11-01 (`9d618a670`, shipped in 1.13.2),
`ToneLayers.responseDb` looks up the nearest manual band by comparing a band
**index** with a frequency, so every band receives the last band's gain and
the headroom shift cancels that uniform amount. Twenty dB below reference,
intended lifts of +6.0, +6.0, +5.5 and +3.2 dB in the bass were applied as
+0.26 dB everywhere; the configured bands are identical with the switch on
or off.

Design:
- The corrected lookup uses frequency (nearest band in log frequency, or
  interpolation between bands; chosen in the milestone with tests).
- A user whose stored setting is "on" from a build before the fix keeps the
  **current behaviour** (the old computation, kept bit-for-bit) until they
  choose the corrected version. Extra effects says that the setting has not
  been applying anything and offers "Use corrected Auto loudness" with an
  explanation: up to +6 dB at low volume in the bass, and the level drop the
  headroom shift brings.
- Switching Auto loudness on after the fix uses the corrected version.
- Home and diagnostics say which of the two is in effect.
- Tests: an Auto loudness case added to the 1.13.2 reference capture
  (regenerated with `tools/regen_core_eq_legacy_golden.py`) proves the
  legacy path is unchanged; the corrected path follows the intended band
  gains; Low-volume bass stays mutually exclusive.

### N: measurement quality, microphone and projector noise (D7)

- **Capture-path audit.** Which microphone and input source each capture
  path uses (remote, phone, USB), its sample rate, and whether Android input
  processing (gain control, noise suppression, echo cancellation) can be
  detected or disabled on that path. Recorded per measurement.
- **Ambient check before a sweep.** The noise floor per band; a warning, with
  the affected bands named, when the room is too noisy to measure them.
- **Post-sweep noise assessment.** Signal-to-noise per band from the silent
  segments around the sweep; bands below the threshold are marked unreliable
  and left uncorrected, as weak bands already are.
- **Calibration and provenance.** Microphone identity, calibration status
  (uncalibrated unless a calibration is applied) and the noise readings are
  stored with the profile, alongside C's provenance.
- **Actionable guidance.** Name what the readings suggest (a steady tone near
  a fan's frequency, broadband noise) and what to try: pause the projector
  fan if it allows, move the microphone, lower other sources, measure again.
- **Recovery.** Re-measure without losing the previous profile; keep a
  partial correction with the noisy bands excluded; never apply a measurement
  whose quality check failed without the user's choice.
- Never: an always-on microphone, volume or compression that follows
  noise, or a claim that EQ or compression removes fan noise.

### K2 and E

- **K2:** a real compressor in the multi-band stage (one full-range band)
  with the protection limiter fixed at −1 dBFS behind it. This adds a knee and
  separates listening compression from protection. It needs evidence that a
  TV honours that stage before any user gets it.
- **E:** preset imports after P. AutoEq, Equalizer APO and Poweramp presets
  already import as ten peaking bands; shelves and free frequencies come with
  the parametric model. Units, filter types, validation and gain limits are
  defined there. A headphone correction is stored as listening EQ, never as a
  room profile.

## Capability matrix

| Feature | Present | Engine support | TV UI | Companion UI | Fallback | Tests | Hardware | Milestone |
|---|---|---|---|---|---|---|---|---|
| Night Off / Light / Strong | Yes (#474) | Limiter stage, read back | Yes | No | Unavailable, says so | Curve, 1.13.2 reference | NOT RUN | S for the phone |
| Custom dynamics | No | Same stage; four values settable | Planned | Planned | Unavailable + reason | Curve, transitions, persistence, restore | Needed | K (analysis first) |
| Compressor + fixed protection limiter | No | API has it; unproven on TVs | Later | Later | Falls back to K, then Equalizer | Config and read-back fakes | Required first | K2 |
| Makeup gain | No | Post-gain exists | Not shown | Not shown | — | Protection-only rule | — | Not planned |
| Knee, lookahead, sidechain, meters | No | Not available on the used stage | Never | Never | — | — | — | Not planned |
| Clarity | Yes | Pre-EQ bands | Yes | Planned | Approximate | Yes | NOT RUN | S |
| Warmth, Brightness | No | Shelf sampled in bands | Planned | Planned | About one band each | Planned | Needed | T |
| Manual EQ, 10 fixed bands | Yes | Sampled in 25 bands | Yes | No | Five bands, coarse | Yes | Partly | S for the phone |
| Parametric bands, shelves, bypass | Model stores freq, Q, gain | Approximated; error to be measured | Planned | Planned | Coarse, labelled | Approximation error | Needed | P |
| Combined-response preview | Correction + manual | Calculation | Extend | Planned | Same | Yes | None | P |
| Automatic headroom shown | Manual EQ only | Calculation | Extend | Planned | Same | Headroom audit | None | P |
| User trim, cut only | No | Input gain or band offset | Planned | Planned | Band offset | Whole gain chain | Needed | P |
| Live clipping or limiter activity | No | No telemetry | Never | Never | — | — | — | Not planned |
| Revision, request states, capabilities | No | — | TV authoritative | Planned | Older TV: editing hidden | Fake transport | TV + phone | S |
| Profile choose | Yes | — | Yes | Yes | — | Yes | NOT RUN | — |
| Profile rename, duplicate, details | TV (#474) | — | Yes | Planned | — | TV tests | NOT RUN | S |
| Profile provenance | No | — | Details | Later | "not recorded" | Round trips, legacy load | None | C |
| Timings and benchmark | Counts only (1.13.2) | — | Diagnostics | — | — | Span maths | Benchmark run | C |
| Listening presets, per-output | No | — | Planned | Planned | Global default | Migration reference | Needed | L |
| Listening A/B | Correction A/B only | Calculation | Planned | Planned | Same | Yes | Listening | L |
| Guidance with named triggers | Room hints only | Calculation | Planned | Planned | — | Rule tests | None | L |
| Auto loudness | Switch; applies nothing (defect) | Pre-EQ bands | Yes | No | — | None today | — | AL |
| Measurement-quality checks | SNR score only | Microphone capture | Planned | Planned | — | Planned | Needed | N |
| Preset imports | 10 peaking bands | — | Yes | No | — | Yes | None | E |

## Wireframes

Rows marked "unavailable" are controls the engine cannot support. The phone
gets the dense editor; the TV gets the same supported functions as rows
stepped with the D-pad, not touch knobs.

Phone, everyday screen:
```
+----------------------------------+
| Control                          |
| [Applied by DynamicsProcessing   |
|                         in sync] |
| Correction                    On |
| Room profile       Living room > |
| Listening preset   My settings > |
| Night mode                       |
|  ( Off ) (*Light*) (Strong) (Custom)
| Clarity              -  +2 dB  + |
| Warmth               -   0 dB  + |
| Brightness           -   0 dB  + |
| Compare A / B                  A |
| [ Advanced sound editor        >]|
+----------------------------------+
```

Phone, advanced editor (EQ tab):
```
+----------------------------------+
| Editor - EQ          Undo  Reset |
| 2 changes sent    waiting for TV |
| +------------------------------+ |
| | - - measured correction      | |
| | ~~~ your EQ                  | |
| | === combined, as applied     | |
| +------------------------------+ |
| +-- Band 2 ----On-+ +- Band 3 -On+
| | (*Peak*)(Low sh)| | (*Peak*)   |
| | Gain   -4.0 dB  | | Gain +2.0  |
| | Freq   280 Hz   | | Freq 1.10k |
| | Q      1.50     | | Q    1.00  |
| +-----------------+ +------------+
| Tap a value to type it. Bypass   |
| and reset per band.              |
| Headroom (automatic)     -2.0 dB |
|  EQ | Tone | Dynamics | Trim | Presets
+----------------------------------+
```

Phone, dynamics tab (values illustrative; ranges not approved, D4):
```
+----------------------------------+
| Editor - Dynamics    Undo  Reset |
|  ( Simple ) (*Advanced*)         |
|  (Off) (Light) (Strong) (*Custom*)
| Threshold           -  -18 dB  + |
| Ratio               -   2 : 1  + |
| Attack              -   10 ms  + |
| Release             -  200 ms  + |
| Makeup gain       off (unavailable)
| Gain-reduction meter              |
|            Android reports none  |
|  EQ | Tone | Dynamics | Trim | Presets
+----------------------------------+
Equalizer fallback: "Not available: this TV's engine has no dynamics."
```

TV, Night mode advanced:
```
+-----------+-------------------------+--------------------------+
| Core EQ   | Night mode              | What this does           |
| Manual EQ | Preset     < Custom >   | Passages louder than the |
|>Extra     |[Threshold  < -18 dB >]  | threshold are turned     |
|  effects  | Ratio      < 2 : 1 >    | down. Changing a value   |
| Profiles  | Attack     < 10 ms >    | makes the preset Custom. |
|           | Release    < 200 ms >   | Android does not report  |
|           | Back to Light      OK   | how much it is reducing. |
+-----------+-------------------------+--------------------------+
Up/Down choose a row. Left/Right step. Hold to step faster.
```

TV, parametric bands:
```
+-----------+---------------------------+------------------------+
| Core EQ   | Bands - Everyday          | Band 2                 |
|>Manual EQ | 1 Low shelf 50 Hz    0.0  | Type       < Peak >    |
| Extra     |[2 Peak 64 Hz        -5.0] | Frequency  < 64 Hz >   |
|  effects  | 3 Peak 280 Hz       -4.0  | Gain       < -5.0 dB > |
| Profiles  | 4 Peak 1.1 kHz  bypassed  | Q          < 1.40 >    |
|           | Trim -5.0 dB              | Bypass / Reset band OK |
|           | Headroom -2.0 dB          |                        |
+-----------+---------------------------+------------------------+
```
