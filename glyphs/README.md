# Core Builds Glyphs

The icon pack's square twin: the same icons, mapped to the same components,
with no banners. Package `tv.corebuilds.iconpack.glyphs`, label *Core Builds
Glyphs*. It is not a sixth product and not in `suite.json` — it is part of the
Icon Pack release and has no version of its own.

## Why a second package exists

A launcher auto-applies whatever the pack it was told to use maps in that
pack's own `appfilter.xml`, read out of the APK. One package can therefore only
ever auto-apply one art style. `tv.corebuilds.iconpack` maps every component to
its 16:9 banner — the default since 1.9.5 — so the square glyphs have to be a
second package, and the in-app Banners/Glyphs toggle
(`app/src/main/java/tv/corebuilds/iconpack/GlyphsCompanion.kt`) works by
telling the launcher to apply one package or the other.

There is a third package to keep straight. `tv.corebuilds.glyphs` is the
`:app` module's `glyphs` flavor: the whole icon pack — catalog, wallpapers,
settings — built with the square appfilter, for a user who wants glyphs as
their only pack and no toggle. This module stays the resource-only one the
toggle installs, and its generated `appfilter.xml` / `drawable.xml` are written
into `app/src/glyphs/` at the same time by the same generator, byte-identical
(`tools/build_banners_pack.py --check` fails if they ever part). That is also
why this pack is labelled Core Builds Glyphs **Pack**: both appear in a
launcher's icon-pack list mapping square glyphs.

1.9.4 shipped the opposite split, a banners companion
`tv.corebuilds.iconpack.banners`. It is retired; do not resurrect `banners/`.

## What lives here, and what does not

Committed, and all of it hand-written:

| Path | What it is |
|---|---|
| `src/main/AndroidManifest.xml` | The launcher discovery intent filters — the same set the icon pack declares, so any launcher that lists one lists the other — plus `queries` for the icon-pick forward, and one `MAIN` filter (`LAUNCHER` + `LEANBACK_LAUNCHER`) on the home activity. |
| `src/main/java/…/GlyphsActivity.java` | Translucent, no UI. Exists so launchers can discover the pack through an activity intent filter, and forwards icon-pick requests to the icon pack. It has to stay translucent: an opaque window would flash on every forward. |
| `src/main/java/…/GlyphsHomeActivity.java` | The drawer / Android TV row entry. What the pack is, how many icons and components it covers, and the way through to the icon pack where the toggle lives. |
| `src/main/res/layout/`, `res/drawable/`, `res/color/`, `res/values/` (not the generated `strings.xml`) | That one screen: a stack of framework widgets, and a state selector for the TV focus ring. |
| `src/main/res/xml/appfilter.xml`, `src/main/res/xml/drawable.xml` | **Generated** — see below. |
| `src/main/assets/appfilter.xml`, `src/main/assets/drawable.xml` | **Generated** copies for launchers that read `assets/` instead of `res/xml/`. |
| `src/main/res/values/strings.xml` | **Generated** — the `app_name` launchers list. UI strings live in `strings_home.xml`; never add one here. |
| `build.gradle.kts` | `copyGlyphArt` + the Android config. |

Deliberately absent:

- **No Kotlin and no library dependencies.** Both activities are Java against
  the framework, and `tests/test_glyphs_pack.py` fails the day an
  `implementation(` appears in `build.gradle.kts` — the front door was added
  without giving up the property that makes this module cheap to build and
  impossible to drift.
- **No art in git.** The glyphs and fallback furniture are copied from
  `app/src/main/res/drawable-nodpi` at build time by `copyGlyphArt`, so each
  art file exists exactly once and the two packages cannot drift. That copy
  takes the square WebPs only — `*_banner.webp` is excluded, because banners
  are the icon pack's own art — plus `cb_banner.png`, the `cb_back_*` /
  `cb_mask` / `cb_upon` fallback furniture its appfilter names, `aliases.xml`
  (identical glyphs ship once, by alias) and the launcher/TV mipmaps.
- **No version.** `versionCode` / `versionName` are read out of
  `app/build.gradle.kts`, because the companion is released *with* the icon
  pack and the pack installs exactly its own version: a companion a version
  behind would silently miss every icon added since.

## Generated files

`tools/build_icons.py` writes this module's square `appfilter.xml` and its icon
browser straight from `tools/catalog.json`; `tools/build_banners_pack.py` then
derives the *icon pack's* banner XML from those files (never from the catalog,
so the two packages cannot disagree about which components are covered) and
writes `strings.xml` here. Run them in this order, after `build_banners.py` has
produced the banner art the derivation checks for:

```bash
python tools/fit_classic_glyphs.py
python tools/build_icons.py
python tools/build_banners.py
python tools/build_banners_pack.py
```

Never hand-edit `appfilter.xml`, `drawable.xml`, `assets/*` or `strings.xml`
here.

## Build

From the repo root — `glyphs/` is a module of the root Gradle build, not its
own root:

```bash
./gradlew :glyphs:assembleRelease
# -> glyphs/build/outputs/apk/release/*.apk
```

`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` are read
from the environment exactly as `:app` reads them; without them the release APK
is unsigned. Signing is not cosmetic here: the icon pack refuses to install a
companion whose certificate differs from its own, so an unsigned or
separately-signed companion is a broken toggle. `build.yml` fails a tag whose
two APKs do not carry the same SHA-256 certificate digest.

`assembleRelease` is not the whole story for a usable build — `copyGlyphArt`
runs off `preBuild`, so the art in the APK is whatever `app/`'s generated
`drawable-nodpi` currently holds. Regenerate before you build if the catalog
moved.

## Shipping

`build.yml` copies the built APK to `dist/iconpack-glyphs-release.apk` and
attaches it to both the `v<version>` release and the floating `iconpack`
release. **The filename is a contract**: it is the `GlyphsCompanion.ASSET`
literal the icon pack downloads, and the in-app toggle offers no fallback when
that asset is missing from the release it expects.

## Gates

```bash
python tools/build_banners_pack.py --check   # generator in sync, every drawable present
python tools/validate.py                     # catalog + appfilter
python tests/test_glyphs_pack.py             # the joins: both packs' resources,
                                             # manifest filters, asset filename,
                                             # release workflow
```

None of them needs an Android SDK. `tests/test_glyphs_pack.py` is the one that
holds the four separately-edited things together: the two packages' generated
resources, this manifest, the icon pack code that names the package, and the
workflow that publishes the asset under the filename that code downloads.
