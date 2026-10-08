# Core EQ changelog

Core EQ (`tv.corebuilds.eq`) keeps its own changelog. The repository-root
`CHANGELOG.md` belongs to the icon pack: `tools/prepare_release.py` turns its
`[Unreleased]` section into the icon pack's in-app What's New, so Core EQ notes
must never land there. Releases are `coreeq-v<version>` tags (see
`PUBLISHING.md`).

## [Unreleased]

## [1.3.0] — 2026-10-08

### Added

- **Every sweep gets a quality score.** The Measure screen shows
  `Quality 0–100` after each sweep, in green, amber or red. It weighs signal
  to noise (30), a measurable decay (20), untouched nulls (20), bands that
  pass the minimum-phase gate (15) and the transition frequency (15). The
  score is saved with the profile (`quality_score` in the JSON backup) and
  shown on Home and Profiles. It describes the measurement and never changes
  the correction. REW imports carry no signal-to-noise or decay data, so
  they say "not scored" instead of showing a low number; profiles saved
  before 1.3.0 show no score.
- **Dialogue boost** (Extra effects, off by default): +2 dB across
  2.2–4.5 kHz, back to neutral by 6.5 kHz, the speech-intelligibility
  plateau from research pass 6. Profiles measured with the Dialogue or House
  target already carry it, so there it adds nothing.
- **Low-volume bass** (Extra effects, off by default): ISO 226 loudness
  compensation. Switching it on records the current volume, read through
  Android's own volume curve in dB, as the reference. Each output keeps its
  own reference (Android keeps a separate volume, on its own curve, per
  output), recorded the first time that output reports a volume, so
  switching from TV speakers to a soundbar adds nothing until the soundbar
  is turned down. The Extra effects screen follows the volume keys while
  open. Below the reference, half the
  ISO 226 difference at 63 Hz is added as a shelf under 100 Hz, in 0.5 dB
  steps, capped at +3 dB, and never below the speaker's measured roll-off.
  It follows the volume as it changes. On a fixed-volume output (a
  soundbar that keeps its own volume over HDMI-CEC) Android reports no
  volume, so it adds nothing, and the screen says so.
- **A brief notice when a profile is applied.** A toast, "Core EQ · profile ·
  mode", shows over whatever is playing when a different profile or mode
  takes effect: an output switch, a new measurement, a choice in Profiles, a
  mode change. Plain reapplies (a volume step, an Extra effects change, a
  player attaching) stay silent. On by default; switch it off on Profiles.
- **Home warns about HDMI bitstream.** When Android may send Dolby or DTS
  to the HDMI output as a bitstream, Home shows the warning on its own line:
  a bitstream skips all on-device EQ. Until now it was only a clause at
  the end of the status text.

### Changed

- Dialogue boost and low-volume bass are summed into the correction before
  headroom is taken, on DynamicsProcessing and on the platform Equalizer, so
  their boost lowers everything else instead of clipping. They are never
  written into a profile, backup or export.
- The Extra effects **Loudness** switch is now **Loudness enhancer**, its
  Android effect name, so it cannot be mistaken for low-volume bass.
- **Home fits on one screen.** At 1080p, Home showed 3 of its 8 menu rows
  without scrolling. The active profile, its quality score, Correction, Mode
  and Re-measure are now one card at the top. The six other screens are a
  menu grouped under **Sound** (Manual EQ, Extra effects, Content type) and
  **Set up** (Profiles, Capability, Display calibration). The graph takes
  whatever height is left, so the status line under it is no longer cut
  off. While correction is off, the status line says what Off means
  instead of repeating "Correction: Off".
- **Every on/off switch shows its state.** Correction and the switches on
  Extra effects, Profiles, Mode, Capability and Content type have a
  switch drawn at the end of the row and a cyan-tinted background when on.
  Until now, the only sign of state was the word at the end of the label.
- **Button labels are in sentence case.** Android's default button style
  capitalised every label, which wrapped "LOW-VOLUME BASS: OFF" and similar
  labels onto two lines.
- **Content type is a grid of ten chips** with the selection filled. It
  no longer dims the other nine to 60%, uses no emoji, and fits without
  scrolling. Auto-detect sits beside Done.
- **Display calibration's panel sits inside the screen edges.** It ran to
  the left, right and bottom edges, where TV overscan can cut text. It now
  keeps the same 48dp side and 24dp bottom gutters as every other screen.
  Its buttons have side padding (their labels touched their borders), and
  the panel uses the app's type and spacing tokens instead of fixed sizes.
