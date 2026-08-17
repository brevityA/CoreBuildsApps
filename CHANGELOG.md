# Changelog

All notable changes to the Core Builds Icon Pack. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[SemVer](https://semver.org/spec/v2.0.0.html).

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
