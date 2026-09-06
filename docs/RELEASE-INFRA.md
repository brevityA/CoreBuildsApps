# CoreBuildsApps release infrastructure

## CI overview

The suite now has one central PR gate plus the existing per-app workflows.

- `.github/workflows/suite-ci.yml`
  - `contracts`: runs `python tools/audit_contract.py` and fails if any shippable app is missing a tracked workflow/artifact path, updater metadata drifts from Gradle, an updater regresses to stale manifest URLs / code-0 fallback / unbounded reads, or keystore-like files appear in git.
  - `android`: matrix builds every Android app (`Icon Pack`, `Core Line`, `Core Shift`, `Core Doctor`, `Core Motion plugin`) with `lintDebug`, unit tests, and `assembleDebug`, then uploads the debug APK artifact.
  - `web-and-content`: runs Core Line Node parser/update/feed tests and Python content/validator tests.
- Existing per-app workflows remain for compatibility with current release tags and Downloader flows:
  - `build.yml` — Icon Pack.
  - `core-line-apk.yml` — Core Line.
  - `core-shift-apk.yml` — Core Shift.
  - `core-doctor-apk.yml` — Core Doctor.
  - `core-motion-apk.yml` — Core Motion plugin.

## Release workflow

`.github/workflows/suite-release.yml` is the new release/dry-run entry point.

Triggers:

- Tag push:
  - `iconpack-v*`
  - `coreline-v*`
  - `shift-v*`
  - `doctor-v*`
  - `motion-v*`
- Manual `workflow_dispatch` with `app` and `publish` inputs.
  - `publish=false` is a dry run: builds artifacts and checklist only.
  - `publish=true` requires signing secrets and publishes GitHub Release assets.

For each selected app, the workflow:

1. Installs JDK 17 and the Android SDK.
2. Decodes the signing keystore only from GitHub Actions secrets.
3. Fails published releases when signing secrets are absent.
4. Runs release lint, release unit tests, `assembleRelease`, and `bundleRelease`.
5. Copies the stable Downloader APK filename into `dist/`.
6. Verifies the APK signature on published releases.
7. Builds AAB output for Google Play upload.
8. Generates updater metadata with `apkSha256` for apps with in-app sideload updaters.
9. Uploads a release checklist artifact.
10. Publishes GitHub Release assets when requested.

## Required GitHub secrets

Never commit keystores or credentials. Configure these in repository or environment secrets:

- `KEYSTORE_BASE64` — base64-encoded upload/release keystore.
- `KEYSTORE_PASSWORD` — keystore password.
- `KEY_ALIAS` — signing alias.
- `KEY_PASSWORD` — key password.

Play credentials are intentionally not in the repo. If Play upload automation is later enabled, use an environment-scoped service account secret such as `PLAY_SERVICE_ACCOUNT_JSON` and restrict it to release jobs only.

## Updater metadata

Updater metadata lives in `Latestrelease/` and is backward-compatible JSON:

- `Latestrelease/version.json` — Icon Pack.
- `Latestrelease/coreline-version.json` — Core Line.
- `Latestrelease/shift-version.json` — Core Shift.

Mandatory fields:

- `versionCode`
- `versionName`
- `apkUrl`
- `releaseDate`
- `minSdk`

Optional but now generated for releases:

- `apkSha256`
- `releaseNotesUrl`

Runtime behavior:

- App compares the remote `versionCode` against `BuildConfig.VERSION_CODE` only.
- No package-manager `0` fallback is allowed.
- Manifest reads are bounded.
- APK downloads must stay on HTTPS GitHub allowlisted hosts.
- APK is rejected before install if the package name differs, versionCode is not the expected newer code, signature certificate does not match the installed app, or `apkSha256` is present and mismatched.

## Cutting a sideload/GitHub release

1. Bump the selected app's `versionCode` and `versionName` in its Gradle file.
2. Update the matching `Latestrelease/*.json` version fields. `apkSha256` can be omitted before the first workflow run; the release workflow generates it in `dist/update-metadata.json`.
3. Run locally where Android SDK exists, or open a PR and let `Suite CI gate` run:
   - `python tools/audit_contract.py`
   - app lint/tests/build from `suite-ci.yml`
4. Merge to main.
5. Push the appropriate release tag, or run `Suite release` manually with `publish=false` first.
6. Confirm artifacts:
   - stable APK filename (`iconpack-release.apk`, `coreline-release.apk`, `coreshift-release.apk`, `coredoctor-release.apk`, or `coremotion-release.apk`)
   - AAB
   - `update-metadata.json` for in-app-updater apps
   - checklist summary
7. For published releases, `suite-release.yml` syncs `update-metadata.json` back to the matching `Latestrelease/*.json` on `main` after `tools/audit_contract.py` passes.
8. Smoke test updater:
   - current `versionCode` == metadata `versionCode`: no update prompt.
   - metadata `versionCode` > installed: prompt appears.
   - corrupt APK or wrong hash/signature: app reports failure and does not invoke installer.

## Google Play release runbook

1. Ensure the app's target API satisfies current Play requirements.
   - Android TV apps: target API 34+ under the verified 2026 rule.
   - Non-TV/general apps: target API 36+ for new apps/updates after 2026-08-31.
2. Use the AAB from `suite-release.yml`.
3. Enroll/use Play App Signing; keep upload key in GitHub secrets only.
4. Fill Play Console app content:
   - Data safety form.
   - Privacy policy URL `[USER TO SUPPLY]`.
   - Permissions declarations / restricted permission justifications.
   - TV listing assets and screenshots for TV apps.
5. Prefer a Play flavor without `REQUEST_INSTALL_PACKAGES` once Play distribution is primary. Sideload-first builds keep it for Downloader/GitHub Releases.

## Release checklist artifact contents

Every `suite-release.yml` run writes a checklist summary with:

- APK built.
- AAB built.
- Signing enforced for published releases.
- Updater metadata/hash generated where applicable.
- Keystore source confirmed as GitHub Actions secrets only.

## No-backend observability

No analytics or telemetry was added. Current no-backend path:

- Android update/feed failures are surfaced in-app rather than swallowed where UI exists.
- Core Doctor share reports are redacted by `Redactor` before sharing.
- Doctor includes a local suite-health check for installed sibling app versions and update metadata reachability.
- Future crash capture should be local-only and opt-in: write a small rolling crash/error log in app-private storage, expose a user-visible “share diagnostics” action, and let Doctor import/share it after redaction. Do not add SDK telemetry without explicit approval.
