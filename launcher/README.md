# Core Builds Launcher — private prototype

An installable, **private-use** Android TV / Google TV HOME launcher experiment. This is not a suite release: it has no public release workflow, Downloader code, store listing, update channel, or `suite.json` entry.

## Implemented in the prototype (source-level; hardware validation pending)

- Registers a real Android HOME activity and a Leanback launcher entry.
- Scans installed `LEANBACK_LAUNCHER` and `LAUNCHER` activities through narrow `<queries>` filters; it does not request `QUERY_ALL_PACKAGES`.
- Shows an alphabetised app grid, a D-pad-friendly favourites shelf, and an immersive spotlight with a large selected-app name.
- Uses the Core Builds icon pack's Void/Night palette, hex-and-diamond brand mark, and minimal banner lockup as its visual starting point.
- Uses an app's installed TV banner in the focused spotlight when available, with that package's launcher icon as fallback. Favourites use wide icon/category/name lockups built from the package icon and label; artwork stays on-device and unaltered.
- Prefers a TV-specific launch activity when a package exposes both TV and mobile entries.
- Opens the selected app and stores favourites plus up to five recent package IDs in local `SharedPreferences`; the recent shelf stays hidden until a non-favourite app has been opened.
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

To try it as HOME, select **SET AS HOME**, choose the prototype in Android's Home-app settings, then press the remote's Home button. To revert, use the device's **Settings → Apps → Default apps → Home app** screen (wording varies by vendor) and select the previous launcher.

Some televisions restrict third-party HOME selection. This prototype intentionally does not use ADB, `WRITE_SECURE_SETTINGS`, root, or an accessibility service to bypass those restrictions; if the system does not offer it, keep the app as a manually launched dashboard.

## Required physical-device validation: merge gate

**Current sign-off: BLOCKED — 0/8 hardware checks run.** Every result below remains NOT RUN because this environment has no attached physical Android TV / Google TV device. Passing build, lint, and source-level checks does not sign off remote behavior. Record the actual target and results below; do not merge until every required check passes on the target device(s).

### Target-device record

| Vendor / family | Manufacturer and model | Android / Google TV version | API level | Remote | APK / PR commit | Tester and date |
|---|---|---|---|---|---|---|
| Pending physical run | Not recorded | Not recorded | Not recorded | Not recorded | Record at test time | Not recorded |

Add one row per target device. If Fire TV or another vendor is an intended v0.1 target, test and record it separately; otherwise do not claim compatibility for it.

### Results

Use **NOT RUN**, **PASS**, **FAIL**, or **BLOCKED** in the status column and add concise evidence/notes. All rows are currently **NOT RUN**.

| Check | Status | Result / evidence |
|---|---|---|
| D-pad reachability and focus order across the Home spotlight, Favourites shelf, conditional Recents shelf, and on-Home All Apps grid; every action is reachable and the focused item remains visibly outlined. | NOT RUN | |
| D-pad movement between rows/sections; focused items remain visible and focus does not get trapped or lost. | NOT RUN | |
| App launch and Back behavior: launch several installed TV apps; Back returns as expected; Back on the launcher does not unexpectedly exit or crash. | NOT RUN | |
| HOME role: **SET AS HOME** opens Android's default-Home settings; selecting the prototype and pressing Home repeatedly relaunches it; the previous Home app can be restored. | NOT RUN | |
| TalkBack: each app tile announces its app label and launch action as one useful description, with no duplicate label announcements; the focus indicator remains visible. | NOT RUN | |
| Favourites and Recents: add/remove pins; launch a non-favourite app; verify newest-first Recents, five-item limit, and no pinned duplicates; force-stop/reopen and verify local choices persist. | NOT RUN | |
| Catalog refresh and focus: install/uninstall a launchable app while away, return, verify the catalog updates and unavailable Recent entries disappear; focus returns to the same package where it remains available. | NOT RUN | |
| Banner/icon fallback, crop, safe-area placement, and text legibility from the normal viewing distance. | NOT RUN | |

Favourites/Recent persistence and package-based focus restoration after a catalog refresh are v0.1 checks. Persisting the exact focused item across process recreation is **not currently promised** by v0.1; if required, add it to scope and record it as a separate check.

The launcher CI build/lint gate is separate from this table: it checks that the debug APK builds and lints, not that the UI works with a physical remote or TalkBack. No hardware pass is claimed until the target-device fields and results are filled in.
