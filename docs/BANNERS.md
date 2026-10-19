# 16:9 banners

## Current contract

Core Builds Icon Pack applies **transparent 320 × 180 banners** by default. The
pack's appfilter points every catalog component at its app's banner; the square
alternative is the separate **Core Builds Glyphs Pack** (`tv.corebuilds.iconpack.glyphs`).
The banner lets Projectivy or another launcher own the card surface, crop,
focus treatment, and background colour.

Each catalog entry is generated from `tools/catalog.json` by
`tools/build_banners.py`:

| Artifact | Size | Purpose |
| --- | ---: | --- |
| `assets/banners/<drawable>.svg` | 1280 × 720 | 4× vector master |
| `app/src/main/res/drawable-nodpi/<drawable>_banner.webp` | 320 × 180 | shipping transparent banner |
| `app/src/main/res/values/banner_aliases.xml` | — | aliases for identical banner art |

All catalog entries are generated; there is no per-icon banner opt-in or
partial build. Run:

```bash
python tools/build_banners.py
python tools/build_banners_pack.py
python tools/validate.py
```

`build_banners_pack.py` derives the main pack's banner appfilter from the
square companion's generated component list. Do not hand-edit generated SVG,
WebP, aliases, appfilter, browser or preview files.

## Composition

The standard lockup is:

1. one Core Builds glyph;
2. a small uppercase category kicker in that app's resolved accent;
3. the displayed app name (using `banner_name` when set) in the pack's rounded
   stroke-letter alphabet and light ink.

It is centered on a transparent canvas. There is no left rail, hexagon host,
pack wordmark, or baked background. Categories remain visible on banners and
in catalog metadata; they are useful labels even though the app name and mark
carry the product identity. Square glyphs and banners share the same catalog
color policy and glyph construction.

Long names are balanced across lines and measured against the actual text
column; `banner_name` may shorten only the card label when necessary. The
actual ink is raster-measured and recentered after rendering, and the validator
holds every banner to at most 3 px center drift, 90% width, and 72% height.

## Support-feedback scale update — 6 October 2026

Support feedback asked for the logo and name to be a little larger and easier to
distinguish at a glance. The first approved step increased the glyph cap from
360 to 396 and the name range from 124/62 to 136/68. After reviewing larger
options, a further 5% step was selected: the current cap is **416**, the name
range **143/71**, and the overall lockup width cap **88.2%** (up from 78% in
v2.1.1). That leaves the category size (46), horizontal gap (80 master units),
accents, transparency, and mappings unchanged. Compared with v2.1.1, the mark
and name caps are now about 15% larger; the fit solver still shrinks long labels
when needed.

The [first scale receipt](research/banner-scale-study-2026-10.png) compares
v2.1.1 with the initial +10% step. The
[follow-up comparison](research/banner-scale-options-2026-10.png) records the
selected +5% option alongside the larger alternative. Square glyphs and the
102 wallpapers are unchanged. The six-icon Heat family keeps the original
v2.0.0 HeatLive square construction; see the
[Heat family review](research/heat-family-review-2026-10.md).

## Design background

Projectivy cards are 16:9 by default, and its developer recommends 16:9 custom
images. Core ships the established 320 × 180 transparent size, authored on a
1280 × 720 master for crisp downscaling. The reference pack's original median
ink box was approximately 78% wide by 43% high; Core's current 88.2% fit budget
is a response to direct legibility feedback, not a claim that every banner has
an identical ink box. Each mark still uses its own geometry, optical fit, accent,
and word-length handling.

The square companion continues to supply the 512 × 512 glyph-only version for
launchers or layouts that use 1:1 icons. The Banners/Glyphs switch changes the
selected icon-pack package, not the catalog, app identities, mappings, or
wallpapers.
