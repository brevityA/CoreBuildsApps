# CoreBuildsApps agent guide

## Suite map

`suite.json` is the suite registry. It owns product names, current versions, package IDs, release prefixes, floating tags, Downloader codes, and Gradle roots.

| Product | Path | Package ID | Version | Downloader / stable tag |
|---|---|---|---:|---|
| Core Builds Icon Pack | `app/` with repo-root Gradle | `tv.corebuilds.iconpack` | `1.9.0` | `5270601` / `iconpack` |
| Core Builds Pixel Neon | `pixel-neon/` | `tv.corebuilds.pixelneon` | `0.1.0` | `[USER TO SUPPLY]` / `pixel-neon` |
| Core Builds Pop | `pop/` with repo-root Gradle | `tv.corebuilds.iconpack.pop` | `1.0.0` | `[USER TO SUPPLY]` / `pop` |
| Core Line | `ticker/` + `ticker/android/` | `dev.corebuilds.line` | `1.3.0` | `7375676` / `coreline` |
| Core Shift | `shift/` | `dev.corebuilds.shift` | `2.3.5` | `8829421` / `shift` |
| Core Motion | `motion-plugin/` | `tv.corebuilds.motion` | `1.0.0` | `[USER TO SUPPLY]` / `motion` |
| Core Doctor | `doctor/` | `dev.corebuilds.doctor` | `0.1.0` | `[USER TO SUPPLY]` / `doctor` |

`pop/` is the one intentional exception to "one app, one Gradle root": it is a
second module on the repo-root build that compiles `app/src/main/java` rather
than a copy of it. Two packs, one codebase, two package IDs. Do not fork the
Kotlin, and do not turn either pack into a product flavour of the other — that
relocates the release APK path and breaks `build.yml`.

Do not merge Gradle roots. Do not split the GitHub repo. Do not rename package IDs. Do not repoint floating Downloader tags. New apps use `tv.corebuilds.<name>`; the current `dev.` IDs are history, not a style to copy.

## One rule

`tools/catalog.json` is the only source of truth for **both** icon packs. Never hand-edit generated icon XML, icon PNGs, banner PNGs, `docs/IconPackList.md`, `docs/PopIconList.md`, `docs/preview.*`, `docs/pop-*`, `pop/src/main/**`, `assets/pop/**`, or generated wallpaper output under `Wallpapers/series-5-pop/**` and
`Wallpapers/series-8-amoled/**`.

Classic pack changes run the four generators and the validator:

```bash
python tools/build_icons.py
python tools/build_banners.py
python tools/build_branding.py
python tools/build_brand_preview.py
python tools/validate.py
python tests/test_icon_identity.py    # 35 style/colour/reference/mapping regressions (after all packs build)
```

Paste the validator receipt. Current receipt: `Validated 943 icons · 1765 components · 25856 checks run`.

Anything touching the catalog, `tools/glyphs.py`, or shared `app/` resources also rebuilds Pop, because Pop mirrors those resources and renders the same catalog:

```bash
python tools/build_pop.py            # ~5 min: 1850 icon/banner PNGs, 1850 SVGs, XML, branding, docs
python tools/validate_pop.py
python tests/test_pop.py
```

Pop receipts: `Validated 943 icons · 1157 components · 16 swatches · 14767 checks run` and `Ran 29 tests ... OK`.

**The pack identity takes precedence over literal vendor-logo reproduction.**
Reviewed brand entries use `style: core_monoline`: 32px rounded primary strokes,
26.2px / 21.8px detail, no solid fills/effects/containers, and one accent. Keep
the common Outfit + category + cyan/violet rail banner for NoBuffr and every
other reviewed app. Do not reintroduce vendor-wordmark-only banners.

Catalog `artwork` entries are `usage: reference-only`: pinned SVG hashes, URLs
and rights, never a live glyph registry. Actual geometry belongs in
`tools/glyphs.py`. Both Classic and Pop use the shared catalog/style validator.
`brand` groups must share glyph and accent. Classic applies its shared
`tools/icon_style.py` contrast fallback without rewriting the source accent.
See `THIRD_PARTY_NOTICES.md` and `docs/research/icon-fidelity-and-demand-2026-09.md`. Brand language for this suite: `docs/BRAND-GUIDE.md`.
For the visual receipt, run `python tools/build_icon_review.py` after Classic
has built. It must show the new icons beside established, unchanged Classic
neighbours and actual-size banners, not only an isolated vendor-logo gallery.

`tools/pop_glyph_metrics.json` is committed on purpose. `popart.py` must stay a pure function of committed inputs — measuring glyph bounding boxes at render time makes output depend on the installed rasteriser version and blows up the SVG drift gate on an unrelated dependency bump. Re-run `tools/measure_pop_glyphs.py` (~33 s) only when glyph geometry changes, and rebuild Pop after.

Pop wallpapers are separate and rarely need rebuilding:

```bash
python tools/build_pop_wallpapers.py  # ~70 s
```

