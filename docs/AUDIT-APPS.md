# CoreBuildsApps audit — apps, weak points, release readiness

Date: 2026-09-06. Agent knowledge cutoff: 2024-06; current Play/Android requirements below were verified against official docs during this audit where cited, otherwise marked `[UNVERIFIED]`.

## Official policy/readiness checks verified

- Google Play target API: starting 2026-08-31, new apps and updates must target Android 16 / API 36+, except Android TV and Android XR apps which must target Android 14 / API 34+; existing apps must target Android 15 / API 35+ to remain available to new users on devices above the app target. Source: <https://developer.android.com/google/play/requirements/target-sdk>.
- Android TV launchability: TV apps need a `CATEGORY_LEANBACK_LAUNCHER` activity; must declare touchscreen not required; TV launch banners are required and documented as 320 x 180 px xhdpi resources. Source: <https://developer.android.com/training/tv/start/start.html>.
- Play App Signing: Play manages/protects the app signing key; developer keeps an upload key and signs the AAB before upload. Source: <https://support.google.com/googleplay/android-developer/answer/9842756>.
- Data safety: every Play app must complete an accurate Data safety form; data processed only locally on-device is not in scope for collection disclosure. Source: <https://support.google.com/googleplay/android-developer/answer/10787469>.
- Privacy policy / personal data: apps must provide accurate disclosures and privacy policy when required; data practices must match Data safety. Source: <https://support.google.com/googleplay/android-developer/answer/10144311>.

## Repository inventory

| App/module | Gradle root | AGP/Kotlin | SDKs | Version | Signing | Build types | Key deps | Permissions/components | CI/release status | Tests/update path |
|---|---|---:|---|---|---|---|---|---|---|---|
| Icon pack (`app/`) | repo root | AGP 8.5.2 / Kotlin 1.9.24 | min 21 / target 34 / compile 34 | code 15 / `1.8.2` | release from `KEYSTORE_*` env only; no keystore in git | debug/release; release no minify/shrink to preserve by-name drawables | appcompat 1.7.0, core-ktx 1.13.1, recyclerview 1.3.2; outdated vs current `[UNVERIFIED]` | Internet/network, request-install, wallpaper/storage maxSdk 28; exported launcher/icon-pack discovery; non-exported FileProvider/wallpaper screens | Existing `.github/workflows/build.yml`; new `suite-ci.yml` gates build/lint/test/artifact; `suite-release.yml` builds APK/AAB and metadata | Python validator/content tests; in-app `Latestrelease/version.json` updater hardened in this change |
| Core Line (`ticker/android`) | `ticker/android` | AGP 8.5.2 / Kotlin 1.9.24 | min 24 / target 34 / compile 34 | code 6 / `1.3.0` | `KEYSTORE_*` env only | debug/release; release no minify | core-ktx 1.13.1; web modules in assets | Internet/network/wakelock/request-install/overlay/FGS/notifications; exported TV/mobile launcher; non-exported overlay service/FileProvider | Existing `core-line-apk.yml`; new suite CI/release gates | 114 Node tests after change; updater now uses `Latestrelease/coreline-version.json`, native `BuildConfig.VERSION_CODE`, native package/signature/hash verification |
| Core Shift (`shift/`) | `shift` | AGP 8.5.2 / Kotlin 1.9.24 | min 26 / target 34 / compile 34 | code 11 / `2.3.5` | `KEYSTORE_*` env only | debug/release; release no minify/shrink | appcompat/core/recyclerview, Media3 1.4.1; outdated vs current `[UNVERIFIED]` | Internet, SET_WALLPAPER, WRITE_EXTERNAL_STORAGE maxSdk 28; exported launcher and DreamService with bind permission; non-exported FileProvider | Existing `core-shift-apk.yml`; new suite CI/release gates | Python content/screensaver tests; updater hardened and manifest URL fixed |
| Core Doctor (`doctor/`) | `doctor` | AGP 8.5.2 / Kotlin 1.9.24 + serialization | min 26 / target 35 / compile 35 | code 1 / `0.1.0` | `KEYSTORE_*` env only | debug/release | Compose BOM 2024.06, activity-compose 1.9.0, appcompat 1.7.0, okhttp 4.12.0, serialization 1.6.3, coroutines 1.8.1; outdated vs current `[UNVERIFIED]` | Network diagnostics only; launcher activity; no backend | Existing `core-doctor-apk.yml`; new suite CI/release gates | Existing JUnit + new redaction test; Doctor now runs a local suite-health check for installed sibling apps and update metadata; no in-app updater yet (release APK built by CI) |
| Core Motion plugin (`motion-plugin/`) | `motion-plugin` | AGP 8.5.2 / Kotlin 1.9.24 + parcelize | min 26 / target 34 / compile 34 | code 1 / `1.0.0` | `KEYSTORE_*` env only | debug/release | core-ktx, appcompat, leanback 1.0.0, annotation 1.8.0; outdated vs current `[UNVERIFIED]` | Internet; exported Projectivy wallpaper provider service by required action; launcher settings activity | Existing `core-motion-apk.yml`; new suite CI/release gates | Contract workflow validates Projectivy plugin; no in-app updater (plugin discovered by Projectivy) |
| Motion engine/shaders/content | `motion-engine`, `motion-shaders`, `Motion`, `Wallpapers`, `tools` | Python/Node/static | n/a | content manifests | no secrets | n/a | CairoSVG/Pillow/resvg; shader/render tooling | GitHub-hosted feeds/release assets | render workflow and suite Python/content tests | feed/content validators |

