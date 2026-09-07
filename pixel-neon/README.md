# Core Builds Pixel Neon

The second Core Builds icon pack: the same 924-app coverage, rebuilt as a
bright 8-bit arcade sprite set for Projectivy Launcher and Android TV.

![Pixel Neon preview](docs/preview.png)

## The look

- **64 px arcade grid** — every square icon is snapped to hard pixels and
  nearest-neighbour scaled to 512×512.
- **Neon bloom** — a compact violet/cyan halo sits behind each sprite without
  painting a background into the transparent icon.
- **Pixel bevel** — one-pixel top-left highlights and a darker bottom-right
  edge keep symbols legible from the sofa.
- **Transparent by default** — the launcher still owns the card colour.
- **16:9 companion banners** — existing wide-card layouts are pixelated too,
  so cards and square picker icons feel like one set.

The pack is intentionally icon-only for its first release. It does not bundle
Core Builds wallpapers, so it stays a small companion install beside the
original pack.

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
colours, and glyphs. The alternate renderer does not copy or fork the catalog.
Run the normal vector/banner generators first, then build the second pack:

```bash
pip install -r tools/requirements.txt
python tools/build_icons.py
python tools/build_banners.py
python tools/build_pixel_neon.py
python tools/validate_pixel_neon.py
```

Generated output is under `pixel-neon/app/src/main/res/`, with the compact
review sheet at `pixel-neon/docs/preview.png`. `pixel-neon/docs/build-receipt.json`
records the catalog and appfilter counts for the build.

## Build the APK

From this directory, with JDK 17 and the Android SDK installed:

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

The release build uses the same `KEYSTORE_*` environment variables as the
original pack. Resource shrinking is disabled because launcher mappings resolve
icons by drawable name at runtime.
