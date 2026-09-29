# Community input — "no, i usually use REW and a1 evo express"

Date: 2026-09-30 · Status: input researched; M10 and the honesty sentence decided and implemented in the v1.1 working tree
Question (owner): *"[Discord, Esteban007, 2026-09-29]: no, i usually use REW and
a1 evo express"* — offered as community input for Core EQ. The preceding
question is not in the screenshot; the working reading is "do you use a
TV-side equaliser / auto-calibration app?", answered **no** — this member's
room correction runs through the PC + AVR stack. If the question was different,
the analysis below still holds for what the answer *is*.

## 1. Answer

Esteban007 is not a lapsed user of our category — he is a **power user of the
adjacent category**: REW (Room EQ Wizard, the free PC measurement/analysis
tool, usually with a UMIK-1) plus **A1 Evo Express** (OCA's free one-click
optimizer on AVSForum that drives REW sweeps and loads the result into a
Denon/Marantz AVR's Audyssey filter bank). Three things follow:

1. **Our core physics has external precedent — the enthusiast community
   converged on it independently.** A1 Evo's own published workflow splits
   correction at the Schroeder frequency ("below Schroder frequency will
   already be equalized", above it is manual), measures MLP-first with 3–9
   energy-combined sweeps at matched height, and preserves full impulse
   decay. That is `docs/research/core-eq-correction-science-2026-09-27.md`
   and pass 4's spatial-averaging rule almost word for word. **Validation —
   cite it, do not change course.**
2. **Their bug reports are our test cases.** The loudest complaint in the
   A1 Evo Express thread is a mis-detected roll-off: a 45 Hz room mode read
   as the subwoofer's roll-off point, costing half an octave of bass. That is
   precisely the failure mode of our `rolloffHz` floor detection
   (`CorrectionLimits.MIN_HZ = 40`). **P1 — a regression fixture: a simulated
   45 Hz mode above a speaker that plays flat to 30 Hz must not move the
   correction floor.** Test-only, no product surface.
3. **For this crowd the honest role is measure-and-export, not apply.** An
   AVR running A1 Evo already corrects the room downstream of the TV; a
   Core EQ effect on top would double-correct — the exact "never both at
   once" rule we enforce between our own paths must also be told to the
   user (§4.3). The bridge that makes enthusiasts allies rather than
   dismissers is **importing their REW measurements** instead of asking them
   to re-measure with a TV remote (§4.2).

Priorities: **P1** roll-off mis-detection fixture (now, cheap) · **P2** REW
import (v1.2 candidate, decision-gated below) · **P2** downstream-correction
wording (v1.1 polish candidate) · explicit non-recommendations in §5.

## 2. Method and sources

- Local: `docs/CORE_EQ_PLAN.md` §4/§5, research passes 3–6, `CorrectionLimits`,
  `SweepAnalysis`'s roll-off floor, the export formats in `export/Formats.kt`.
- Web (2026-09-30):
  - REW official help, "Importing Measurement Data" — generic REW text rows
    are frequency, magnitude and optional phase; space/tab/comma/semicolon
    delimiters are supported, values must ascend, and decimal-comma locales
    should use a non-comma delimiter. This is the M10 parser's format
    contract — https://www.roomeqwizard.com/help/help_en-GB/html/dataimport.html
  - AVSForum "A1 Evo AcoustiX from OCA" — the successor suite; FAQ pins the
    Schroeder split and the measurement protocol
    (REW 48 kHz ≥256k sweeps, MLP first, XY offsets at identical height, no
    gating on exported IR) — https://www.avsforum.com/threads/a1-evo-acoustix-from-oca-the-latest-version-of-sound-optimization-suite-diracart-denon-marantz.3336786/
  - AVSForum "A1 Evo Express from OCA" — the tool Esteban007 names; the
    roll-off-vs-mode mis-detection complaint and manual recovery path
    (https://www.avsforum.com/threads/a1-evo-express-from-oca-the-latest-version-hes-conjured-up.3331417/)
  - r/hometheater "A1 Evo Express for Absolute Dummy (Me)" — the workflow as
    users describe it: mic at MLP, 3–9 sweeps, result pushed to an AVR preset
    (https://www.reddit.com/r/hometheater/comments/1p63gvo/)
  - r/hometheater "REW Measurements" — Express runs calcs through REW and
    talks to the AVR; Acoustica accepts UMIK-1/REW measurements
    (https://www.reddit.com/r/hometheater/comments/1myblcr/)

## 3. What the stack actually is

| | REW | A1 Evo Express | Core EQ |
|---|---|---|---|
| Measures with | UMIK-1 / Audyssey mic, PC | Audyssey mic (Express) or UMIK (Acoustica) | the TV remote mic |
| Computes | FR/IR analysis, filters by hand | delay, trims, crossovers, EQ, one click | two-regime room correction, named limits |
| Applies via | miniDSP, AVR tools, exports | Denon/Marantz Audyssey filter bank | TV equaliser sessions / export |
| Needs | PC + mic | PC + Audyssey AVR | nothing but the TV |

Esteban007's "no" is the correct answer *for his chain*: with an AVR
calibrated by A1 Evo, a TV-side equaliser has nothing honest to add to the
apply path. It is not evidence against the unoccupied quadrant of
`core-eq-android-tv-market-and-competitors-2026-09-28.md` — TV speakers,
soundbars and streamers without downstream correction. It is evidence about
where our **measurement credibility** should come from.

## 4. Plan deltas under consideration

1. **Roll-off mis-detection fixture (P1, test-only; implemented).**
   `RewImportTest` pins the community's reported failure: a strong ~45 Hz
   modal peak on a speaker that extends lower must not raise `rolloffHz`
   or shrink the correctable band. Protects the correction floor against
   the one bug this community already paid for.
2. **REW measurement import (P2, *decided: v1.1 M10 — implemented; see §6*).**
   Accept REW's exported magnitude data (text/CSV/FRD) as a measurement
   input: the profile is explicitly `REW import` / `rew_import`, skips the
   remote-mic sweep, and the correction maths stays identical. The app does
   not infer a mic model or date from the file. Cheaper than USB-mic support
   and a strict subset of its value — and it is the bridge that turns the
   enthusiast stack into Core EQ's best evangelists instead of its harshest
   reviewers.
3. **Downstream-correction honesty (P2, wording).** The Measure screen
   already says to turn the TV's own sound effects off. Extend it by one
   sentence: if an AVR or soundbar already calibrates the room (Audyssey,
   YPAO, A1 Evo, Dirac), use Core EQ to measure and export — do not stack a
   second correction. Cheap, fits honesty rule 1 of `CORE_EQ_PLAN.md` §5.

## 5. Non-recommendations

- **Do not build an Audyssey/`.ady` or AVR-preset export.** That is A1 Evo's
  whole product; competing inside a Denon's filter bank is out of our
  quadrant and out of our competence.
- **Do not chase REW parity** (gating UI, spectrograms, hand filter editing).
  Import the measurement, own the correction + limits + TV apply path.
- **Do not treat "no" as churn.** One enthusiast naming his stack is a data
  point about the adjacent category, not a request to move Core EQ up-stack.

## 6. Decision (decided 2026-09-30)

1. **REW import (§4.2)** — *decided: fold into `docs/CORE_EQ_V11_PLAN.md` as
   M10, in v1.1.* Magnitude-only text/FRD/CSV; an optional phase column is
   deliberately not analysed. The existing Target button sets the curve; the
   room-size choice is retained as context but cannot alter the 300 Hz
   transition fallback without decay data. Provenance is `micType "REW import"`
   / `stimulus "rew_import"`; RT60, Schroeder and SNR stay null, and the
   unverified min-phase gate is a persisted profile note and a screen warning.
   Save is for export, not active by default; selecting the profile in
   Profiles is the explicit apply action.
2. **Downstream-correction sentence (§4.3)** — *decided: one string in v1.1
   now*, appended to `measure_step1_sub`; importing is also save-for-export
   and never auto-applies.

3. The P1 fixture (§4.1) is pinned in `RewImportTest`, exercising
   `Correction.detectLowRolloff` end to end: a ~45 Hz room mode leaves the
   floor at 40 Hz for a speaker flat below it.

Recorded in `docs/CORE_EQ_V11_PLAN.md` §2 (In), §7 (M10), §8 (gates) and
§10.5; implemented as `dsp/RewImport.kt` + `RewImportTest.kt`, profile-note
persistence, and the Measure screen import path.