## Build/test audit results

Local Android builds could not be executed in this sandbox because neither `JAVA_HOME` nor an Android SDK is installed (`./gradlew` exits before Gradle starts). The new CI workflows install JDK/Android SDK and run the missing Android build/lint/unit-test matrix. Local tests that do not need the Android SDK were run; results are in the final verification section.

## P0/P1 findings and fixes

| ID | Severity | Area | Evidence | Repro/root cause | Fix implemented | Regression gate |
|---|---|---|---|---|---|---|
| F1 | P1 broken update | Icon pack updater | `app/src/main/java/tv/corebuilds/iconpack/UpdateChecker.kt:60`, `:90` | Previous code fell back to package-manager version `0` on failure and read manifest via unbounded `readText()`. A version read failure could make every remote build look newer; a hostile/buggy manifest could OOM. | Use `BuildConfig.VERSION_CODE` only; bounded 64 KiB manifest read; keep correct `CoreBuildsApps/Latestrelease/version.json` only. | `tools/audit_contract.py`, `tests/test_v151_robustness.py` |
| F2 | P1 broken update | Core Shift updater URL | `shift/app/src/main/java/dev/corebuilds/shift/UpdateChecker.kt:14`, `:36`, `:66` | Updater pointed at stale `brevityA/CoreBuildsIconPack` repo path for `shift-version.json`; on 404 users never saw updates. | Point to `brevityA/CoreBuildsApps/main/Latestrelease/shift-version.json`; use `BuildConfig.VERSION_CODE`; bound reads. | `tools/audit_contract.py` |
| F3 | P1 update integrity | Icon pack / Shift / Line sideload updater | `app/.../UpdateInstaller.kt:215`, `:234`, `:243`; `shift/.../UpdateInstaller.kt:215`, `:234`, `:243`; `ticker/.../UpdateManager.kt:149`, `:162`, `:170` | Downloaders only checked host/size/ZIP magic. A compromised release URL/redirect/asset name could hand an unsigned or wrong-package APK to the installer. | Verify HTTPS allowlist, max size, ZIP/APK structure, optional SHA-256 from metadata, package name, exact expected newer versionCode, and signing certificate match before install. | `tools/audit_contract.py`; Core Line Node version metadata tests |
| F4 | P1 Core Line updater source/version | `ticker/public/js/app.js:56`, `:474-480`, `:495-497`; `ticker/lib/version.mjs:125` | Web UI parsed GitHub releases by semver only and had no authoritative native `versionCode`; it could not pass a hash/version contract to the Android installer. | Add `Latestrelease/coreline-version.json`; compare versionCode from `CoreLineNative.getVersionCode()`; pass `apkSha256` + `latestCode` to `installUpdateVerified`. | `ticker/tests/version.test.mjs` |
| F5 | P1 privacy | Core Doctor share reports | `doctor/.../DebridChecks.kt:73`, `:164`; `ReportCard.kt:33-35` | Exception messages and result text could contain bearer tokens, query tokens, or credential URLs; reports are explicitly shareable. | Add `Redactor`; sanitize all shared summary/fix text; bound debrid response bodies to 512 KiB. | `doctor/app/src/test/.../ReportRedactionTest.kt` |
| F6 | P1 TV lifecycle | Core Shift DreamService | `shift/.../CoreDreamService.kt:103-121` | Dream playback lifecycle only released on detach. Enter/exit or dream stop could leave player state ambiguous and buffer timeout callbacks armed. | Explicit `onDreamingStarted()` resume/prepare/play and `onDreamingStopped()` pause/remove callbacks. | `tests/test_core_shift_screensaver.py` + new suite CI |
| F7 | P1 release infra | suite workflows | `.github/workflows/suite-ci.yml`, `.github/workflows/suite-release.yml`, `tools/audit_contract.py` | Past failure: a shippable APK had no CI workflow. Existing workflows varied in gates and did not provide one central assertion. | Add matrix CI for every app; add contract job asserting every app has workflow/artifact/metadata/updater hardening; add tag/workflow-dispatch release builder for signed APK+AAB and metadata hash artifacts. | `python tools/audit_contract.py` |
| F8 | P2 observability | Core Doctor | `doctor/.../SuiteHealthChecks.kt`, `DoctorEngine.kt:14` | There was no no-backend way to inspect sibling app/update-feed status from one place. | Add local Doctor suite-health check for installed sibling apps and updater metadata reachability with bounded 64 KiB reads. | `tools/audit_contract.py` + Doctor unit/redaction tests |

