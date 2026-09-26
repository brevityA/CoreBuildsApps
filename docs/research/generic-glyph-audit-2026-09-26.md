# Generic Glyph audit — 26 September 2026

## Decision

Every generic app-letter row was reviewed as a catalogue, not as a single
visual family. A letter is useful only when the app has no defensible
functional or brand cue. Where the app name, package, component, catalogue
category, or product documentation identifies a function, the letter now sits
in the matching Core Builds functional shell (`broadcast`, `film`, `music`,
`tool`, and so on). The app's short mark remains inside that shell, so the
result is still Core Builds geometry rather than an imported vendor logo.

This is the honest boundary between **inline with the real app** and **a
traced brand mark**: a broadcaster gets a broadcaster cue; it does not get an
invented FOX, CTV, or local-network logo. Stable brand cues that already have
reviewed Core glyphs remain bespoke and were not replaced by a shell.

## Census

The audit covers every catalogue row whose glyph was `app_<letter-or-digit>`:

| Result | Rows |
|---|---:|
| Generic app-letter rows reviewed | 452 |
| Reassigned to a functional shell | 452 |
| Neutral app-letter rows remaining | 0 |
| Existing non-app family-shell rows | 106 |
| Catalogue rows | 962 |

The 452 assignments are emitted by `tools/classify_families.py --write`, not
hand-edited in generated resources. The classifier is deterministic and makes
no claim that a functional shell is the vendor's logo. It chooses the closest
stable product-function cue, then lets the existing Core adaptive mark carry
the app's short identity.

## Functional results

| Core family | Rows moved |
|---|---:|
| broadcast | 320 |
| tool | 36 |
| film | 20 |
| sport | 12 |
| gaming | 11 |
| music | 10 |
| store | 11 |
| anime | 5 |
| vpn | 4 |
| photos | 8 |
| debrid | 8 |
| kids | 3 |
| browser | 2 |
| files | 2 |
| **Total reassigned** | **452** |

The family decision uses this evidence in order:

1. An explicit catalogue category already reviewed for the mapped app.
2. The app name and mapped package/component, with narrow terms taking
   precedence over broad `TV`, `play`, or `media` matches.
3. Product or package research for names that are ambiguous in isolation.
4. A documented explicit correction for known misleading names, recorded in
   the classifier so a future regeneration cannot silently change it.

Examples of corrections include Ace Stream and Better XC as broadcast/player
services rather than browser or gaming marks, Oilers+, MUTV, and TOD as sport,
F-Droid and APK tools as store/tool, EarthCam and Moonfin as broadcast, and
Gallery/Mi Gallery as photos.

## Final seven research checks

The last seven neutral rows were individually checked before the final write:

| Row | Evidence used | Functional result |
|---|---|---|
| AllSaves Social | `com.allsaversocial.gl` is documented as a social-media downloader/save utility | `tool_A` |
| Amnis | `com.amnis` is described as a torrent/video player | `broadcast_A` |
| Avoid | `com.hritwik.avoid` is the Void Jellyfin client | `broadcast_A` |
| Flicky | `app.flicky` is an Android TV F-Droid client | `store_F` |
| Fluffy | `app.fluffy` is a TV-friendly file manager/archive utility | `files_F` |
| Gain | `com.trgain.mikrogain` is a series/movie streaming service | `film_G` |
| GenPlay | `com.genplay.apps` is a games/rewards app | `gaming_G` |

Research references for this last check include the [AllSaves package
record](https://www.apkshub.com/app/com.allsaversocial.gl), [Amnis package
listing](https://amnis.en.aptoide.com/app), [Void Play listing](https://play.google.com/store/apps/details?id=com.hritwik.avoid&hl=en),
[Flicky F-Droid record](https://apt.izzysoft.de/fdroid/index/apk/app.flicky),
[Fluffy source](https://github.com/mlm-games/fluffy), [Gain package
listing](https://apkcombo.com/gai%CC%87n/com.trgain.mikrogain/), and [GenPlay
package record](https://apkpure.com/genplay/genplay.money/download). These
sources support the functional classification only; their vendor artwork was
not imported.

## Rendering and research boundary

- `tools/catalog.json` remains the only catalogue source of truth.
- `tools/classify_families.py` is deterministic and supports `--show FAMILY`
  for inspecting every assignment; it changes only current `app_*` rows.
- `tools/glyphs.py` supplies the shared monoline family shells and adaptive
  app marks; no vendor SVG or bitmap is imported.
- Existing bespoke marks, sourced colours, duotone declarations, and verified
  component mappings are preserved.
- A future pass may promote a functional shell to a bespoke brand-informed
  glyph when a first-party page, APK/resource evidence, or an auditable brand
  source supports a distinctive cue. This pass deliberately does not invent
  vendor marks from weak or conflicting images.

The generated square masters, banners, raster assets, appfilters, Glyphs
resources, catalogue table, previews, and UI mockups must be rebuilt from this
catalogue before release.
