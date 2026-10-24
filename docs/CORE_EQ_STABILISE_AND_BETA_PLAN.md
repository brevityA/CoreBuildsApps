# Core EQ: stabilise, beta release, then roadmap

Status: plan approved in outline by the user on 2026-10-19. Scope is Core EQ
(`tv.corebuilds.eq`) only. Other apps in the repo are out of scope. `coreeq-test`
stays untouched.

## Decisions taken

- **Goal:** all three stages, in order. Stabilise, then beta, then roadmap. Each
  stage ends before the next starts.
- **Approval:** stop and report before each stage, and before any tag or release.
- **Testing:** emulator and CI only. No phone or TV hardware is available. Every
  hardware-dependent claim stays unverified in the release notes.
- **Roadmap items in scope:** item 2, the narrow-only correction ceiling (approved,
  and it changes the safety limits in Kotlin and the Python reference together).
  Auto-apply once per output (already built, see the risks below). Items 1 and 3 are
  already on the branch.
- **Out of scope:** the USB measurement mic (roadmap item 4), until a hardware choice
  is made. Phase or time correction, which Android's effect APIs cannot support.
- **Version:** open. Decided when the beta is ready. Current release is 1.5.2 (code 13).
- **Tags:** no existing tag is moved, and no check is relaxed to get a release through.

## Open assumptions

These are not decided. Each one is checked at the stage gate, not assumed.

1. **Public copy.** Whether the store copy claims "the only remote-mic room measurement
   app" or "best room correction". The research supports only the first claim.
2. **TV auto-open pairing switch.** Approved for the plan, but its exact behaviour is not
   yet defined. It is defined and confirmed at the start of Stage 1.
3. **Device identity for auto-apply.** The policy keys on the output kind, so two HDMI
   TVs share one entry. The beta either accepts this and says so in the notes, or the
   key is refined first.

## Stage 1: stabilise the branch

Exit: CI green on the branch tip, review findings closed or recorded, auto-apply and
the pairing path reviewed.

1. Find the cause of the `Install on Android API 30` failure on `2bc7305`. Rerun the job
   once before changing code. If it fails again, read the emulator log and fix the cause.
2. Close the review findings in the list below, most critical first.
3. Define the TV auto-open pairing switch (assumption 2) and implement it, or record why
   it is dropped.
4. Add tests for each fixed defect. Kotlin tests run in CI. Python parity tests run
   locally.
5. Confirm that the branch matches the remote before each push, so no commit is lost.

## Stage 2: beta release

Exit: a beta tag on the released branch, with notes that state every limit.

1. Resolve assumptions 1 and 3 and record the answers in the changelog.
2. Choose the version (open) and update the version and changelog together.
3. Confirm the release contract tests pass. Do not edit the contract to pass them.
4. Write release notes that state: no hardware qualification has been done; auto-apply
   changes audio on the first clean measurement per output; the imported target is
   limited to +6 / −12 dB; and the phone pairing is untested on devices.
5. Tag, publish, and confirm the release artifacts. Stop for approval before the tag.

## Stage 3: roadmap item 2, narrow-only correction ceiling

Exit: a user can lower the correction's upper limit. The control can never raise it above 8 kHz.

1. Change the Kotlin limits and the Python reference (`tools/core_eq_dsp.py`) together.
2. Update `tests/test_core_eq_parity.py` and add a Kotlin test for the narrow-only rule.
3. Check that the limits match in both languages, and that no path can exceed 8 kHz.
4. Stop for approval before merging or releasing this stage.

## Risks, ranked from most to least

Each risk has a mitigation in the plan. Ranking is by product impact multiplied by how
unlikely our current checks are to catch it.

1. **Auto-apply changes audio with no hardware check.** A bad first correction plays
   through the user's TV. *Mitigation:* GREEN gate, once per output, a clear status
   message, switch back in Profiles, and a beta note that hardware is unverified.
2. **Narrow ceiling touches the safety contract.** A mismatch between Kotlin and Python,
   or a path above 8 kHz, would ship an unsafe limit. *Mitigation:* change both sides in
   one stage, parity test, a narrow-only test, and a stage gate before merge.
3. **Pairing and discovery are untested on real networks.** mDNS, QR scanning, and
   reconnect may fail on real routers. A failed pairing is visible; a wrong pairing is not.
   *Mitigation:* the code requires the one-time code on every pairing; the release notes
   say the flow is untested on devices; and the TV can revoke the paired phone.
4. **Play services dependency for QR scanning** (`play-services-code-scanner`). It is a
   new dependency, and it is only exercised on a device with Play services.
   *Mitigation:* the scan button reports unavailability; typed codes still work.
5. **Imported target parser misreads some files.** A decimal-comma file with spaces was
   silently read as the wrong frequency. *Mitigation:* fixed in Stage 1 with a regression
   test. The parser rejects any number it cannot read, so a half-read curve never applies.
6. **Process-wide mutable target state.** The imported curve is held in one object shared
   by threads. An import during an analysis could change the curve mid-analysis.
   *Mitigation:* the holder is volatile, and imports only happen on the Measure screen.
   Stage 1 records this as a known limit, or moves the curve into the analysis input.
7. **Stale local state and lost commits.** The local branch has been reset to `main`
   several times between turns, which risks lost work or a rejected push.
   *Mitigation:* fetch and compare with the remote before every push, and keep a
   backup patch of any uncommitted work.
8. **Release contract and version drift.** Changing the version without the changelog,
   or moving a tag, breaks the release process. *Mitigation:* contract tests in CI, no
   moved tags, and a stage gate before the tag.
9. **CI flake on the emulator.** The API 30 install job has failed once on a commit
   that did not change install code. *Mitigation:* one rerun before any code change,
   then an emulator log review.
10. **Profile display for an imported target.** Profiles now store their own curve, so
    older profiles are correct. The risk is a profile exported without its curve.
    *Mitigation:* the export keeps `target_points`, and the import path checks them.

## Phase 3 review findings (most critical first)

Found in the review of the current branch. Each one is fixed or recorded in Stage 1.

1. **Critical (accuracy): decimal-comma columns read as the wrong point.** A line like
   `20,5 -3,2` split on commas into four numbers and was read as 20 Hz at 5 dB, with no
   error. *Fixed:* the parser reads a decimal comma when a line has exactly two
   space-separated fields and the first holds a comma. A regression test covers it.
2. **High (release risk): one CI job failing on the newest commit.** `Install on Android
   API 30` on `2bc7305` is unexplained, because the job logs are not available to read.
   *Open:* Stage 1, step 1.
3. **High (process): the local branch was reset between turns, twice.** Uncommitted work
   could have been lost, and a push could be rejected. *Mitigated:* backup patches kept in
   `/tmp`, and fetch-and-compare before each push. *Recurs if not checked.*
4. **Medium (design): auto-apply keys on the output kind.** Two devices sharing a kind
   share one entry. *Open:* assumption 3.
5. **Medium (robustness): auto-apply and the profile save have no error handling.** A
   failure in `saveProfile` would surface as a crash on the Measure screen. The Save path
   has the same exposure, so this matches existing behaviour. *Open:* Stage 1 should wrap
   the auto-apply save and report a failure in the status line.
6. **Low (concurrency): the imported curve is process-wide.** See risk 6 above.
7. **Low (correctness, checked and correct):** the post-correction check's alignment, skip
   mask, and minimum-band rule are consistent across `compare` and `residual`. The EQ
   service resumes before auto-apply reapplies, because both messages are processed in
   order. Profiles store their own imported curve, so the auto-apply path keeps it.
