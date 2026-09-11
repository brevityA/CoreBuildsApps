# Classic presence pass — 11 September 2026

The Classic pack is clean and then disappears. Monet's transparent tiles and
Projectivy cards over photography both expose it: a 32px monoline on a 512
grid is about a 2px stroke at dock size, with no holdout against the wallpaper.

## What we would not do

- Put `feGaussianBlur` in the SVG masters. Style AA and `validate.py` reject it.
- Fill the marks or add a container. That is Pop's job.
- Raise the primary stroke globally. That rewrites the identity contract and
  fattens detail that is supposed to stay subordinate.

## What we did

`tools/presence.py` runs after `svg2png` inside `build_icons.py`.

1. Night keyline (`#070B12`) as a *ring* around existing ink.
2. Short accent bloom, also ring-masked, so YouTube's play counter and MUBI's
   seven gaps stay alpha.
3. Adaptive radius: dense marks (TiviMate, letter tiles) get 5px; sparse marks
   get 11px. Stops the ring turning a heavy glyph into a slab.

Vectors are unchanged. Banners are unchanged. Pop and Pixel Neon are unchanged.

## How to judge it

Look at the five dock icons from a Monet screenshot — WuPlay, TizenTube,
TiviMate, Janky Player, Plex — on void and on a bright band. The geometry is
the same. The mark just holds.
