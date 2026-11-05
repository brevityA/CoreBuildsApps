# Core Builds Launcher — private prototype

An installable, **private-use** Android TV / Google TV HOME launcher experiment. This is not a suite release: it has no `suite.json` entry, store listing, Downloader code, or update feed. The only distribution lane is a dispatch-only GitHub **pre-release** that attaches the debug-signed APK for a `launcher-v*` tag; see [Private pre-release lane](#private-pre-release-lane).

**Supported Android: 11 (API 30) to the latest.** `minSdk` is 30 and `targetSdk` 37. Home opens with a focused card on API 30, 31, 32, 33, 34 and 35 under Robolectric. No device has run 0.2 yet; see [the device checklist](#required-physical-device-validation-merge-gate).

## What 0.2 is

0.2 is a rebuild of the home screen in the "immersive list" pattern Android's TV design guidance describes, which Google TV, Projectivy and tvOS's top shelf all use in some form: a large preview of the focused item over a background that follows it, with rows of cards below. The research behind the choice is in [`docs/CORE_LAUNCHER_PROTOTYPE_RESEARCH.md`](../docs/CORE_LAUNCHER_PROTOTYPE_RESEARCH.md#home-screen-survey-and-the-02-direction-2026-11-05).

- **A background that follows focus.** The focused app's banner, softened and darkened behind the whole screen, cross-fading as focus moves; an app with no banner gets a glow in its icon's colour. Scrims from the left and the bottom keep text readable on any art. Customise offers it, a Core Builds glow, or plain Void.
- **The focused app, named large.** An eyebrow (Favourite, Recently opened, All apps), the app's name in Outfit Bold at 44sp (down to 30sp and two lines for long names), and what OK does.
- **Shelves of 16:9 cards.** Favourites (in your order, with an Add card at the end), Recently opened (the last five non-favourite apps, newest first; can be turned off), and All apps as a grid of 4, 5 or 6 cards a row. A TV app's banner fills its card; any other app shows its icon and name on a plate tinted from the icon.
- **Focus you can always see.** The focused card lifts 1.1x (the TV focus system's card scale) with a 3dp cyan ring drawn inside its own bounds, so no container can clip it. The focused row scrolls to one fixed line; rows above fade out instead of ending in a cut.
- **A remote that never surprises.** Every arrow is decided by the launcher, not Android's geometric search. Left/Right walk a row and stop at its ends; Up/Down move to the nearest card in the next row and stop at the last; Up from the first row reaches Customise and Down comes back to the card you left. Back closes a panel, ends a move, or returns to the first card; it never leaves Home. Home pressed on Home does the same.
- **Options in one side panel.** Hold OK on a card (or press Menu): Open, Add to / Remove from favourites, Move, Hide from All apps, Change artwork, App info. Customise uses the same panel. While a panel is open nothing behind it takes focus.
- **Custom artwork per app.** Change artwork offers:
  - the icon packs' own art for that app (the Core Builds banner and glyph, say), each applied with one press;
  - **Browse** for each pack that has an icon browser, which opens it through the open `ACTION_PICK_ICON` contract the Core Builds Icon Pack answers;
  - **Image on this TV**, a PNG or JPG through Android's file picker, copied into the launcher's own storage and capped at 960 px wide;
  - **Default**, to undo the pick.

  A pack pick is saved as a reference, so it redraws at any card size and follows pack updates. If the pack or file goes away, the app falls back to its default. **Customise › Custom artwork** lists every app with a pick and resets each with OK.
- **Customisation.**
  - **Artwork:** each app's own banner or icon, or any installed icon pack. Core Builds banners are used by default when the Core Builds Icon Pack is installed; Core Builds glyphs and other packs can be chosen. Apps a pack does not cover keep their own art.
  - **Background:** follows the focused app, Core Builds, or Void, with Light, Medium or Strong dim.
  - **Card size:** Compact (6 a row), Standard (5) or Large (4).
  - **Rows:** Recently opened row on or off, clock on or off.
  - **Favourites:** reorder them with Move (Left/Right, OK to finish). The Add card or Customise › Favourites picks apps.
  - **Hidden apps:** hide an app from All apps and Recently opened; Customise › Hidden apps lists them to bring back.
  - **Set as Home app** hands off to Android's settings, and **Reset home screen** asks twice.
- **Stays current.** Installing, removing or updating an app while Home is showing refreshes it.
- **Local and permission-free.** The manifest declares no permission at all. App discovery uses three narrow `<queries>` intents: the two launcher categories, plus `org.adw.launcher.THEMES` for icon packs. Favourites, hidden apps, settings and history stay in local `SharedPreferences`. 0.1's unordered favourites migrate once, in the A-Z order 0.1 showed them. A favourite that is uninstalled is remembered and comes back where it was if the app returns.

Wallpaper galleries, recommendations/channels, search, weather/scores, TV inputs, remote setup, and vendor-specific HOME-key workarounds remain out of scope.

### Implementation

- [LauncherActivity.kt](app/src/main/java/tv/corebuilds/launcher/LauncherActivity.kt): the home screen. Layout, the coalesced catalog scan, every arrow, move mode, and the panel pages.
- [HomeViews.kt](app/src/main/java/tv/corebuilds/launcher/HomeViews.kt): the cards with their lift and ring, the Add card, the background, and the Customise pill.
- [SidePanel.kt](app/src/main/java/tv/corebuilds/launcher/SidePanel.kt): the right-hand sheet. Stacked pages, rows, and focus kept inside.
- [LauncherApps.kt](app/src/main/java/tv/corebuilds/launcher/LauncherApps.kt): launchable activities (TV entry preferred, permission-guarded entries skipped), with card art cached per app version.
- [CardArt.kt](app/src/main/java/tv/corebuilds/launcher/CardArt.kt): card art drawn once at card size (banners rounded, icons on a tinted plate), plus the 48 × 27 softened copy the background scales up.
- [IconPacks.kt](app/src/main/java/tv/corebuilds/launcher/IconPacks.kt): icon packs through the open appfilter contract. It reads `res/xml/appfilter.xml` (or `assets/appfilter.xml`) once per installed version, matching components first and then by package, and lists every drawable a pack maps to an app as its suggestions.
- [CustomArt.kt](app/src/main/java/tv/corebuilds/launcher/CustomArt.kt): per-app artwork. It covers pack references and imported images, reads a pack browser's result (resource reference first, else the bitmap it sent), and imports and deletes image files.
- [LauncherPreferences.kt](app/src/main/java/tv/corebuilds/launcher/LauncherPreferences.kt): ordered favourites with the 0.1 migration, hidden apps, bounded history, and the Customise settings.
- [LauncherUi.kt](app/src/main/java/tv/corebuilds/launcher/LauncherUi.kt): Outfit type, the focus lift, API 30-safe package queries, and immersive mode that is safe before `setContentView()`.

### Tests

The launcher CI job runs `:app:testDebugUnitTest` before lint and assemble. These tests run the real activity under Robolectric at 1920 × 1080, with invented apps whose icons and banners are drawn by the test; they prove behaviour on the platform's own framework code, not on a TV.

- [HomeScreenTest.kt](app/src/test/java/tv/corebuilds/launcher/HomeScreenTest.kt), 17 tests (API 35). It checks:
  - what Home opens on, and that every card and Customise is reachable by remote;
  - that row ends hold, and Up from the top goes to Customise and back;
  - that every focused card shows whole on screen, lift included, and that text fits, including a 57-character app name;
  - that options open with Hold OK and with Menu, Back returns to the card, and arrows stay inside the panel;
  - that Move reorders favourites and the order survives a restart, and Add puts a favourite at the end;
  - that Hide takes an app out of All apps and Hidden apps brings it back;
  - that Recently opened is right and its setting turns it off, card size changes the row and is saved, and background and clock apply at once;
  - that Home on Home returns to the first card, a newly installed app appears, and OK starts the TV entry;
  - that an image picked from the TV's storage becomes that app's card, is listed in Custom artwork, and resets with its file deleted.

  Renders land in `app/build/screens`.
- `HomeOpensTest`, in the same file: Home opens with a focused card on API 30–35.
- [ModelTest.kt](app/src/test/java/tv/corebuilds/launcher/ModelTest.kt), 9 tests. It checks:
  - first-run seeding and the 0.1 migration;
  - that reordering keeps an uninstalled favourite's slot;
  - recent history rules, and settings round trip and reset;
  - appfilter parsing with package fallback, and every drawable a pack maps to an app;
  - banner versus icon art;
  - custom-art storage, and both kinds of pack-browser result.
- [tests/test_gradle_envelope.py](../tests/test_gradle_envelope.py) pins the source and manifest invariants:
  - the HOME and launcher entry points;
  - zero permissions and narrow queries;
  - no uninstall, kill or network code, and no forced Home role;
  - the Android 11 floor, the coalesced off-main-thread scan, the icon-pack contract, branding, and the release lane.

The 1920×1080 design illustrations and the [single-file offline HTML preview](../docs/mockups/core-launcher-offline.html) in [`docs/mockups`](../docs/mockups/README.md) are 0.1-era concepts, clearly labelled as such; the renders from `HomeScreenTest` show 0.2 itself.

## Build and install

Requires JDK 17 and Android SDK Platform 37 / Build Tools 36, matching the suite's current Gradle envelope.

```bash
cd launcher
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK installs as `tv.corebuilds.launcher.prototype.debug`, so it can sit beside a future non-debug build. Open **Core Builds Launcher · Private** from the TV app list.

## Obtain the exact debug APK for hardware validation

For the PR head, open a successful [Build and lint private launcher Actions run](https://github.com/brevityA/CoreBuildsApps/actions/workflows/core-launcher-prototype-ci.yml) and download its `core-builds-launcher-debug-<full-commit-SHA>` artifact. It contains `app-debug.apk` and `BUILD_COMMIT.txt`; the workflow checks out the PR head SHA and records that exact commit. Confirm the marker matches the SHA entered in the target-device record. Artifacts are retained for 14 days and are debug/test builds only, not a release or redistribution channel. If an artifact has expired, check out the exact recorded commit and build it with the commands above.

### Private pre-release lane

Tagged builds go through [core-launcher-release.yml](../.github/workflows/core-launcher-release.yml). It is **dispatch-only** by design: `tools/check_suite_truth.py` requires every workflow `tags:` glob to match a `tagPrefix` declared in `suite.json`, and the launcher deliberately has no entry there. Publish with:

```bash
git tag launcher-v0.2.0 <commit>
git push origin launcher-v0.2.0
gh workflow run core-launcher-release.yml \
  -f tag=launcher-v0.2.0 -f publish=true
```

The workflow rebuilds the debug APK from that tag, re-runs the suite-truth, Gradle-envelope and launcher contract gates, writes `BUILD_COMMIT.txt`, and creates a **pre-release** whose assets are the APK copied to `core-builds-launcher-<version>-debug.apk` (a release asset takes its name from the file, not from a `path#label` suffix) plus `BUILD_COMMIT.txt`; the release body also records that APK's SHA-256. Re-running the job repairs an existing release with `gh release upload --clobber` rather than failing on it.

A client that holds `contents: write` but not `actions: write` can start the same job through `repository_dispatch`:

```bash
gh api -X POST repos/brevityA/CoreBuildsApps/dispatches \
  -f event_type=launcher-release \
  -f 'client_payload[tag]=launcher-v0.2.0' \
  -f 'client_payload[publish]=true'
```

There is no release signing key, no `assembleRelease`, no floating "latest" tag, and no update feed; a tag alone publishes nothing until someone dispatches the workflow with `publish=true`. The APK stays debug-signed and sideload-only, and this lane makes no suite-release claim.

To try it as HOME, open **Customise › Set as Home app**, choose the prototype in Android's Home-app settings, then press the remote's Home button. To revert, use the device's **Settings → Apps → Default apps → Home app** screen (wording varies by vendor) and select the previous launcher.

Some televisions restrict third-party HOME selection. This prototype intentionally does not use ADB, `WRITE_SECURE_SETTINGS`, root, or an accessibility service to bypass those restrictions; if the system does not offer it, keep the app as a manually launched dashboard.

## Required physical-device validation: merge gate

**Current sign-off for 0.2: NOT RUN.** No device has run 0.2. This README section is the single source of truth for hardware-validation status and results.

**History: the 0.1 record.** The repository owner reported all eight 0.1 checks as PASS on a **Chromecast with Google TV** on 2026-11-04 (PR #435 review), against the `f01b5194bc71c3fd917b79ce5e6b02e19150d4e6` debug artifact, with no logs, screenshots, OS build, remote model or APK SHA-256 captured.

**Tested build vs shipped tag.** That pass attests to `f01b519`. The [launcher-v0.1.0 pre-release](https://github.com/brevityA/CoreBuildsApps/releases/tag/launcher-v0.1.0) was cut later from `7c017a5e7e5728e01f19cd46a34d5a42230ab761` (APK SHA-256 `6cdfbc4cd18da4ddad116e15b6d4494331e374ccf42cd03b255ef2f4fa308e9f`), whose three later review fixes are **not covered by the Google TV pass**.

**Known defect in launcher-v0.1.0: it closes as it opens on Android 11 and later.** `enterImmersiveMode()` ran before `setContentView()` and asked the window for its insets controller. From Android 11 (API 30), `Window.getInsetsController()` reads the window's decor view without creating it, so `onCreate` threw a `NullPointerException`. This was found under Robolectric on API 30–35 and confirmed in the API 34 framework bytecode, not on a device. `f01b519` has the same call order, so the 0.1 record and this finding cannot both hold for an up-to-date Android 11+ device. 0.2 reads the decor view's own controller, and `HomeOpensTest` opens Home on API 30–35 on every launcher PR.

**Validation owner:** Repository owner.

**Target completion date:** Before `launcher-v0.2.0` is tagged.

**Estimated effort:** About 45 minutes per device after the APK and device are set up.

Record the actual target, exact installed build, owner, date, and results below; do not merge until every required check passes on the target device(s).

### Target-device record

| Vendor / family | Manufacturer and model | Android / Google TV version | API level | Remote | Exact build under test (version + full commit SHA or APK SHA-256) | Tester and test date |
|---|---|---|---|---|---|---|
| — | Not run | — | — | — | — | — |

Record the exact APK installed, preferably its version and full source commit SHA or APK SHA-256; the current PR head is not evidence that a device was tested. Add one row per target device. If Fire TV or another vendor is an intended target, test and record it separately; otherwise do not claim compatibility for it.

### Results (0.2)

Use **NOT RUN**, **PASS**, **FAIL**, or **BLOCKED** in the status column.

| # | Procedure | PASS criterion | Status | Evidence / result |
|---|---|---|---|---|
| 1 | Open the launcher from the app list on the target. | Home opens and a card has a visible cyan ring; it does not close. | NOT RUN | |
| 2 | With only the D-pad, walk every row end to end; Up from Favourites to Customise and Down back; go to the last All apps row. | Left/Right stop at row ends; Up/Down land on the nearest card; focus never disappears or jumps to the top bar; the focused card is never clipped. | NOT RUN | |
| 3 | Watch the background while moving between a banner app and an icon-only app. | The background cross-fades to the focused app's art or colour; text stays readable. | NOT RUN | |
| 4 | Hold OK on a card, then press Menu on another. Walk the panel with Up/Down/Left/Right; press Back. | Both open App options; arrows stay in the panel; Back closes it and focus returns to the same card. | NOT RUN | |
| 5 | Add a favourite from All apps; Move it two places left; press OK; force-stop and reopen. | It is added at the end, moves as pressed, and keeps its place after the restart. | NOT RUN | |
| 6 | Hide an app; find it in Customise › Hidden apps and show it again. | It leaves All apps and Recently opened, and comes back. | NOT RUN | |
| 7 | In Customise, change Artwork (with the Core Builds Icon Pack installed), Background, Background dim, Card size, Recently opened row and Clock. | Each change applies without leaving the panel and persists after a restart; Core Builds banners replace app art for covered apps. | NOT RUN | |
| 7a | Hold OK on an app › Change artwork: apply a Core Builds suggestion; then Browse the Core Builds pack and pick another icon; then pick an image from a USB drive; then Default. Restart between steps. | Each pick shows on that app's card at once and after a restart; Default restores it; Customise › Custom artwork lists the app until then. | NOT RUN | |
| 8 | HOME role: Customise › Set as Home app, select the prototype, press Home repeatedly (including from inside another app and from deep in All apps), then restore the previous Home app. | Home opens the prototype and returns to the first card; the previous Home app can be restored. | NOT RUN | |
| 9 | Install and uninstall an app (for example with `adb`) while Home is showing. | The new app appears and the removed one disappears without leaving Home. | NOT RUN | |
| 10 | Enable TalkBack; walk cards, the options panel and Customise. | Each card announces its name and the hold-OK hint once; panel rows announce their title, value and selected state. | NOT RUN | |

Persisting the exact focused card across process recreation is **not currently promised**; if required, add it to scope and record it as a separate check.

The launcher CI gate is separate from this table. It checks that the debug APK builds and lints, and that the screen tests above pass under Robolectric. It does not check that the UI works with a physical remote or TalkBack, and nothing here claims coverage of other vendors, other Android TV OS versions, or the unused release variant.
