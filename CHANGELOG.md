# Changelog

All notable changes to the Core Builds Icon Pack. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[SemVer](https://semver.org/spec/v2.0.0.html).

## [1.7.1] — 2026-08-21

Hot patch on 1.7.0: export wallpapers to `Pictures/CoreBuilds/` for launcher
auto-rotation, plus hardening for the in-app wallpapers surface shipped in 1.7.0.

### Added
- **Multi-select export.** Long-press a wallpaper (or press the header Export
  button) to enter selection mode; Select all / Clear / Export N start a bulk
  copy to `Pictures/CoreBuilds/`. A progress screen reports saved, skipped and
  failed counts with **Retry failed** — no unnamed errors.
- **`WallpaperExporter`** — sequential download-then-copy that streams original
  bytes (no bitmap decode, no re-encode). Idempotent: same-named, same-size
  files are skipped; pre-flight free-space check; failed files never leave
  half-written MediaStore rows.
- **`ExportProgressActivity`** — determinate progress, then a row of installed
  launchers (detected via the existing `ApplyIconPack` catalog) so the user can
  open Monet/Projectivy/etc. and finish enabling rotation.
- **Save button** on the wallpaper preview, alongside Set. Saves the original
  4K file into `Pictures/CoreBuilds/` without setting it.
- `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion=28` for API 21–28 (Fire TV /
  older Shield); API 29+ uses scoped storage with no runtime permission.
- `tests/test_wallpaper_export.py` — contract tests for export wiring.

### Fixed
- Wallpaper series labels crashed on API 21–23 (`CharSequence.titlecase()` is
  API 24+). Now uses `toUpperCase(Locale)`.
- Preview could recycle a bitmap still held by its ImageView on destroy
  ("Canvas: trying to use a recycled bitmap"). Detaches before recycling and
  guards all background callbacks against a destroyed activity.
- Concurrent wallpaper downloads could write the same cache file from two
  threads. Requests for a URL already in flight now coalesce onto one fetch;
  downloads write through a `.part` temp and atomically rename.
- Wallpaper preview now requests initial focus after layout (not in `onCreate`),
  and thumbnail decoding runs on a shared 2-thread pool instead of one thread
  per bind.

### Changed
- `WallpaperSetter` gained a public `copyFileToPictures(File)` used by both Save
  and export; the bitmap-only path remains for the Fire TV set fallback.
- `WallpaperDownloader` exposes `fetchUrl()` and uses a single worker plus a
  shared main Handler (was one Handler allocated per thumbnail load).

## [1.7.0] — 2026-08-21

In-app wallpapers. The Core Builds wallpaper collection is now browsable and
settable from inside the app, on Android TV / Google TV and (via the Pictures
fallback) Fire TV.

### Added
- **Wallpapers browser** (`WallpapersActivity`): a night-chrome grid of the full
  Core Builds collection with series filter chips. Thumbnails are bundled so the
  grid renders instantly offline.
- **Full-screen preview** (`WallpaperPreviewActivity`): shows the bundled thumb
  immediately, then downloads the 4K image on demand with a byte progress label
  and sets it in one press.
- **30 new "Core Mark" wallpapers** (series 4, #41–#70): the lit hex + faceted
  core diamond rendered from the Brand Guide v1.0 construction constants. Added
  as 3840×2160 PNGs in `Wallpapers/series-4-core-mark/` with thumbs; collection
  manifest bumped to **v3.0 (70 wallpapers)**.
- **`WallpaperSetter`**: sets the system wallpaper through `WallpaperManager`
  (the path Monet uses to extract its Material You palette). On devices that
  block third-party writes (Fire TV) it saves to `Pictures/CoreBuilds` and opens
  the system crop/set intent.
- **`WallpaperDownloader`**: on-demand full-image fetch with a GitHub-host
  allowlist, https-only, and a 12-file internal-storage LRU cache. Reuses the
  app's no-library HTTPS discipline.
- A `Wallpapers` entry chip on the main screen showing the live collection count.
- `SET_WALLPAPER` permission (only consulted on API ≤ 28).
- `tests/test_wallpapers.py` — 17 contract checks covering the manifest, bundled
  thumbs, series-4 files, and Android wiring; wired into `build.yml`.

### Changed
- Version bumped to 1.7.0 (version code 10).

## [1.6.0] — 2026-08-20

Projectivy-scale coverage and evidence-based launcher matching.

### Added
- **401 new original icons and 16:9 banners**, bringing the pack from 516 to **917 icons**.
- Exact component mappings from the Projectivy Icon Pack 1.1.9 reference set: **1,090 source mappings**, **958 packages**, and **1,646 generated appfilter rows**.
- A decoded, auditable mapping snapshot at `tools/reference/projectivy-1.1.9-appfilter.xml`; inherited entries carry `mapping_source` provenance and remain marked unverified pending hardware confirmation.
- Byte-identical `assets/appfilter.xml` and `assets/drawable.xml` compatibility copies for launchers that do not read `res/xml`.
- Canonical component and asset-parity validation.
- `resvg-py` as the deterministic primary rasterizer so generation is stable on minimal hosts without libcairo.
- Floating `iconpack` release automation with permanent `iconpack-release.apk` and compatibility `app-release.apk` assets, restoring Downloader code `5270601` without competing with Core Line's floating release.
- Expanded Android launcher matching research in `docs/COMPARISON.md`.

### Changed
- Version bumped to 1.6.0 (version code 9).
- Coverage claims now distinguish selectable icons, mapped art, source components, generated aliases, and unique packages instead of conflating them.

## [1.5.1] — 2026-08-19

Robustness. Mixed-launcher apply. Safer updater. Picker can hand a banner or a square. In-app update download. Name audit.

### Fixed
- **In-app install crashed.** `UpdateInstaller` wrote a FileProvider path but the manifest never declared the provider.
- **Updater followed any redirect host.** Now https-only and GitHub CDN allowlisted; download must be a ZIP/APK and ≥ 200 KB.
- **Install permission return did nothing.** After Unknown Sources, `onResume` now opens the installer once.
- **Picker only sent a bitmap unless a rare extra was set.** Now always returns `EXTRA_SHORTCUT_ICON_RESOURCE` plus a bitmap fallback.
- **Picker always delivered the 1:1 glyph.** Default is the 16:9 banner; a Banner / Square chip switches.
- **Twitch / 19 other aliases** from SicMundus 1.1.9. Monet 1.0.76 has **no apply extra** — Manual path is the real contract.
- **Launcher Manager** was labelled Lucky Manager (`com.wolf.google.lm`).
- **180+ display names** that were still package slugs or vendor fragments
  (Acorn TV, A&E, HISTORY, Fubo, Hulu, F1 TV, YouTube Kids, Paramount+,
  Crave, Streamyfin, TIDAL, FLauncher, CinemaGlow, DRM Info, …).
- **Twitch** was mapped to the mobile activity
  (`tv.twitch.android.app/.core.LandingActivity`). Android TV launches
  `tv.twitch.android.apps.TVLandingActivity` (older) or
  `tv.twitch.starshot64.app.StarshotActivity` (current, SicMundus 1.1.9).
  Both are now mapped, plus the older `TwitchActivity` alias (unverified).
- **19 more documented aliases** harvested from Projectivy Icon Pack 1.1.9
  for packages we already ship (10 Play You.i, iview `.ui.MainActivity`,
  Kayo Fox Sports Martian, Stan splash, Max Beam, Trakt TV, S0undTV Fire TV,
  Solid Explorer class name, SmartTube beta, …). Marked `unverified`.
  Same approach as other TV packs: extra `ComponentInfo` lines, not a
  package wildcard — Projectivy matches the literal string.

### Added
- FileProvider `tv.corebuilds.iconpack.update` + validator guards.
- CI runs `build_banners.py` so renamed wordmarks cannot drift in XML/SVG.
- Apply names Home first and lists every other known installed launcher.
- **Download / Install** bar when `Latestrelease/version.json` is newer.
  One press pulls the APK from GitHub and opens the system installer.
- Apply targets every known installed launcher. The primary button names
  the Home launcher (Projectivy, Monet, AT4K, Leanback on Fire, L TV,
  FLauncher, ChillHub, Nova, Lawnchair, Apex, ADW). Extra chips list the
  others. No apply contract → opens the launcher with the named path.

## [1.5.0] — 2026-08-18

Outfit wordmarks, dedicated file/NAS icons, named in-app browser.

### Added
- **Outfit Bold / ExtraBold** bundled under `tools/fonts/` (SIL OFL). Banner
  wordmarks and square monograms are converted to paths from that one family,
  so a row of cards no longer mixes hand-drawn letters with whatever sans the
  host has installed.
- **14 icons**: LocalSend, RS File Manager, Sparkle TV, DS file, DS video,
  DS photo, DS audio, DS finder, Synology Drive, DS get, FX File Explorer,
  Solid Explorer, Material Files, Ghost Commander.
- 10 new glyphs: `localsend_nodes`, `sparkle_burst`, `nas_stack`, `nas_play`,
  `nas_image`, `folder_rs`, `folder_wifi`, `folder_fx`, `folder_solid`,
  `radar_dish`.
- In-app **category chips**, **name search**, and **labeled tiles**. D-pad
  moves apply → chips → search → grid; the grid is no longer trapped inside
  a NestedScrollView.
- `drawable.xml` grouped by catalog category (Banners · Files, Square · Live
  TV, …) so Projectivy's icon picker can jump a section.

### Fixed
- **200+ slug names** imported from package fragments now read as app names
  (WiFi File Explorer, CX File Explorer, X-plore, 9Now, Peacock, …).
- **Files** no longer steals CX / MiXplorer / FX components. Each file
  manager maps to its own icon. X-plore is Files, not Gaming.
- Banner wordmarks no longer depend on DejaVu/Liberation being present.

### Notes
- New Synology / LocalSend / RS File Manager / Sparkle TV components are
  best-known, not device-confirmed. If one does not auto-assign, open an
  issue with `adb shell cmd package resolve-activity --brief <package>`.

## [1.1.0] — 2026-08-17

Built from a real device scan (`himalaya`, Android 14 / API 34).

### Fixed — icons that were silently not applying
Eight apps were mapped to activities the device does not launch, so their
icons never applied and nothing reported it. Device-verified components added:

- **Projectivy Launcher** `.ui.home.MainActivity`
- **Prime Video** `com.amazon.ignition.IgnitionActivity`
- **Stan** `au.com.stan.presentation.tv.splash.SplashScreenActivity`
- **10 Play** `com.tenplay.MainActivity`
- **Just Player** `.PlayerActivity`
- **Disney+** `com.bamtechmedia.dominguez.main.MainActivity`
- **Downloader** `.ui.main.MainActivity`
- **Spotify** `com.spotify.app.androidtv.MainActivity`

### Added
- **28 icons** (40 → 68), every component read off hardware: WuPlay, Aurora
  Store, Aptoide TV, SAI, APKTime, Strexo, ADB App Control, Monet, Moonlight,
  Janky, Wave TV, Strmr, Send Files to TV, Projectivy Blueprint, TV Quick
  Actions, Home Button, Cinema HD, Unlinked, Vimu, TizenTube, Play Store,
  Live TV, SD Maid SE, Shizuku, Tasker, Poweramp EQ, ATV Tools, Lucky Manager.
- 11 new glyphs: store bag, install box, stream tower, gamepad, wrench, send
  arrow, broom, shield key, automation graph, home button, tv stack.
- Scanner reports mismatches on catalogued system apps instead of filtering
  them out.

## [1.0.1] — 2026-08-17

### Fixed
- **Projectivy could not see the pack.** The manifest never declared
  `com.spocky.projengmenu.icons.ACTION_PICK_ICON`, Projectivy's own discovery
  action, so the pack installed successfully and remained invisible in
  Appearance → Cards → Icon Pack. Found by decompiling the shipped v1.0.0 APK
  and diffing its manifest against the reference pack.
- **Android 11+ package visibility.** With `targetSdk 34` and no `<queries>`
  block, every launcher lookup returned "not installed", so direct apply could
  never have fired on a modern device — silently, since nothing throws.

### Added
- One-press **direct apply** for Projectivy, Nova, Lawnchair, Apex and ADW.
  The button names its target before it is pressed and reports
  Applied / NotInstalled / Manual by name, with the exact menu path on fallback.
- Remaining launcher discovery actions (Sony, Fede, Lawnchair PICK_ICON,
  OnePlus, Turbo, Nova CUSTOM_ICON_PICKER).
- 10 validator checks covering the intent contract and `<queries>` (419 total).

## [1.0.0] — 2026-08-17

First release.

### Added
- **40 icons** covering **78 launcher components** — Core Builds ecosystem
  (Stremio, Kodi, Jellyfin, Emby, Plex, Nuvio TV, Syncler, Weyd, Trakt, TorBox,
  Real-Debrid, AllDebrid, Premiumize, Downloader, VLC, MX Player, Just Player,
  Kore, SmartTube), mainstream streaming (Netflix, Prime Video, Disney+, Max,
  Apple TV, YouTube, Spotify, Twitch), and AU free-to-air (ABC iview, 9Now,
  7plus, 10 Play, SBS, Stan, Binge, Kayo).
- Transparent-background glyphs on a 512 grid, drawn as original geometry in
  the Core Builds icon language (Brand Guide v1.0 §07).
- Android module with `appfilter.xml` auto-assignment, Leanback banner,
  `LEANBACK_LAUNCHER` category, and seven icon-pack discovery intents
  (Projectivy, Nova, Lawnchair, ADW, Apex, Tesla, GO).
- In-app browser grid on night chrome with the signature cyan-gradient CTA.
- Generator pipeline: `build_icons.py`, `build_branding.py`,
  `build_brand_preview.py` — all output derives from `tools/catalog.json`.
- Validator with 409 coherence checks (`tools/validate.py`).
- CI that regenerates assets, fails on drift from the catalog, validates,
  builds the APK, and publishes on a `v*` tag.

### Notes
- Designed for dark card backgrounds. Light launcher themes will wash the
  icons out.
- Component names for niche apps are best-known values. If one doesn't
  auto-assign, open an issue with the output of
  `adb shell cmd package resolve-activity --brief <package>`.

[1.0.0]: https://github.com/brevityA/CoreBuildsIconPack/releases/tag/v1.0.0
