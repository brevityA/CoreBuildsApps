# Icon Pack 2.5.3 — completed request batch

Reviewed **26 October 2026 (Australia/Sydney)**. All **7 actionable user
reports** from the 10-open-issue triage are implemented in this branch;
the other 3 issues are standing QA trackers. This is not a published release.

**Build version: 2.5.3 / versionCode 61** for both Play and sideload variants.
**1026 icons · 1253 catalog components · 1967 emitted mappings** after the
29 October follow-up below (1023 · 1247 · 1958 in the 26 October batch).

## 29 October follow-up: six more reports

Built on this branch after `main` (Core Premium, the Projectivy walls source)
was merged in, so one release carries both batches. The Play build is the
only one planned for it.

- **New icons, each an original Core monoline redraw with a hashed
  128 px reference and pixel-sampled colour under `tools/reference/`:**
  Plezy (#390, `plezy_play_waves`), StreamFC (#404, `streamfc_ball`) and
  Radio France (#391, `radiofrance_ring`, a two-stop gradient).
- **Device-reported components added to existing icons:** PBS (#402,
  `com.pbs.video.startup.StartupActivity`), YouTube TV (#403,
  `ChrobaltMainActivity`) and 8K Player VIP+ (#405, the same developer's
  build, on the existing 8K Player VIP icon). #406 repeats #380 (ABC iview),
  which this branch already maps.
- **Not built:** Aurora Player (#392) has no Play listing and its photo is not
  readable here, so there is no brand reference to draw from; Blackout (#394)
  is a photo only, with no component. Both stay open for the reporter.

## SPORT FM TV — new icon

[#382](https://github.com/brevityA/CoreBuildsApps/issues/382) supplied the exact
SHIELD scanner component:

`gr.sportfmtv.app/com.yinzcam.connectedtv.MainActivity`

The package and activity namespace differ. No speculative
`gr.sportfmtv.app/.MainActivity` is added.

- Original Core monoline crest, FM lettering and TV tag; normal SPORT banner.
- Red sampled from the listing icon; light lettering declared as a secondary
  paint. The common contrast policy remains unchanged.
- The Greek vendor wordmark is not traced or pasted into the pack.
- Source pages, image hashes and sampling method are recorded in
  [`tools/reference/sportfm-tv/`](../tools/reference/sportfm-tv/).

## TVLok — corrected artwork and verified mappings

[#352](https://github.com/brevityA/CoreBuildsApps/issues/352) still has no valid
reporter-supplied component, but that no longer blocks implementation: the
vendor's official APK was statically inspected instead.

The official download page linked **1.0.192 / code 1129**. Its SHA-256 was
checked against the download filename before inspection. The APK declares:

| Entry | Component |
|---|---|
| MAIN + LAUNCHER activity | `com.tvlok.app/com.tvlok.app.ui.MainActivity` |
| MAIN + LEANBACK_LAUNCHER activity-alias | `com.tvlok.app/com.tvlok.app.ui.TvLauncherActivity` |

The TV alias targets the main activity, but the alias itself must be mapped.
Both exact names are now present in all packs. The old `com.tvlok.tv` package
and `com.tvlok.app.MainActivity` guesses are removed, bringing unverified
mappings down from **75 to 73**.

The generic TL broadcast shell is replaced with an original monoline drawing
of the app's tilted violet tile behind a cyan play tile. Two sampled flat
accents replace the vendor gradients; the normal banner supplies the name.

[`tools/reference/tvlok/`](../tools/reference/tvlok/) retains the static
manifest/resource receipt and small, hash-pinned reference thumbnails.
Original resource hashes and downsized-image hashes are separate. No APK,
vendor pixels or vendor paths are shipped as pack artwork.

The [read-only inspection job](https://github.com/brevityA/CoreBuildsApps/actions/runs/40303327290)
never installed or executed the APK. Its temporary branch-only workflow is
removed from the final patch. No normal build fetches TVLok. This is static
APK evidence, **not physical-device confirmation** or proof of the exact
version installed by the reporter.

## Five existing-icon mapping fixes retained

Each reported component was missing; existing artwork and older mappings are
preserved for all five apps.

| Issue | App | Newly covered component |
|---|---|---|
| [#377](https://github.com/brevityA/CoreBuildsApps/issues/377) | F-Droid | `org.fdroid.fdroid/org.fdroid.MainActivity` |
| [#378](https://github.com/brevityA/CoreBuildsApps/issues/378) | Voltra TV | `com.amhkor.voltratv/com.voltra.voltratv.tv.SplashActivity` |
| [#379](https://github.com/brevityA/CoreBuildsApps/issues/379) | Poweramp Equalizer | `com.maxmpz.equalizer/com.maxmpz.audioplayer.standard_adaptive` |
| [#380](https://github.com/brevityA/CoreBuildsApps/issues/380) | ABC iview | `au.net.abc.iview/au.net.abc.iview.ui.router.TvLauncherActivity` |
| [#383](https://github.com/brevityA/CoreBuildsApps/issues/383) | Moonfin | `org.moonfin.androidtv/org.moonfin.androidtv.MainActivity` |

F-Droid, Voltra and Poweramp use different Java namespaces from their package
IDs; those names are kept literal. Voltra's scan text says Projectivy while
its launcher field says Monet. This is recorded in provenance without
changing the reported component.

All seven requests use the same catalog-to-banner / standalone-glyphs /
Glyphs Pack generation path, including both `res/xml` and `assets` copies.

## Verification

- Validator: **Validated 1026 icons · 1967 components · 33930 checks run;
  33930 passed** (29 October; 1023 · 1958 · 33803 on 26 October).
- Mapping hygiene: **15 tests**; icon identity: **75 tests**; glyph-pack
  contracts: **40 tests**; themed icons: **4 tests** — passed locally.
- Icon centring, uniformity, wordmark, stroke-spacing, palette and look-alike
  checks pass: **70 accepted pairs, no new collisions**, no baseline waiver.
- Play listing/build policy, changelog, updater-manifest, CI coverage and CI
  dependency tests pass.
- Suite truth, release contracts, issue-prefill, Gradle envelope, Dependabot,
  resources, appfilter integrity, glyph metrics/fit and UI/store-listing
  freshness gates pass.
- The new mapping regressions failed before the catalog changes. Reference
  hashes, pixel-derived accents, the TV alias and all six appfilters are
  pinned by offline tests.
- Visual review: [`request-icons-preview.png`](request-icons-preview.png),
  generated with `python tools/build_icon_review.py --requests`. It shows
  both requests at 128/64px, actual 320×180 banners and unchanged neighbours.
- Existing unrelated icon artwork and native launcher branding are unchanged.
  Only SPORT FM TV is added and TVLok is redrawn. The temporary inspection
  workflow is absent from the final tree.

Android compilation, APK packaging and emulator checks run in the PR's CI;
this authoring workspace has no Java or Android SDK. The vendor APK inspection
is not an Android build of this icon pack. Physical-device auto-assignment
still needs confirmation after installation.

## Release boundaries

The branch started at `main` commit
`da6812d49b9fd41bb41a072772b13223b2bff68c`, with **2.5.2 / 60** and the glyphs
identity fix from [#384](https://github.com/brevityA/CoreBuildsApps/pull/384).
The [2.5.2 Play bundle build](https://github.com/brevityA/CoreBuildsApps/actions/runs/40260587118)
succeeded, but those AABs do **not** contain this request batch. Play build
success is not evidence of an upload or rollout in Play Console.

At triage, the sideload feed still advertised **2.5.1 / 59** and no `v2.5.2`
release was listed. The prior glyphs fix therefore remains under
`[Unreleased]`, alongside these changes. This work does not publish or modify
`Latestrelease/version.json`, release assets, tags or Downloader pointers.

No paid-tier changes or major dependency upgrades are included. Premium/Plus
PRs #360/#370, their review #371 and Play Billing upgrade #381 remain separate.

## Remaining release work

1. Review the changes and PR CI results; merge only when approved.
2. Build the **2.5.3 Play AABs** from the reviewed main revision. Each bundle
   goes to its existing Play package; do not reuse the 2.5.2 artifacts.
3. Publish the sideload release before its public update manifest is advanced.
4. Perform [#73 Downloader QA](https://github.com/brevityA/CoreBuildsApps/issues/73)
   and request on-device confirmation for the seven user reports. Keep them
   open until the fixes are shipped; none is closed by this work.
5. Continue the standing [#71 version-truth](https://github.com/brevityA/CoreBuildsApps/issues/71)
   and [#72 unverified-components](https://github.com/brevityA/CoreBuildsApps/issues/72)
   trackers. Removing two disproven guesses does not complete all mapping QA.