- **No control sits below the screen any more.** At 1080p, Profiles' Export
  and Delete, Manual EQ's Done, and Mode's app rules and Done were below
  the bottom edge. Profiles' graph now takes the height that is left, Mode's
  three modes are one row of chips, and Manual EQ's Save and Reset share a
  row.
- **Profiles' list rows no longer clip.** A long name wrapped onto a second
  line and pushed the details line out of the card; names now end in "…".
  The quality score leads the details line, as on Home, so the "…" never
  hides it. The export summary is set in the body size instead of a bold
  16sp.
- **Mode explains automatic switching in plain bullets** under a heading,
  instead of one paragraph about session UIDs. Its status lines (app rules,
  conflicts, players now) are in sentence case.
- **Manual EQ's profile card** names the profile and its output. The mode
  being edited is only on the Editing mode row, where it was already shown.
  Headroom fits on one line.
- **Measure's Stop works only while a sweep runs**, and looks unavailable
  otherwise. Focus moves to Stop when a sweep starts and back to Start when
  it is stopped.
- **Unavailable buttons look unavailable.** A disabled primary button kept
  its dark label on a grey body, which could not be read. Disabled
  secondary buttons dim their label and border.
- **A focused switch that is on keeps its cyan tint** under the focus ring.
  Until now, focus hid the on state.
- **Graphs:** the legend sits on a backing, so a curve that runs into the
  corner passes under it. Frequency labels are centred on their grid
  lines, and a long title ends in "…" instead of running off the graph.
- Scrolling columns (Capability, Measure, Extra effects) fade at the edge
  that has more content, instead of cutting a card in half.

### Removed

