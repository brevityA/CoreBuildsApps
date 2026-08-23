# Core Motion — why Projectivy doesn't detect it

Deep-dive root-cause analysis, verified against Spocky's official template
(`spocky/projectivy-plugin-wallpaper-provider`, `main`) and the state of this
repo at `1d5c692` (Core Shift v2.0.1).

---

## TL;DR

**The plugin is not detected because the plugin is not installed — because it is
never built.** Nothing in CI compiles `motion-plugin/`, and no GitHub Release in
this repo has ever contained a Core Motion APK. Every release asset to date is
`coreshift-release.apk`, `coreline-release.apk`, or `iconpack-release.apk`.

Everything downstream of that (manifest keys, AIDL, UUID) is actually **correct**
— I diffed it byte-for-byte against the upstream template and it matches. So the
fix is a release pipeline, not a code rewrite. The secondary findings below are
real but would only bite *after* you get an APK onto the device.

---

## Verification: how Projectivy actually discovers a provider

Projectivy enumerates installed packages with

```
PackageManager.queryIntentServices(Intent("tv.projectivy.plugin.WALLPAPER_PROVIDER"), GET_META_DATA)
```

and for each hit reads the **service-level** `<meta-data>`: `apiVersion`, `uuid`,
`name`, `settingsActivity`, `itemsCacheDurationMillis`, `updateMode`. It then
binds to the service and calls `IWallpaperProviderService.getWallpapers(Event)`.

For a plugin to appear in *Settings → Appearance → Wallpaper → Launcher
wallpaper*, **all** of these must hold:

| # | Requirement | Core Motion status |
|---|---|---|
| 1 | The APK is installed on the device | ❌ **never built or published** |
| 2 | Exported service with `<action tv.projectivy.plugin.WALLPAPER_PROVIDER/>` | ✅ correct |
| 3 | `apiVersion` meta-data = `1` | ✅ correct |
| 4 | `uuid` meta-data, valid UUID v4, not `CHANGE_ME` | ✅ `295b637c-aa92-4d09-8f54-74c4806e1e80` |
| 5 | `name` meta-data | ✅ `Core Motion` |
| 6 | AIDL interface descriptor byte-identical to upstream | ✅ verified by diff |
| 7 | `Wallpaper` / `Event` parcelables byte-identical to upstream | ✅ verified by diff |
| 8 | Projectivy **Premium** is active | ⚠️ user-side, must confirm |
| 9 | App is installed for the *current* Android user/profile | ⚠️ user-side |
| 10 | Service class survives R8 | ✅ `isMinifyEnabled = false` |

---

## Finding 1 — CRITICAL: no build, no artifact, no release

`motion-plugin/` is a standalone Gradle root (`motion-plugin/settings.gradle.kts`,
`rootProject.name = "CoreMotionPlugin"`). It is referenced by **zero** workflows:

```
$ grep -rn "motion-plugin" .github/workflows/
(no matches)
```

`.github/workflows/` contains `build.yml` (icon pack), `core-line-apk.yml`,
`core-shift-apk.yml`, `device-check.yml`. `core-shift-apk.yml` builds
`shift/` only — its `working-directory: shift` and its path filters
(`shift/**`, `Motion/**`, `tools/validate_motion.py`) never touch the plugin.

Release assets, via the API:

```
shift-v2.0.1  → coreshift-release.apk
shift         → coreshift-release.apk
v1.7.1        → app-release.apk, iconpack-release.apk
coreline      → coreline-release.apk
…             (no coremotion-*.apk anywhere)
```

Meanwhile `README.md:239` and `motion-plugin/README.md` both tell the user to
"sideload the plugin APK" — an APK that has never existed. And Core Shift
`strings.xml` says *"Projectivy users: install the Core Motion plugin."*

Worth stating plainly, because it's the most common misunderstanding here:
**Core Shift is not a Projectivy plugin and can never be detected as one.**
`shift/app/src/main/AndroidManifest.xml` declares two activities and no service
at all. Installing Core Shift and then looking in Projectivy's wallpaper-source
list will always come up empty. They are two separate APKs with two separate
package names (`dev.corebuilds.shift` vs `tv.corebuilds.motion`).

**Fix:** `.github/workflows/core-motion-apk.yml` (added) — builds, signs and
publishes `coremotion-release.apk` on `motion-v*` tags, and force-moves a
floating `motion` tag so there is a stable Downloader URL, exactly mirroring the
`shift` pattern.

---

## Finding 2 — HIGH: `android.software.leanback` is `required="true"`

```xml
<uses-feature android:name="android.software.leanback" android:required="true" />
```

Inherited from the upstream template. It hard-gates *installation* on the
feature flag. In practice this bites on:

- **Google TV / Chromecast with Google TV** builds that report
  `android.software.leanback` inconsistently across OS updates;
- **Fire TV** — Fire OS is Leanback-ish, but the flag has moved between Fire OS
  releases, and `adb install` will reject with `INSTALL_FAILED_MISSING_SHARED_LIBRARY`
  / `INSTALL_FAILED_MISSING_FEATURE` rather than anything obvious;
- **projector/AOSP TV boxes** running plain AOSP with Projectivy on top;
- any attempt to test on an emulator or handset.

The failure mode is indistinguishable from "Projectivy doesn't see it", because
the user sees an install that silently fails or a package that isn't there.
Projectivy itself does not require your plugin to declare leanback.

**Fix:** `required="false"`. Keeps TV-store categorisation via
`LEANBACK_LAUNCHER`, removes the install gate.

