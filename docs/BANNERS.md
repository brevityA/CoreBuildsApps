# 16:9 banners

## Correction to earlier advice

I previously said an icon pack cannot supply banners. **That was wrong.**

`android:banner` — the attribute each app declares for the Android TV home row
— genuinely can't be overridden by a third party. But that is not how Projectivy
draws its cards.

Projectivy cards are **16:9 by default**, and the card aspect ratio is a user
setting. From the developer (Spocky_12):

> "Custom icons: use your favorite graphic editor to create images with a ratio
> of **16:9** (ex: 160x90px)"

People who want square icons *change the card to 1:1*. So a pack drawable was
never required to be square — ship 16:9 art and every card renders as a full
banner. That is exactly what the WuPlay reference banner is.

## What we generate

`tools/build_banners.py` produces a 16:9 lockup per app: glyph left, app name in
the display serif, transparent ground so the launcher's card colour shows
through.

**No pack branding on the artwork.** The reference pack puts nothing of its own
on any icon — their DAZN icon is just DAZN. A `CORE BUILDS` label on someone
else's app card is noise the user did not ask for. The Core Builds signature is
carried by the glyph geometry and the accent colour, not by a wordmark.

```bash
python tools/build_banners.py          # icons flagged "banner": true
python tools/build_banners.py --all    # every icon in the catalogue
```

Output:

```
assets/banners/<drawable>.svg                    master vector, 1280x720
app/src/main/res/drawable-nodpi/<d>_banner.png   1280x720 transparent
```

## Why a separate drawable

Banners are `<icon>_banner`, distinct from the square `<icon>`. The square set
stays the pack default; banners are listed under their own category in
`drawable.xml`, so a user picks the banner treatment **per app** from the
launcher's icon browser without changing anything globally.

Mark an app for a banner in `tools/catalog.json`:

```jsonc
{ "name": "Stremio", "drawable": "stremio", "banner": true, ... }
```

25 are flagged today — the ecosystem headliners.

## The grid, measured from the reference pack

Decompiled Projectivy Icon Pack 1.1.9 and counted every PNG:

| Dimensions | Count | What |
| --- | --- | --- |
| **320×180** | **1002** | **the app icons — 16:9, RGBA, ~10 KB each** |
| 150×150 | 178 | Blueprint UI chrome, not app icons |
| 512×512 | 34 | launcher/adaptive icons |

So the established pack's icons are *already* 16:9 banners at 320×180. We now
emit exactly that: **320×180 RGBA, ~6 KB each**. Authored on a 1280×720 master
(4×) and downscaled, so the art stays crisp.

Long names auto-shrink: the generator solves for the largest font size that
fits the text column rather than guessing, so "Projectivy Launcher" and
"SmartTube Next" stay inside the frame.

## Guarded

The validator checks every flagged banner exists, is **within 0.01 of 16:9**, is
RGBA, and is listed in `drawable.xml`. Verified it bites: rendering one at
800×800 produces

```
✗ Stremio: banner is 800x800 (ratio 1.000), expected 16:9
```

1357 checks total.

## Composition grammar

Measured across a 150-icon sample of Projectivy Icon Pack 1.1.9:

| Property | Theirs | Ours |
| --- | --- | --- |
| Canvas | 320×180 RGBA | 320×180 RGBA |
| Ink width | median 78% | 78% |
| Ink height | median 43% | 38% |
| Centring | 0.0px both axes | 0.0px (max 1px drift) |
| Ink coverage | ~12% | ~11% |
| Layout | glyph + wordmark, centred | glyph + wordmark, centred |

Those are their **structural** rules and they are sound for a 10-foot UI: a
centred lockup filling ~78% of the width reads from across a room without
crowding the card edge.

What we do **not** take is the art. Their icons are official third-party logos
placed as-is. Ours are original geometry in the Core Builds language — simple
shapes, rounded ends, one accent colour per app (Brand Guide §07). Same grid,
different vocabulary.

### Two implementation notes

**Type is measured, not estimated.** Earlier passes guessed an em-width per
character and long names overflowed. The generator now measures the actual
rendered string and solves for the largest size that fits.

**Centring is measured, not calculated.** Glyph grids are not tight to their
ink and serif text carries side bearings, so geometric centring left every
lockup ~4px right of centre — invisible alone, obvious in a row. The generator
rasterises once, reads the real alpha bounding box, and shifts to compensate.
The validator then enforces ≤3px drift on every banner; verified it fails a
deliberately shifted one at +19.5px.

## Typography — why the wordmark is bold sans

Brand Guide §04 splits the voices: **serif for display copy** (splash headlines,
question cards, doc covers) and it is explicit — *"Never bold. Never all-caps."*
Working UI text belongs to the **system-ui stack at 600–800 weight**.

An app card label is not display copy. It is a name read from across a room, so
it takes the sans stack at 700:

```
-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif
```

This is also what gives the reference pack its punch — their wordmarks are heavy
sans, and a light serif at card size looks tentative beside them. The serif
still owns the docs and splash surfaces; it just does not belong on a 320×180
card.

Tracking is -1.5 at display size, per §04's "tight (-.03em)" guidance for
headline-scale type.

**Guarded.** A silently-failed edit once left every banner still rendering in
Georgia while the source *looked* updated. The validator now asserts no banner
wordmark uses Georgia and every one carries `font-weight="700"`. Verified it
bites: reverting one banner to serif fails by name.

## The chosen treatment — H + F

Concept **H** carries the layout, with concept **F**'s hexagon as the glyph
host:

* **cyan→violet rail** on the left edge (H)
* **uppercase mono category kicker** above the name (H) — real information,
  not decoration: STREAM, DEBRID, MEDIA, PLAYER, LIVE, VOD, MUSIC, VIDEO,
  TOOL, STORE, LAUNCHER, SYSTEM, REMOTE, GAMING, TRACK, CORE
* **point-up hexagon host** in the app's accent (F) — §02's container, and
  the stance is never rotated
* **lit glyph** (D) — the §02 halo

The hexagon is deliberately a light wash (10% fill, 42% stroke) rather than a
solid container. At full strength across 81 icons a row reads as "hexagons"
before it reads as apps — the generic trap. At this weight it hosts the mark
and signs the pack without competing with it.

E, G and I were rejected on a structural ground rather than taste: their
signal lives in the card *background*, which we do not own. These PNGs are
transparent and Projectivy paints whatever colour the user chose behind them.
Rail, kicker, hex and halo are all drawn ink, so they survive any card colour.
