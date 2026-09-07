# Pixel Neon Wallpapers

This is Pixel Neon’s separate 70-wallpaper collection. Each source is an
original 8-bit scene rendered from a 240×135 logical canvas and nearest-neighbor
scaled to 3840×2160. The five series pair arcade grids, cyber circuits, space
runs, neon nature, and boss-stage arenas with a cast of original game-like
character renders: runners, knights, mages, pilots, androids, rangers, rogues,
alien scouts, and arena bosses.

- `manifest.json` is the catalog consumed by the Android wallpaper browser.
- `thumbs/` contains the small JPEGs bundled into the APK for offline browsing.
- `series-*/` contains the full PNG sources downloaded and cached on demand.

Regenerate the deterministic artwork from the repository root with:

```bash
python tools/build_pixel_neon_wallpapers.py
```

These sources are not recoloured or copied from `Wallpapers/`, which belongs to
the original monoline pack.
