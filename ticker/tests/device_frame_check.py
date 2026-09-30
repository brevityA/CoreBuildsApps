#!/usr/bin/env python3
"""Did a rendered board actually reach the screen?

This runs on a screenshot taken from the Android TV emulator, inside the
device-check job, and it exists because the assertions that *can* be made over
adb are all blind to the one failure that matters most here.

`adb shell am start -W` says the activity started. `dumpsys` says it is
resumed. `logcat` says nothing crashed. None of that says a single pixel was
painted — and the catastrophic failure this whole branch is exposed to (a
Content-Security-Policy the Android WebView enforces differently from desktop
Chromium) produces exactly that: an app that launches, resumes, crashes
nothing, and shows a black rectangle, because `html { background }` is painted
by the platform and the module graph that would fill it never ran.

So the check is about surfaces, not about content:

  * the frame is overwhelmingly **dark** — true black is the default surface,
    so a white or bright frame means either the stylesheet was blocked (the
    page arrives unstyled) or something other than the app is on screen;
  * the frame contains a real number of **bright** pixels — text and accent ink
    are bright, and a black rectangle has none.

Both together are the cheapest honest proof that a UI is on the display. It
does not read the content: a text-level assertion needs the WebView's DOM,
which `uiautomator` cannot see and which needs CDP. The grid it prints is there
so that a human looking at a failure can tell *where* on the screen the ink is,
which distinguishes "the board" from "a centred dialog".

Thresholds are deliberately loose. This is a smoke alarm, not a layout test —
the browser job owns layout, at five viewport sizes.

Usage:  python3 ticker/tests/device_frame_check.py <screenshot.png>
Exit:   0 when the frame looks like a rendered board, 1 otherwise.
"""

from __future__ import annotations

import sys

from PIL import Image

# Luminance at or below this counts as a dark surface. The OLED ramp tops out
# at #1E1E1E = 30, so every surface the app draws is under it, and the emulator
# renders through swiftshader — exact colours, no dithering to argue with.
DARK_MAX = 32
# Text and accent ink. Cyan (#00D4FF) is ~170, white is 255, the slate greys
# are ~130-150. Nothing the app draws as a *surface* reaches this.
BRIGHT_MIN = 128

MIN_DARK_RATIO = 0.80
MIN_BRIGHT_PIXELS = 500
GRID = 4


def main(path: str) -> int:
    try:
        img = Image.open(path).convert("L")
    except Exception as err:  # a truncated pull, a missing file
        print(f"::error title=frame::could not read {path}: {err}")
        return 1

    width, height = img.size
    total = width * height
    hist = img.histogram()

    dark = sum(hist[: DARK_MAX + 1])
    bright = sum(hist[BRIGHT_MIN:])
    dark_ratio = dark / total

    print(
        f"::notice title=frame-stats::{width}x{height} "
        f"dark={dark_ratio:.4f} (min {MIN_DARK_RATIO}) bright={bright}px "
        f"(min {MIN_BRIGHT_PIXELS})"
    )

    # Where the ink is. A board has it spread across the rail and the cards; a
    # single centred dialog has it in the middle and nowhere else.
    rows = []
    for gy in range(GRID):
        cells = []
        for gx in range(GRID):
            box = (
                gx * width // GRID,
                gy * height // GRID,
                (gx + 1) * width // GRID,
                (gy + 1) * height // GRID,
            )
            cells.append(sum(img.crop(box).histogram()[BRIGHT_MIN:]))
        rows.append(" ".join(f"{c:>7}" for c in cells))
    print("::notice title=frame-grid::bright pixels per cell, " + " | ".join(rows))

    problems = []
    if dark_ratio < MIN_DARK_RATIO:
        problems.append(
            f"only {dark_ratio:.1%} of the frame is a dark app surface — "
            "an unstyled page, or something that is not the board"
        )
    if bright < MIN_BRIGHT_PIXELS:
        problems.append(
            f"only {bright} bright pixels — the app is on screen but has "
            "painted no text, which is what a blocked module graph looks like"
        )
    if problems:
        for problem in problems:
            print(f"::error title=frame::{problem}")
        return 1

    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("usage: device_frame_check.py <screenshot.png>")
        raise SystemExit(2)
    raise SystemExit(main(sys.argv[1]))
