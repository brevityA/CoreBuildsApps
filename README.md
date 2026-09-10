<div align="center">

<img src="docs/apps-banner.png" alt="Core Builds Apps" width="720">

# Core Builds Apps

**Seven Android apps. Same brand, same living-room bar.**

</div>

---

<!-- suite-stamp:start -->
> | App | Current | What it does | Downloader | Release tag |
> |---|---:|---|---|---|
> | **[Core Builds Icon Pack](#-icon-pack)** | `v1.8.11` | 926 transparent icons + 58 wallpapers for Projectivy Launcher | `5270601` | [`v*` / `iconpack`](../../releases) |
> | **[Core Builds Pixel Neon](#-pixel-neon-icon-pack)** | `v0.1.0` | 926 transparent 8-bit neon icons + 70 wallpapers for Projectivy Launcher | `[USER TO SUPPLY]` | [`pixel-neon-v*` / `pixel-neon`](../../releases) |
> | **[Core Builds Pop](#-core-builds-pop)** | `v1.0.0` | The same 926 icons, pop-art cartoon: 16 swatches, one container | `[USER TO SUPPLY]` | [`pop-v*` / `pop`](../../releases) |
> | **[Core Line](#-core-line)** | `v1.3.0` | Sports scores & channel RSS ticker (chyron) | `7375676` | [`coreline-v*` / `coreline`](../../releases) |
> | **[Core Shift](#-core-shift)** | `v2.3.5` | Android TV screensaver + motion wallpaper browser | `8829421` | [`shift-v*` / `shift`](../../releases) |
> | **[Core Motion](#-core-motion)** | `v1.0.0` | Projectivy wallpaper-provider plugin for Core Motion loops | `[USER TO SUPPLY]` | [`motion-v*` / `motion`](../../releases) |
> | **[Core Doctor](#-core-doctor)** | `v0.1.0` | Local-only streaming and suite diagnostics (phone) | `8664938` | [`doctor-v*` / `doctor`](../../releases) |
>
> Each app has its own CI workflow and release tag. Do not merge Gradle roots, split the repo, or repoint floating Downloader tags.
<!-- suite-stamp:end -->
---

## 🔷 Icon Pack

**Transparent app icons for Projectivy Launcher on Android TV.**
`925 icons` · `58 wallpapers` · `v1.8.10`

The **Core Builds Icon Pack** is designed for the [Projectivy Launcher](https://play.google.com/store/apps/details?id=com.spocky.projengmenu) on Android TV and Google TV, built to the [Core Builds Brand & Style Guide v1.0](https://github.com/brevityA/Core-Builds).

Icons share the **Core Builds visual language**: original geometry, rounded-line brand motifs, one accent and transparent backgrounds. The reviewed glyphs use the canonical **32px main stroke**, with 26.2px / 21.8px detail. Brand references inform the recognisable cue and colour; they do **not** replace the pack's style with filled vendor silhouettes or custom logotypes. The existing Outfit fallback letters remain unchanged. [Reference provenance and rights](THIRD_PARTY_NOTICES.md).

**v1.8.10 — TV focus & 7plus hotfix:** the Wallpapers button is reachable by D-pad straight from Apply (mirrored in Pixel Neon and Pop), the 7plus setup-activity mapping now renders the same red monoline artwork as the primary icon — no more legacy blue/orange tiles — and wallpaper copy describes any launcher instead of Monet. The v1.8.9 Android TV hardening and the 925-icon Core Builds monoline system remain intact. [Mixed-row preview and research](docs/research/icon-fidelity-and-demand-2026-09.md).

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

925 icons across 21 categories — streaming, media centres, debrid services, players, launchers, tools, stores, live TV, music, sport, gaming, VPN, browsers, files, and more.

Highlights: **[NoBuffr](https://downloads.nobuffr.com/android/nobuffr.apk)**, Stremio, Kodi, Jellyfin, Emby, Plex, Nuvio TV, Syncler, Weyd, TorBox, Real-Debrid, AllDebrid, Premiumize, Trakt, VLC, MX Player, SmartTube, YouTube, Spotify, Twitch, Downloader, Aurora Store, TiviMate, TV Bro, SYNC, LocalSend, RS File Manager, Sparkle TV, DS file, Ultimate File Manager Pro — plus Netflix, Prime Video, Disney+, Max, Apple TV, Stan, Binge, Kayo, ABC iview, 9Now, 7plus, 10 Play, SBS, and 860+ more.

Full table with every mapped component: [**docs/IconPackList.md**](docs/IconPackList.md)

<div align="center"><img src="docs/preview.png" alt="All icons" width="760"></div>

---

### Wallpapers

58 curated wallpapers in four active series — browse in the Wallpapers tab, preview full-screen, Set as device wallpaper or Save to `Pictures/CoreBuilds`. Multi-select export lets you bulk-save to a folder any launcher (or the system wallpaper picker) can rotate from. Thumbnails are bundled; the full images download on demand from GitHub, at the resolution each series was authored at (`1376×768` for series 6, 4K for series 1–3). Series 4 "Core Mark" was retired in v1.8.6 — see [`Wallpapers/README.md`](Wallpapers/README.md).

---

### 16:9 Banners

Every icon ships a **320×180 transparent banner** for Projectivy's wide-card layout. Third-party app banners use the same **monoline glyph + cyan→violet rail + category + path-outlined Outfit Bold name** — including NoBuffr and Plex. No standalone vendor-wordmark exception. The pack's own Core Builds mark remains unchanged. Generated from `tools/build_banners.py`.

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
| One accent per glyph; 32px round primary strokes with subordinate detail | reviewed brands use the same geometric language, not a collection of vendor styles |
| Original Core Builds linework; vendor material is reference-only | brand recognition must not override the pack's identity |
| The hex stance is never rotated | the point-up hexagon is load-bearing |
| Shared 3:1 dark-card contrast floor | squares, banners and previews use the same light-ink alternative when needed |
| `isShrinkResources = false` | drawables resolve by name at runtime |

---

## 🔷 Pixel Neon Icon Pack

**The 8-bit neon companion pack for Projectivy Launcher on Android TV.**
`925 icons` · `70 wallpapers` · `0.1.0` · clean monoline sibling: **Core Builds Icon Pack**

If the original pack is the quiet night-mode set, **Core Builds Pixel Neon** is
its arcade cabinet: the same mappings and coverage, but every mark is drawn as
a unique 32 px sprite, given a violet/cyan bloom, and scaled with hard
nearest-neighbour pixels. Brand glyph cues from the catalog become
independently drawn tile monograms, play marks, eyes, shields, waves, and other
recognizable pixel signals; each sprite also varies in silhouette, internal
pattern, and pose instead of reusing the monoline geometry. The PNGs remain
transparent, so the launcher still owns the card colour.

![Core Builds Pixel Neon preview](pixel-neon/docs/preview.png)

### Install

Download the `pixel-neon-release.apk` asset from the [**Pixel Neon stable
release**](../../releases/tag/pixel-neon), or use the versioned `pixel-neon-v*`
release tags. Sideload it beside the original pack — the two packages are
separate and can be selected independently:

- **Core Builds Icon Pack** — `tv.corebuilds.iconpack`
- **Core Builds Pixel Neon** — `tv.corebuilds.pixelneon`

Open the app and press **Apply**, or choose it manually in **Projectivy Launcher
Settings → Appearance → Cards → Icon Pack → Core Builds Pixel Neon Icon Pack**.
The pack includes the same full/short component mappings and 16:9 banner
fallback as the original, plus individually generated square pixel sprites for
per-app selection. Banners use three arcade sign layouts rather than the
original rail-and-wordmark treatment.

Pixel Neon also includes its own 70-wallpaper 8-bit collection: original
pixel-art scenes across arcade grids, cyber circuits, space runs, neon nature,
and boss-stage arenas. Thumbnails and the manifest are bundled for offline
browsing, while full 4K sources download only when a preview or export needs
them. Set a preview directly when supported, save it to `Pictures/CoreBuilds`
for launcher rotation, or bulk-export a selection with progress, cancellation,
and retry support. The renderer is derived from the one source catalog, so new
component coverage lands in both packs together. See
[`pixel-neon/README.md`](pixel-neon/README.md) for the build and regeneration
commands. The research-informed sprite rules are documented in
[`pixel-neon/DESIGN.md`](pixel-neon/DESIGN.md).
## 🔷 Core Builds Pop

**The same 925 icons, drawn as pop art.** Android TV's only cartoon icon pack.
`925 icons` · `12 wallpapers` · `16 swatches` · `v1.0.0`

<p align="center"><img src="docs/pop-preview.png" alt="Core Builds Pop contact sheet" width="820"></p>

Where the classic pack is transparent and lets Projectivy own the card, **Pop
brings its own card**: one superellipse container, a heavy ink keyline, a cream
mark, and a Ben-Day halftone screen — the neo-brutalist / comic-book language,
executed as a system rather than as a mood.

Both packs are generated from the same `tools/catalog.json`, so **coverage can
never diverge** — a component mapped in one is mapped in the other, and
`tests/test_pop.py` fails the build if that stops being true.

### Uniform, by construction

The brief was "uniform but high quality", and those usually fight. Pop resolves
it by making the loudness the constant. Five invariants, each enforced in
`tools/popart.py` and checked by `tools/validate_pop.py` — not written down in
a style guide and hoped for:

| Invariant | What it replaces |
|---|---|
| One superellipse container on all 925 | no container; every launcher looked different |
| 171 brand accents → **16 locked swatches**, snapped by hue | 171 unbounded accents |
| Every mark optically normalised to one ink box | mark sizes varying by more than 2× |
| Line weight snapped **after** scaling, not before | strokes thinning as glyphs grew |
| One halftone screen, one angle, one pitch | — |

The palette keeps colour-as-language — Netflix stays red, Spotify stays green —
and takes away only the saturation and value, which were never carrying
meaning. Every swatch, its app count, and a real icon rendered in it:
[**docs/pop-palette.png**](docs/pop-palette.png).

The biggest single legibility win is the third rule. 614 of the 925 icons were
a letter inside a rounded box; Pop's container **is** that box, so the box is
dropped and the letter is scaled to the same ink box as every other mark. The
letters roughly double in size.

### Covers apps it has never seen

Pop's whole claim is one container, and a single unthemed app breaks that claim
on sight. So the pack hands the launcher the furniture to build one: an
`iconback` in each of the 16 swatches, an `iconmask`, an `iconupon` keyline and
a `scale` factor. Apps we do not cover get the Pop field, halftone and ink
keyline with their own icon composited inside — so the answer to "925 icons" is
really *every app on your device*. No other Android TV pack ships this.

Pop also themes **Projectivy's own cards** — settings, categories, channels and
HDMI 1–4 / AV inputs, numbered so you can tell which input is which. That uses
the internal-activity mapping Projectivy added in 4.70
([miproja1#512](https://github.com/spocky/miproja1/issues/512)), answering a
[posted request](https://www.reddit.com/r/Projectivy_Launcher/comments/1icfbn7/how_to_i_install_icon_packs_also_can_i_make_my/)
that no pack had filled.

### Matching wallpapers

Twelve 4K walls in `series-5-pop`, built from the same primitives as the icons
— same swatches, same ink, same halftone. Dark-weighted with a calm lower half,
because that is where Projectivy draws its card rows.

<p align="center"><img src="docs/pop-wallpapers.png" alt="Core Builds Pop wallpapers" width="820"></p>

Flat art needs no grain dither, which means it survives an indexed palette
losslessly: the whole 12-wall 4K series is **2.5 MB**.

### Install

Pop is a **separate APK with its own package**, so you can install it alongside
the classic pack and switch between them in Projectivy's own icon-pack list.

1. **https://github.com/brevityA/CoreBuildsApps/releases/download/pop/corepop-release.apk**
   (versioned builds under `pop-v*` tags)
2. Sideload, open, press **apply**.
3. Or: **Projectivy Settings** → **Appearance** → **Cards** → **Icon Pack** → **Core Builds Pop**

> **Tip:** Pop icons carry their own field colour, so set Projectivy's card
> background to something dark and neutral and let the icons do the colour.

### Build it

```bash
python tools/build_pop.py              # 925 icons + 925 banners + branding (~5 min)
python tools/build_pop_wallpapers.py   # 12 × 4K walls + thumbs + manifest (~70 s)
python tools/validate_pop.py           # 13,700+ coherence checks
python tests/test_pop.py               # 24 contract tests
```

`tools/pop_glyph_metrics.json` is a committed measurement of every glyph's ink
bounding box. It exists so the generators are a pure function of committed
inputs — measuring at render time would make the output depend on which
rasteriser version CI happened to install, and the SVG drift gate would fail on
an unrelated dependency bump. Re-run `tools/measure_pop_glyphs.py` only when
glyph geometry actually changes.

### Why this pack exists

Full research, with sources: [**docs/research/iconpack-demand-2026.md**](docs/research/iconpack-demand-2026.md)
[**docs/research/wallpaper-directions.md**](docs/research/wallpaper-directions.md),
and [**docs/research/android-tv-icon-packs.md**](docs/research/android-tv-icon-packs.md)
— the launcher landscape, the ADW spec features packs leave unused, and a
prioritised roadmap.

The short version: Android TV has roughly three icon packs, and all three are
minimal line art. Pop art / cartoon sells well on phones — the leading pack in
that style ships 7,850 icons and bundles matching halftone wallpapers — and
nobody ships it on TV. Coverage was already won here at 925 icons; style was
the open axis.

---

## 🔷 Repo Layout

```
tools/catalog.json           the single source of truth
tools/glyphs.py              original Core Builds glyph primitives (no vendor override)
tools/brandmarks/            pinned reference-only SVGs (provenance in catalog)
tools/icon_style.py          shared Classic dark-card colour policy
tools/typeface.py            Outfit Bold/ExtraBold → SVG paths
tools/fonts/                 Outfit OFL sources for wordmarks + monograms
tools/build_icons.py         catalog → SVG, PNG, appfilter, docs
tools/build_banners.py       catalog → 16:9 monoline banners
tools/build_branding.py      launcher icon + Leanback banner
tools/build_brand_preview.py branding preview sheet
tools/build_pixel_neon_wallpapers.py  original 8-bit Pixel Neon wallpapers
tools/build_pixel_neon.py              catalog → 8-bit neon companion pack
tools/validate_pixel_neon.py           alternate-pack coherence checks
tools/validate.py            coherence checks (20,000+ at 925 icons)
tools/popart.py              Pop render engine (container, ink, halftone, fit)
tools/measure_pop_glyphs.py  one-off glyph ink-bbox measurement
tools/pop_glyph_metrics.json committed metrics, so renders are reproducible
tools/build_pop.py           catalog → the whole Pop module
tools/build_pop_wallpapers.py catalog-free: 12 × 4K Pop walls + manifest
tools/validate_pop.py        Pop coherence checks (13,700+)
assets/svg/                  master vectors (925)
assets/banners/              16:9 banners (925)
app/src/main/res/            the original icon-pack Android module
pixel-neon/                   8-bit neon companion pack + its Gradle root
PixelNeonWallpapers/          original 8-bit Pixel Neon sources + manifest
pop/                         Core Builds Pop — second pack, shares app/'s Kotlin
assets/pop/                  Pop master vectors (925 square + 925 banner)
Wallpapers/series-5-pop/     12 × 4K Pop wallpapers (2.5 MB total)
Latestrelease/version.json   Icon Pack update manifest
Latestrelease/pixel-neon-version.json  Pixel Neon update manifest
Latestrelease/pop-version.json  Pop's in-app update manifest
docs/IconPackList.md         original supported apps + components
docs/PopIconList.md          the same apps, with their Pop swatch
docs/research/               why this pack exists, with sources
ticker/                      Core Line — sports & channel ticker (see ticker/README.md)
shift/                       Core Shift — live wallpaper browser (see shift/HANDOVER.md)
doctor/                      Core Doctor — streaming diagnostics (see doctor/SPEC.md)
motion-plugin/               Core Motion — Projectivy wallpaper-provider plugin
motion-shaders/              GLSL fragment shaders (hex plasma, starfield, flow)
Motion/live/                 Motion asset set (MP4 loops, thumbnails, live-feed.json)
```

---

## 🔷 Core Line

**TV-first sports & channel ticker (chyron).** `v1.3.0`

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

Tests: `cd ticker && npm test` (114 tests in the current suite).

Release signing uses the same `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` secrets as the icon pack.

Full architecture and remaining debt: [`ticker/HANDOVER.md`](ticker/HANDOVER.md) · Detailed readme: [`ticker/README.md`](ticker/README.md).

---

## 🔷 Core Shift

**Android TV screensaver + motion wallpaper browser.** `v2.3.5`

Two delivery paths for motion wallpapers on Android TV:

- **Monet Launcher** — browse live wallpapers in-app, full-screen looping preview, download MP4 loops to `Movies/CoreBuilds`. Point Monet Premium's video wallpaper picker at that folder.
- **Projectivy Launcher** — the **Core Motion** plugin (`motion-plugin/`, `tv.corebuilds.motion`) implements Spocky's `IWallpaperProviderService` AIDL. Serves an Overflight-compatible JSON feed as `VIDEO` wallpapers plus bundled Lottie vector loops. Install [`coremotion-release.apk`](https://github.com/brevityA/CoreBuildsApps/releases/download/motion/coremotion-release.apk) → Projectivy Settings → Appearance → Wallpaper → Launcher wallpaper → Core Motion. Requires Projectivy Premium. Not detected? See [`docs/PROJECTIVY_DETECTION.md`](docs/PROJECTIVY_DETECTION.md).
- **Monet Launcher** — no plugin API exists, so the route is the **Aerial Views bridge**: point Aerial Views at [`Motion/aerial-entries.json`](Motion/aerial-entries.json), then set Monet's wallpaper source to Aerial. Auto-updating, and gives you a matching screensaver. Local-file fallback via Core Shift → `Movies/CoreBuilds`. See [`docs/MONET_LAUNCHER.md`](docs/MONET_LAUNCHER.md).

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

## 🔷 Core Motion

**Projectivy wallpaper-provider plugin for Core Motion loops.** `v1.0.0`

Core Motion is the Projectivy-side delivery app: a small APK detected by Projectivy Premium as a wallpaper source. It serves the GitHub-hosted Core Motion feed plus bundled vector loops; it is separate from Core Shift so Monet/Aerial and Projectivy paths can evolve without coupling.

### Install

1. Download the stable APK directly:

   **https://github.com/brevityA/CoreBuildsApps/releases/download/motion/coremotion-release.apk**

   Downloader code: **[USER TO SUPPLY]**. Versioned builds remain under `motion-v*`; the `motion` release is the floating stable target and must not be moved to a different asset name.
2. Sideload it.
3. In Projectivy: **Settings → Appearance → Wallpaper → Launcher wallpaper → Core Motion**. Requires Projectivy Premium.

### Building

CI: [`.github/workflows/core-motion-apk.yml`](.github/workflows/core-motion-apk.yml) → push a `motion-v*` tag to cut a release.

Locally (needs JDK 17 + Android SDK):

```bash
cd motion-plugin && ./gradlew :app:assembleDebug
```

Plugin details: [`motion-plugin/README.md`](motion-plugin/README.md) · Detection notes: [`docs/PROJECTIVY_DETECTION.md`](docs/PROJECTIVY_DETECTION.md).

---

## 🔷 Core Doctor

**Streaming infrastructure diagnostics for your phone.** `v0.1.0`

Not a TV app — Core Doctor runs on your phone and checks whether your streaming setup is healthy. Six checks, gated on what credentials you provide:

| Check | Gate | What it tests |
|---|---|---|
| DNS resolution | Always | Resolves api.real-debrid.com, api.torbox.app, v6-4.aiostreams.elfhosted.com |
| VPN detection | Always | ConnectivityManager TRANSPORT_VPN |
| Addon manifest | Addon URL | HTTP GET {url}/manifest.json — validates JSON with `id` field |
| Stream probe | Addon URL | HTTP GET {url}/stream/movie/tt0133093.json — checks for results |
| Real-Debrid | RD key | GET api.real-debrid.com/rest/1.0/user — premium status, expiry |
| TorBox | TB key | GET api.torbox.app/v1/api/user/me — plan status, expiry |

- No backend, no persistence, no analytics — everything runs client-side
- API keys are sent only to the provider they belong to (Bearer token)
- Share reports are **redacted by construction** — `ReportCard.render()` emits verdicts and summaries only; keys and URLs are structurally excluded
- `allowBackup=false`, no SharedPreferences, no Room, no files
- Permissions: INTERNET, ACCESS_NETWORK_STATE — nothing else

### Install

1. Download using **Downloader code `8664938`**, or grab the APK from [**Releases**](../../releases) under `doctor-v*` tags.
2. Sideload it (Downloader, `adb install`, or a file manager).
3. Open the app — enter your addon URL and/or debrid API keys, tap Run.

### Building

CI: [`.github/workflows/core-doctor-apk.yml`](.github/workflows/core-doctor-apk.yml) → push a `doctor-v*` tag to cut a release.

Locally (needs JDK 17 + Android SDK):

```bash
cd doctor && ./gradlew :app:assembleDebug
```

Tests: `cd doctor && ./gradlew test` (unit tests for JSON parsing).

Release signing uses the same `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` secrets as the other apps.

Spec: [`doctor/SPEC.md`](doctor/SPEC.md).

---

## 🔷 Credits

Icon-pack conventions follow the approach proven by [Projectivy Icon Pack](https://github.com/SicMundus86/ProjectivyIconPack) by SicMundus86. Projectivy Launcher is by Spocky. App names and trademarks belong to their respective owners; see [artwork sources and notices](THIRD_PARTY_NOTICES.md) for the offline references and original Core Builds interpretations. No endorsement is implied.

Part of the [Core Builds](https://github.com/brevityA/Core-Builds) ecosystem · [ko-fi.com/branding_brevity](https://ko-fi.com/branding_brevity)