- 466 lines of 1.2.0 Kotlin that nothing called: `ImprovedAudioDeviceManager`
  (its "passthrough" test was "is the output HDMI";
  `OutputRoute.mayPassThrough` checks the TV's surround setting), night
  mode's never-applied 3.2 kHz dialogue peak, the volume-to-phon estimate that
  treated a volume step as linear amplitude, and 27 unused strings.

## [1.2.1] — 2026-10-07

### Fixed

- **Updating no longer changes your sound.** 1.2.0 switched Bass Boost,
  Loudness Enhancer and a second DynamicsProcessing stage on for everyone,
  with no screen to turn them off. In 1.2.1 every extra is off until you
  switch it on, and settings stored by 1.2.0 cannot switch one back on.
- **Room correction is precise again.** 1.2.0 moved correction onto the
  platform Equalizer (about 5 fixed bands): a measured filter was applied
  only if it sat within 100 Hz of a band centre, and the preamp was
  ignored, so boosts could clip. Correction is back on DynamicsProcessing,
  with the platform Equalizer as the fallback, exactly as in 1.1.1.
- **Several players at once.** 1.2.0 held one effect chain for the whole
  service and released it whenever another audio session attached, while
  the first session still used it; closing a session left its bass and
  loudness effects running. Extras now belong to each session's correction
  effect and are created and released with it.

### Added

- **Extra effects screen** (Home → Extra effects): Bass boost, Loudness
  and Night mode switches, each applied at once. Bass and loudness strength
  follow the Content type screen, which until now changed nothing at all.
  Night mode tightens the DynamicsProcessing limiter to -12 dB at 4:1, and
  Home says when the engine in use cannot run it.

### Removed

- The 1.2.0 effect chain (`EnhancedEffectChain`, `EffectChainManager`) and
  its unused status card. The time-based night-mode schedule is gone with
  it: it was never evaluated after the effect was created.

## [1.2.0] — 2026-10-06

### Added

- **Content-aware listening and optional night-mode processing.** Core EQ adds content-type controls and app-aware suggestions, with optional bass, loudness and DynamicsProcessing effects where the device supports them. Effect availability and playback coverage remain device- and route-dependent; the app does not claim that every stream is processed.
- **Guided picture setup for Android TV.** A remote-friendly calibration walkthrough uses on-screen patterns and TV-setting guidance; it does not change the television's picture settings automatically.

## [1.1.1] — 2026-10-05

### Added

- **Core EQ updates itself from the same release feed the other Core apps use.** Home shows an update bar when `Latestrelease/coreeq-version.json` on `main` names a newer build, and the Capability screen gains an App updates card with the installed version, a Check-for-updates button, the automatic-check toggle and the exact reason when a check fails. The release APK is only offered to Android's installer after it matches this app's package, is newer than this build, matches the SHA-256 the feed published, and is signed by the certificate that signed the installed app; anything else is deleted and named as the refusal it is. The public test package (`tv.corebuilds.eq.debug`) carries no feed at all and says so, because a production APK could never install over it. Checks default to on and can be switched off, in which case the app makes no network request until asked; "Later" is remembered per version. `UpdateRulesTest` pins the feed/URL/version rules, and CI writes the feed only after the release assets exist, so an installed copy can never be told about an APK that is not downloadable yet.
- **A real manual equaliser is available on TV, alongside room correction.** The Manual EQ screen exposes ten editable peaking bands from 40 Hz to 8 kHz (±6 dB in 0.5 dB steps), controlled by left/right and up/down on a remote or by touch-and-drag. Flat, Bass lift, Speech clarity and Less bass presets ship in the app; custom presets can be saved and recalled. Manual tone is persisted in per-profile, per-mode overlays, separate from the measured room curve, so it can be edited/reset without rewriting measurement data. The active overlay is composed into DynamicsProcessing, the platform Equalizer fallback, Home/Profile previews, TV-settings export and the selected-mode Poweramp/GraphicEQ export; the Core EQ JSON backup preserves all three overlays. Positive response peaks receive a computed headroom cut before an engine without a preamp is programmed. The Profiles screen labels the preamp for the selected export format; GraphicEQ uses its serialized curve's peak, while the base-only GraphicEQ export still has no preamp line (a pre-existing limitation). A viewer can create a manual-only profile without taking a fake room measurement; Home and Profiles label it as not measured. `ManualEqTest` pins the band limits, response, headroom and exports.
- **Movie / TV, Everyday and Gaming modes layer over the room base.** Select a mode directly with the TV remote, or opt into best-effort app rules. DUMP-discovered session UIDs are the only app-identity source; identity is used for a rule only when the package mapping is unambiguous. Session broadcasts and public playback callbacks are rescan triggers only—their session/package extras are ignored. Android documents DUMP as not for third-party apps, so an ADB grant may be refused on stock or release builds. Unmapped, unidentified or conflicting active players fall back to Everyday when automatic switching is on. Manual selection can override automatic rules either stickily or until the detected player set changes. `ContentModeResolverTest` and `DiscoveredPackageResolverTest` cover arbitration, fallback, manual priority, override lifetime and ambiguous UID mappings.
- **The Home indicator distinguishes playback from verified coverage.** When Android reports media playback while Core EQ has configured its output-mix effect, the badge shows `PLAYING · COVERAGE UNKNOWN` with a pulsing dot; it shows `STANDBY` when armed but no playback is reported and `NOT APPLIED` when the platform refuses the effect. `EqService` publishes the same playback hint to the status line and notification. Public playback callbacks do not identify the app or its route, so this is not proof that the active stream traverses the effect. Per-session correction stays unbadged because Core EQ cannot associate the public playback callback with a held session. `CorrectionIndicatorTest` pins the state table.
- **Optional DUMP discovery can expose additional player sessions.** If the device permits it, try the `android.permission.DUMP` command shown on the Capability screen; Android marks DUMP as not for third-party apps, so stock/release builds may refuse the grant. `EqService` reads `dumpsys` output and attempts to attach only to session IDs with a usable UID and media/game-compatible usage; it releases discovered effects only when a session disappears from a complete, recognized pair of dumps. Failed or unrecognized scans preserve the last-known player set and do not expire temporary overrides. UID-less media/game candidates are never attached, but count as unidentified for the Everyday fallback. This is heuristic parsing of firmware text, not a stable Android API. The unit-test fixtures are representative shapes, not captures from a supported TV. Reports of YouTube being a gap belong to other products/setups and do not establish Core EQ's coverage; app and firmware behavior remains unverified without device runs. A failed player attachment does not stop other sessions being tried. The app's own sweep is excluded, and only media/game sessions are eligible. `DumpsysSessionsTest` pins the parsed formats and attachability rules.
- **DynamicsProcessing is now an apply path on API 28+.** Core EQ builds a 25-band PreEQ (24 correction bands through 8 kHz plus a neutral high-frequency guard band) with MBC/PostEQ off and a linked protection limiter, verifies the framework's configuration read-back, and falls back to the platform Equalizer on any refusal. The status line names the engine actually used and the Home band rail follows its live band layout. Real-TV engagement and limiter-clamp validation remain a release gate (M6a).
- **A REW measurement can be imported instead of a sweep.** The Measure screen's "Import REW measurement" takes REW's exported response text (including FRD/CSV and locale-safe decimal commas) and runs the magnitude through the same band, null, roll-off, two-regime correction and filter-fit chain as a sweep. The phase column is deliberately not analysed in M10: RT60 and Schroeder stay null, SNR is empty, and the profile carries a measurement note that the minimum-phase gate is unverified and transition defaults to 300 Hz. The limit note persists in the profile, JSON and parametric exports. Unreadable or out-of-range files are refused with the reason. The imported profile is saved for export and is **not applied to the TV** unless the user explicitly selects it in Profiles — important when an AVR or soundbar already corrects downstream. Because REW files do not name the TV output, the profile is tagged to the current Android route estimate at import time (or `unknown`), with a provenance note asking the user to confirm that it matches the measured chain; it never silently becomes an all-output profile. `RewImport` and `RewImportTest` pin the parser, correction chain, locale format and the community's 45 Hz roll-off fixture.

### Changed

- **Android 11 (API 30) is now the minimum; Core EQ builds and targets Android 17 (API 37).** Android 8–10 devices are no longer supported. CI installs the API 30 and API 37 emulator images to check APK installation at both ends of the supported range.
- **The Measure screen now says what to do when the sound is already calibrated.** If an AVR or soundbar already calibrates the room (Audyssey, YPAO, A1 Evo, Dirac), do not stack Core EQ on it: measure and export the profile instead.

- **The band maths has one home.** `EqService` and the TV-settings export both sample the correction through `BandMapping`, so the preview, the export and the applied bands cannot drift apart. TV-settings and Poweramp exports include the currently selected mode overlay and identify that mode in the filename.
- **Output matching uses the device name when Android supplies one.** On API 33+, route selection prefers `getAudioDevicesForAttributes()` for media, while clearly treating it as a route estimate rather than proof of another app's active route. Same-kind device profiles match normalized output names; an unmatched named output will not borrow another named device's room curve. Generic same-kind profiles remain a fallback. `OutputRouteTest` covers exact, generic and mismatch cases.
- **The waiting status names the optional discovery path without promising it will work.** When whole-TV correction is unavailable and no DUMP grant is present, the status points to the Capability-screen ADB command and explains that Android may refuse it; profile export remains the manual fallback.

### Fixed

- **Exact manual boosts no longer lose an extra hundredth of headroom.** The parametric preamp still rounds conservatively, but now ignores sub-nanodecibel floating-point overshoot before flooring (so an exact +3 dB peak needs −3.00 dB, not −3.01 dB). It still preserves whichever value requests more attenuation.
- **Opening the manual editor no longer changes the chosen profile.** It edits the profile matched to the current output without rewriting the user's shared fallback selection; a newly created manual-only profile is saved without silently becoming the chosen profile.
- **The manual editor follows an output change made while it was paused.** On resume it re-resolves the route-matched profile and asks the running service to reapply, so edits remain attached to the output they are being made for.
- **A room or target change cannot save a stale sweep under a new label.** Sweep re-analysis locks the choices and Save button until the matching result completes, ignores stale completions, and drops the previous capture when a new measurement begins.
- **Capability only calls session-0 Equalizer supported when it has control.** If global EQ is unavailable but DUMP is granted, the ladder identifies discovery before companion export; export is no longer described as infallible.
- **Existing session effects are reapplied when DUMP discovery changes the mode.** A mode transition now updates held sessions as well as newly discovered sessions.
- **Legacy manual EQ migration preserves and merges edits safely.** Legacy bands merge into Everyday without overwriting newer per-mode edits at the same band; overlay import, profile persistence and the completion marker check `commit()` results so a failed migration can retry without clearing the only saved copy.
- **GraphicEQ headroom is calculated from the curve points actually exported.** A larger measured-curve peak can no longer be hidden by a lower parametric fit; an existing more-negative room reserve is preserved.
- **Player identity no longer comes from broadcast claims.** Open/close session broadcasts and public playback callbacks only trigger a coalesced DUMP rescan; Core EQ ignores their package and session extras. A discovered UID selects an app rule only when `PackageManager` reports one distinct non-Core-EQ package; missing or ambiguous mappings remain unidentified and demand the Everyday fallback in automatic mode.
- **Correction follows the output it was measured on.** Asked on Discord (2026-09-30): does it matter whether the sound comes from the TV, a soundbar or a system? It does. A measurement captures one chain, but a profile recorded nothing about which one, so switching from the TV speakers to a soundbar kept applying the speakers' curve. Each new profile now records its output (`TV speakers`, `HDMI ARC (soundbar or receiver)`, `HDMI`, `Bluetooth`, `USB audio`, `Wired`, plus the device's own name where Android gives one, e.g. "Sonos Beam") and puts it in the profile name. `EqService` re-picks when a device comes or goes: the chosen profile on its own output, else the newest one measured on the current output, else correction pauses on that output and says so ("Nothing measured on Bluetooth yet…") instead of applying another chain's curve. Profiles saved before this apply on every output, as they did, so an update switches nothing off. A profile's output kind uses the sweep's own `AudioTrack` route when Android reports a recognized device, and the connected-output estimate only as a fallback. At playback Android still cannot reveal another app's actual destination; if its route estimate differs from the measured chain—or Android reports no recognized route—Core EQ pauses rather than guessing with another output's curve (only legacy all-output or explicitly unknown-output profiles apply while unidentified). `OutputRoute.kt`, `OutputRouteTest` (16 tests).
- **Passthrough is named.** Dolby and DTS sent to a soundbar or receiver as a bitstream never pass through Android's mixer, so no on-device EQ reaches them. On HDMI outputs where Android 12+ reports surround passthrough as possible, the status says so and how to correct them (Surround sound → PCM).
- **Profiles distinguish the chosen profile from the one matched to this output.** The ACTIVE badge follows the route-matched profile, including when the route changes while Profiles is paused. A profile for another output remains previewable/exportable, but selecting it warns that it will not be applied to the current chain.
- **The test channel's APK is signed by the production certificate, so an installed test build can update in place.** Every build of `tv.corebuilds.eq.debug` published to `coreeq-test` was previously signed with whichever throwaway debug key the CI runner generated, so a new build's signature never matched the installed one and Android refused the update. Versioned releases and the public test lane now decode the repository's shared signing key, the workflow refuses to publish if that key is missing, and it compares the candidate's certificate against the APK currently served by the floating `coreeq` release before anything is uploaded. The first install over an older runner-debug-signed test build needs that test package uninstalled once; the production package `tv.corebuilds.eq` is separate and is not touched.

