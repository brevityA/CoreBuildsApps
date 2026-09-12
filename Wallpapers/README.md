# Core Builds — Wallpaper Collection v4.0

50 wallpapers · 3840×2160 (series 1–3) and 1376×768 (series 6) · 125 MB ·
Core Builds palette:
Core Cyan #00e5ff · Signal #00d4ff · Glow #7eeeff · Build Blue #4facfe ·
Dusk Violet #8a4890 · Ember #c03a20 · Night #0d1117 · Void #04070f.

`manifest.json` indexes every wallpaper with a raw GitHub `url`, a bundled
`thumb` URL, its `series`, and `resolution`. Both the repo's README and the
in-app browser read this same file — there is one source of truth.

**Two collections, two manifests.** `manifest.json` is the classic Core Builds
Icon Pack's 50. `pop-manifest.json` is Core Builds Pop's 12, bundled into that
APK at `pop/src/main/assets/manifest/wallpapers.json`. They are kept disjoint
and asserted so in `tests/test_pop.py`: a shipped pack should not have its
advertised contents change because a *different* pack was rebuilt. Rationale
and the wallpaper research behind series 5 is in
[`docs/research/wallpaper-directions.md`](../docs/research/wallpaper-directions.md).

## Series

| Series | # | Theme |
|---|---|---|
| `series-0-originals` | (not in manifest) | The original 20 JPGs, kept for history |
| `series-1-fieldwork` | 01–24 | Mesh gradients, aurora, light trails, topo, deepfield |
| `series-2-motion` | 25–32 | Long-exposure kinetics: orbitals, warp, fogbanks, spiral, slipstream |
| `series-3-horizons` | 33–40 | One horizon, eight meanings — the §03 semantic accent slots as landscapes |
| `series-5-pop` | (separate manifest) | **Core Builds Pop.** 12 walls built from `tools/popart.py`'s own primitives — the 16 Pop swatches, ink `#151019`, cream `#FFF4E0`, one halftone screen. Flat art, so no grain dither is needed and 128-colour PNG is lossless: 12 × 4K in **2.5 MB**. Indexed in `pop-manifest.json`, not `manifest.json`. |
| `series-6-circuit-core` | 41–50 | **Circuit Core.** Ten lit-circuit fields on near-black — the same §06 lighting language the retired Core Mark series used (cyan first, violet/ember ambient), authored at 1376×768 rather than 4K. Replaced `series-4-core-mark` in v1.8.6. |

Retired: `series-4-core-mark` (41–70, 30 × 4K PNGs of the lit hex + faceted core
diamond) shipped in v1.7.0 and was removed in v1.8.6. Series index 4 stays empty
and 5 is left to Pop, so the two collections never share a number — the classic
pack's new series is 6.

## In-app browser

The icon-pack app ships a built-in Wallpapers screen:

- **Thumbs are bundled** (`app/src/main/assets/wallpapers_thumbs/`, 494 KB for
  all 50) so the grid is instant and works offline.
- **Full images download on demand** from the raw URLs below and are cached in
  internal storage (12-file LRU, ~30 MB ceiling) — the APK stays small. Series 1–3
  are 4K PNG, series 6 is 1376×768 JPEG; `resolution` per entry is the truth.
- **Set** writes the system wallpaper via `WallpaperManager` for launchers
  that theme from it. On Fire TV — which blocks third-party wallpaper writes —
  the image is saved to `Pictures/CoreBuilds` and handed to the system
  crop/setter.
- **Send to Monet** replaces Set when Monet Launcher is HOME. Monet does not
  read the system wallpaper at all; the cached file is shared straight into
  Monet's `WallpaperShareActivity`, which copies it into Monet's own background
  library and themes from it. Requires Monet 1.0.72+ and Monet Premium (Monet
  says so itself if not). See `docs/MONET_LAUNCHER.md`.

## Adding a wallpaper

1. Drop `corebuilds-NN-slug.png` into the right `series-N-name/`, at whatever
   size the art actually is. Do not upscale a smaller source to claim 4K —
   `tests/test_wallpapers.py` decodes the file and compares it with the
   `resolution` you write in step 3.
2. Add a 480×270 JPG thumb to `thumbs/` (centre-crop to 16:9 first, keep it well
   under 60 KB — that byte budget is asserted too).
3. Append an entry to `manifest.json`:
   ```json
   {
     "name": "NN Slug Title",
     "series": "series-N-name",
     "url": "https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Wallpapers/series-N-name/corebuilds-NN-slug.png",
     "thumb": "https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Wallpapers/thumbs/corebuilds-NN-slug.jpg",
     "resolution": "3840x2160"
   }
   ```
4. Copy the thumb into `app/src/main/assets/wallpapers_thumbs/` and the manifest
   into `app/src/main/assets/manifest/wallpapers.json` (CI's wallpaper tests
   enforce that the bundled copies stay in sync).
5. Bump `manifest.json` `version` and `count`.
6. Run `python tests/test_wallpapers.py`. It is the gate that keeps the repo
   manifest, the bundled copies, the files on disk, and the claimed resolutions
   the same fact.

Design notes (series 1–3): OLED-friendly (70–95% dark coverage, asymmetric),
calm bottom third for launcher cards, film-grain dither baked in to prevent
gradient banding on TV panels. Do not run lossy/palette PNG optimizers over them.

```
https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Wallpapers/manifest.json
```

## Monet Launcher

In the app: open a wallpaper → **Send to Monet** (single), or select several →
**Export** → **Send N to Monet** (they land in Monet → Settings → Background →
Gallery). Custom backgrounds are a Monet Premium feature.

Manual, Monet 1.0.80+ layout: Settings → Background → Sources → **Choose
folder** → `Pictures/CoreBuilds` (after an Export), then turn on **Wallpaper
rotation** under the same screen. Cyan-dominant by design, so Monet's dynamic
theming pulls Core Cyan as the accent.
