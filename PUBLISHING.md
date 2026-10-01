# Publishing CoreBuildsApps

This repo ships five products from independent Gradle roots. Keep versioned tags and floating Downloader tags separate. Never use GitHub `latest/download` for permanent Downloader URLs because `latest` can point at the wrong app.

## Stable tags and assets

| Product | Versioned tag | Floating tag | Stable APK asset | Downloader |
|---|---|---|---|---|
| Icon Pack | `v<version>` | `iconpack` | `iconpack-release.apk` plus legacy `app-release.apk` | `5270601` |
| Core Line | `coreline-v<version>` | `coreline` | `coreline-release.apk` | `7375676` |
| Core Shift | `shift-v<version>` | `shift` | `coreshift-release.apk` | `8829421` |
| Core Motion | `motion-v<version>` | `motion` | `coremotion-release.apk` | `[USER TO SUPPLY]` |
| Core Doctor | `doctor-v<version>` | `doctor` | `coredoctor-release.apk` | `8664938` |
| Core EQ | `coreeq-v<version>` | `coreeq` | `coreeq-release.apk` | `7946159` |
| Core EQ (test channel) | none — pushes to `main` only | `coreeq-test` | `coreeq-debug.apk` | none |
| Icon Pack (test channel) | none — `workflow_dispatch` only | `iconpack-test` | `iconpack-test.apk` | `9255317` |

Do not create `line-v*` tags. Core Line is `coreline-v*`.

### The Icon Pack test channel

`iconpack-test` is a public prerelease lane driven by
`.github/workflows/iconpack-test-apk.yml`, dispatched by hand. It exists so a
build can be put on a real Android TV before it is tagged, and it is deliberately
**not** the production pack:

- package `tv.corebuilds.iconpack.test`, so it installs beside the real pack
  rather than over it;
- debug-signed — the workflow reads no `KEYSTORE_*` value;
- its updater authority is `…test.update`, so **it never auto-updates**. Anyone
  who installs it stays on that build until they sideload another.

Each dispatch force-moves the `iconpack-test` tag and replaces the asset, so the
filename is load-bearing: `iconpack-test.apk` is baked into a numeric code that
cannot be repointed afterwards. Never add a version or commit sha to it.

## Before tagging

1. Update `suite.json`.
2. Update the selected app Gradle `versionName` and `versionCode`.
3. For Icon Pack, also update `tools/catalog.json` `meta.version`, `Latestrelease/version.json`, and run all four generators.
4. For updater apps, ensure matching metadata in `Latestrelease/`.
5. Run:

```bash
python tools/build_readme_badge.py
python tools/check_suite_truth.py
python tools/audit_contract.py
```

6. Let PR CI pass.

## Tagging

```bash
git tag <tag>
git push origin <tag>
```

The per-app workflow builds the stable APK asset and moves the matching floating tag/release. `suite-release.yml` covers the other four apps and can run as a signed dry-run from `workflow_dispatch`; publishing needs a tag push, because it names the release after the ref it ran on (see `docs/RELEASE-INFRA.md`). Icon Pack is not on its tag list — bare `v*` belongs to `build.yml`, which alone repoints the floating `iconpack` tag and ships `app-release.apk` plus the Glyphs companion.

### Core Line: `tools/release_coreline.sh`

Core Line has a script for the whole release, run from the repo root with `gh`
logged in. It reads the version from Gradle, so do the version-bump PR first.

```bash
tools/release_coreline.sh check      # read-only: main on the new version, CI green, tag free
tools/release_coreline.sh tag        # push coreline-v<version>; core-line-apk.yml publishes it
tools/release_coreline.sh metadata   # once the APK is live: opens the updater-metadata PR
```

`tag` needs no secrets on your machine: `core-line-apk.yml` signs with the
repo's `KEYSTORE_*` Actions secrets and moves the floating `coreline` release.
`release` is the fallback for when CI cannot publish; it builds and signs
locally and so needs your own copy of the key in `KEYSTORE_PATH`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, and refuses to publish if the
certificate differs from the APK already on `coreline`. `metadata` writes
`Latestrelease/coreline-version.json` from the published APK's SHA-256 and opens
a PR; merge it last, because installed copies act on it.

## Signing secrets

Keystores and Play credentials never go in git. GitHub Actions secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Play credentials are `[USER TO SUPPLY]` and must be environment-scoped if Play upload automation is added.