## [1.0.0] — 2026-09-29

### Fixed

- **Measurements are real.** `CaptureEngine` opened the remote mic but only used it for a level meter: the spectrum every profile saved was `SyntheticRoom` data plus random jitter, RT60 was the constant `0.50`, and without mic permission it quietly ran a simulation. It now records the sweep through `VOICE_RECOGNITION` at 16 kHz (48 kHz decimated when that is all the TV offers) and `SweepAnalysis` works out the room from the capture itself. It deconvolves with the Farina inverse, takes RT60 from a noise-compensated T20, reads 1/3-octave magnitudes from the direct sound on, and lines the level up with the target over 300 Hz–3 kHz. A sweep the room drowns out (under 20 dB SNR), or one that clips the mic, is refused with the reason, and nothing is saved. Microphone permission is requested at Start.
- **Correction reaches players.** `SessionReceiver` called `startService` from a manifest receiver, which Android 8+ blocks, and implicit session broadcasts no longer reach manifest receivers at all. `EqService` is now started from Core EQ's own screen by a Correction On/Off switch, registers for the session broadcasts itself, and uses either the whole-TV output mix or per-player sessions, never both (that would correct twice). Band gains come from the profile's curve at the TV's own band centres, lowered by the largest boost so the platform `Equalizer`, which has no preamp, cannot clip. Correction resumes after a reboot, or on the next launch where Android 15 forbids that.
- **Profiles carry what was measured.** Room volume (chosen on the Measure screen), RT60, transition, roll-off floor, SNR, mic and device come from the measurement, and the graph caption uses the computed transition. Before, every profile said 54 m³, 40 Hz, "dialogue", "Living room (Calibrated)" and "384 Hz". `ProfileStore` also never read `volume_m3` and `rt60_s` back, so saved profiles reloaded with the defaults.
- **No demo data.** The three seeded profiles (Living room, Bedroom TV, Kitchen), built from a synthetic room, are gone, and existing installs purge them. A fresh install shows "No measurement yet".
- **Failures are named.** Every apply outcome, including another equaliser app holding priority, a refused whole-TV mix and an Android refusal to start, goes to the notification and the Home screen status line. Exports save to Downloads/CoreEQ through MediaStore and say where they went or why they could not. Before, the file write failed on Android 10+ and the app said "Copied to clipboard".
- **`ROLLOFF_DROP_DB` matched to the reference (6 dB, was 10).** `test_core_eq_parity.py` now also holds `ROLLOFF_DROP_DB`, `NULL_DEPTH_DB`, `NFFT` and `ESS_SECONDS`.
- **Reference: `ir_magnitude_db` no longer windows out the direct sound.** `analyze_sweep_recording` cropped the IR at its peak and applied a Hann window, whose value there is 0, so the analysed response was the reflections alone. Both chains now use a window that rises over 2 ms before the direct arrival, stays flat, and fades over its second half; a Python loopback measures flat to within 0.03 dB from 63 Hz to 6.3 kHz.

