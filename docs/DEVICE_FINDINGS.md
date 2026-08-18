# Device evidence: the wordmark lockup is too quiet

Measured from a real TCL Google TV home row (screenshot, 1431×805).
This is the first time the pack has been judged at true size next to real
neighbours rather than on a 4× contact sheet.

## What the row actually shows

| Icon | Width | Ink height |
| --- | --- | --- |
| Apple TV | 65px | 48px |
| **WuPlay (ours)** | 90px | **39px** |
| SYNC | 161px | 76px |
| **Plex (ours)** | 37px | **37px** |
| **TizenTube (ours)** | 108px | **30px** |
| TV Bro | 201px | **100px** |

Ours render at **30–39px** of ink. Everything else runs **48–100px**. We are
the quietest icons on the shelf, and Plex at 37px wide is nearly invisible
beside SYNC at 161px.

## Why

The row renders ~100px tall, so the 720px master scales by **0.139**:

| Element | Master | On screen |
| --- | --- | --- |
| glyph box | 360px | 50px |
| wordmark | 124px | 17px |
| **category kicker** | 46px | **6.4px** |
| **rail** | 16px | **2.2px** |

Two elements fail outright. The kicker at 6.4px is far below the ~12px
legibility floor for a 10-foot UI — it is noise, not information. The rail at
2.2px reads as a rendering artifact.

The wordmark is the deeper problem: it consumes roughly 60% of the canvas to
repeat a name **Projectivy already draws beneath the card**. The screenshot
shows "WuPlay", "Plex" and "TizenTube" rendered twice — once by us, once by
the launcher. We spend most of the canvas saying something the platform
already said, and starve the mark to do it.

## The variants tested

Composited into the real screenshot, in the measured slots:

| Variant | Ink height achieved |
| --- | --- |
| CURRENT wordmark lockup | 30–39px |
| **L1** mark only, monoline | **100px** |
| **L2** mark only, heavier stroke | **100px** |
| **L3** mark only, heavy + glow | **100px** |

All three triple the mark and match TV Bro exactly. See
`device-comparison.png` and `device-sbs.png`.

## Recommendation

**L2 — mark only, heavier stroke.**

- The mark goes from 50px to ~78px of drawn art inside a 100px row.
- The launcher keeps supplying the name, so nothing is lost.
- The heavier stroke matters: at 2–3px on screen our monoline strokes were
  the faintest in the row. TV Bro and SYNC both use bright, weighty fills.

This reverses the earlier AA decision, and the reason is worth recording. AA
was chosen because Google's TV guidance says to avoid borders and show
"icon + text". That guidance is written for an app's **own** banner on the
Android TV home screen, where nothing else draws the name. Inside Projectivy,
the launcher draws it — so the text is redundant and the guidance does not
transfer. Device evidence beats a spec read out of context.

## Caveat on the mocks

The composites patch our icons over the original screenshot by sampling
wallpaper from above the row. Faint rectangles and leftover text fragments are
artifacts of that patching, not of the icons. Sizes and weights are accurate.