## Additional P2 / deferred findings

- Current target SDKs are Play-submittable for TV apps under the verified 2026 rule (target 34+) but phone/general Play updates will need API 36 after 2026-08-31; Icon Pack target 34 and Doctor target 35 are not Play-ready for non-TV update submission after that date.
- Existing per-app workflows still coexist with new suite workflows. They build releases but do not all run Android lint; the suite gate now does.
- Core Doctor and Core Motion do not have in-app self-updaters. They are produced by release automation, but the in-app updater hardening in this change covers Icon Pack, Core Line, and Core Shift only.
- Dependency freshness was not fully verified against live Maven/npm version indexes; marked `[UNVERIFIED]` above.
- Full Android emulator soak could not be performed locally due missing SDK/JDK/KVM in sandbox; CI emulator/device jobs remain the place to run this.

## Security/privacy audit notes

- No keystore-like files are present in the repository; `tools/audit_contract.py` checks `.jks`, `.keystore`, `.p12`, `.pfx`, and `keystore.properties` are absent.
- Exported components are intentional: launcher activities, Projectivy provider service, DreamService with system bind permission. FileProviders and internal activities/services are not exported.
- Update installers never auto-install; they download only after user action and then invoke the system package installer.
- Doctor remains local-only; added redaction before share. No analytics/tracking was added.

## Play-readiness gates

| App | Target API gate | AAB | TV leanback/banner | Adaptive icon | Permissions justification | Data safety / privacy |
|---|---|---|---|---|---|---|
| Icon Pack | target 34: TV OK; non-TV Play update after 2026-08-31 requires API 36 | suite release builds AAB | has leanback launcher/banner; `leanback required=false` | present | Internet/update, install packages, wallpaper/storage maxSdk 28 | likely no collection; privacy policy URL `[USER TO SUPPLY]` |
| Core Line | target 34: Android TV OK | suite release builds AAB | leanback launcher/banner present; touchscreen not required | present | overlay/FGS/notifications must be justified for ticker overlay; request-install only sideload flavor ideally | feeds processed locally; no backend; privacy policy/data safety `[USER TO SUPPLY]` |
| Core Shift | target 34: Android TV OK | suite release builds AAB | leanback launcher/banner present; DreamService | present | Internet feed/download; wallpaper/storage maxSdk 28 | no analytics; remote GitHub content fetch; privacy policy `[USER TO SUPPLY]` |
| Core Doctor | target 35: non-TV update after 2026-08-31 requires API 36 | suite release builds AAB | phone/tablet diagnostic; no TV listing claim unless UX reviewed | present `[UNVERIFIED]` | Internet diagnostics; user-entered API keys transmitted only to selected providers | Data safety must disclose user-provided tokens if considered collection by app/provider; privacy policy `[USER TO SUPPLY]` |
| Core Motion | target 34: TV OK | suite release builds AAB | Projectivy plugin + settings launcher/banner | present | Internet feed access | no backend/analytics; privacy policy `[USER TO SUPPLY]` |
