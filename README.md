<div align="center">

<img src="docs/banner.png" alt="Core Builds Apps" width="420">

# Core Builds Apps

**Three Android TV apps. Same brand, same living-room bar.**

</div>

---

> | App | What it does | Downloader | Release tag |
> |---|---|---|---|
> | **[Icon Pack](#-icon-pack)** | 921 transparent icons + 70 wallpapers for Projectivy Launcher | `5270601` | [`v*`](../../releases) |
> | **[Core Line](#-core-line)** | Sports scores & channel RSS ticker (chyron) | `7375676` | [`coreline-v*`](../../releases) |
> | **[Core Shift](#-core-shift)** | Live wallpaper browser + Projectivy plugin for Monet Launcher | `8829421` | [`shift-v*`](../../releases) |
>
> Each app has its own CI workflow — changes to one never rebuild the others.

---

## 🔷 Icon Pack

**Transparent app icons for Projectivy Launcher on Android TV.**
`921 icons` · `70 wallpapers` · `v1.7.1`

The **Core Builds Icon Pack** is designed for the [Projectivy Launcher](https://play.google.com/store/apps/details?id=com.spocky.projengmenu) on Android TV and Google TV, built to the [Core Builds Brand & Style Guide v1.0](https://github.com/brevityA/Core-Builds).

Icons are **original geometry** drawn on a shared 512 grid — simple shapes, rounded ends, one accent colour per app — not traced vendor logos. Backgrounds are **fully transparent**, so the launcher's own card colour shows through.

> **Tip:** Use a **dark card background** in Projectivy. These icons are drawn for night chrome (`#0d1117`).

> **Note:** Designed and mapped against Android TV / Google TV builds of each app. Devices running mobile variants sometimes expose a different launcher activity — if an icon doesn't auto-assign, [open an issue](../../issues) with the component name and it gets added.

---

### Install

1. Download using the existing **Downloader code `5270601`**, or use the permanent APK URL:

   **https://github.com/brevityA/CoreBuildsApps/releases/download/iconpack/iconpack-release.apk**

   Versioned builds remain available from [**Releases**](../../releases) under `v*` tags. The `iconpack` release is a floating stable target; do not use the repository-wide `latest/download/...` URL as a permanent link because Core Line has separate releases in this repository.
2. Sideload it (Downloader, `adb install`, or a file manager).
3. Open the app and press the **apply** button — it detects your launcher and hands off directly.

   Or apply manually: **Projectivy Launcher Settings** → **Appearance** → **Cards** → **Icon Pack** → **Core Builds Icon Pack**

> **Android 11+:** The APK declares a `<queries>` block so launcher detection works under package-visibility filtering. No `QUERY_ALL_PACKAGES` permission required.

> **Updates:** At launch the app checks `Latestrelease/version.json`. If a newer build exists, a **Download** button pulls the APK from GitHub and opens the system installer.

---

### What's covered

921 icons across 21 categories — streaming, media centres, debrid services, players, launchers, tools, stores, live TV, music, sport, gaming, VPN, browsers, files, and more.

Highlights: Stremio, Kodi, Jellyfin, Emby, Plex, Nuvio TV, Syncler, Weyd, TorBox, Real-Debrid, AllDebrid, Premiumize, Trakt, VLC, MX Player, SmartTube, YouTube, Spotify, Twitch, Downloader, Aurora Store, TiviMate, TV Bro, SYNC, LocalSend, RS File Manager, Sparkle TV, DS file, Ultimate File Manager Pro — plus Netflix, Prime Video, Disney+, Max, Apple TV, Stan, Binge, Kayo, ABC iview, 9Now, 7plus, 10 Play, SBS, and 870+ more.

Full table with every mapped component: [**docs/IconPackList.md**](docs/IconPackList.md)

<div align="center"><img src="docs/preview.png" alt="All icons" width="760"></div>

---

### Wallpapers

70 curated wallpapers — browse in the Wallpapers tab, preview full-screen, Set as device wallpaper or Save to `Pictures/CoreBuilds`. Multi-select export lets you bulk-save to the folder where launchers like Monet auto-rotate. Thumbnails are bundled; full 4K images download on demand from GitHub.

---

### 16:9 Banners

Every icon ships a **320×180 monoline banner** for Projectivy's wide-card layout. One glyph, cyan→violet rail, Outfit Bold wordmark (path-outlined so every machine renders the same) — generated from `tools/build_banners.py`.

Appfilter maps to banners by default; square icons stay opt-in via `drawable.xml`.

---

### Branding assets

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

### Adding an icon

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
python tools/build_banners.py    # 16:9 monoline banners
python tools/build_branding.py   # launcher icon + TV banner
python tools/validate.py         # 22,800+ coherence checks
```

**Finding a component name** for an installed app:

```bash
adb shell dumpsys package com.example.tv | grep -A1 "android.intent.action.MAIN"
```

Need a new shape? Add a function to `tools/glyphs.py` and register it in `GLYPHS`. Glyphs are plain SVG path strings on the 512 grid — stroke `34`, safe area `432`, rounded caps and joins.

---

### Building the APK

CI does it on every push: [`.github/workflows/build.yml`](.github/workflows/build.yml) regenerates assets, **fails if the committed files drift from the catalog**, runs the validator, then builds and uploads the APK. Push a `v*` tag to cut a release — CI verifies the tag is on `main` and its versionName matches, so a mis-placed tag fails before it publishes.

On tag release, CI also syncs `Latestrelease/version.json` so the in-app update checker stays current automatically.

Locally (needs JDK 17 + Android SDK):

```bash
./gradlew assembleDebug     # unsigned, installable
./gradlew assembleRelease   # signed if keystore env vars are set
```

Release signing reads `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. In CI, store the keystore as the base64 secret `KEYSTORE_BASE64`.

---

### Design rules

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

## 🔷 Repo Layout

```
tools/catalog.json           the single source of truth
tools/glyphs.py              glyph primitives, original geometry
tools/typeface.py            Outfit Bold/ExtraBold → SVG paths
tools/fonts/                 Outfit OFL sources for wordmarks + monograms
tools/build_icons.py         catalog → SVG, PNG, appfilter, docs
tools/build_banners.py       catalog → 16:9 monoline banners
tools/build_branding.py      launcher icon + Leanback banner
tools/build_brand_preview.py branding preview sheet
tools/validate.py            coherence checks (22,800+ at 921 icons)
assets/svg/                  master vectors (921)
assets/banners/              16:9 banners (921)
app/src/main/res/            the Android module
Latestrelease/version.json   in-app update manifest
docs/IconPackList.md         supported apps + components
ticker/                      Core Line — sports & channel ticker (see ticker/README.md)
shift/                       Core Shift — live wallpaper browser (see shift/HANDOVER.md)
motion-plugin/               Core Motion — Projectivy wallpaper-provider plugin
motion-shaders/              GLSL fragment shaders (hex plasma, starfield, flow)
Motion/live/                 Motion asset set (MP4 loops, thumbnails, live-feed.json)
```

---

## 🔷 Core Line

**TV-first sports & channel ticker (chyron).** `v1.0.2`

Not a player, not a playlist, not streams — a reader that crawls the listings other channel apps already publish as RSS:

```
LIVE  TOR 3-2 MTL  ·  TSN4  SN 3     ◆     LAL vs BOS  7:00 PM  ·  ESPN
```

- One Android APK for phone, Shield, Google TV, Fire TV (`LAUNCHER` + `LEANBACK_LAUNCHER`)
- Parses messy listing lines (`Team vs team epn, tsn4, sn 3` → ESPN, TSN4, SN 3)
- Public scoreboards (ESPN, NHL, MLB) with a labeled demo-slate fallback
- Same-Wi-Fi QR pairing so a Fire remote never types an RSS URL
- Zero npm dependencies; web version runs with `cd ticker && node server.mjs`

### Install

1. Download using **Downloader code `7375676`**, or grab the APK from the [**stable release**](../../releases/tag/coreline) (tags starting with `coreline-v` have versioned notes).
2. Sideload it (Downloader, `adb install`, or a file manager).
3. Open the app — it loads a demo ticker immediately. Add your RSS feeds via the settings panel or pair from a phone on the same Wi-Fi using the QR code.

### Building

CI: [`.github/workflows/core-line-apk.yml`](.github/workflows/core-line-apk.yml) → push a `coreline-v*` tag to cut a release. Debug APKs are uploaded as CI artifacts on every push.

Locally (needs JDK 17 + Android SDK):

```bash
cd ticker/android && ./gradlew :app:assembleDebug
```

Tests: `cd ticker && npm test` (24 tests).

Release signing uses the same `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` secrets as the icon pack.

Full architecture and remaining debt: [`ticker/HANDOVER.md`](ticker/HANDOVER.md) · Detailed readme: [`ticker/README.md`](ticker/README.md).

---

## 🔷 Core Shift

**Live wallpaper browser + Projectivy plugin for Monet Launcher.** `v2.0.1`

Two delivery paths for motion wallpapers on Android TV:

- **Monet Launcher** — browse live wallpapers in-app, full-screen looping preview, download MP4 loops to `Movies/CoreBuilds`. Point Monet Premium's video wallpaper picker at that folder.
- **Projectivy Launcher** — the **Core Motion** plugin (`motion-plugin/`, `tv.corebuilds.motion`) implements Spocky's `IWallpaperProviderService` AIDL. Serves an Overflight-compatible JSON feed as `VIDEO` wallpapers plus bundled Lottie vector loops. Sideload the plugin APK → Settings → Appearance → Wallpaper → Core Motion.

Content pipeline: 10 ffmpeg-filter MP4 loops (1080p H.264), 3 self-authored GLSL shaders (hex plasma, starfield, flowing noise), and bundled Lottie vectors — all §03 palette.

- HTTPS-only downloads with GitHub host allowlist
- Leanback UI built for D-pad navigation
- Remote manifest with bundled fallback — works offline after first sync
- CI-validated motion asset set

### Install

1. Download using **Downloader code `8829421`**, or use the permanent APK URL:

   **https://github.com/brevityA/CoreBuildsApps/releases/download/shift/coreshift-release.apk**

   Versioned builds remain available from [**Releases**](../../releases) under `shift-v*` tags. The `shift` release is a floating stable target.
2. Sideload it (Downloader, `adb install`, or a file manager).
3. Open the app — browse live wallpapers, preview full-screen, and download to `Movies/CoreBuilds` for Monet.

### Building

CI: [`.github/workflows/core-shift-apk.yml`](.github/workflows/core-shift-apk.yml) → push a `shift-v*` tag to cut a release. Debug APKs are uploaded as CI artifacts on pushes to `main` and matching pull requests.

Locally (needs JDK 17 + Android SDK):

```bash
cd shift && ./gradlew :app:assembleDebug
```

Motion asset validation: `python tools/validate_motion_feed.py`.

Release signing uses the same `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` secrets as the icon pack and Core Line.

Full architecture and remaining debt: [`shift/HANDOVER.md`](shift/HANDOVER.md).

---

## 🔷 Credits

Icon-pack conventions follow the approach proven by [Projectivy Icon Pack](https://github.com/SicMundus86/ProjectivyIconPack) by SicMundus86. Projectivy Launcher is by Spocky. App names and trademarks belong to their respective owners; this pack ships original artwork only.

Part of the [Core Builds](https://github.com/brevityA/Core-Builds) ecosystem · [ko-fi.com/branding_brevity](https://ko-fi.com/branding_brevity)
