# Icon Pack: where the megabytes go, and what's missing

**Date:** 2026-09-24 · **Against:** v1.9.3 (release APK 25.5 MB) · **Method:**
measured from the source tree, the pipeline, and the published release
metadata. The release-asset CDN refused the sandbox twice (EOF), so the
in-APK split below is estimated from source sizes against the known 25.5 MB
total rather than from an `aapt dump` — the dominant term is unambiguous
either way.

## Part 1 — the size diet

### Where the 25.5 MB goes

| Contents | Size | Share |
|---|---:|---:|
| `drawable-nodpi`: 973 glyph PNGs (512²) + 962 banner PNGs (320×180) + fallback furniture | 25.2 MB raw | ~92% |
| DEX (appcompat + Kotlin stdlib + recyclerview + core-ktx, **unshrunk**) | ~1–2 MB in-APK | ~6% |
| `xml/` (appfilter 230K + drawable.xml 74K + iconpack.xml 33K) | ~0.3 MB | ~1% |
| Wallpaper thumbs (96 × ~10K) + manifests, layouts, values, arsc | ~1.5 MB | ~5% |

Drawables are the APK. Everything else is rounding. Ranked levers, none of
which touches a visible pixel:

### 1. PNG → lossless WebP: ~25.5 MB → ~10 MB (the one big win)

Measured on a random 40-file sample: 438 KB of PNG → **166 KB lossless
WebP, 62.1% saved**, pixel-identical. (Google's own guidance cites ~30%
average [3]; monoline art on transparency does far better than average.)
Projected across 25.2 MB of drawables: **~15 MB saved**.

Why it's safe:

- Lossless + transparent WebP is guaranteed since Android 4.2.1 [3];
  minSdk is 21.
- Resource lookup is extension-agnostic: `getIdentifier("tenplay",
  "drawable", …)` and every `drawable="…"` string in appfilter/drawable.xml
  resolve identically. No Kotlin changes.
- Launcher compatibility is proven in the wild: shipping packs serve
  `.webp` drawables through appfilter to 20 launchers **including
  Projectivy** [1].
- One change covers both APKs: the Banners companion copies its art from
  `:app`'s drawable-nodpi at build time.

Migration surface, all mechanical: the generator emits WebP (Pillow is
already pinned — resvg renders, one `im.save(..., "WEBP", lossless=True)`
step), and the `.png` references in `build_icons.py`, `validate.py`, and
~6 test files move over. Verify on the `candidate` build against
Projectivy + one phone launcher before release.

Two honest caveats: WebP costs slightly more CPU to decode [3] — the grid
already caches resource IDs but decodes per bind, so watch scroll on the
weakest stick; and the pack's own mipmap launcher icon stays PNG (Google
explicitly excludes launcher icons [3]).

### 2. R8 + shrinkResources with a generated keep.xml: ~1–2 MB

`isMinifyEnabled = false` / `isShrinkResources = false` ships all of
appcompat, the stdlib, and every locale. The build.gradle comment is
half-right: appfilter's `drawable="…"` attributes are plain strings, so
the shrinker genuinely can't see them — but that's exactly what `keep.xml`
is for, and it can be **generated from catalog.json + appfilter** by the
existing pipeline, so it can never drift. Then:

- `isMinifyEnabled = true` shrinks the DEX (~30–50% of it is unused
  appcompat/stdlib).
- `isShrinkResources = true` + `resConfigs("en")` strips appcompat's
  ~70 translated locales from an English-only app (~0.5–1 MB alone).

Risk is near-zero with generated keeps; the `candidate` build type exists
for exactly this verification.

### 3. Alias the 43 byte-identical duplicate PNGs: 883 KB, zero risk

38 groups of pixel-identical files ship under different names
(`bbc_iplayer`/`bbciplayer`/`iplayer`, `adguard`/`adguard_2`, …) — aapt
does not dedupe. A generated `values/aliases.xml` with
`<drawable name="adguard_2">@drawable/adguard</drawable>` entries keeps
every appfilter name resolving while shipping one bitmap. Side benefit:
it surfaces a design question — identical art for different apps is
either a deliberate alias or a uniformity-gate miss.