- **The minimum-phase gate runs on device.**
  - **What it does.** `SweepAnalysis` computes excess group delay and closes the correction on any band below the transition whose median exceeds 5 ms. Those are regions an amplitude correction would distort without fixing. The Measure status line says how many bands it left alone.
  - **Reference fixes found while porting (`tools/core_eq_dsp.py`):**
    - the measured phase was windowed by a Hann that was 0 at the direct sound;
    - the minimum-phase reference was a Hilbert transform run along the half spectrum, which is not exact, and is now the folded real cepstrum of the full spectrum;
    - latency counted as excess, so any real capture (always more than 5 ms of sound travel) would have gated every band. The analysis is now aligned to the direct arrival, as REW does;
    - each band was judged at one frequency, where the group delay spikes at nulls, and is now judged by its median.
  - **Why only below the transition.** That is where the corrector inverts. Above it the field is diffuse, excess group delay is always large, and gating would switch off the one-octave shaping (the dialogue presence lift) in every real room.
  - **Tests.** Both chains pin the same physics: the identity and pure latency pass, a room mode (a resonance) passes, and an allpass fails at its centre but passes at 1 kHz. The Kotlin simulated room was rebuilt with low-frequency modes and a diffuse tail only above 300 Hz, because a diffuse field below the Schroeder frequency is not physical.
  - **Parity.** `MIN_PHASE_TOLERANCE_MS` joins the parity test.

