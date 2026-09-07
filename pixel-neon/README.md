# Core Builds Pixel Neon

The second Core Builds icon pack: the same 924-app coverage, rebuilt as a
bright 8-bit arcade sprite set for Projectivy Launcher and Android TV.

![Pixel Neon preview](docs/preview.png)

## The look

- **32 px arcade sprites** — every square icon is drawn on a small hard-pixel
  canvas and nearest-neighbour scaled to 512×512.
- **Brand-aware silhouettes** — the catalog's semantic glyph cue chooses the
  brand family (tile monogram, play mark, eye, shield, wave, folder, sport
  mark, and more). The 33 researched overrides cover marks such as MUBI's
  seven dots, Netflix's folded ribbon, YouTube's filled play badge, the
  NFL/MLB/NBA plates, and wordmark-led brands. Hash-seeded variations keep
  every app distinct without pixelating the monoline set.
- **Neon bloom** — a compact violet/cyan halo sits behind each sprite without
  painting a background into the transparent icon.
- **Pixel bevel** — one-pixel top-left highlights and a darker bottom-right
  edge keep symbols legible from the sofa.
- **Transparent by default** — the launcher still owns the card colour.
- **16:9 companion banners** — each banner is composed from the sprite and a
  tiny bitmap label in one of three arcade sign layouts, rather than reusing
  the original rail-and-wordmark banner.

Pixel Neon also includes the shared Core Builds wallpaper experience: 70
wallpaper entries and lightweight thumbnails are bundled for immediate offline
browsing. Open a tile to preview it full-screen; the 4K source downloads and
caches only when needed, so the APK stays compact. Set it directly when the
platform supports it, save it to `Pictures/CoreBuilds` for launcher rotation,
or multi-select and export a collection with progress, retry, and cancellation.

## Install

Download `pixel-neon-release.apk` from the `pixel-neon` release, sideload it,
then open **Core Builds Pixel Neon Icon Pack**. Press the apply button for a
supported launcher, or choose it manually in:

**Projectivy Launcher → Settings → Appearance → Cards → Icon Pack → Core Builds
Pixel Neon Icon Pack**

The two packs have different package IDs and can be installed side by side:

| Pack | Package | Style |
| --- | --- | --- |
| Core Builds Icon Pack | `tv.corebuilds.iconpack` | clean monoline glow |
| Core Builds Pixel Neon | `tv.corebuilds.pixelneon` | 8-bit neon arcade |

## Regenerating the art

`tools/catalog.json` remains the only source of truth for names, components,
colours, and semantic brand glyph cues. The alternate renderer does not copy or
fork the catalog, and it never imports the monoline SVG paths.
From the repository root, build the alternate art directly from the catalog:

```bash
pip install -r tools/requirements.txt
python tools/build_pixel_neon.py
python tools/validate_pixel_neon.py
```

Generated output is under `pixel-neon/app/src/main/res/`, with the compact
review sheet at `pixel-neon/docs/preview.png`. The build also mirrors the
canonical wallpaper manifest and 70 lightweight JPEG thumbs into
`app/src/main/assets/`; full-resolution wallpaper PNGs remain remote and are
fetched on demand. `pixel-neon/docs/build-receipt.json` records the catalog,
appfilter, unique-sprite, and wallpaper counts for the build. The source art
direction is documented in [`DESIGN.md`](DESIGN.md).

## Build the APK

From this directory, with JDK 17 and the Android SDK installed:

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

The release build uses the same `KEYSTORE_*` environment variables as the
original pack. Resource shrinking is disabled because launcher mappings resolve
icons by drawable name at runtime.
