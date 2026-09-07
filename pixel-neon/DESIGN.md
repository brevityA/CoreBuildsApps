# Core Builds Pixel Neon · art direction

Pixel Neon is a separate product treatment for the same catalog. It is not a
pixel filter, recolour, or layout variant of Core Builds Icon Pack. The goal is
an 8-bit arcade vocabulary that remains readable on a television at launcher
thumbnail size.

## Research translated into constraints

The art decisions are based on these references:

- The repository's [Logo Fidelity Research & Audit](../docs/logo-research/ICON_LOGO_RESEARCH.md)
  is the brand-fidelity source for the researched overrides. It distinguishes
  real marks from long-tail apps whose public identity is wordmark-only or
  unknown.
- [Google's Android launcher icon codelab](https://codelabs.developers.google.com/design-android-launcher)
  recommends simple, recognizable artwork, a grid/keyline approach, contrast,
  and safe zones. It also calls out that fine details and many effects are lost
  at small sizes.
- [Pixelle's pixel-art icon guidance](https://pixelle.io/pixel-art-icons)
  emphasizes one fixed grid, a limited palette, hard edges, and checking the
  silhouette before internal detail.
- [Pixel Art Complete Tutorial](https://generalistprogrammer.com/tutorials/pixel-art-complete-tutorial)
  reinforces intentional low colour counts, readable silhouettes, and avoiding
  anti-aliased scaling for small sprites.
- [IconPackSupporter](https://github.com/sigv/IconPackSupporter) and the
  [Projectivy Icon Pack example](https://github.com/SicMundus86/ProjectivyIconPack)
  were used as format references: transparent icon resources, an
  `appfilter.xml` mapping, and launcher-facing install/apply instructions are
  more important than coupling the alternate art to the original renderer.

The resulting rules are:

1. Draw on a **32×32 integer pixel canvas**. Upscale with nearest-neighbour to
   the 512×512 icon resource; never downsample the monoline SVGs.
2. Start from the catalog's **semantic brand glyph cue**, not a copied logo
   outline. Tile glyphs become compact brand monograms; named cues become
   independently drawn pixel families for plays, eyes, shields, waves, folders,
   sports marks, stars, arrows, clouds, satellites, and other recognizable
   brand signals. Category silhouettes remain the fallback for future rows with
   no brand cue.
3. Keep the visible sprite to a small palette: one primary neon, one secondary
   accent, dark outline/shadow, a midtone, and a single light highlight. The
   glow is a restrained raster halo around the sprite, not a baked card
   background.
4. Vary the object position, proportions, internal pattern, highlight, accent
   hue, and one deterministic signature pixel from the catalog drawable/name
   hash. A collision check makes every catalog row's final raster distinct.
5. Keep banners independent as well. Each 320×180 fallback is composed from the
   generated sprite, a 3×5 bitmap label, and one of three layouts; it does not
   reuse the original rail-and-wordmark banner.
6. Preserve transparency in square art so Projectivy/Android TV owns the card
   surface and can provide the dark contrast that neon artwork needs.

## Delivery contract

`tools/build_pixel_neon.py` reads only `tools/catalog.json` for product names,
components, source accent colours, categories, and semantic brand glyph cues.
Its sprite recipes are owned by the alternate renderer, so the brand cues are
reinterpreted as pixel art rather than converted from vector paths. It has no
read path to `assets/svg` or `assets/banners`.

The generated pack keeps the Android icon-pack contract:

- `app/src/main/res/drawable-nodpi/` contains 512×512 square sprites and
  320×180 banner fallbacks.
- `app/src/main/res/xml/appfilter.xml` maps catalog components to banner
  drawables, including full and short component spellings.
- `app/src/main/res/xml/drawable.xml`, `iconpack.xml`, and
  `res/values/icon_pack.xml` expose the square and banner catalog to the
  picker.
- `app/src/main/assets/` mirrors the launcher XML resources.
- `docs/build-receipt.json` records `pixelGrid: 32`,
  `uniqueSprites: 924`, and the brand-glyph recipe source;
  `tools/validate_pixel_neon.py` verifies those claims, dimensions, mappings,
  and asset parity.

The wallpaper companion has its own 8-bit scene language: the 70 source images
are original low-resolution pixel compositions across arcade grids, cyber
circuits, space runs, neon nature, and boss-stage arenas. They do not reuse or
recolour the original pack's wallpapers. The manifest and small thumbnail
JPEGs are bundled for offline browsing, while the full 4K PNG sources stay in
`PixelNeonWallpapers/` and are downloaded and cached only when a user previews,
sets, or exports one. The browser keeps the night/pixel chrome, series chips,
name search, full-screen preview, direct system setter, Android TV save
fallback, and `Pictures/CoreBuilds` bulk-export flow.

This keeps the product compatible with the existing Android delivery format
while making the visual source and composition genuinely independent.