### Added

- **`SweepAnalysisTest`: the measurement chain against physical ground truth.** Rooms are simulated at 48 kHz and captured at 16 kHz, and the analysis must:
  - measure a loopback flat to ±1.5 dB (63 Hz–6.3 kHz);
  - recover 350 ms latency to within 3 ms, and a 0.5 s RT60 to within 0.1 s;
  - show a +9 dB 80 Hz mode as a rise of more than 5 dB, and deepen the cut there;
  - refuse a buried sweep with a reason;
  - keep every gain inside the trusted band and limits.

  `ApplyPathTest` covers band interpolation and decimation. 22 Kotlin tests in all.

- **Core EQ has a launcher icon and a real TV banner.** The manifest named no `android:icon`, and `tv_banner` was an outlined rectangle. Both now draw the Icon Pack's `coreeq` mark (three equaliser faders in the brand hex, `#00D4FF` on `cb_night`) from the pack's own 512-grid path data: an adaptive `ic_launcher`/`ic_launcher_round` sized inside the 66dp safe zone, and the mark centred on the 320×180 banner. All vector, no rasters.
- **Core EQ — a room equaliser built from pink noise and the TV remote mic.** `docs/research/core-eq-measurement-and-capability-2026-09.md` settles the two questions the idea lives or dies on: the controller microphone *is* supported on Android TV, but an uncalibrated remote capsule cannot be trusted below 40 Hz or above 8 kHz; and Android has no global equaliser API, so applying the correction is a capability to probe and state, not a promise to make.
- **Core EQ plan and design frames.** `docs/CORE_EQ_PLAN.md` lays out the four screens, the capability ladder and the milestones, and `docs/core-eq-*.png` show them in the suite's own night chrome — Outfit and DejaVu Sans Mono, the `cb_*` tokens, the 3dp ring focus treatment, all read live from `app/src/main/res/values` rather than hand-drawn.
- **`tools/core_eq_dsp.py` is the measurement chain, written before the Android.** Pink-noise and exponential-sine-sweep synthesis, Welch PSD to 1/3-octave bands anchored at 1 kHz, Flat / B&K / Harman / House targets, and a correction capped at +6/−12 dB with a 6 dB/octave slope limit across 40 Hz–8 kHz — plus the peaking-filter fit, the millibel collapse onto the device's own bands, and the three export formats with a preamp that rounds toward more negative.
- **`tests/test_core_eq_dsp.py` pins that chain where it cannot drift.** Pink noise is proved to be −3 dB/octave and deterministic, `cut_only` is proved never to boost, the slope limiter is proved to cap a 15 dB null rather than echo it, fitting is proved deterministic, and the exported preamp is proved unable to under-compensate the largest boost. 41 tests.
- **`tools/build_core_eq_mockups.py` renders the frames from their sources.** It reuses the icon pack's token loaders and font pins, feeds the graphs real DSP output rather than a hand-shaped curve, and refuses to draw any screen whose content passes the 540dp panel — the frame-level twin of `tests/test_tv_layout_fit.py`.
- **`.github/workflows/suite-ci.yml` runs the new gates.** The DSP self-test, the pytest chain and the frame check are wired into the no-path-filter job, so `tests/test_ci_coverage.py` sees them on every push.
- **Second research pass: precedents, the apply path, and the audio path.** `docs/research/core-eq-precedents-poweramp-and-audio-path-2026-09-27.md` finds a shipping mainstream precedent in Sonos Trueplay — and the reason it refuses Android microphones, which is the objection Core EQ has to answer — then settles three things the first pass missed: Poweramp Equalizer is the apply target rather than a rival and already imports the exact AutoEQ `.txt` Core EQ exports; the audio output path decides whether an app-level equaliser can reach the sound at all, so it must be probed before anyone measures a room; and every target curve so far has been a music target, when the dominant TV problem is dialogue intelligibility.
- **Third research pass: the correction science.** `docs/research/core-eq-correction-science-2026-09-27.md` settles what a measured response is *allowed* to be corrected toward. Correction has a computable ceiling — the room stops being a set of discrete resonances above its Schroeder frequency, `2000·sqrt(RT60/V)`, and the industry inverts only below about 300–500 Hz. A null is destructive interference and cannot be filled at any price, so the corrector must cut peaks and leave cancellations alone, while cutting a modal peak genuinely does kill its ringing because low-frequency room modes are minimum phase. It also audits the reference DSP: its `harman` target is speaker-family rather than headphone-family, which is lucky, because the headphone curve's 3 kHz ear-gain peak would double-count pinna gain if baked into a speaker correction — but the name overclaims and the bass sits at half the published Olive 2013 preference. And the swept-sine stimulus the tool already generates should be what it measures with: it buys 15–20 dB of dynamic range over noise, and it is the only path to the RT60 and group-delay numbers the ceiling and the minimum-phase gate need.
- **Built what the third research pass found.** The DSP reference is now a two-regime corrector: full inversion below the room's transition frequency (`min(2·f_s, 400 Hz)` from `2000·√(RT60/V)`, 300 Hz when the room is unknown), and shaping-only within ±3 dB above it — because above that line a microphone at one point is measuring reflections as much as the speaker. Dips deeper than 6 dB below the trend are detected as cancellations and never boosted into, and a minimum-phase gate built on the sweep's excess group delay zeroes any region inverting would only distort. The measurement stimulus moved to the exponential sine sweep the tool already generated and did not use: it buys 15–20 dB of dynamic range and is the only path to the RT60 and group-delay numbers the ceiling and the gate need. Targets are named for what they are — `room` replaces `harman`, `olive` ships the published Olive 2013 shelves as a preset, `dialogue` adds SII-weighted presence that stops before sibilance — and the correction floor now follows the loudspeaker's measured roll-off instead of a fixed 40 Hz. Exports carry their own band limit, the transition, the untouched nulls and the Poweramp "Bands Overlap = Cascade" instruction. 63 tests pin it.
- **Test pipeline and Core EQ Android TV app scaffolding (M1).** `tools/core_eq_dsp.py` now includes `read_wav` (16/24/32-bit), automatic resampling, and `--process` to deconvolve recorded sine sweeps directly into AutoEQ/Poweramp Equalizer presets — giving an immediate acoustic test loop before on-device audio wiring. The Farina inverse sweep filter was also corrected: the +3 dB/octave amplitude envelope is now applied before time-reversal, eliminating an inverted-slope deconvolution error and yielding flat-to-0.2 dB loopback deconvolution. Milestone M1 scaffolds the standalone `coreeq/` Android Gradle root (`tv.corebuilds.eq`), implementing `TvActivity` 1080p density normalization, Leanback launcher, Home/Measure/Profiles/Capability activities, UI tokens matching the icon pack palette, and `.github/workflows/core-eq-apk.yml` to assemble debug APK artifacts on PRs. 66 DSP tests; 472 suite tests passing.
- **Fourth research pass: remote hardware reality, equal loudness, and spatial averaging.** `docs/research/core-eq-remote-hardware-loudness-and-spatial-averaging-2026-09-28.md` resolves the open hardware and acoustic questions from pass three: TV remotes stream 16 kHz ADPCM over Bluetooth LE (ATVV / Voice over HOGP), proving the 8 kHz correction limit is a physical Nyquist constraint; Android CDD §5.4.2 explicitly mandates that `AudioSource.VOICE_RECOGNITION` disables both AGC and noise reduction while enforcing flat response and 30 dB linearity; spatial seat averaging must use power (energy) averaging preceded by 300–3000 Hz broadband alignment, because complex vector averaging creates artificial comb nulls; and ISO 226:2003 equal-loudness contours explain the evening TV volume trap — taming room modes relieves upward masking on consonants so viewers hear dialogue without cranking the master volume.
- **Core EQ joins the suite registry.** `suite.json` declares it (`tv.corebuilds.eq`, tag prefix `coreeq-v`, floating tag `coreeq`, Downloader `[USER TO SUPPLY]`), so `check_suite_truth.py` accepts its release trigger, and the README, `AGENTS.md`, `PUBLISHING.md`, the PR template and the icon pack's suite hub list it. Its notes moved here from the icon pack's changelog, whose `[Unreleased]` becomes that app's in-app What's New.
- **Release and test lanes.** `coreeq-v*` tags on main now build a signed, verified `coreeq-release.apk` and publish the versioned and floating `coreeq` releases; the `coreeq-test` prerelease updates only from pushes to `main`, so unreviewed PR and agent-branch code is never published. PRs build a debug artifact only.
- **First unit test.** `CorrectionLimits` holds the 40 Hz–8 kHz band and the +6/−12 dB caps; `CorrectionLimitsTest` pins them, and `tests/test_core_eq_parity.py` holds them equal to `tools/core_eq_dsp.py`.
- **Fifth research pass: Android TV audio app market and competitive analysis.** `docs/research/core-eq-android-tv-market-and-competitors-2026-09-28.md` surveys the current Android TV audio market across TV-native Play Store apps (SoundWave TV, TV DSP Center), sideloaded mobile engines (Poweramp Equalizer, Wavelet, Volume Booster Goodev), and OEM auto-calibration systems (Sony Bravia Acoustic Auto Calibration, LG AI Acoustic Tuning, Samsung SpaceFit). It identifies the unoccupied market quadrant: existing TV apps are manual graphic sliders with zero measurement, phone tools are unusable without a mouse, and OEM auto-calibration shuts down the moment an external soundbar or HDMI ARC device is connected. Core EQ is the only solution combining remote-mic room measurement, modal physics, speech intelligibility targets, and companion export to Poweramp Equalizer.
- **Full production application build: DSP core, measurement engine, effect ladder, profiles, and TV UI.** Implemented the complete Core EQ application inside `coreeq/` (`tv.corebuilds.eq`): (1) Kotlin DSP core in `tv.corebuilds.eq.dsp` (`DspConstants`, `Targets`, `Correction`, `Peaking`, `SyntheticRoom`) translating the reference mathematical chain into pure Kotlin with exact parity; (2) measurement engine in `tv.corebuilds.eq.measure` (`StimulusPlayer` for 10 s Farina swept-sine and pink noise playback; `CaptureEngine` with `VOICE_RECOGNITION` audio source, 1/3-octave real-time RTA spectrum tracking, noise floor metering, and energy-domain spatial power averaging); (3) effect ladder and lifecycle in `tv.corebuilds.eq.apply` (`EffectLadder` hardware capability probe, `SessionReceiver` for `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`, and `EqService` foreground service preventing sleep/wake audio dropouts); (4) profile persistence and export in `tv.corebuilds.eq.export` (`ProfileStore` with SharedPreferences JSON storage pre-seeded with calibrated defaults, and `Formats` exporting Poweramp Equalizer `.txt` with `"Bands Overlap = Cascade"`, EqualizerAPO `GraphicEQ`, and `corebuilds.core-eq/1` JSON); (5) 10-foot Leanback TV UI with custom hardware-accelerated `CurveGraphView` and interactive D-pad `BandSlidersView` across Home, Measure, Profiles, and Capability screens; (6) `DspParityTest.kt` and Python parity suites pinning acoustic constants and formulas.
- **Sixth research pass: MEMS capsule acoustics, body shadowing, dialogue SII, and crossover dynamics.** `docs/research/core-eq-capsule-acoustics-body-shadow-and-dialogue-sii-2026-09-29.md` models the physical interaction between the remote microphone capsule, the human body, and home theater acoustic configurations. It reveals: (1) remote MEMS packages exhibit a Helmholtz front-chamber resonance at 15–21 kHz which is physically eliminated by the 16 kHz BLE ATVV decimation low-pass filter, making 40 Hz–8 kHz response exceptionally linear ($\pm 1\text{ dB}$); (2) human torso reflections within 15 cm of the chest induce comb-filtering notches at 800–2600 Hz, requiring an extended-arm protocol ($\ge 35\text{ cm}$) and a 35 Hz high-pass filter for hand-tremor rejection; (3) ANSI S3.5-1997 Speech Intelligibility Index (SII) proves that 71.6% of speech intelligibility is concentrated in the 1–4 kHz bands, whereas low-frequency room modes ($<300\text{ Hz}$) cause severe upward auditory masking over consonants; (4) subwoofer latency differentials (10–25 ms in wireless subs) create severe destructive phase interference at the 80–100 Hz crossover that must not be equalized with positive boost filters; (5) multi-seat spatial variance identifies global axial modes (safe to cut up to $-12\text{ dB}$) versus localized position-dependent interference nulls.

