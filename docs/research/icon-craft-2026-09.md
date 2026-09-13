# Icon craft tranche — 13 September 2026 (v1.8.17)

Scope: the **Classic Icon Pack v1.8.17 candidate**, mirrored by Pop and Pixel
Neon. Five new icons (931 total), nineteen new component mappings (1120
total), one launcher-badge redraw and one banner-contract fix. Everything is
built through the generators; nothing here is a hand-edited asset.

## 1. The launcher icon becomes a pack icon

The v1.8.16 badge was the last asset speaking the brand-mark language: a lit
hexagon scene with depth gradients, glow filters and a disc ground. On a home
row full of rounded monoline tiles it read as a different product from the
pack it opens. The v1.8.17 badge is redrawn **as a pack icon** — the
universal icon-pack symbol, a rounded app tile holding a 2×2 grid of marks —
in the catalog's own grammar:

| Contract | Launcher badge treatment |
|---|---|
| Geometry | Rounded-square frame, 32px primary; four inner marks at 26.2px detail |
| Paint | One accent `#00D4FF`, `fill="none"` everywhere, transparent ground |
| Effects | None — no gradient, glow, filter or disc. The old `MARK_DEFS` scene is gone |
| Contract check | `build_branding.py` asserts the mark against `icon_style.core_monoline_errors` **before writing any asset** — the app's own icon is held to the 931-icon contract |
| Raster | Legacy PNGs get the same `tools/presence.py` pass as every square icon (night keyline + accent bloom, ring-masked) |
| Adaptive | Foreground is the mark scaled to the 66/108 safe zone; background keeps the **night card** radial field so OEM masks never crop onto flat black |

One of the four inner marks is a circle so the grid reads as *icons* — a set
of different marks — rather than four identical panes.

`cb_banner` (the Leanback 320×180 row banner) and `docs/banner.png` carry the
same mark, night chrome and outlined Outfit wordmark as before; only the
glyph changed, and the strapline safe-area assert is unchanged.

## 2. The icon app gets a banner like every other icon

The Core Builds catalog entry carried `banner_style: glyph` — a mark-only
card, the one exception to the common banner recipe. The exception existed
because "the name adds nothing", but on a Projectivy card next to every
other app it read as a missing banner, not a design choice. The field is
removed: Core Builds now renders the standard lockup — rail, CORE kicker,
glyph, Outfit wordmark — like all the other icons. Pop never honoured the
exception, so the two packs now agree.

## 3. Source & input marks

Consoles reach the TV through HDMI, so on a Projectivy home they exist as
**input tiles**, not apps. Four new marks give those tiles — and the
companion apps users keep beside them — the same rounded-line identity:

| Icon | Glyph | Construction | Colour |
|---|---|---|---|
| Xbox | `xbox_orb` | Orb ring with two crossing flow strokes — the cue without the vendor path | `#107C10` (brand reference) |
| Nintendo Switch | `switch_joycons` | Two detached rails, sticks on opposite corners | `#E60012` (brand reference) |
| PlayStation | `playstation_shapes` | The four button shapes, one per quadrant | `#0070D1` (brand reference) |
| HDMI Source | `hdmi_connector` | Plug face: square shoulders, cut lower corners, three pin ticks | `#00D4FF` (input family) |

All four are `style: core_monoline` constructions: open linework, no vendor
silhouette, validated by the same contract as every reviewed brand.

### 3.1 HDMI maps Projectivy's source activities — both paths

Projectivy Launcher exposes per-input shortcut activities, and their names
moved in the 4.0.1 rewrite. The developer thread documents both:

- legacy: `com.spocky.projengmenu/.activities.input.SourceHDMI1Activity`
  (confirmed dead on 4.0.1+)
- current: `com.spocky.projengmenu/.ui.guidedActions.activities.input.SourceHDMI1Activity`
  (developer-confirmed replacement, with the caveat that it may move again)

