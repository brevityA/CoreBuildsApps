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

Do not create `line-v*` tags. Core Line is `coreline-v*`.

## Before tagging

1. Update `suite.json`.
2. Update the selected app Gradle `versionName` and `versionCode`.
3. For Icon Pack, also update `tools/catalog.json` `meta.version`, `Latestrelease/version.json`, and run all four generators.
4. For updater apps, ensure matching metadata in `Latestrelease/`, including an explicit `releaseDate` (`YYYY-MM-DD`).
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
