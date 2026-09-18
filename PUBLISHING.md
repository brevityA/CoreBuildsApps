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

The per-app workflow builds the stable APK asset and moves the matching floating tag/release. `suite-release.yml` can also run as a signed dry-run or published release when secrets are configured.

## Signing secrets

Keystores and Play credentials never go in git. GitHub Actions secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Play credentials are `[USER TO SUPPLY]` and must be environment-scoped if Play upload automation is added.
