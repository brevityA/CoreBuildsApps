# CoreBuildsApps agent guide

## Suite map

`suite.json` is the suite registry. It owns product names, current versions, package IDs, release prefixes, floating tags, Downloader codes, and Gradle roots.

| Product | Path | Package ID | Version | Downloader / stable tag |
|---|---|---|---:|---|
| Core Builds Icon Pack | `app/` with repo-root Gradle | `tv.corebuilds.iconpack` | `1.8.2` | `5270601` / `iconpack` |
| Core Builds Pixel Neon | `pixel-neon/` | `tv.corebuilds.pixelneon` | `0.1.0` | `[USER TO SUPPLY]` / `pixel-neon` |
| Core Line | `ticker/` + `ticker/android/` | `dev.corebuilds.line` | `1.3.0` | `7375676` / `coreline` |
| Core Shift | `shift/` | `dev.corebuilds.shift` | `2.3.5` | `8829421` / `shift` |
| Core Motion | `motion-plugin/` | `tv.corebuilds.motion` | `1.0.0` | `[USER TO SUPPLY]` / `motion` |
| Core Doctor | `doctor/` | `dev.corebuilds.doctor` | `0.1.0` | `8664938` / `doctor` |

Do not merge Gradle roots. Do not split the GitHub repo. Do not rename package IDs. Do not repoint floating Downloader tags. New apps use `tv.corebuilds.<name>`; the current `dev.` IDs are history, not a style to copy.

## One rule

`tools/catalog.json` is the only source of truth for icon-pack icons. Never hand-edit generated icon XML, icon PNGs, banner PNGs, `docs/IconPackList.md`, or `docs/preview.*`.

Icon-pack changes run the four generators and the validator:

```bash
python tools/build_icons.py
python tools/build_banners.py
python tools/build_branding.py
python tools/build_brand_preview.py
python tools/validate.py
```

Paste the validator receipt. Current receipt: `Validated 924 icons · 1660 components · 20046 checks run`.

## Truth gates

Before any PR:

```bash
python tools/build_readme_badge.py
python tools/check_suite_truth.py
python tools/audit_contract.py
```

`check_suite_truth.py` fails stale README/agent/doc claims, catalog/Gradle/version metadata drift, missing stamped README block, and the `line-v*` trap. Core Line's prefix is `coreline-v*`.

## Product verification shortcuts

- Icon Pack: four generators + `python tools/validate.py`.
- Pixel Neon: `python tools/build_pixel_neon_wallpapers.py`, `python tools/build_pixel_neon.py`, `python tools/validate_pixel_neon.py`, plus `cd pixel-neon && ./gradlew :app:lintDebug :app:assembleDebug`.
- Core Line: `cd ticker && npm test`.
- Core Shift: `python tools/validate_motion_feed.py` plus Android lint/build in CI.
- Core Motion: `python tools/validate_projectivy_plugin.py` plus Android lint/build in CI.
- Core Doctor: `cd doctor && ./gradlew :app:testDebugUnitTest` where Android SDK exists.

If local SDK/device access is missing, say so. A named unverified step is better than a confident guess.
