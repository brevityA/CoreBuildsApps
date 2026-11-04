# Core Builds Launcher — private prototype

An installable, **private-use** Android TV / Google TV HOME launcher experiment. This is not a suite release: it has no `suite.json` entry, store listing, Downloader code, or update feed. The only distribution lane is a dispatch-only GitHub **pre-release** that attaches the debug-signed APK for a `launcher-v*` tag; see [Private pre-release lane](#private-pre-release-lane).

## Implemented in the prototype (source-level; validated on one Google TV device)

- Registers a real Android HOME activity and a Leanback launcher entry.
- Scans installed `LEANBACK_LAUNCHER` and `LAUNCHER` activities through narrow `<queries>` filters; it does not request `QUERY_ALL_PACKAGES`.
- Shows an alphabetised app grid, a D-pad-friendly favourites shelf, and an immersive spotlight with a large selected-app name.
- Uses the Core Builds icon pack's Void/Night palette, hex-and-diamond brand mark, and minimal banner lockup as its visual starting point.
- Uses an app's installed TV banner in the focused spotlight when available, with that package's launcher icon as fallback. Favourites use wide icon/category/name lockups built from the package icon and label; artwork stays on-device and unaltered.
- Prefers a TV-specific launch activity when a package exposes both TV and mobile entries.
- Opens the selected app and stores favourites plus up to five recent package IDs in local `SharedPreferences`; the recent shelf stays hidden until a non-favourite app has been opened.
- Long-press an app tile to open Android's App info screen or add/remove that app from local favourites; no uninstall, force-stop, or broad package permission is added.
- Suggests installed Core Builds apps as first-run pins, then respects the user's edits; pinned apps are omitted from the recent shelf to avoid duplicate shortcuts.
- Offers Android's Home-app settings screen rather than silently taking over HOME.
- Has no network permission, telemetry, ads, sign-in, or Home-key/accessibility interception; saved favourites and recents stay on the TV.

### Implementation and source-level checks

- [LauncherActivity.kt](app/src/main/java/tv/corebuilds/launcher/LauncherActivity.kt) builds the HOME surface, refreshes the catalog, and manages focus and app launches.
- [LauncherApps.kt](app/src/main/java/tv/corebuilds/launcher/LauncherApps.kt) discovers launchable activities and loads package-provided labels/artwork.
- [LauncherPreferences.kt](app/src/main/java/tv/corebuilds/launcher/LauncherPreferences.kt) stores local favourites and bounded Recent history.
- [LauncherUi.kt](app/src/main/java/tv/corebuilds/launcher/LauncherUi.kt) defines reusable TV tiles, focus styling, and accessibility descriptions.
- [ManageFavoritesActivity.kt](app/src/main/java/tv/corebuilds/launcher/ManageFavoritesActivity.kt) provides the favourites editor; [AndroidManifest.xml](app/src/main/AndroidManifest.xml) declares HOME and launcher entry points.
- [tests/test_gradle_envelope.py](../tests/test_gradle_envelope.py) contains source/manifest contract checks for the prototype. These are not Android UI automation and do not establish physical-device behavior.

Wallpaper galleries, recommendations/channels, search, weather/scores, remote setup, and vendor-specific HOME-key workarounds are deliberately out of scope for v0.1.

The 1920×1080 design illustrations and the [single-file offline HTML preview](../docs/mockups/core-launcher-offline.html) are in [`docs/mockups`](../docs/mockups/README.md). They are clearly labelled concepts, not APK/device screenshots; the examples use the repository's Core Builds icon-pack artwork as style references, while the running launcher preserves each installed package's own identity. The HTML embeds its CSS, script, and images and makes no network requests.

## Build and install

Requires JDK 17 and Android SDK Platform 37 / Build Tools 36, matching the suite's current Gradle envelope.

```bash
cd launcher
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK installs as `tv.corebuilds.launcher.prototype.debug`, so it can sit beside a future non-debug build. Open **Core Builds Launcher · Private** from the TV app list.

## Obtain the exact debug APK for hardware validation

For the PR head, open a successful [Build and lint private launcher Actions run](https://github.com/brevityA/CoreBuildsApps/actions/workflows/core-launcher-prototype-ci.yml) and download its `core-builds-launcher-debug-<full-commit-SHA>` artifact. It contains `app-debug.apk` and `BUILD_COMMIT.txt`; the workflow checks out the PR head SHA and records that exact commit. Confirm the marker matches the SHA entered in the target-device record. Artifacts are retained for 14 days and are debug/test builds only, not a release or redistribution channel. If an artifact has expired, check out the exact recorded commit and build it with the commands above.

### Private pre-release lane

Tagged builds go through [core-launcher-release.yml](../.github/workflows/core-launcher-release.yml). It is **dispatch-only** by design: `tools/check_suite_truth.py` requires every workflow `tags:` glob to match a `tagPrefix` declared in `suite.json`, and the launcher deliberately has no entry there. Publish with:

```bash
git tag launcher-v0.1.0 <commit>
git push origin launcher-v0.1.0
gh workflow run core-launcher-release.yml \
  -f tag=launcher-v0.1.0 -f publish=true
```

The workflow rebuilds the debug APK from that tag, re-runs the suite-truth, Gradle-envelope and launcher contract gates, writes `BUILD_COMMIT.txt`, and creates a **pre-release** whose assets are `core-builds-launcher-<version>-debug.apk` plus `BUILD_COMMIT.txt`. Re-running the job repairs an existing release with `gh release upload --clobber` rather than failing on it.

A client that holds `contents: write` but not `actions: write` can start the same job through `repository_dispatch`:

```bash
gh api -X POST repos/brevityA/CoreBuildsApps/dispatches \
  -f event_type=launcher-release \
  -f 'client_payload[tag]=launcher-v0.1.0' \
  -f 'client_payload[publish]=true'
``` There is no release signing key, no `assembleRelease`, no floating "latest" tag, and no update feed; a tag alone publishes nothing until someone dispatches the workflow with `publish=true`. The APK stays debug-signed and sideload-only, and this lane makes no suite-release claim.

To try it as HOME, select **SET AS HOME**, choose the prototype in Android's Home-app settings, then press the remote's Home button. To revert, use the device's **Settings → Apps → Default apps → Home app** screen (wording varies by vendor) and select the previous launcher.

Some televisions restrict third-party HOME selection. This prototype intentionally does not use ADB, `WRITE_SECURE_SETTINGS`, root, or an accessibility service to bypass those restrictions; if the system does not offer it, keep the app as a manually launched dashboard.

## Required physical-device validation: merge gate

**Current sign-off: PASS — 8/8 hardware checks run on Google TV.** The repository owner reported all eight checks as PASS on a **Chromecast with Google TV** on 2026-11-04, during PR #435 review, against the `f01b519` debug artifact. No logs, screenshots, or per-check notes were supplied, and the exact Google TV OS build, remote model, and APK SHA-256 were not captured; those gaps are marked in the record below rather than filled in. This README section is the single source of truth for hardware-validation status and results.

**Attestation scope:** the sign-off covers the single Google TV device recorded below. It is not a claim about Fire TV, other vendors, other Android TV OS versions, or the unbuilt release variant, which do not exist. Build, lint, and source-level checks never substitute for this record.

**Validation owner:** Repository owner (reported PASS in PR #435 review, 2026-11-04).

**Target completion date:** Met — results recorded 2026-11-04.

**Estimated effort:** About 30–45 minutes per device after the APK and device are set up.

Record the actual target, exact installed build, owner, date, and results below; do not merge until every required check passes on the target device(s).

### Target-device record

| Vendor / family | Manufacturer and model | Android / Google TV version | API level | Remote | Exact build under test (version + full commit SHA or APK SHA-256) | Tester and test date |
|---|---|---|---|---|---|---|
| Google TV | Chromecast with Google TV (exact HD/4K variant not recorded) | Google TV / Android TV OS — exact version and API level not recorded | Not recorded | Not recorded | v0.1.0-private debug, commit `f01b5194bc71c3fd917b79ce5e6b02e19150d4e6` (CI artifact `core-builds-launcher-debug-f01b5194…`, APK SHA-256 not captured) | Repository owner, 2026-11-04 |

Record the exact APK installed, preferably its version and full source commit SHA or APK SHA-256; the current PR head is not evidence that a device was tested. Add one row per target device. If Fire TV or another vendor is an intended v0.1 target, test and record it separately; otherwise do not claim compatibility for it.

### Results

Use **NOT RUN**, **PASS**, **FAIL**, or **BLOCKED** in the status column. Run the procedure and judge it against the explicit PASS criterion for every row; attach concise observations, screenshots where useful, and any failure reproduction. All eight rows below are **PASS** on the owner attestation described above; each row's evidence states what was recorded, which is the attestation rather than a captured log.

| # | Procedure | PASS criterion | Status | Evidence / result |
|---|---|---|---|---|
| 1 | From Home, use only the remote D-pad to focus and activate the spotlight, each visible action, Favourites, conditional Recents, and the on-Home All Apps grid. Long-press a focused app tile to open its actions, then choose each action and Cancel. | Every actionable item can be reached and activated; the focused item has a clearly visible outline and is not clipped; focus never gets trapped or lost. | PASS | Owner attestation, 2026-11-04; no per-check notes or capture recorded. |
| 2 | Move Left/Right within each row and Up/Down between the spotlight, Favourites, Recents when visible, app grid, and Edit Favourites; test both ends of each row. Open the app-actions menu from each shelf/grid and traverse/dismiss it using the remote. | Focus follows the adjacent item/section predictably, stays visible after scrolling, and does not jump to an unrelated control or disappear at row edges; app actions are D-pad reachable. | PASS | Owner attestation, 2026-11-04; no per-check notes or capture recorded. |
| 3 | Open at least three installed TV apps. Exercise an in-app Back stack if available, then back out to the launcher; also press Back on Home and in Edit Favourites. | Back first unwinds an app's own screen; exiting the app returns to the launcher; Back on Home leaves Home usable without a crash; Back from Edit Favourites returns to Home. | PASS | Owner attestation, 2026-11-04; no per-check notes or capture recorded. |
| 4 | HOME role: choose **SET AS HOME**, inspect the system's default-Home settings, select the prototype, press the remote's Home button repeatedly (including after opening another app), then restore the previous Home app. | The settings handoff opens the system Home-app chooser/settings; Home consistently opens the selected prototype; the previous Home app can be restored. | PASS | Owner attestation, 2026-11-04; no per-check notes or capture recorded. |
| 5 | Enable TalkBack, navigate app tiles, their long-press action menu, and Edit Favourites controls, and focus/activate representative items. | Each tile announces its app label, launch action, and app-actions hint once; the menu title and actions are announced; pin controls announce their action/state without duplicate labels; the visual focus indicator remains visible. | PASS | Owner attestation, 2026-11-04; no per-check notes or capture recorded. |
| 6 | Add and remove several favourites; launch more than five distinct non-favourite apps; force-stop and relaunch the launcher. | Favourite choices persist; Recents are unique, newest-first, capped at five, and omit favourites; saved local state remains after relaunch. | PASS | Owner attestation, 2026-11-04; no per-check notes or capture recorded. |
| 7 | Focus a package, leave the launcher, install a launchable app and uninstall another, then return to Home. | The new app appears, the uninstalled app and stale Recent entry disappear, saved favourites are retained, and focus returns to the same package if it remains launchable. | PASS | Owner attestation, 2026-11-04; no per-check notes or capture recorded. |
| 8 | Focus an app with a TV banner and another without one; inspect the spotlight and shelf cards from the normal viewing distance. | Banner art is legible and safely cropped; missing banners fall back to a visible, undistorted app icon; no art cell is blank and focus styling remains obvious. | PASS | Owner attestation, 2026-11-04; no per-check notes or capture recorded. |

Favourites/Recent persistence and package-based focus restoration after a catalog refresh are v0.1 checks. Persisting the exact focused item across process recreation is **not currently promised** by v0.1; if required, add it to scope and record it as a separate check.

The launcher CI build/lint gate is separate from this table: it checks that the debug APK builds and lints, not that the UI works with a physical remote or TalkBack. The rows above carry the owner's attestation for one Google TV device; nothing here claims coverage of other vendors, other Android TV OS versions, or the unused release variant.
