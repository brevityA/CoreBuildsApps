# Core Builds — Wallpaper Collection v5.1

102 wallpapers · 3840×2160 (series 1–3, 7–10) and 1376×768 (series 6) · 128 MB ·
Core Builds palette:
Core Cyan #00e5ff · Signal #00d4ff · Glow #7eeeff · Build Blue #4facfe ·
Dusk Violet #8a4890 · Ember #c03a20 · Night #0d1117 · Void #04070f.

`manifest.json` indexes every wallpaper with a raw GitHub `url`, a bundled
`thumb` URL, its `series`, and `resolution`. Both the repo's README and the
in-app browser read this same file — there is one source of truth.

`manifest.json` is the Core Builds Icon Pack's collection. Rationale
and the wallpaper research behind earlier series is in
[`docs/research/wallpaper-directions.md`](../docs/research/wallpaper-directions.md).

## Series

| Series | # | Theme |
|---|---|---|
| `series-0-originals` | (not in manifest) | The original 20 JPGs, kept for history |
| `series-1-fieldwork` | 01–24 | Mesh gradients, aurora, light trails, topo, deepfield |
| `series-2-motion` | 25–32 | Long-exposure kinetics: orbitals, warp, fogbanks, spiral, slipstream |
| `series-3-horizons` | 33–40 | One horizon, eight meanings — the §03 semantic accent slots as landscapes |
| `series-5-pop` | (retired with Pop) | Was Core Builds Pop's 12-wall set, indexed by `pop-manifest.json`. Removed with the pack in 2026-09; the number stays empty so history keeps its meaning. |
| `series-6-circuit-core` | 41–50, 79–80 | **Circuit Core.** Twelve lit-circuit fields on near-black — the same §06 lighting language the retired Core Mark series used (cyan first, violet/ember ambient), authored at 1376×768 rather than 4K. Replaced `series-4-core-mark` in v1.8.6; `tools/build_circuit_wallpaper_extensions.py` reproduces the final pair. |
| `series-7-retrowave` | 59–68, 81–82 | **Retrowave.** Twelve 4K walls in 2026's nostalgic retro-gradient genre: sliced gradient suns, perspective grids, chrome ridges and starfields on the night ground, per `tools/build_synthwave_wallpapers.py`. |
| `series-8-amoled` | 69–78, 83–84 | **AMOLED.** Twelve minimalist 4K walls on exact `#000000`: sparse cyan, violet, and ember geometry with 92.4–99.8% true-black coverage. `tools/build_amoled_wallpapers.py` asserts the ≥50% contract and writes the [contact sheet](../docs/amoled-wallpapers.png). |
| `series-9-deep-space` | 85–96 | **Deep Space.** Twelve 4K space walls in the house palette — event horizon, nebulae, ringed planet, spiral galaxy, comets, novae — all procedural and seeded (`tools/build_deep_space_wallpapers.py`, [contact sheet](../docs/deep-space-wallpapers.png)). Smooth gradients ship grain-free like series 7/8: the full-frame dither cost ~9 MB per PNG for a texture invisible at TV distance. |
| `series-10-cinema` | 97–102 | **Cinema.** Six 4K night-time cinema scenes in neon and marquee light — marquee frontage, velvet stage, projector beam, neon lounge, late-night rentals, box office. Each is drawn once as layers and animated by weighting them, so every wall has a seamless 60 fps loop that is the same drawing (`tools/build_cinema_wallpapers.py`, [contact sheet](../docs/cinema-wallpapers.png)). |

Retired: `series-4-core-mark` (41–70, 30 × 4K PNGs of the lit hex + faceted core
diamond) shipped in v1.7.0 and was removed in v1.8.6. Series index 4 stays empty
and 5 belonged to Pop (retired 2026-09); subsequent additions continue at
series 7 and beyond, currently through series 10.

## In-app browser

The icon-pack app ships a built-in Wallpapers screen:

- **Thumbs are bundled** (`app/src/main/assets/wallpapers_thumbs/`, about 1 MB for
  all 102) so the grid is instant and works offline.
- **Full images download on demand** from the raw URLs below and are cached in
  internal storage (12-file LRU, ~30 MB ceiling) — the APK stays small. Series 1–3
  and 7–8 are 4K PNG, series 6 is 1376×768 JPEG; `resolution` per entry is the truth.
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
4. Run `python tools/sync_wallpaper_manifest.py`. It verifies every manifest
   entry against the files on disk, copies the manifest into
   `app/src/main/assets/manifest/wallpapers.json`, and keeps the bundled
   thumb set exactly the manifest's set — it removes stale thumbs and adds
   missing ones (CI's wallpaper tests enforce that the bundled copies stay
   in sync; `--check` reports drift without writing).
5. Bump `manifest.json` `version` and `count`.
6. Run `python tests/test_wallpapers.py`. It is the gate that keeps the repo
   manifest, the bundled copies, the files on disk, and the claimed resolutions
   the same fact.

Design notes (series 1–3): OLED-friendly (70–95% dark coverage, asymmetric),
calm bottom third for launcher cards, film-grain dither baked in to prevent
gradient banding on TV panels. Do not run lossy/palette PNG optimizers over them.
Series 8 is stricter: its untouched ground must remain exact `#000000`, and every
wall must retain at least 50% true-black pixels.

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