`Wallpapers/manifest.json` belongs to the classic pack and currently has 84 entries. Pop uses `Wallpapers/pop-manifest.json`. The two collections are asserted disjoint.

## Truth gates

Before any PR:

```bash
python tools/build_readme_badge.py
python tools/check_suite_truth.py
python tools/audit_contract.py
python tools/build_issue_prefills.py --check
python tools/check_gradle_envelope.py
python tools/build_dependabot.py --check
```

`check_gradle_envelope.py` holds the Android build inside its own limits. It reads
every Gradle root's `compileSdk` / `minSdk` / AGP / Kotlin and every coordinate it
declares, and fails if a version sits above the ceiling recorded in
`tools/gradle_envelope.json` — which carries the reason and the evidence for each
cap. It also fails if a Gradle root has no `dependabot.yml` entry, if a workflow
installs an Android platform other than the one its module compiles against, or if
the Compose compiler is wired the wrong way for the Kotlin major in use. The
ignore rules those caps imply are *generated*, not hand-written:
`build_dependabot.py --check` fails on drift, because hand-written rules were
silently deleted once already (9f5d266d) and the four Dependabot PRs that followed
— #122 #125 #126 #132 — all failed CI for the same three libraries. To take a
capped dependency forward, change the envelope first (migration steps are at the
end of `gradle_envelope.json`), watch CI go green, then lift the ceiling and
regenerate.

`check_suite_truth.py` fails stale README/agent/doc claims, catalog/Gradle/version metadata drift, an AGENTS.md suite-table or wallpaper-count mismatch, missing stamped README block, and the `line-v*` trap. Core Line's prefix is `coreline-v*`.

`build_issue_prefills.py --check` holds the README's prefilled icon-request links to
`.github/ISSUE_TEMPLATE/`: a renamed form file, a new or shadowing field `id`, or a
hand-edited `<!-- issue-prefills -->` stamp fails it. Regenerate with
`python tools/build_issue_prefills.py` — never hand-edit an `issues/new?...` URL. GitHub
prefills `input`/`textarea` fields only, and the tool refuses to promise more.

## Product verification shortcuts

- Icon Pack: four generators + `python tools/validate.py`.
  Square PNGs get a raster presence pass (`tools/presence.py`) after svg2png —
  night keyline + accent bloom as rings. Vectors stay style-AA. Banners skip it.
- Icon Pack UI (all three modules): `python tools/check_ui_resources.py`
  (every resource reference resolves, no focusable view stranded),
  `python tests/test_navigation_graph.py` (every clickable has a handler, every
  screen is reachable, every focus edge lands somewhere),
  `python tests/test_tv_scale.py` (the dp box normalises per panel), and
  `python tools/build_app_ui_mockups.py --check` against the frames committed in
  `docs/` — regenerate them with the same tool and no flag after any layout
  change, then commit the PNGs *and* `docs/app-ui-mockups.json`. The check
  compares sources and drawn structure, not PNG bytes: the manifest records a
  hash of every layout, values file, catalog, manifest, font and pasted raster
  the frames were built from, plus the generator's own source and what each
  frame drew. Text rendering is not reproducible across machines — the same
  pins on CPython 3.11 and 3.12 differ in every frame — so bytes are the wrong
  thing to compare, and structure is the stricter half anyway: it fails on a
  one-dp move.
- Changelog: `python tests/test_changelog_contract.py`. `## [Unreleased]` runs
  Added, Changed, Fixed once each, in that order, every top-level bullet leading
  `- **Name.**` — `prepare_release.py` takes those leads as the in-app what's-new
  card, one `re.search` per kind, so a second heading of the same kind hides
  every bullet in it. Lead a gate bullet with its backticked `tests/` or `tools/`
  path to keep the receipt out of the card.
- CI coverage: `python tests/test_ci_coverage.py`. Every file in `tests/` and
  every `tools/check_*.py` / `tools/validate*.py` must be invoked by a workflow,
  or excused in that file's `LOCAL_ONLY` with a written reason. A new gate that
  is not wired into `.github/workflows/suite-ci.yml` — the one with no path
  filter — does not exist: five tests and two validators had been local-only
  before that check was written.
- Pixel Neon: `python tools/build_pixel_neon.py`, `python tools/validate_pixel_neon.py`, plus `cd pixel-neon && ./gradlew :app:lintDebug :app:assembleDebug`.
- Core Builds Pop: `python tools/build_pop.py` + `python tools/validate_pop.py` + `python tests/test_pop.py`.
- Core Line: `cd ticker && npm test`.
- Core Shift: `python tools/validate_motion_feed.py` plus Android lint/build in CI.
- Core Motion: `python tools/validate_projectivy_plugin.py` plus Android lint/build in CI.
- Core Doctor: `cd doctor && ./gradlew :app:testDebugUnitTest` where Android SDK exists.

If local SDK/device access is missing, say so. A named unverified step is better than a confident guess.
