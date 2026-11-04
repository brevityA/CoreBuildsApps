# Core EQ — closing the loop with the check sweep

Date: 2026-10-28. Follows `core-eq-correction-science-2026-09-27.md` (what may be
corrected, and how hard) and `core-eq-remote-hardware-loudness-and-spatial-averaging-2026-09-28.md`
(why one uncalibrated capture is weak evidence). Ships as Core EQ 1.8.0.

## The question

Core EQ already had the makings of a feedback loop and never closed it. **Check
correction** plays the sweep again with the correction applied and reports three
numbers — the room alone, the residual the correction *should* leave, and what
the check *heard* — then stops. When those two disagree, the app says so and
does nothing about it.

The disagreement is the measurement. If the room came out 2 dB louder than
predicted at 90 Hz, the correction under-cut there. If it came out 1 dB quieter
at 1.2 kHz, it under-lifted. That is a per-band error term, available for the
cost of one more sweep the user was already able to take.

## What the loop does

For each band `k` both sweeps can speak for:

```
predicted[k] = measured[k] + correction[k] - target[k]     (what should be heard)
heard[k]     = checkMeasured[k] - target[k]                (aligned to the target)
want[k]      = predicted[k] - heard[k]                     (+ = needs more gain)
```

and the correction becomes `correction[k] + want[k]`, subject to the rules
below. The app side is `CorrectionRefinement.kt`; the reference is
`refine_with_check` in `tools/core_eq_dsp.py`.

## The seven rules, and why each exists

1. **Bands both sweeps can speak for only.** Nulls, minimum-phase-gated bands,
   outside the floor/ceiling and bands under the noise are excluded by the same
   mask the on-screen figures use (`PostCorrectionCheck.sharedSkip`); a band the
   check did not hear over its own noise is excluded too. A correction is judged
   on the bands it was allowed to correct.
2. **Shape, not level.** A check is taken at another moment and possibly another
   volume, and its alignment to the target over 300 Hz–3 kHz leaves a constant
   behind that could equally be the correction being a fraction loud or the
   alignment being a fraction off. The confidence-weighted mean of `want` is
   removed, so a level difference moves nothing. This is load-bearing: the
   Kotlin harness has a test where a band the correction may not touch is 8 dB
   off in the check, which corrupts the alignment for every trusted band. With
   the mean removal the refinement is a no-op; without it every trusted band
   moves 0.7 dB.
3. **Graded trust, again.** The step is scaled by the weaker of the two sweeps'
   per-band confidence (`Correction.bandConfidence`, 1.7.1). A band at 15 dB over
   the noise asks for half of what a clean band gets.
4. **Never a new direction.** A band the first measurement cut may relax to 0 dB
   but never become a boost; a band it boosted may relax to 0 dB but never become
   a cut; a band left at 0 dB stays there. One more capture does not overturn the
   first one's *sign*, only its size. This also makes the refinement leave
   cut-only mode alone by construction.
5. **A lift needs a deficit the check heard itself**, at least 0.5 dB deep, and
   must not sit on a cancellation the check found. Without this, a check taken a
   touch quiet asks for gain everywhere, including into dips nothing should fill
   — the same rule as the first measurement, applied to the second.
6. **The correction's own budget still applies**, through
   `Correction.budgetEnvelope` — the function the curve itself is clamped by, so
   a refined curve cannot ask for more bass below the transition (2 dB from one
   seat, 3 dB from two or more) or more shaping above it than the original was
   allowed. A band the base curve already sits outside that envelope for is not
   touched at all, rather than moved without evidence.
7. **One band moves at most ±3 dB**, the slope limit is applied to the step, and a
   step under 0.1 dB is dropped rather than spending a filter on it.

The predicted residual after the change is computed from **what the check
heard** plus the step, not from the original measurement: once the two disagree,
the older curve no longer describes the room. So the "before → after" figure the
user sees starts from the same check residual already on screen.

## What is not claimed

- **No hardware has run this.** The arithmetic is covered off-device by 14 cases
  in `CorrectionRefinementTest` (Kotlin, run in the repository harness) and 12 in
  `tests/test_core_eq_dsp.py`, and the two implementations are pinned to the same
  room: the same fixture prints −2.0080 dB at 100 Hz on both.
- **One check is not convergence.** A single capture at one position is evidence,
  not truth; that is what the 3 dB step cap is for. Nothing iterates
  automatically: the user takes a check, accepts or ignores the offer, and can
  check again. There is no auto-apply of a refinement.
- **The room is not re-measured.** The refined result keeps the original
  measurement, target, nulls, gates and confidence; only the correction, the
  fitted filters and the preamp change. The result card and the saved profile say
  so.
- **It cannot rescue a bad measurement.** If the first sweep was wrong, the
  refinement moves a wrong curve by up to 3 dB per band, within the same budget.
  It is a second look, not a calibration.
