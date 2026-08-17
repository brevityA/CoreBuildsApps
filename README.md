<div align="center">

<img src="docs/banner.png" alt="Core Builds Icon Pack" width="420">

# Core Builds Icon Pack

**Transparent app icons for Projectivy Launcher on Android TV.**
Precision-made. Verified live. Nothing hides.

`40 icons` · `78 mapped components` · `v1.0.0`

</div>

---

## 🔷 About

The **Core Builds Icon Pack** is designed for the [Projectivy Launcher](https://play.google.com/store/apps/details?id=com.spocky.projengmenu) on Android TV and Google TV, built to the [Core Builds Brand & Style Guide v1.0](https://github.com/brevityA/Core-Builds).

Icons are **original geometry** drawn on a shared 512 grid — simple shapes, rounded ends, one accent colour per app — not traced vendor logos. Backgrounds are **fully transparent**, so the launcher's own card colour shows through.

> **Tip:** Use a **dark card background** in Projectivy. These icons are drawn for night chrome (`#0d1117`).

> **Note:** Designed and mapped against Android TV / Google TV builds of each app. Devices running mobile variants sometimes expose a different launcher activity — if an icon doesn't auto-assign, [open an issue](../../issues) with the component name and it gets added.

---

## 🔷 Install

1. Download the APK from [**Releases**](../../releases/latest).
2. Sideload it (Downloader, `adb install`, or a file manager).
3. Apply it in Projectivy:
   - **Projectivy Launcher Settings** → **Appearance** → **Cards** → **Icon Pack** → **Core Builds Icon Pack**

The app's own **Open Projectivy settings** button takes you there. It never changes a setting for you — the launcher owns that choice.

---

## 🔷 What's covered

The starter set targets the Core Builds ecosystem first: Stremio, Kodi, Jellyfin, Emby, Plex, Nuvio TV, Syncler, Weyd, Trakt, TorBox, Real-Debrid, AllDebrid, Premiumize, Downloader, VLC, MX Player, Just Player, Kore, SmartTube — plus mainstream and AU free-to-air apps (Netflix, Prime Video, Disney+, Max, Apple TV, Stan, Binge, Kayo, ABC iview, 9Now, 7plus, 10 Play, SBS).

Full table with every mapped component: [**docs/IconPackList.md**](docs/IconPackList.md)

<div align="center"><img src="docs/preview.png" alt="All icons" width="760"></div>

---

## 🔷 Branding assets

The pack's own identity — Leanback banner, launcher icon at true sizes, adaptive-icon masks, and a home-row mock.

<div align="center"><img src="docs/brand-preview.png" alt="Branding assets" width="820"></div>

| Asset | Path | Size |
| --- | --- | --- |
| Leanback banner | `res/drawable-nodpi/cb_banner.png` | 320×180 dp (640×360 px) |
| Launcher icon | `res/mipmap-{xhdpi,xxhdpi}/ic_launcher.png` | 96 / 144 px |
| Adaptive foreground | `res/mipmap-{xhdpi,xxhdpi}/ic_launcher_foreground.png` | 216 / 324 px |
| Adaptive icon | `res/mipmap-anydpi-v26/ic_launcher.xml` | mark on `#0d1117` |

Regenerate with `python tools/build_branding.py && python tools/build_brand_preview.py`.

---

## 🔷 Adding an icon

Everything generates from one file — `tools/catalog.json`. You never hand-edit XML.

```jsonc
{
  "name": "Example TV",
  "drawable": "example_tv",          // a-z0-9_ , unique
  "color": "#00D4FF",                // the app's accent
  "glyph": "play_round",             // from tools/glyphs.py
  "components": ["com.example.tv/.MainActivity"]
}
```

Then regenerate and verify:

```bash
pip install -r tools/requirements.txt
python tools/build_icons.py      # SVGs, PNGs, appfilter, docs, preview
python tools/build_branding.py   # launcher icon + TV banner
python tools/validate.py         # 400+ coherence checks
```

**Finding a component name** for an installed app:

```bash
adb shell cmd package resolve-activity --brief com.example.tv | tail -1
```

Need a new shape? Add a function to `tools/glyphs.py` and register it in `GLYPHS`. Glyphs are plain SVG path strings on the 512 grid — stroke `34`, safe area `432`, rounded caps and joins.

---

## 🔷 Building the APK

CI does it on every push: [`.github/workflows/build.yml`](.github/workflows/build.yml) regenerates assets, **fails if the committed files drift from the catalog**, runs the validator, then builds and uploads the APK. Push a `v*` tag to cut a release.

Locally (needs JDK 17 + Android SDK):

```bash
./gradlew assembleDebug     # unsigned, installable
./gradlew assembleRelease   # signed if keystore env vars are set
```

Release signing reads `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. In CI, store the keystore as the base64 secret `KEYSTORE_BASE64`.

---

## 🔷 Design rules

Inherited from the brand guide, enforced by the generator and validator:

| Rule | Why |
| --- | --- |
| Transparent background, always | the launcher owns the card colour |
| 512 grid · 432 safe area · 34 stroke | survives 10-foot downscaling |
| One accent colour per icon, flat | colour-as-language stays legible |
| Original geometry, never traced logos | ours to ship, ours to license |
| The hex stance is never rotated | the point-up hexagon is load-bearing |
| Palette locked to §03 swatches | new accents need a meaning slot first |
| `isShrinkResources = false` | drawables resolve by name at runtime |

---

## 🔷 Layout

```
tools/catalog.json          the single source of truth
tools/glyphs.py             33 glyph primitives, pure geometry
tools/build_icons.py        catalog → SVG, PNG, appfilter, docs
tools/build_branding.py     launcher icon + Leanback banner
tools/build_brand_preview.py branding preview sheet
tools/validate.py           coherence checks
assets/svg/                 master vectors (40)
app/src/main/res/           the Android module
docs/IconPackList.md        supported apps + components
```

---

## 🔷 Credits

Icon-pack conventions follow the approach proven by [Projectivy Icon Pack](https://github.com/SicMundus86/ProjectivyIconPack) by SicMundus86. Projectivy Launcher is by Spocky. App names and trademarks belong to their respective owners; this pack ships original artwork only.

Part of the [Core Builds](https://github.com/brevityA/Core-Builds) ecosystem · [ko-fi.com/branding_brevity](https://ko-fi.com/branding_brevity)