HDMI Source therefore maps **SourceHDMI1–4 on both activity paths** (eight
components, sixteen appfilter entries once both name forms expand). Whichever
path a device's Projectivy build ships, the HDMI tiles auto-theme. If the
activities move again, add the new path rather than replacing — the mapping
is per-device-harmless when an activity is absent.

### 3.2 Consoles map companion packages for manual theming

There is no Android component for "the Xbox on HDMI 2", so the console marks
map the companion packages that actually appear on TV devices; users assign
the mark manually in Projectivy's icon picker (or use it for shortcuts):

| Console | Mapped companion components |
|---|---|
| Xbox | `com.microsoft.xboxone.gamepass/.MainActivity` (Game Pass / cloud gaming) |
| PlayStation | `com.playstation.remoteplay/.MainActivity`, `com.scee.psxandroid/.MainActivity` (PS App) |
| Nintendo Switch | `com.nintendo.znba/.MainActivity` (Nintendo Switch Online) |

Companion activities are device-build dependent, so every one is mirrored in
`unverified` until seen on hardware — the same honesty the catalog applies to
the reference-pack inheritances.

## 4. Stremize

Stremize (debrid + playlist all-in-one, TV-first, Downloader code 8300665)
ships as `com.stremize.player` with separate mobile/TV builds under the same
package. Mapped on `/.MainActivity` plus the fully-qualified twin, mirrored
in `unverified`; glyph `stremize_z` is the Z the product names itself with,
drawn as three rounded strokes. Colour: no published brand colour found —
`#6C5CE7` from the core-builds palette.

## 5. Binge / Kayo platform components, Wolf lms

Streamotion's Foxtel apps share a platform main activity in the
`au.com.foxsports.martian.tv` namespace — already proven on
`au.com.kayosports.tv`. The same activity is now mapped on the rest of the
family:

- Binge: `au.com.binge.tv` and `au.com.streamotion.ares` (both new)
- Kayo: `au.com.streamotion.hyperion` (new; `au.com.kayosports.tv` kept)

Launcher Manager gains the `com.wolf.lms` build alongside the mapped
`com.wolf.lm`: both the shared LM-family activity namespace
(`com.wolf.lm.main.MainActivity`) and its own `/.MainActivity`, so whichever
form the build declares, the wrench tile applies. All four additions are
mirrored in `unverified`.

## 6. The arithmetic

| Measure | v1.8.16 | v1.8.17 | Delta |
|---|---:|---:|---|
| Icons | 926 | **931** | +5 (Stremize, Xbox, Nintendo Switch, PlayStation, HDMI Source) |
| Catalog components | 1101 | **1120** | +19 |
| Classic appfilter entries | 1664 | **1696** | +32 (both name forms) |
| Pop / Pixel Neon components | 1101 | **1120** | lockstepped with the catalog |

Receipts: Classic `Validated 931 icons · 1696 components · 25232 checks run`
· Pop `Validated 931 icons · 1120 components · 16 swatches · 14410 checks
run` · Pixel Neon `Validated 931 icons · 1120 components · 12721 checks run`
(its census counts every component spelling; the mapped set is identical).

## Sources

- Projectivy Launcher XDA thread — HDMI shortcut activity names, old and
  4.0.1+ paths (spocky12, developer):
  https://xdaforums.com/t/app-android-tv-projectivy-launcher.4436549/
- Stremize package name and TV/mobile split:
  https://play.google.com/store/apps/details?id=com.stremize.player ·
  https://stremize.en.uptodown.com/android
- Launcher Manager family (`com.wolf.lm`, `com.wolf.google.lm`, FireOS
  builds): https://xdaforums.com/t/app-firetv-noroot-launcher-manager-change-launcher-without-root.4176349/
- Canonical console accents: Xbox `#107C10`, PlayStation `#0070D1`,
  Nintendo `#E60012` (brand reference, pre-provenance).
