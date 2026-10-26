# Icon Pack 2.5.3 — issue triage and first patch

Reviewed **26 October 2026 (Australia/Sydney)** against `main` at
`da6812d49b9fd41bb41a072772b13223b2bff68c` and all **10 open issues**.
This is work in progress, not a published release.

## Release baseline

- Source at the start of this branch: **2.5.2 / versionCode 60**, including
  the glyphs-app identity fix merged in [PR #384](https://github.com/brevityA/CoreBuildsApps/pull/384).
- The [2.5.2 Play build](https://github.com/brevityA/CoreBuildsApps/actions/runs/40260587118)
  completed successfully, including Play upload-key decoding, bundle signature
  verification and upload of `core-builds-play-bundles`. This does **not** prove
  an upload or rollout in Play Console.
- Published sideload release and `Latestrelease/version.json`: **2.5.1 / 59**.
  No `v2.5.2` release was listed at review time. The glyphs fix remains in
  `[Unreleased]`; do not invent a published 2.5.2 changelog entry.
- Next patch is staged as **2.5.3 / 61**, shared by the Play and sideload
  variants. The public update feed stays unchanged until release publication.

## First batch: five mapping fixes

All five reports include a component from the in-app scanner. Each app already
has artwork, but the reported launch component was absent from the catalog.
The branch adds the exact reported names, retains every older mapping and
keeps all artwork unchanged. These are device-reported mappings, not a claim
that the apps were installed and tested in this workspace.

| Issue | App | Newly covered launch component |
|---|---|---|
| [#377](https://github.com/brevityA/CoreBuildsApps/issues/377) | F-Droid | `org.fdroid.fdroid/org.fdroid.MainActivity` |
| [#378](https://github.com/brevityA/CoreBuildsApps/issues/378) | Voltra TV | `com.amhkor.voltratv/com.voltra.voltratv.tv.SplashActivity` |
| [#379](https://github.com/brevityA/CoreBuildsApps/issues/379) | Poweramp Equalizer | `com.maxmpz.equalizer/com.maxmpz.audioplayer.standard_adaptive` |
| [#380](https://github.com/brevityA/CoreBuildsApps/issues/380) | ABC iview | `au.net.abc.iview/au.net.abc.iview.ui.router.TvLauncherActivity` |
| [#383](https://github.com/brevityA/CoreBuildsApps/issues/383) | Moonfin | `org.moonfin.androidtv/org.moonfin.androidtv.MainActivity` |

Implementation details:

- F-Droid, Voltra and Poweramp use activity namespaces different from their
  installed package IDs. Do not rewrite them to `package/.MainActivity`.
- Voltra's scan text says Projectivy while its launcher field says Monet.
  That inconsistency does not change the literal component to map, and the
  mapping applies in either compatible launcher. It is recorded in provenance.
- Catalog provenance cites each issue separately from the older reference or
  device audit. New components are not falsely attributed to Projectivy 1.1.9.
- Regeneration updates all three packs and both `res/xml` and `assets`
  appfilters. Banners map to the existing banner; the two glyph packs map to
  the existing square artwork.
- `tests/test_mapping_hygiene.py` pins the new and legacy mappings, their
  provenance, and all six generated appfilters. The new catalog regression
  failed for all five reports before the fix.
- Counts: **1022 icons**, **1246 catalog components**, **1957 emitted
  component mappings** (full and shorthand forms where applicable).

Leave these issues open until the changes are reviewed and shipped; ask for
on-device confirmation after users install the new build and reapply the pack.

## Requests not marked ready

| Issue | Status | What is needed |
|---|---|---|
| [#382](https://github.com/brevityA/CoreBuildsApps/issues/382) — SPORT FM TV | New-icon request; component supplied, artwork pending | `gr.sportfmtv.app/com.yinzcam.connectedtv.MainActivity` is reported from a SHIELD. Obtain a verifiable stock-icon reference from the linked Play listing or a clear screenshot, then draw and review its Core monoline mark, square and banner. Do not guess artwork or substitute an unrelated sports icon. |
| [#352](https://github.com/brevityA/CoreBuildsApps/issues/352) — TVLok | Still `needs-info`; no new reporter reply | The component field contains `Using monet launcher`, not `package/activity`. Need the scanner's exact component and a screenshot of the correct icon. Keep the two existing package guesses flagged unverified until there is evidence. |

SPORT FM TV can join this release once its artwork is verified. The five
mapping fixes do not depend on that research or on TVLok's reply.

## Standing issues

- [#71 — version truth](https://github.com/brevityA/CoreBuildsApps/issues/71):
  keep Gradle, suite, catalog, bundled metadata, README and agent-guide versions
  synchronized. The published feed intentionally lags an unshipped build.
- [#72 — unverified components](https://github.com/brevityA/CoreBuildsApps/issues/72):
  ongoing evidence collection, not a reason to invent launcher activities.
  This patch does not clear or add any unverified flags.
- [#73 — Downloader QA](https://github.com/brevityA/CoreBuildsApps/issues/73):
  after a real release/floating-tag move, verify the stable APKs' sizes,
  package IDs, version codes, signatures and existing Downloader codes.
  This branch does not move tags or claim that external QA happened.

## Keep outside this compatibility patch

- [#370 — Plus / Projectivy wallpapers](https://github.com/brevityA/CoreBuildsApps/pull/370):
  separate feature work with TV/provider verification still needed.
- [#360 — Premium](https://github.com/brevityA/CoreBuildsApps/pull/360) and
  [#371 — launch review](https://github.com/brevityA/CoreBuildsApps/pull/371):
  settle the paid-tier decision and reconcile it with existing Plus before
  including it in a later feature release. No paid-tier changes here.
- [#381 — Play Billing 9.1.0](https://github.com/brevityA/CoreBuildsApps/pull/381):
  major dependency update needs its own Play compilation, minimum-SDK and
  purchase-flow review. It is not needed for any of the five mapping fixes.
- The open Core Shift, Core Line dependency, Play-console documentation and
  morph-experiment PRs are not part of these icon-pack issue fixes. In
  particular, the morph experiments are marked test-only.

## Verification completed on this branch

- `python tools/validate.py`: **Validated 1022 icons · 1957 components ·
  33768 checks run; 33768 passed**.
- Mapping hygiene: **12 tests**; glyph-pack contracts: **40 tests**; icon
  identity: **72 tests**; themed icons: **4 tests** — all passed.
- Play listing/build, changelog, updater-manifest and CI-coverage tests passed.
- Suite truth, release/update contracts, issue-prefill, Gradle envelope,
  Dependabot, UI resources, appfilter integrity, glyph metrics/fit, palette,
  look-alikes, UI mockups, Play listing freshness and TV layout checks passed.
- Diff verification confirms **no catalog SVG, square/banner drawable,
  launcher-layer or published-feed changes**. The changed preview images
  reflect the new version, counts and release highlights, not new icon art.
- **Not run:** a new Android APK/AAB build or device test. Java, `adb` and the
  Android SDK are absent from this workspace. No 2.5.3 PR has been merged, no
  release has been published, and no issue has been closed by this work.

## Before release

1. Finish the scope decision for SPORT FM TV; do not count it as delivered yet.
2. Run the catalog generators, validator, mapping/identity/glyph-pack tests,
   release truth gates and generated UI/store-listing checks.
3. Review and merge this branch after CI passes; nothing is auto-merged here.
4. Build the **2.5.3** Play AABs and sideload APKs from that reviewed source.
   The existing 2.5.2 AABs do not contain these five new mappings.
5. Publish the sideload release before updating its public manifest, perform
   Downloader QA, and request device confirmation for the five fixes. Play
   Console upload/rollout is a separate owner action.
