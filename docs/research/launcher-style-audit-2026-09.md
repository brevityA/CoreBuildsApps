# Launcher style audit — Monet, Projectivy 4.70, AT4K

Date: 2026-09-14 · Status: research only, nothing implemented
Question (owner): *"Deeply research what we might be missing. Look at launchers
like Monet or Projectivy to see what styles they use or if we are missing anything."*
Companion doc: `docs/research/community-input-icons-2026-09.md` (app coverage +
Projectivy input cards). This doc covers **style surfaces** — the shapes, tiles,
glow, tinting, animation and wallpaper machinery the launchers put *around* our
icons, and where our pack meets or misses it.

## 1. TL;DR

The ecosystem has converged on exactly the style we already ship — transparent,
single-accent marks on launcher-drawn tiles — and **Monet was already tuned for**
(the `presence.py` night-keyline + accent-bloom pass, sized for Monet's ~100px
dock tile, shipped in v1.8.18). What we are genuinely missing:

1. **G1 — APNG glow set.** Projectivy 4.70 supports animated **apng icons** and a
   customisable **card glow** system; the community already trades glowing APNGs,
   but only as Drive PNGs — **no icon pack ships them**. First-mover white space;
   high asset cost. Decision item.
2. **G2 — circle-safe gate.** Monet offers **7 icon shapes**; a pixel audit of all
   933 square marks shows **122 (13%)** spill past a 512px circle mask (worst
   +26px, e.g. watchcorridor, mubi, justwatch). A validate gate + a shrink-or-
   document decision.
3. **G3 — Monet's input cards.** Monet also surfaces **HDMI inputs and Live TV
   channels from the home screen**, but it is closed-source: its internal
   components cannot be guessed (no public namespace), so it goes on the ADB-scan
   checklist rather than the catalog.
4. **G4 — tint modes.** Projectivy can **tint pack icons with a custom colour**;
   Monet has a **themed mode** that recolors icons to the wallpaper palette.
   Our single-accent marks survive this; the gradient icons (Nuvio) are the risk
   edge. Note, not work.
5. **G5 — wallpaper series for dynamic-colour launchers.** Monet (palette
   extraction, movie-poster mode, ambient scenes) and Projectivy 4.70 (wallpaper
   tinting, solid colour, CardFocused provider events) both *compute colour from
   the wallpaper*. A research-backed Series 9 — gradient sets with dominant
   cyan/violet for clean extraction — fits the standing wallpaper series rule.

## 2. The launchers

### Monet Launcher (com.klevico.monet, Klevico)

First Android TV launcher with real **Material You** theming; 50k+ installs,
~40k downloads in 30 days, Play May 2026 → 1.0.80 (2026-08/09). The fastest-growing
third-party TV launcher right now.

Style surfaces (Play listing + r/AndroidTV release threads, dev active in-thread):

- **Icons**: icon-pack support (premium); **7 icon shapes**; **4 tile styles**
  ("themed, transparent and more"); adjustable tile size; per-app overrides
  (custom image/colour/name). "Themed" mode = launcher recolors the icon to the
  wallpaper-derived palette. Pack consumption follows the standard ADW appfilter
  convention (same convention the Projectivy dev points users to for Arcticons/
  Whicons); we emit appfilter to both `res/xml` and `assets`, so we are compatible
  by construction. Monet's exact 7 shapes and pack-parsing details are not
  publicly documented — verify on-device.
- **Dynamic colour**: accent palette pulled from the wallpaper in real time; 22
  palettes + 96-shade picker; light & dark mode; custom text colours.
- **Tiles**: 4 styles incl. transparent — tiles are launcher-drawn; the icon is
  the mark inside. This is precisely the surface our presence pass targets.
- **Inputs**: **Live TV channels and HDMI inputs on the home screen** (on TVs
  with tuners/TV-input apps) → internal input cards, components unknown (closed
  source).
- **Wallpapers**: own photos/videos, Reddit subreddits, Aerial Views, **movie
  poster mode** (rotating artwork from installed streaming apps), blur/brightness
  controls, 26+ ambient scenes (rain, aurora, fireside, deep space, aquarium,
  lava lamp…) usable as screensaver.
- **Motion**: 9 launch animations, 7 focus animations (launcher-side).
- **Layout**: 8 rows, dock mode (favorites pinned bottom), folders, hidden apps,
  4 profiles, PIN lock, widgets (weather/clock/Now Playing/status bar).

### Projectivy Launcher 4.70 (com.spocky.projengmenu, spocky)

The reference community; 4.70-beta (2026-04) is the current development line.

Style surfaces (official release notes, `github.com/spocky/miproja1/releases`):

- **Cards**: 16:9 banner cards, corner radius, background colour/opacity (0 =
  borderless), "show icon in front of title", grid mode, dock = grid-category
  background, ripple toggle, per-card and global margins.
