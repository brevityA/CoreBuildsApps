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
glyph artifacts from the same release — `iconpack-glyphs-release.apk`, the
Glyphs companion the Art style toggle fetches and the asset Downloader `5804177`
resolves to, and `corebuilds-glyphs-release.apk`, the glyphs-only build of the
pack itself, which is URL-only. A release cut here for Icon Pack would carry a
versioned APK and nothing users actually install from. `tools/check_suite_truth.py` now fails if
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

The icon pack's step-by-step, from developer account to Core Builds Plus,
is [`PLAY-CONSOLE.md`](PLAY-CONSOLE.md). The list below is the suite-wide
summary.

1. Ensure the app's target API satisfies current Play requirements.
   - The icon pack is at **compileSdk/targetSdk 36**, which meets the phone
     floor (36 for new apps and updates after 2026-08-31) as well as the
     Android TV one (34).
   - The Play build is **TV-only**, as `docs/play/listing.json`
     (`form_factors: ["tv"]`) and `PLAY-CONSOLE.md` §4 say: its manifest
     overlays require `android.software.leanback`, so Play never offers it to
     a phone. With leanback optional the bundle would be phone-compatible,
     Play would list it for phones, and there are no phone screenshots and no
     tested phone UI. The sideload builds keep leanback optional.
   - Reaching 36 needed AGP 9.4.0 and built-in Kotlin (see
     `tools/gradle_envelope.json`): on the Gradle 9.7.0 wrapper every AGP 8.6+
     fails at plugin apply, so 8.5.2 could never compile API 36.
2. Use the AAB from `suite-release.yml`.
3. Enroll/use Play App Signing; keep upload key in GitHub secrets only.
4. Fill Play Console app content:
   - Data safety form.
   - Privacy policy URL `[USER TO SUPPLY]`.
   - Permissions declarations / restricted permission justifications.
   - TV listing assets and screenshots for TV apps.
5. Prefer a Play flavor without `REQUEST_INSTALL_PACKAGES` once Play distribution is primary. Sideload-first builds keep it for Downloader/GitHub Releases.

### Icon Pack on Google Play

The icon pack's Play apps are separate apps from the sideload ones, so both
can sit on one TV and neither ever updates the other:

| | Sideload (GitHub, Downloader `5270601`) | Google Play |
|---|---|---|
| Icon Pack | `tv.corebuilds.iconpack` | `tv.corebuilds.iconpack.play` |
| Glyphs | `tv.corebuilds.iconpack.glyphs` | `tv.corebuilds.iconpack.glyphs.play` |
| Built by | `build.yml` on `v*` tags | `play.yml`, run by hand from `main` |
| Signed with | `KEYSTORE_*` release key | `PLAY_KEYSTORE_*` upload key |
| Updates | in-app, from GitHub | Play only |

The `play` build type (`app/` and `glyphs/` `build.gradle.kts`) drops
`REQUEST_INSTALL_PACKAGES` and the GitHub update check, About's update row
opens the app's Play listing, and the Art style switch opens the Glyphs
twin's Play listing instead of downloading an APK. Only the `banners` flavor
has a Play variant: the `glyphs` flavor (Core Builds Glyphs,
`tv.corebuilds.glyphs`) would take the Play icon pack's FileProvider
authority. `tests/test_play_build.py` pins all of it.

Secrets for `play.yml` (a new upload keystore, never the sideload key):

- `PLAY_KEYSTORE_BASE64` — base64-encoded Play upload keystore.
- `PLAY_KEYSTORE_PASSWORD`, `PLAY_KEY_ALIAS`, `PLAY_KEY_PASSWORD`.

Without them the workflow still builds, warns, and uploads unsigned bundles
Play Console will refuse. With them set, an unsigned bundle fails the run.
The artifact `core-builds-play-bundles` holds `app-banners-play.aab` and
`glyphs-play.aab`; upload each to its own Play Console app.

**No tips in the Play build.** Play's Payments policy forbids leading a user
to a payment method other than Play Billing, and a tip to one developer is
not the tax-exempt donation the policy exempts — Google rejected real apps
over exactly this (AnkiDroid, August 2026). Support is two QR codes for
GitHub Sponsors and Ko-fi, so the Play build hides every door to it: the home
rail closes up to `Settings | About` and rewrites the D-pad edges that named
the hidden entry (`MainActivity.railHead`), Settings hides its row, and About
hides its row. The GitHub/Downloader build keeps all three. Adding a tip jar
to the Play build means Play Billing consumables, not a link.