---

## Finding 3 — HIGH: 20 s blocking network call on the binder thread

`MotionFeed.TIMEOUT_MS = 20_000`, and `WallpaperProviderService.getWallpapers()`
calls `MotionFeed.load()` synchronously with **both** `connectTimeout` and
`readTimeout` at 20 s — a worst case of ~40 s inside a single binder
transaction.

`getWallpapers()` is not on Projectivy's main thread, but Projectivy's binding
code has its own patience. A provider that blocks for tens of seconds looks
broken: Projectivy falls back to its cached/default wallpaper, and on some
Android TV builds the binder transaction itself is reaped. Users read that as
"the plugin doesn't work / isn't detected". Spocky's own guidance in the
template README is explicit — *"be responsible: even though getWallpapers()
isn't called from the UI thread, it doesn't mean you can waste precious device
resources"*.

Compounding it: the plugin returns **two bundled Lottie wallpapers first**, then
appends the feed. If the fetch throws, you still return 2 items, so the plugin
*appears* selectable but only ever cycles two vectors — an easy misdiagnosis.

**Fix:** 6 s connect / 8 s read, plus an in-memory feed cache keyed on URL with a
TTL, so repeat `getWallpapers()` calls inside `itemsCacheDurationMillis` never
hit the network at all.

---

## Finding 4 — MEDIUM: no way to see what Projectivy sees

There is no diagnostic anywhere. When it doesn't work you have no signal.

**Fix:** the settings screen (`SettingsFragment`) now renders a live
self-check — is Projectivy installed, does `queryIntentServices` resolve *this
plugin's own service*, and are `apiVersion` / `uuid` / `name` readable from the
resolved `ServiceInfo.metaData`. That is precisely the query Projectivy runs, so
if that panel is green and Projectivy still shows nothing, the problem is
Premium or the launcher's cache — not the plugin.

Also added `tools/verify_motion_plugin.py`: a static check that the manifest
declares every required key and that the vendored AIDL + parcelables still match
upstream. Wired into the new workflow so a future refactor can't silently break
discovery.

---

## Finding 5 — LOW / user-side, but check these first

- **Premium.** Third-party wallpaper providers are a Projectivy **Premium**
  feature. Without it the plugin section does not render at all. Overflight's
  own store listing says the same. Confirm via *Settings → About → Get premium*.
- **Projectivy caches the plugin list.** Historically it refreshes on package
  add/remove, but the launcher is long-lived and users report stale lists.
  After sideloading: `adb shell am force-stop com.spocky.projengmenu`, or
  Settings → Apps → Projectivy → Force stop, then reopen.
- **Wrong Android user.** Sideloading via a file manager running in a secondary
  profile installs into that profile; Projectivy in the primary profile can't
  see it. `adb install -r --user 0`.
- **Signature.** Sideloaded debug-signed builds are fine; Projectivy does no
  signature check. But if you install a debug build over a release build (or
  vice-versa) the install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` and
  leaves nothing installed.

---

## Reproduce / verify on-device

```bash
# 1. Is it installed at all, in user 0?
adb shell pm list packages --user 0 | grep tv.corebuilds.motion

# 2. Does the system resolve the discovery intent? This is Projectivy's query.
adb shell dumpsys package tv.corebuilds.motion | sed -n '/WallpaperProviderService/,/^$/p'
adb shell cmd package query-services -a tv.projectivy.plugin.WALLPAPER_PROVIDER

# 3. Are the meta-data keys present on the *service*?
adb shell dumpsys package tv.corebuilds.motion | grep -A2 -E 'apiVersion|uuid|settingsActivity'

# 4. Watch the plugin get bound and serve wallpapers
adb logcat -c && adb logcat | grep -iE 'CoreMotion|projengmenu|WallpaperProvider'

# 5. Force Projectivy to re-enumerate
adb shell am force-stop com.spocky.projengmenu
```

Expected from step 2: one service, `tv.corebuilds.motion/.WallpaperProviderService`.
If that prints nothing, the APK isn't installed (Finding 1/2). If it prints the
service and Projectivy still shows no source, it's Premium or the launcher cache
(Finding 5).

---

## What was checked and found correct

Recorded so nobody re-litigates it:

- `motion-plugin/.../aidl/tv/projectivy/plugin/wallpaperprovider/api/*.aidl` —
  identical to upstream (`diff` clean, all three files).
- `.../java/tv/projectivy/plugin/wallpaperprovider/api/{Event,Wallpaper,WallpaperProviderContract}.kt`
  — identical to upstream (`diff` clean).
- Package of the API classes is `tv.projectivy.plugin.wallpaperprovider.api`,
  **not** relocated under `tv.corebuilds.*`. Relocating it is the classic way to
  break discovery; this repo did it right.
- `buildFeatures { aidl = true }` is set (mandatory from AGP 8).
- `kotlin-parcelize` plugin applied — required for `@Parcelize` on `Wallpaper`.
- `isMinifyEnabled = false`, so R8 cannot strip `WallpaperProviderService` or the
  generated `Stub`. (If you ever turn minify on, you need
  `-keep class tv.projectivy.plugin.wallpaperprovider.api.** { *; }` and
  `-keep class tv.corebuilds.motion.WallpaperProviderService { *; }`.)
- The service is `android:exported="true"` with the correct action, and the
  meta-data sits on the `<service>` (not the `<application>`), which is where
  Projectivy reads it.
- `applicationId` does **not** need to live under `tv.projectivy.*`; third-party
  plugins in the wild use their own namespaces.
