# CoreBuildsApps agent guide

## Suite map

`suite.json` is the suite registry. It owns product names, current versions, package IDs, release prefixes, floating tags, Downloader codes, and Gradle roots.

| Product | Path | Package ID | Version | Downloader / stable tag |
|---|---|---|---:|---|
| Core Builds Icon Pack | `app/` with repo-root Gradle | `tv.corebuilds.iconpack` | `1.8.2` | `5270601` / `iconpack` |
| Core Builds Pixel Neon | `pixel-neon/` | `tv.corebuilds.pixelneon` | `0.1.0` | `[USER TO SUPPLY]` / `pixel-neon` |
| Core Builds Pop | `pop/` with repo-root Gradle | `tv.corebuilds.iconpack.pop` | `1.0.0` | `[USER TO SUPPLY]` / `pop` |
| Core Line | `ticker/` + `ticker/android/` | `dev.corebuilds.line` | `1.3.0` | `7375676` / `coreline` |
| Core Shift | `shift/` | `dev.corebuilds.shift` | `2.3.5` | `8829421` / `shift` |
| Core Motion | `motion-plugin/` | `tv.corebuilds.motion` | `1.0.0` | `[USER TO SUPPLY]` / `motion` |
| Core Doctor | `doctor/` | `dev.corebuilds.doctor` | `0.1.0` | `8664938` / `doctor` |

`pop/` is the one intentional exception to "one app, one Gradle root": it is a
second module on the repo-root build that compiles `app/src/main/java` rather
than a copy of it. Two packs, one codebase, two package IDs. Do not fork the
Kotlin, and do not turn either pack into a product flavour of the other — that
relocates the release APK path and breaks `build.yml`.

Do not merge Gradle roots. Do not split the GitHub repo. Do not rename package IDs. Do not repoint floating Downloader tags. New apps use `tv.corebuilds.<name>`; the current `dev.` IDs are history, not a style to copy.

## One rule

`tools/catalog.json` is the only source of truth for **both** icon packs. Never hand-edit generated icon XML, icon PNGs, banner PNGs, `docs/IconPackList.md`, `docs/PopIconList.md`, `docs/preview.*`, `docs/pop-*`, `pop/src/main/**`, `assets/pop/**`, or `Wallpapers/series-5-pop/**`.

Classic pack changes run the four generators and the validator:

```bash
python tools/build_icons.py
python tools/build_banners.py
python tools/build_branding.py
python tools/build_brand_preview.py
python tools/validate.py
```

Paste the validator receipt. Current receipt: `Validated 924 icons · 1660 components · 23743 checks run`.

Anything touching the catalog, `tools/glyphs.py`, or shared `app/` resources also rebuilds Pop, because Pop mirrors those resources and renders the same catalog:

```bash
python tools/build_pop.py            # ~5 min: 1848 PNGs, 1848 SVGs, XML, branding, docs
python tools/validate_pop.py
python tests/test_pop.py
```

Pop receipts: `Validated 924 icons · 1098 components · 16 swatches · 13722 checks run` and `Ran 24 tests ... OK`.

`tools/pop_glyph_metrics.json` is committed on purpose. `popart.py` must stay a pure function of committed inputs — measuring glyph bounding boxes at render time makes output depend on the installed rasteriser version and blows up the SVG drift gate on an unrelated dependency bump. Re-run `tools/measure_pop_glyphs.py` (~33 s) only when glyph geometry changes, and rebuild Pop after.

Pop wallpapers are separate and rarely need rebuilding:

```bash
python tools/build_pop_wallpapers.py  # ~70 s
```

`Wallpapers/manifest.json` belongs to the classic pack and stays at 70 entries. Pop uses `Wallpapers/pop-manifest.json`. The two collections are asserted disjoint.

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
- Pixel Neon: `python tools/build_pixel_neon.py`, `python tools/validate_pixel_neon.py`, plus `cd pixel-neon && ./gradlew :app:lintDebug :app:assembleDebug`.
- Core Builds Pop: `python tools/build_pop.py` + `python tools/validate_pop.py` + `python tests/test_pop.py`.
- Core Line: `cd ticker && npm test`.
- Core Shift: `python tools/validate_motion_feed.py` plus Android lint/build in CI.
- Core Motion: `python tools/validate_projectivy_plugin.py` plus Android lint/build in CI.
- Core Doctor: `cd doctor && ./gradlew :app:testDebugUnitTest` where Android SDK exists.

If local SDK/device access is missing, say so. A named unverified step is better than a confident guess.
