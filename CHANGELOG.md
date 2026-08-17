# Changelog

All notable changes to the Core Builds Icon Pack. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[SemVer](https://semver.org/spec/v2.0.0.html).

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