**Data safety.** About's "everything this app sends" list is the source for
the Console form, so it is an enumeration and not a summary. Item 3 is the
missing-icon report, which POSTs the app name, the component and a device
note (pack version, TV model, Android version, detected launcher). The
closing line denies only what is still true — no analytics, no installed-app
list, no identifier.

### Console, screen by screen

`docs/PLAY-CONSOLE.md` is the operator's page: what to type into each Console
screen, in Play's own order, with the values taken from `listing.json` and the
assets in `docs/play/`. Start with §7 — a new personal account needs 12 testers
opted in for 14 continuous days before it can apply for production, and
internal testing does not count toward it. That clock is the launch's critical
path, not the store copy.

### Listing assets and copy are generated, not exported

`tools/build_play_listing.py` writes everything the store page needs into
`docs/play/`, from the same sources the app ships:

| Asset | Size | Comes from |
|---|---|---|
| `icon-512.png` | 512x512, RGBA, < 1 MB | the adaptive background plus `PACK_ICON` in `tools/build_branding.py` |
| `feature-graphic-1024x500.png` | 1024x500, no alpha | the mark, the wordmark and the live counts |
| `tv-banner-1280x720.png` | 1280x720, no alpha | `build_branding.banner_svg()` — the *same drawing* as the on-device 320x180 Leanback banner, at store size |
| `tv-1..6-*.png` | 1920x1080, no alpha | the committed `docs/app-ui-*.png` frames, caption band cropped off |
| `listing.json` | — | title, short and full description, Data safety and content-rating answers, every count and source digest. The Plus paragraph, the purchases answer and Data safety's `collected` follow `broker_enabled()` (Core Builds Plus is on sale exactly when the broker URL is baked in) |

The counts in the copy (icons, walls, series, loops) are substituted from
`icon_pack.xml`, `wallpapers.json` and `LiveLoop.kt`. `tests/test_play_listing.py`
holds the committed copy to them, so a catalog that grows cannot leave a stale
number on the store page; `--check` does the same for anyone editing by hand.

**TV-only, deliberately.** `form_factors: ["tv"]`. There are no phone
screenshots because there is no tested phone UI — the layouts are TV-first, and
rendering fake ones is exactly the drift this repo generates its way out of.
Listing for phones needs real captures from a phone build and a decision about
supporting phones at all. The Play manifests make it stick: they require
`android.software.leanback`, because Console cannot keep a phone-compatible
bundle off phones. A TV-only app could have stayed at API 34; the API 36
migration is still correct and worth keeping, because a phone listing later
then needs no target change.

**Privacy policy:** `docs/privacy.html`. Serve it over GitHub Pages
(Settings → Pages → source `main` / `/docs`) and the URL to paste into Play
Console is
`https://brevityA.github.io/CoreBuildsApps/privacy.html`. `tests/test_privacy_policy.py`
holds every claim in it to the code — the hosts the app calls, the one POST
payload, the Play build's removed permission and absent update check.

### The two Play listings go out together

Icon Pack and Glyphs Pack are separate Play listings, and the pack checks the
companion's `versionCode` before pointing a launcher at it: `GlyphsCompanion.
ready()` requires `>= BuildConfig.VERSION_CODE` (both numbers are parsed from
`app/build.gradle.kts`, so they cannot drift in the repo). Play rolls listings
out **independently**, which is the trap:

- Pack reaches 100% while the companion is still staged at an older code →
  the app decides the companion is stale, opens the pack's own banner art, and
  a user who picked square glyphs silently stops getting them until the
  companion catches up. Nothing crashes, nothing is logged — it just applies
  the wrong shape.

So: **submit both listings in the same window with matched staged rollouts.**
If they must diverge, the companion goes first, never the pack. A pack that
believes its companion is ahead is harmless; one that believes it is behind is
not.

### Blocked pending the Play Console account

The Play work (the Play build type, `play.yml`, the premium purchase in its own
PR) is complete and tested, but nothing can be uploaded until the owner has:

1. A Play Console developer account, identity verified.
2. A **payments profile** — merchant/tax/bank details. Needed for the premium
   in-app product; without it `premium_unlock` cannot be created or priced.
3. A privacy policy URL (About links it; the string is still
   `[USER TO SUPPLY]`).
4. Upload key + the `PLAY_KEYSTORE_*` repository secrets for `play.yml`.

Until (1) and (2) exist, the premium PR stays parked — the gate only lives in
the Play build, so there is nothing to gate.

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
