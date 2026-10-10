# Core EQ gap and improvement review (2026-10-10)

Scope: Core EQ 1.3.2 plus the uncommitted Bar 2.0, setup, brand-detection and
passthrough work. Sources: the code under `coreeq/app/src`, the plans and
research under `docs/`, the CI workflow, and two web searches. Nothing here
was run on a TV, and the Kotlin was not compiled in this environment. Treat
each item as a finding to check, not a verified result.

## 1. What was checked

| Area | Evidence | Result |
| --- | --- | --- |
| Open items in the plan and features docs | `docs/CORE_EQ_PLAN.md` §9, `docs/CORE_EQ_V1.2.0_FEATURES.md` (Known Limitations, Roadmap) | Several "Future" items are still open (below). |
| TODO / FIXME in code | grep of `coreeq/app/src/main` | None. |
| CI gate | `.github/workflows/core-eq-apk.yml` | PRs run `:app:testDebugUnitTest` and `:app:lintDebug`, then build. This is the only compile and test gate for the new Kotlin. |
| Unit tests | 27 test files, about 230 `@Test` functions | Strong on pure logic. No tests touch the audio path on a device. |
| Import formats | grep for `GraphicEQ`, `Preamp`, `ParametricEQ`, `ACTION_OPEN_DOCUMENT` | Export only for correction files; the only import is REW magnitude data. |
| Spatializer / Virtualizer | grep | Zero uses. |
| Localisation | `res/` | `values/` only (English). |
| Accessibility labels | `res/layout` | One `contentDescription` across 12 layouts. |
| Update safety | `update/UpdateInstaller.kt` | HTTPS only, allow-listed hosts, SHA-256 check. Looks sound. |
| Doc cross-references | `docs/eq-*.md`, `docs/BRAND-GUIDE.md` | All referenced files exist. |

## 2. Missing functions, ranked

Ranked by how much they block the user's main job (correct the sound on the
TV they own) and how well-supported the need is. Each is a candidate. None is
approved.

1. **Import of correction files (AutoEq / GraphicEQ / Parametric text).**
   Core EQ exports these formats but cannot import them. A user with an
   AutoEq or Equalizer APO file has to retype the bands. Wavelet, a popular
   Android equaliser, ships AutoEq and imports custom files (see
   [1](https://play.google.com/store/apps/details?id=com.pittvandewitt.wavelet),
   [2](https://pittvandewitt.github.io/Wavelet/Features/)). This is a parser
   plus a mapping to the 10-band manual EQ. Needs a decision on which formats.

2. **Real passthrough detection.** Core EQ only knows that HDMI *may* bypass
   the EQ, from Android's encoded-surround setting. `V1.2.0_FEATURES.md` lists
   automatic detection as "Future". The routing note says a public API cannot
   show the current stream format, so full detection is probably impossible on
   stock builds. The check I added is the honest ceiling; do not claim more.

3. **Virtual surround (Spatializer).** The features doc lists this as future.
   The platform API exists from Android 12L (API 32) and reports whether
   spatialization is available and enabled
   ([3](https://developer.android.com/reference/android/media/Spatializer),
   [4](https://android-developers.googleblog.com/2023/04/delivering-immersive-sound-experience-with-spatial-audio.html)).
   Core EQ's minimum is API 30, so it needs an SDK gate. Caution: the routing
   note says a Spatializer or effect on one track is not proof that it reaches
   other apps. Scope it to Core EQ's own output before promising anything.

4. **Model-level presets for brands other than JBL Bar 800 / Bar 2.0.** Deferred
   by decision. Needs your device list (brand and model per TV, soundbar and
   speaker), plus a sourced preset basis.

5. **Build.MODEL-based model matching in DeviceIdentity.** Deferred. The
   manufacturer string is in place; the model string is not used yet.

6. **Auto-apply once per device, then suggest-only.** Deferred by decision. Not
   built, and needs a per-device flag store.

7. **On-TV ADB execution.** Deferred. Needs an ADB protocol library and TV
   pairing. Setup shows copy-to-computer commands instead.

8. **Bluetooth latency and USB DAC handling.** Listed as "Future" in
   `V1.2.0_FEATURES.md`. Not in code. Latency needs a loopback measurement the
   app does not have. Low priority without a user report.

9. **Localisation.** No translations exist. Decide whether the audience needs
   them before investing.

## 3. Improvements needed

| # | Finding | Why it matters | Suggested fix |
| --- | --- | --- | --- |
| I1 | Accessibility: one `contentDescription` across 12 layouts. | TalkBack and focus users get unlabelled controls. | Audit each layout; label icon-only buttons. |
| I2 | Setup check counts only NEEDED rows. | Fixed this round: the passthrough row is now INFO, so it no longer blocks "Everything is in place". | Done in this round. |
| I3 | Stale statements in the features doc. `V1.2.0_FEATURES.md` roadmap still shows v1.3.0 as Q1 2027, while the app is 1.3.2. | Readers will trust dates that no longer hold. | Mark the roadmap as historical, or rewrite it from the current CHANGELOG. |
| I4 | `IMPLEMENTATION_SUMMARY.md` mixes historical and current claims (it has version notes at the top). | Easy to mis-read. | Keep the header note; move the body under a "historical" label. |
| I5 | The new Kotlin (DeviceIdentity, SetupGuide, SetupActivity, tests) has not been compiled. | Compile or lint failures are possible. | The CI gate will catch them on the next PR. Run it before any merge. |
| I6 | Brand-detection regexes are unverified against real manufacturer strings (for example LG reports `LGE`). | A wrong string means a missed brand, not a crash. | Fill in the device test record on each TV. |
| I7 | Preset labels: "reviewer-informed, not measured" is kept for every hardware preset. | This follows the earlier honesty rule. | Keep it. Only a device record can change the label. |
| I8 | Downstream-calibration warning already exists in Measure (`measure_step1_sub`). | My earlier reply said this was missing; it was not. | Correction noted. |

## 4. Verification status

- Ran this round: the UI resource checker (`tools/check_ui_resources.py`), the
  update-contract test (`tests/test_core_eq_update_contract.py`), and the suite
  truth check (`tools/check_suite_truth.py`). Results are recorded in the
  final report.
- Not run: Gradle unit tests, lint, the Python DSP selftest (needs numpy), and
  any device test. CI is the first real gate for the new Kotlin.

## 5. Decisions needed from you

1. Do you want correction-file import (item 2.1), and in which formats?
2. Do you want a Spatializer-based virtual surround experiment (2.3), scoped to
   Core EQ's own output?
3. Your device list, to unblock model-level presets (2.4).
4. Is accessibility (I1) in scope for the next round?
