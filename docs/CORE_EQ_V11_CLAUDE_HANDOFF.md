# Core EQ v1.1 — handoff for Claude

**Updated:** 2026-09-30
**Purpose:** Continue release preparation and help get a device-test build ready. The user has offered to test extensively before release.

## Non-negotiable scope and release gate

- This is the user's Core EQ app: keep changes in `coreeq/` unless an existing suite-level gate or its docs must change.
- Keep `DynamicsProcessing` in v1.1. **Do not release until real Android TV hardware acceptance (M6a) passes.** Do not tag, bump release metadata, or describe hardware/CI checks as passed prematurely.
- M6a must show: configured effect engages on the tested output route; 25 PreEQ bands/cutoffs read back as expected; a known over-threshold signal is measurably clamped by the limiter; and a refused DP path falls back to Equalizer without double correction.
- M8 wireless-pairing helper is spike-gated. PC ADB DUMP grant is an acceptable v1.1 path; current app copy says on-device pairing is not included in this build. No Shizuku.
- M10 stays magnitude-only: ignore optional phase; report RT60/Schroeder/SNR and minimum-phase verification honestly; save REW imports for export without auto-applying; selecting one in Profiles is the explicit apply action; retain the downstream-calibration warning.

## Checkout and current state

- Work only on `arena/01a0ed18-corebuildsapps`; current `HEAD` is `34d0040`, with v1.1 changes uncommitted/untracked. Do not switch branches. Do not assume PR #212 or commit `369106b` is present locally.
- GitHub authentication previously failed. Do not retry GitHub operations until the user reconnects GitHub in Arena.
- Android changes have not been compiled in this sandbox: JDK/Gradle executable/Android SDK are unavailable here. The user may be able to build locally; use `coreeq/gradlew` with JDK 17 and Android SDK platform 35.
- No tag/release bump has been made. `coreeq/app/build.gradle.kts` remains `versionCode = 2`, `versionName = "1.0.0"`; changelog is still `[Unreleased]`.
- `docs/CORE_EQ_V11_PLAN.md` is the implementation plan/status. `docs/CORE_EQ_M6A_TEST_RUNBOOK.md` is the user-facing device acceptance checklist; use it to collect model/API/build, route, APK hash, logs, and output-capture evidence.

## Implementation already in the worktree

- DP-first service with Equalizer fallback, runtime band read-back, session discovery, playback/status UI, and startup/teardown handling. **Still needs Android compilation and device testing.**
- 25-band DP layout: 24 correction bands through 8 kHz plus a neutral high-frequency guard. The active Home band row uses engine read-back; saved profiles retain the correction curve.
- M10 REW text/FRD/CSV import and measurement-limit/downstream-calibration honesty work are present but not committed or CI-verified.

## Important DP range caveat

The default AOSP AIDL `DynamicsProcessingSw.cpp` descriptor advertises PreEQ cutoff values of 220–20,000 Hz and `gainDb` bounds using `std::numeric_limits<float>::min()`/`max()`; `min()` is the smallest positive float. Its validator checks channel/band indices but not those numeric fields. This is not a Java API guarantee or OEM-HAL contract. Core EQ deliberately keeps its low-frequency layout unclamped; `DynamicsProcessingEngine` checks read-back and should fail over when a device changes/refuses the requested values. M6a must record actual hardware behavior.

## Last recorded local validation

- DSP self-test passed; selected Python pytest set: **124 passed, 18 subtests passed**.
- Mockup check passed with the pinned `Pillow==10.4.0`; `docs/core-eq-profiles.png` is not a retained change.
- Suite-truth, CI-coverage, changelog-contract, Gradle-envelope, UI-resource, XML well-formedness, and `git diff --check` passed. These are local checks only; **no Android Gradle test/APK build, PR CI, or physical acceptance is confirmed**.

## Suggested next steps

1. Inspect the Kotlin changes and run `cd coreeq && ./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon` where JDK 17 + Android SDK 35 are available. Fix build/test failures before asking the user to test; do not label earlier static checks as an Android build.
2. Provide a debug APK and have the user follow `docs/CORE_EQ_M6A_TEST_RUNBOOK.md`. Review the returned logs and output measurements; resolve issues and repeat until the M6a criteria pass.
3. Once the user reconnects GitHub, reconcile the branch/PR, run full CI, and verify the exact tested commit/build before proceeding to M9 version/changelog/tag work.
4. Keep release held until both CI and M6a evidence pass. Preserve the fixed Arena branch; no alternate branch or tag before approval.

## Update from Claude (2026-09-30)

The worktree changes are now committed on `arena/01a0ed18-corebuildsapps`, on top of `7e94376` (the #212 review fixes). Pull before editing: `git pull --rebase origin arena/01a0ed18-corebuildsapps`. The "do not assume #212 or `369106b` is present" note above is out of date.

- **First Android build:** `:app:testDebugUnitTest` 56/56, `:app:lintDebug` 0 errors, `:app:assembleDebug` builds. JDK 17 and Android SDK 35 were used. The APK has still not been run on a real device.
- **Fixed on the way:**
  - `DpMappingTest` was missing its `DpBandLayout` import.
  - `highFrequencyGuardBandOnlyGetsTheSharedHeadroomCut` expected exactly -3 dB, but no DP band centre lands on the 4 kHz peak. The shared cut is the largest boost the centres actually sample, so the test now asserts that.
  - `EqService.configure` tested `is DynamicsProcessing` before `is Equalizer`. On API 26–27 that class does not exist, so the test itself would throw `NoClassDefFoundError` on every re-apply (lint `NewApi`). Equalizer is now checked first, and DP sits behind the API-28 check in a `@RequiresApi(P)` helper.
- **Merged with the #212 fixes:**
  - Discovery keeps attaching after one refusal, and reports it once.
  - `LIVE` lights only on the whole-TV path.
  - Stopped sessions are skipped.
  - The copy names the YouTube gap. The Capability screen keeps both the PC-ADB note and the "some players" wording.
- **M6a:** record the engine the status line names (`DynamicsProcessing` or `Equalizer`) on each output. If the TV clamps cutoffs below 220 Hz (the AOSP software descriptor above), the read-back check refuses DP and every session falls back to Equalizer. That is correct behaviour, but it means DP never engaged, and the test has to catch it.
