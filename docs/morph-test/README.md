# Morph icons — test set (not shipped)

Glyph at rest, banner on focus. Each file is an animated WebP (320x180, 16
frames, ~0.5 s, plays once): the square mark eases from the centre of the card
into its place in the banner lockup while the rail and name slide in. The last
frame is the shipped banner, pixel for pixel.

`preview.gif` shows all ten side by side.

## What this test answers

1. **Icon pack route.** Does Projectivy animate an animated drawable that an
   icon pack's appfilter maps, or only a custom icon picked from a file?
2. **Focus.** Does the animation play when the card gains focus, and does the
   card return to frame 1 (the glyph) when focus leaves?

## Trying it

- **Icon pack:** install the test APK (`tv.corebuilds.iconpack.test`, from the
  `iconpack-test` prerelease). Its appfilter maps these ten apps to the
  `*_morph` drawables; every other app keeps its banner. The icons are also
  first in the picker, under "Morph test".
- **Manual custom icon:** copy one file to the TV and set it as that card's
  custom icon in Projectivy. Three formats of the same 16 frames, because
  launchers differ in which animated format they decode:
  - `apng/*_morph.png` — APNG, the format the Projectivy community already
    uses for animated icons. **Try this first.**
  - `*_morph.webp` — animated WebP (what the test APK ships).
  - `gif/*_morph.gif` — GIF; 1-bit transparency, so edges are harder.

## Result so far (2026-09-25)

The icon-pack route shows no animation. Expected: Android hands an icon
pack's drawable to the launcher as a static bitmap, so the card shows frame 1
(the centred glyph). Animation, if Projectivy supports it at all here, can only
come through the manual custom-icon route.

Apps: Netflix, Prime Video, YouTube, Disney+, Plex, Crunchyroll, Apple TV, Max,
Stremio, Spotify.

Regenerate with `python tools/build_morph_icons.py`. It writes only here and
to `app/src/candidate/`, which only the candidate build type sees; the
production pack is unchanged.