### 4. VectorDrawables: the radical option (~1 MB of art, but don't yet)

The entire 961-glyph corpus is **820 KB of SVG, paths-only, zero `<text>`
and zero gradients** — monograms are already path-outlined, so conversion
is mechanical. In principle the glyph set could ship as vectors (~1 MB)
instead of PNGs (~20 MB). Not recommended as the plan:

- Some old launchers assume `BitmapDrawable` and would crash on vectors;
  the pack supports back to API 21 launchers.
- Rasterization cost moves to weak TV sticks, paid on every bind.
- The banners' gradient rails need API 24+ vector gradients (or banners
  stay WebP while glyphs go vector — a mixed pipeline).

Worth a `candidate`-build experiment after WebP lands, not before.

### 5. Not worth it

- **Downscaling 512px glyphs** — correctly sized for 4K TV cards; this
  *would* sacrifice quality. Don't.
- **appfilter/drawable.xml** (344K) — required by launchers, deflate well
  inside the APK.
- **Wallpaper thumbs** (0.96 MB, ~10K each) — already tiny, and the grid
  needs them offline.
- **Dropping appcompat** (~1 MB) — a theme/activity rewrite to save what
  WebP saves fifteen times over. Bad trade.
- **cb_banner duplication** (nodpi 640×360 + xhdpi 320×180, 38K total) —
  real but trivial; fold into the WebP pass.
- **`noCompress png`** — re-allowing zip deflate on PNGs would save ~1–2%
  and contradicts the pipeline comment; revisit only if WebP is rejected.

### Projected stack

WebP + R8/shrink + aliases + `resConfigs("en")` → **~8–9 MB APK, zero
pixels changed.** The release notes practically write themselves, and
Downloader installs on slow hotel Wi-Fi get three times faster.

## Part 2 — what's missing

The app is genuinely full-featured: 11 activities, search/filter/category
grid, one-click apply, update checker, 96-wallpaper browser with
multi-export and Monet handoff, on-device component inspector, QR auditor
with bot filing, FAQ, changelog, suite hub, accessibility labels, and
thorough launcher intent filters (Projectivy, Nova, ADW incl. pick-icon,
Lawnchair, Apex, GO…). Verified non-gaps — don't build these twice.

The genuine gaps, ranked:

1. **Coverage, forever.** Four open icon requests (#155–#158: TVLok,
   Gridstreamr, Launch on Boot, My File) plus standing issue #72
   (unverified components). Demand research puts this pack already
   best-in-class for TV (961 icons vs the 800+ reference pack) — the gap
   is a treadmill, not a hole.
2. **Wallpaper favorites.** 96 walls and no way to star one. Smallest
   feature with the most obvious UX return on the list.
3. **Dynamic calendar + clock.** Zero support (`calendar_1…31` drawables,
   dynamic-clock layers per the Lawnchair spec [2]). Cheap to generate,
   but honest scoping: it's a phone-launcher feature — Projectivy doesn't
   do dynamic icons, so this only pays if phone users matter.
4. **Themed-icon support.** The pack's own launcher icon has no
   `<monochrome>` layer (Android 13+ themed icons), and there's no
   Lawnchair `grayscale_icon_map` [2]. Cheap; same phone-only scoping as 3.
5. **Housekeeping: close #111.** The Pixel Neon sprite-seed bug is moot
   now that Neon is retired.
6. **Style uniformity (noted, not relitigated).** The retired-Pop research
   measured 169 colors and 65% monograms; today it's **198 colors and 57%
   monograms** — drifting the right way on shapes, the wrong way on
   palette. With one pack kept, this is its accepted character; any
   palette-tightening would be a slow burn, not a project.

### Sources

[1] Blackshield icon pack — `.webp` drawables via appfilter, 20 launchers
incl. Projectivy: https://github.com/synthalorian/blackshield-icon-pack
[2] Lawnchair icon-pack support (dynamic calendar/clock, themed icons):
https://github.com/LawnchairLauncher/lawnchair/wiki/Icon-pack-support
[3] Google #SmallerAPK part 6 (Zopfli & WebP):
https://medium.com/androiddevelopers/smallerapk-part-6-image-optimization-zopfli-webp-4c462955647d
