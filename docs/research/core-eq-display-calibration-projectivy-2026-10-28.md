# Core Display — what Projectivy does with calibration, and what Core EQ adopted

Date: 2026-10-28. Ships as Core EQ 1.9.0, in `DisplayCalibrationActivity`,
`CalibrationGuide` and `TestPatternView`.

## What was studied

Projectivy Launcher (`spocky/miproja1`) is the most widely used third-party launcher for
Android TV, and it ships a display feature. Read from its README, its Play Store listing and the
long XDA thread (`xdaforums.com/t/app-android-tv-projectivy-launcher.4436549/`), that feature is
two things:

1. **Calibration patterns** — full-screen test images, selectable sets for FHD, 4K, judder,
   HDR and Dolby Vision.
2. **Engineering-menu shortcuts** — buttons that deep-link into the manufacturer's service and
   picture menus (MediaTek, Amlogic, Xiaomi, FengOS builds), where settings the normal menu
   hides can be changed.

Alongside that, Projectivy has **display profiles**: named bundles of picture parameters
(contrast, brightness, hue, saturation, sharpness, colour-temperature gain and offset, dynamic
colour and contrast, noise reduction, deinterlacing, zoom, backlight) that are applied on input
change, on dynamic-range change, or from the remote, and edited next to the helper patterns.
Almost every write in that path requires root; the profile editor is essentially a root tool.
The pattern screen has also been the source of out-of-memory crashes over its history.

## What that means for Core EQ

Core EQ is not a launcher and does not have root. That rules out the two things Projectivy's
feature is really built around: writing picture parameters, and deep-linking into service menus.
Copying either as a non-root app would produce a screen full of buttons that do nothing.

What is left is the part that does not need root — and it is the part a viewer can actually
use:

| Projectivy | Core EQ 1.9.0 |
|---|---|
| Pattern sets for FHD / 4K / judder / HDR / DV | 13 patterns, including judder, motion resolution, pixel grid, HDR PQ and both uniformity fields |
| Side-by-side patterns with no guidance beyond the noise | A ten-step guide that asks a question per step and gives the advice for *that* answer |
| Engineering-menu shortcuts (root) | Not attempted. The guide names the setting ("Just Scan", "Auto Motion Plus") and where it usually lives |
| Display profiles applied on input/HDR change (root) | Not attempted. The last step is a written record, which is what a non-root user has instead |
| OOM crashes on the pattern screen | Patterns are drawn per frame with no bitmaps; only the two motion patterns redraw |

## What changed in the screen

The old `DisplayCalibrationActivity` was a fixed list of ten patterns with one paragraph each.
That had two failure modes: it told everybody the same thing, and it could not answer "so what
do I do if it looks wrong?". `CalibrationGuide` replaces it with a model:

- **`Step`** — a title, an instruction, a pattern, and the **`branches`**: the answers a person
  can give. Each branch carries its advice and, where the answer is a fault with a repair, the
  step that repair leads to. "Bar 0 is not black" and "nothing appears until code 8" are
  different faults with opposite fixes.
- **`DisplayFacts`** — what Android actually reports: the mode the UI runs at, the panel's own
  resolution from `Display.getMode()`, refresh rate and HDR types. From it the screen puts
  three notes above the first step: a 1080p interface on a 4K panel is Android's default UI
  resolution and not a picture setting; an unusual refresh rate is not a multiple of 24;
  a display reporting no HDR does not prove the TV has none.
- **The order** — mode, overscan, black, white, gamma, colour, sharpness, motion, uniformity,
  HDR, record. Overscan moved to the front because everything judged against the screen edge is
  wrong until it is fixed; motion moved last because judging it before the panel is settled
  wastes the effort.

The activity draws the branch buttons for each step, shows the advice for the chosen answer, and
makes the Next button continue to the step that answer pointed at. Nothing is written to the TV;
the guide's honesty is that it says which faults are not settings at all — panel response time,
dirty-screen effect, a 1080p UI — instead of sending the user to a menu that cannot fix them.

## Test-pattern honesty (the part Projectivy's patterns do not discuss)

Auditing the old patterns turned up four bugs worth fixing before adding more:

| Fault | Why it mattered | Fix |
|---|---|---|
| Black ladder stepped one 8-bit code (0…15) | Codes 0 and 1 are the same colour on every panel; "make bar 2 visible" could not be followed | Two-code steps, 0…30, each bar labelled with its code |
| White ladder stepped one code from 240 | The top sixteen bars were a flat white block; "253 vs 255" was untestable | 200…255 with the top at 250 / 253 / 255 — 253 and 255 are two codes apart, the smallest step that survives a panel |
| Gamma patches used `255·0.5^(1/γ)` | Not a gamma curve at all: the γ2.4 patch sat at code 191, a near-white square on a 128 field, so every display looked like γ1.8 | `255·(L_rel)^(1/2.2)` with `L_rel = 0.5^γ` for the power-law row and BT.1886's black offset for the second row (γ2.4 → 120 and 123) |
| HDR patches were six hand-picked greys | Claimed to test PQ tracking without any transfer function behind it | ST.2084 inverse EOTF codes (100 nits = 128, 1000 nits = 192), with a caption that says whether the output is actually HDR |
| Sizes in raw pixels | A 28 px label is unreadable at 4K and zones were millimetres wide | dp for everything except the two places where a device pixel is the point |

The arithmetic lives in `DisplayMath` (pure Kotlin, no Android imports) so it can be tested:
`DisplayMathTest` pins the gamma codes, the PQ codes and the separability of both ladders. The
first version of that test suite failed twice for real reasons — the white ladder contained
252/253/254, which are one code apart, and BT.1886 is *brighter* than the plain power law
because the black offset lifts mid-grey, not darker as assumed. Both were corrected in the
model, not in the assertions.

## Not done, and not claimed

- No display was measured with a colorimeter. These are patterns and a guide; the eye is the
  instrument, exactly as with a calibration disc.
- The motion patterns are visual aids, not measurements: judder is shown as a 24-updates-per-
  second marker against a 60, and motion resolution as gratings moving at three speeds.
- `DisplayFacts.panelWidthPx` comes from `Display.getMode()`; on hardware that misreports its
  mode the resolution note can be wrong, and the note is phrased as a consequence of what
  Android reports rather than as a claim about the panel.
