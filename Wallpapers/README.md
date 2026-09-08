# Core Builds — Wallpaper Collection v3.0

70 wallpapers · 3840×2160 · Core Builds palette:
Core Cyan #00e5ff · Signal #00d4ff · Glow #7eeeff · Build Blue #4facfe ·
Dusk Violet #8a4890 · Ember #c03a20 · Night #0d1117 · Void #04070f.

`manifest.json` indexes every wallpaper with a raw GitHub `url`, a bundled
`thumb` URL, its `series`, and `resolution`. Both the repo's README and the
in-app browser read this same file — there is one source of truth.

**Two collections, two manifests.** `manifest.json` is the classic Core Builds
Icon Pack's 70. `pop-manifest.json` is Core Builds Pop's 12, bundled into that
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
| `series-4-core-mark` | 41–70 | **The lit hex + faceted core diamond**, rendered from the Brand Guide v1.0 construction constants: hex outline `#00e5ff→#4facfe` (22/512 stroke), core diamond `#4facfe→#8a4890→#c03a20`, cyan/violet ambient washes. Six compositional groups: Core, Atmosphere, Engine, Skins (Omni/Zenith/Nexus/Minimal/TV), Receipts voice ("279 passed", Preflight ledger, "Mixed — cached first"), and Architectural. |

## In-app browser

The icon-pack app ships a built-in Wallpapers screen:

- **Thumbs are bundled** (`app/src/main/assets/wallpapers_thumbs/`, ~600 KB for
  all 70) so the grid is instant and works offline.
- **Full 4K images download on demand** from the raw URLs below and are cached
  in internal storage (12-file LRU, ~30 MB ceiling) — the APK stays small.
- **Set** writes the system wallpaper via `WallpaperManager`. Monet Launcher
  extracts its Material You accent from it, so Core Cyan carries through to the
  tiles. On Fire TV — which blocks third-party wallpaper writes — the image is
  saved to `Pictures/CoreBuilds` and handed to the system crop/setter.

## Adding a wallpaper

1. Drop `corebuilds-NN-slug.png` (3840×2160) into the right `series-N-name/`.
2. Add a 480×270 JPG thumb to `thumbs/`.
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

Design notes (series 1–3): OLED-friendly (70–95% dark coverage, asymmetric),
calm bottom third for launcher cards, film-grain dither baked in to prevent
gradient banding on TV panels. Do not run lossy/palette PNG optimizers over them.

```
https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Wallpapers/manifest.json
```

## Monet Launcher (manual)

Settings → Appearance → Wallpaper → Custom — sideload the PNGs or use any
file's raw GitHub URL. Cyan-dominant by design, so Monet's dynamic theming
pulls Core Cyan as the accent.
