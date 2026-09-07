# Pixel Neon Wallpapers

This is Pixel Neon’s separate 70-wallpaper collection. Each source is an
original environment-led 8-bit scene rendered from a 320×180 logical canvas and
nearest-neighbor scaled to 3840×2160. The five series use layered depth,
localized neon lighting, reflective surfaces, distant silhouettes, and clear
negative space across arcade cityscapes, cyber districts, space runs, neon
nature, and empty boss-stage architecture. The final hybrid art direction
threads cyber infrastructure through bioluminescent ecology: rain, cables,
water, reeds, pylons, signal vines, and localized mint/cyan light recur across
series without introducing characters.

- `manifest.json` is the catalog consumed by the Android wallpaper browser.
- `thumbs/` contains the small JPEGs bundled into the APK for offline browsing.
- `series-*/` contains the full PNG sources downloaded and cached on demand.

Regenerate the deterministic artwork from the repository root with:

```bash
python tools/build_pixel_neon_wallpapers.py
```

These sources are not recoloured or copied from `Wallpapers/`, which belongs to
the original monoline pack.
