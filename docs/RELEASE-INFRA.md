# CoreBuildsApps release infrastructure

## CI overview

The suite now has one central PR gate plus the existing per-app workflows.

- `.github/workflows/suite-ci.yml`
  - `contracts`: runs `python tools/audit_contract.py` and fails if any shippable app is missing a tracked workflow/artifact path, updater metadata drifts from Gradle, an updater regresses to stale manifest URLs / code-0 fallback / unbounded reads, or keystore-like files appear in git.
  - `android`: matrix builds every Android app (`Icon Pack`, `Core Builds Glyphs`, `Core Line`, `Core Shift`, `Core Doctor`, `Core Motion plugin`) with `lintDebug`, unit tests, and `assembleDebug`, then uploads the debug APK artifact.
  - `web-and-content`: runs Core Line Node parser/update/feed tests and Python content/validator tests.
- Existing per-app workflows remain for compatibility with current release tags and Downloader flows:
  - `build.yml` — Icon Pack (+ Glyphs companion).
  - `core-line-apk.yml` — Core Line.
  - `core-shift-apk.yml` — Core Shift.
  - `core-doctor-apk.yml` — Core Doctor.
  - `core-motion-apk.yml` — Core Motion plugin.

## Release workflow

`.github/workflows/suite-release.yml` is the new release/dry-run entry point.

Triggers:

- Tag push:
  - `coreline-v*`
  - `shift-v*`
  - `doctor-v*`
  - `motion-v*`
- Manual `workflow_dispatch` with `app` and `publish` inputs.
  - `publish=false` is a dry run: builds artifacts and checklist only.
  - `publish=true` requires signing secrets and publishes GitHub Release assets.
  - `publish=true` only makes sense on a tag push: the release step names the
    release after `GITHUB_REF_NAME`, and a dispatch has a branch there, not a
    tag — `--verify-tag` then fails. Dry-run from dispatch; publish from a tag.

Icon Pack is not on this list, and its `iconpack-v*` trigger was removed rather
than reconciled: the pack's `tagPrefix` in `suite.json` is bare `v`, and only
`build.yml` (which triggers on `v*`) moves the floating `iconpack` tag, ships the
legacy `app-release.apk` the existing Downloader code reads, and publishes both
glyph artifacts from the same release — the Glyphs companion the Art style
toggle fetches (`iconpack-glyphs-release.apk`) and the glyphs-only build of the
pack itself (`corebuilds-glyphs-release.apk`, Downloader `5804177`). A release
cut here for Icon Pack would carry a versioned APK and nothing users actually
install from. `tools/check_suite_truth.py` now fails if
any workflow triggers on a prefix `suite.json` does not declare, or declares a
prefix no workflow triggers on, so the two halves cannot drift apart again.

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

Core Doctor and Core Motion have no `Latestrelease/` file, and that is the
contract, not an omission: neither app ships an updater (Doctor is local-only by
spec and its `SuiteHealthChecks` probes exactly these three feeds, and Motion is a
Projectivy plugin with no launcher entry to run an updater from), so
`suite.json` records `"metadata": null` for both and `tools/audit_contract.py`
asserts a manifest only for the three apps that read one. Do not create a
`Latestrelease/*-version.json` for them to make a directory look complete — a
polled-and-ignored manifest is worse than none, because the next reader treats it
as the release record. `PUBLISHING.md`'s `[USER TO SUPPLY]` Core Motion Downloader
code is held to the same rule: no code is assigned, so none is invented.

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
4. Merge to main. **Squash merges copy every commit body into the merge
   commit** — if any commit on the branch says `[skip ci]`, the squash commit
   inherits it, GitHub skips the `main` push workflows, and a tag pointing at
   that commit never builds (v1.8.15 hit this; the tag had to be moved to the
   next commit). Check `git show -s --format=%B origin/main | grep -i 'skip ci'`
   before tagging, or edit the squash message to drop the marker.
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