- **Glow (4.70)**: more glow animations; **customisable glow size** ("small glow
  length will show as a simple outline"); **global glow colour**; focused-card
  tint/background override incl. swap-icon-and-background. The community
  "transparent icons to glow" recipe (card opacity 0) is a first-class look now.
- **Animated icons**: improved **gif/apng/webp** support as custom icons
  (fix #322); crash fix for corrupted APNG (fix #459 in a prior release); cards
  reset to frame 1 on stop. The APNG glow culture is real: miityharu's shared set
  has a dedicated APNG folder of glowing icons.
- **Tinting**: icon-pack icons can be **tinted with a custom colour** (dev:
  "choose from a local image or an icon pack… but also tint the icons").
- **Dynamic colour**: Material 3 **Expressive** theming; dynamic colors
  "à la Material You"; font theming incl. Google Fonts (premium).
- **Wallpapers**: tinting/colorize, solid colour, Now-Playing-image provider,
  theme (.pltheme) provider, Reddit provider, **CardFocused events carry card
  title + package to wallpaper plugins** — wallpapers can react to the focused
  card; improved video palette extraction.
- **Inputs**: HDMI 1/2/3 + AV shortcuts (activity namespace volatile — see
  companion doc §4.5), "auto start directly to any external input or app",
  engineering-menu access, calibration patterns.

### AT4K Launcher (com.overdevs.at4k, Majdooor/overdevs)

- v1.2 (2026-08): **bundles its own free transparent-icon theme** ("icon pack →
  transparent icons"), 20+ customisation options, custom app icons (premium),
  blur dock, all-apps button, watch-next previews, Shield-community favourite.
- **No third-party icon-pack consumption surfaced** — its icon story is the
  bundled transparent set + per-app overrides. Nothing for us to map; noted
  because the *bundled transparent default* is further ecosystem confirmation
  that borderless marks are the baseline expectation, not a style option.

### Mentioned, not audited

ATV Launcher (atvlauncher.trekgonewild.de, Apple-TV-style, German community) and
the "Apple TV-style icon pack" (full-bleed card artwork) represent the
*full-card-artwork* philosophy: the icon IS the card. That is a different product
(one bespoke artwork per app) and is outside our "glyphs, never wordmarks; no
vendor art" identity — explicitly not a gap to chase.

## 3. Where we already line up (no work)

- **Transparent marks on launcher tiles** — our core identity; presence pass
  (night keyline #070B12 @0.88, accent bloom @0.36) exists *specifically* so the
  marks hold on Monet's transparent tiles and against photography, and is "sized
  for a ~100px Monet dock tile" (`tools/presence.py`). Both light and dark tile
  modes are covered by the same keyline (that is why it is dark ink, not card
  colour).
- **Banner geometry** — measured from the reference pack (320×180, 78%×43% ink,
  dead-centre, ~12% coverage); `tools/build_banners.py` enforces it.
- **Standard appfilter** — both name forms, emitted to `res/xml` *and* `assets/`
  (older pickers), per `tools/build_icons.py`.
- **Projectivy input cards** — covered in the companion doc (P0/P1 scope).
- **Wallpapers** — bundled series + manifest sync; the *interplay* gap is G5.

## 4. Gap detail

### G1 — APNG glow set (white space, decision item)

Facts: Projectivy 4.70 renders apng app icons with stable lifecycle behaviour
(reset to frame 1 on stop, idle-stop, corruption-safe); the 4.70 glow system
makes focused cards glow in a user-chosen colour/size; the community already
wants animated glows (dedicated APNG folder in the shared set). **No icon pack
ships APNG marks** — the demand is met ad hoc by individual artists on Drive.
A Core Builds APNG variant (same marks, 2–3 frame presence-bloom pulse) would be
the first pack-native glowing set, and our deterministic pipeline makes a
frame-generating step tractable. Cost: new asset class (APNG ~933 files, each a
few KB–tens of KB — APK size impact must be measured), a builder, validate
extensions (frame count/size caps), and on-device verification (APNG decoding
varies across boxes; the crash-fix history shows it is not trivial on all
hardware). **Recommendation: scope as a v1.9-class feature, gated on a 20-icon
pilot measured on the owner's device before committing to the full set.**

### G2 — circle-safe gate (cheap, measurable)

Monet's 7 shapes include circle. Pixel audit of all 933 square marks (alpha > 8,
radius from centre, 512 grid, mask radius 256): **811 safe; 122 spill**, worst
+26px (watchcorridortv, mubi, dropsync, justwatch, sendfilestotv, synology
photos family…). At typical tile size the clip is ~10–13px of a 100px tile —
visible on corner strokes, not catastrophic. Two-part response:
(a) **gate**: `validate.py` (or the receipt) computes max-ink-radius per square
PNG and fails on >256, so no future mark regresses; (b) **decision**: shrink the
122 wide marks to fit (touches diversity test inputs if glyph bounding changes)
or document "recommended shape: rounded square" for that 13%. My read: gate now,
shrink only the top ~10 offenders if the owner runs Monet with circle shape.

### G3 — Monet internal input cards (scan-gated, not guessable)

Monet's home-screen HDMI/Live-TV surfaces are launcher-internal components with
no public namespace (closed source, no manifest in any public repo). Unlike
Projectivy — where the activity names are known from the dev's own XDA answers —
there is nothing to map by inference, and guessing components would burn
unverified ratchet budget on strings that may never exist. Action: add "Monet
home-screen inputs (HDMI/Live TV)" to the `docs/ADB_SCANNING.md` checklist; map
whenever a Monet device scan yields the components.

### G4 — tint modes (note)

Projectivy custom-colour tint and Monet themed (wallpaper-palette) mode both
recolour pack icons. Our marks are single-accent line art on transparent —
uniform recolouring preserves legibility by construction (ink + one accent, no
multi-hue art). Edge: the **gradient** icons (Nuvio cyan→violet, and any future
gradients) may tint unpredictably (gradient + colour filter = hue smear). If the
owner sees this in practice, the fix is per-icon "tint-safe" fallbacks (flat
accent version), not a rework. No action now.

### G5 — wallpaper series 9 candidate (research-backed, per standing rule)

Both major launchers derive interface colour from the wallpaper (Monet: real-time
palette extraction + 22 palettes; Projectivy: video palette extraction +
tinting). A wallpaper that extracts cleanly to cyan/violet will make the whole
Monet/Projectivy UI harmonise with our accent system. Candidate Series 9:
"dynamic-colour gradient" set — dark, 16:9 4K, dominant hue in the
`#2FCCE6→#A238F0` family with a clear single extraction centroid, designed so
Monet's palette picker lands on our accent. This satisfies the standing
"wallpaper series must be research-backed" rule with this audit as the backing.
Also note Projectivy's **CardFocused provider events**: wallpapers can react to
the focused card — a wallpaper *plugin* is out of scope for an icon pack,
recorded only so it is not mistaken for a pack surface.

## 5. Launcher × surface matrix

| Surface | Monet | Projectivy 4.70 | AT4K | Us today | Gap |
|---|---|---|---|---|---|
| Pack consumption | appfilter (standard) | appfilter (standard) | none (bundled set) | appfilter ×2 locations | — |
| Icon shapes | 7 (incl. circle) | 16:9 card | launcher theme | square 512 + banner 320×180 | **G2** |
| Tiles | 4 styles, transparent default culture | card bg/opacity/corner | bundled transparent | presence pass (Monet-tuned) | — |
| Glow | 7 focus animations (launcher) | card glow system + APNG icons | — | presence bloom (static) | **G1** |
| Tinting | themed mode (wallpaper palette) | custom-colour tint | colour options | single-accent marks (safe); gradients edge | **G4** (note) |
| Inputs on home | HDMI + Live TV (unknown components) | HDMI 1–4 + AV (known) | — | Projectivy set in companion doc | **G3** + companion P1 |
| Wallpaper interplay | palette extraction, poster mode, ambient scenes | tinting, providers, CardFocused | video bg, subreddits | bundled series + manifest | **G5** |
| Animation | launcher-side | APNG/GIF/WEBP icons | — | static only | **G1** |

## 6. Prioritised recommendations

| # | Item | Art cost | Process cost | Gate |
|---|---|---|---|---|
| 1 | G2 circle-safe gate in validate/receipt | 0 | small | ship immediately (gate only) |
| 2 | G3 Monet inputs → ADB checklist line | 0 | 1 doc line | ship immediately |
| 3 | G4 tint note → README/Brand Guide line | 0 | 1 doc line | ship immediately |
| 4 | G2b shrink top ~10 wide marks | 10 glyph tweaks | diversity re-check | only if owner uses Monet circle |
| 5 | G5 wallpaper Series 9 (dynamic-colour gradients) | 7–9 images | sync_wallpaper_manifest | research-backed ✓ (this doc); owner sign-off |
| 6 | G1 APNG glow set | new asset class | new builder + validate + pilot | 20-icon device pilot first; v1.9-class |

Items 1–3 are one small docs/validate pass. 4–6 are owner decisions.

## 7. Open questions (need a device or the owner)

1. Monet's exact 7 shapes and which is default — confirms the circle risk class.
2. Monet's home-screen HDMI/Live-TV component names (ADB scan).
3. Monet themed-mode tint behaviour on gradient icons (Nuvio).
4. APNG decode stability on the owner's box(es) before any G1 commitment.
5. Does the owner want the 122-mark shrink batch (G2b) regardless of launcher?

Sources: Play Store listings (com.klevico.monet, com.overdevs.at4k,
com.spocky.projengmenu); `github.com/spocky/miproja1/releases` (4.70 notes);
r/AndroidTV Monet 1.0.57 release thread; r/Projectivy_Launcher "Getting the
transparent icons to glow" + "Borderless Transparent Icons" (APNG folder);
r/AT4K v1.2 release thread; r/ShieldAndroidTV AT4K threads; local:
`tools/presence.py`, `tools/build_banners.py`, `tools/build_icons.py`,
pixel audit of `app/src/main/res/drawable-nodpi/*.png` (933 squares).
