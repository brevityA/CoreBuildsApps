# CoreBuildsApps agent guide

## Suite map

`suite.json` is the suite registry. It owns product names, current versions, package IDs, release prefixes, floating tags, Downloader codes, and Gradle roots.

| Product | Path | Package ID | Version | Downloader / stable tag |
|---|---|---|---:|---|
| Core Builds Icon Pack | `app/` with repo-root Gradle | `tv.corebuilds.iconpack` | `1.9.6` | `5270601` / `iconpack` |
| Core Line | `ticker/` + `ticker/android/` | `dev.corebuilds.line` | `1.3.0` | `7375676` / `coreline` |
| Core Shift | `shift/` | `dev.corebuilds.shift` | `2.3.5` | `8829421` / `shift` |
| Core Motion | `motion-plugin/` | `tv.corebuilds.motion` | `1.0.0` | `[USER TO SUPPLY]` / `motion` |
| Core Doctor | `doctor/` | `dev.corebuilds.doctor` | `0.1.0` | `8664938` / `doctor` |

(Retired 2026-09-24: Core Builds Pixel Neon and Core Builds Pop. The suite
keeps one icon pack. Their shipped tags stay as history; do not resurrect
their modules, manifests or workflows.)

`glyphs/` is the one intentional exception to "one app, one Gradle root",
and it is not a separate product: Core Builds Glyphs
(`tv.corebuilds.iconpack.glyphs`) is the icon pack's square companion,
released in the same `v*` release as `iconpack-glyphs-release.apk` and
versioned from `app/build.gradle.kts`. Banners are the icon pack's default
(again, since 1.9.5): its appfilter maps every app to the 16:9 banner. A
launcher auto-applies whatever the selected package's appfilter maps, so the
in-app Banners/Glyphs toggle works by pointing launchers at one package or the
other (`GlyphsCompanion.kt`). The companion has no Kotlin, no dependencies, no
launcher entry and no committed art: `python tools/build_icons.py` writes its
square appfilter and browser, `python tools/build_banners_pack.py` derives the
icon pack's banner XML from them (run it after `build_icons.py` and
`build_banners.py`), and its Gradle build copies the glyph WebP from `app/`.
`tests/test_glyphs_pack.py` holds the package name, manifest filters, asset
filename and release workflow together. (1.9.4 shipped the opposite split, a
Banners companion `tv.corebuilds.iconpack.banners`; it is retired - do not
resurrect `banners/`.)

Do not merge Gradle roots. Do not split the GitHub repo. Do not rename package IDs. Do not repoint floating Downloader tags. New apps use `tv.corebuilds.<name>`; the current `dev.` IDs are history, not a style to copy.

## One rule

`tools/catalog.json` is the only source of truth for the icon pack. Never hand-edit generated icon XML, icon/banner art, `aliases.xml`, `keep.xml`, `docs/IconPackList.md`, `docs/preview.*`, or generated wallpaper output under `Wallpapers/series-8-amoled/**` and `Wallpapers/series-9-deep-space/**`.

Classic pack changes run the four generators and the validator:

```bash
python tools/icon_palette.py         # 18-colour fallback palette for icons with no brand colour
python tools/fit_classic_glyphs.py   # optical fit of undersized/off-centre glyphs; reads the catalog
python tools/build_icons.py
python tools/build_banners.py
python tools/build_banners_pack.py   # the icon pack's banner XML from the glyph companion's; needs the banner art above
python tools/build_branding.py
python tools/build_brand_preview.py
python tools/validate.py
python tests/test_icon_identity.py    # 55 style/colour/reference/mapping regressions
```

Paste the validator receipt. Current receipt: `Validated 961 icons · 1179 components`.

**The pack identity takes precedence over literal vendor-logo reproduction.**
Reviewed brand entries use `style: core_monoline`: 32px rounded primary strokes,
26.2px / 21.8px detail, no solid fills/effects/containers, and one accent. Keep
the common Outfit + category + cyan/violet rail banner for NoBuffr and every
other reviewed app. Do not reintroduce vendor-wordmark-only banners.

Catalog `artwork` entries are `usage: reference-only`: pinned SVG hashes, URLs
and rights, never a live glyph registry. Actual geometry belongs in
`tools/glyphs.py`.
`brand` groups must share glyph and accent. The pack applies its shared
`tools/icon_style.py` contrast fallback without rewriting the source accent.
See `THIRD_PARTY_NOTICES.md` and `docs/research/icon-fidelity-and-demand-2026-09.md`. Brand language for this suite: `docs/BRAND-GUIDE.md`.
For the visual receipt, run `python tools/build_icon_review.py` after the pack
has built. It must show the new icons beside established, unchanged
neighbours and actual-size banners, not only an isolated vendor-logo gallery.

`Wallpapers/manifest.json` belongs to the icon pack and currently has 96 entries.

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
  Square art gets a raster presence pass (`tools/presence.py`) after svg2png —
  night keyline + accent bloom as rings. Vectors stay style-AA. Banners skip it.
- Icon Pack UI: `python tools/check_ui_resources.py`
  (every resource reference resolves, no focusable view stranded),
  `python tests/test_navigation_graph.py` (every clickable has a handler, every
  screen is reachable, every focus edge lands somewhere),
  `python tests/test_tv_scale.py` (the dp box normalises per panel), and
  `python tools/build_app_ui_mockups.py --check` against the frames committed in
  `docs/` — regenerate them with the same tool and no flag after any layout
  change, then commit the art *and* `docs/app-ui-mockups.json`. The check
  compares sources and drawn structure, not art bytes: the manifest records a
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
- Core Line: `cd ticker && npm test`.
- Core Shift: `python tools/validate_motion_feed.py` plus Android lint/build in CI.
- Core Motion: `python tools/verify_motion_plugin.py` plus Android lint/build in CI.
- Core Doctor: `cd doctor && ./gradlew :app:testDebugUnitTest` where Android SDK exists.

If local SDK/device access is missing, say so. A named unverified step is better than a confident guess.
