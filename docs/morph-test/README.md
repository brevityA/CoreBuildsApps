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
- **Manual custom icon:** copy a `*_morph.webp` from this folder to the TV and
  set it as that card's custom icon in Projectivy.

Apps: Netflix, Prime Video, YouTube, Disney+, Plex, Crunchyroll, Apple TV, Max,
Stremio, Spotify.

Regenerate with `python tools/build_morph_icons.py`. It writes only here and
to `app/src/candidate/`, which only the candidate build type sees; the
production pack is unchanged.
