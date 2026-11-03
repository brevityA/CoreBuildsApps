# Core Builds Launcher — private prototype

An installable, **private-use** Android TV / Google TV HOME launcher experiment. This is not a suite release: it has no public release workflow, Downloader code, store listing, update channel, or `suite.json` entry.

## What works in this prototype

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

## Private-use test checklist

- D-pad reaches every action; focused tiles are clearly outlined and scaled, and the spotlight artwork/title follows focus.
- Left/Right traverses each row and Up/Down moves between sections without losing focus or hiding the active item.
- Spotlight banner crops stay legible from the sofa; apps without banners use a clean icon fallback.
- Favourite cards show the installed app icon and label; selected cards have a clear cyan outline.
- Select opens each of several installed TV apps; Back from an app returns to the launcher.
- Add/remove pins; force-stop and reopen; verify the selection remains.
- Open a non-favourite app; return and verify the conditional Recent shelf appears, moves the newest app first, stays capped at five, and does not duplicate pinned apps.
- Install/uninstall a launchable app while away, return, and verify the list refreshes and unavailable recent entries disappear.
- Set the default Home app through system settings, press Home repeatedly, then revert.
- Repeat on at least one Google TV device and one Fire TV / other Android TV vendor if those targets matter.
- Check TalkBack and a low-memory device before considering any wider test.

The sandbox has no attached Android TV device. A successful APK build and physical D-pad / HOME-role verification remain required before treating this as tested.
