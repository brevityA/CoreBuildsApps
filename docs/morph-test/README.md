# Morph icons — test set (not shipped)

Glyph at rest, banner on focus. Each file is an animated image (320x180, 16
frames, ~0.5 s): the square mark eases from the centre of the card into its
place in the banner lockup while the rail and name slide in. The last frame is
the shipped banner, pixel for pixel — `tests/test_morph_icons.py` holds it
there and fails if a banner or catalog change ever lets it drift.

`preview.gif` shows all ten side by side; `preview-loop.gif` shows the
ping-pong flavour.

**Two flavours per app.** `*_morph.*` plays once (glyph -> banner, held) — the
shape for a launcher that honours the file's loop count. `*_morph_loop.*` is
the same 16 frames played forward then in reverse, 30 frames looping forever —
the shape for a launcher that loops every animated icon regardless of the file
(Projectivy before 4.72). Why both: [RESEARCH.md](RESEARCH.md).

## What this test answers

1. **Icon pack route.** Does Projectivy animate an animated drawable that an
   icon pack's appfilter maps, or only a custom icon picked from a file?
2. **Focus.** Does the animation play when the card gains focus, and does the
   card return to frame 1 (the glyph) when focus leaves?
3. **Loop.** Does the launcher honour the file's loop count, or loop every
   animated icon until its own setting says otherwise? The play-once and
   ping-pong pair measures this: if the play-once file loops, the ping-pong
   file is the one to recommend.

## Trying it

- **Icon pack:** install the test APK (`tv.corebuilds.iconpack.test`, from the
  `iconpack-test` prerelease). Its appfilter maps these ten apps to the
  `*_morph` drawables; every other app keeps its banner. The icons are also
  first in the picker, under "Morph test".
- **Manual custom icon:** copy one file to the TV and set it as that card's
  custom icon in Projectivy (long-press the card -> Change Icon). Custom card
  icons are a **Premium** feature. Three formats of the same frames, because
  launchers differ in which animated format they decode — **try WebP first**:
  it is the smallest and keeps full-alpha lossless frames, and Projectivy
  decodes it for custom icons since 4.70:
  - `*_morph.webp` — animated WebP (what the test APK ships, and the file to
    try first).
  - `gif/*_morph.gif` — GIF; every Projectivy that animates anything animates
    GIF (it is what the community ships), but 256 colours and 1-bit alpha make
    edges harder.
  - `apng/*_morph.png` — APNG; lossless like WebP, but it needs Projectivy
    4.70+ (its own decoder — APNG is not in the Android framework) and had
    display bugs fixed during the 4.70 betas. Last resort.
  - The same three formats under `*_morph_loop.*` / `apng/*_morph_loop.png` /
    `gif/*_morph_loop.gif` are the ping-pong flavour.

## Result so far

**Icon-pack route (2026-09-25, Projectivy 4.71): no animation.** Expected:
Android hands an icon pack's drawable to the launcher as a static bitmap, so
the card shows frame 1 (the centred glyph). Animation, if Projectivy supports
it at all here, can only come through the manual custom-icon route.

**Manual route (research, 2026-10-10 — [RESEARCH.md](RESEARCH.md)):** on
Projectivy >= 4.70 the prediction is exactly this test's design — the
animation starts on focus, stops when focus leaves, and the card **resets to
frame 1** (the glyph) on stop. Before 4.70 the card freezes on its current
frame instead. Before 4.72 animated icons loop unbounded; 4.72 added a
loop-count setting. Recorded device results for the manual route go here.

**Regenerated 2026-10-10.** The set was stale: the catalog renamed Max to HBO
Max (2.1.2), the banner art moved on (2.1.2/2.2.0, including a new HBO Max
wordmark), and YouTube's banner carries a secondary ink the generator was not
drawing — so the old files' last frames no longer matched the shipped banners.
All files were rebuilt with the fixed generator; the last frame of every
`*_morph.webp` is again pixel-identical to the shipped banner.

Apps: Netflix, Prime Video, YouTube, Disney+, Plex, Crunchyroll, Apple TV, HBO
Max, Stremio, Spotify.

Regenerate with `python tools/build_morph_icons.py`. It writes only here and
to `app/src/candidate/`, which only the candidate build type sees; the
production pack is unchanged.
