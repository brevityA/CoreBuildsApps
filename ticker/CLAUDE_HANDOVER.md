# Core Line — handover to the next agent (Claude)

> **How to use:** paste this whole file as the first message to a fresh Claude
> (or tell it: “Read `ticker/CLAUDE_HANDOVER.md`, then `ticker/HANDOVER.md`.”).
> It is self-contained: enough to continue without the original chat.
> Last updated **2026-09-03** after the audit + polish engagement (Phases 0–4).

---

## 1. One-line mission

Core Line is a **TV-first sports + channel ticker (chyron)** for Android/TV —
not a player, not streams. It renders a line like:

```
LIVE  TOR 3-2 MTL · TSN4 SN 3   ◆   LAL vs BOS 7:00 PM · ESPN
```

This engagement was: **audit the existing app, then fix reliability and visual
polish without replacing the product.** Preserve behavior users rely on.

**Where the code is:** `/home/user/CoreBuildsApps/ticker/` — self-contained,
Node 20+, zero npm runtime deps. The rest of the repo is unrelated.

---

## 2. Status snapshot (2026-09-03)

**All four phases are complete.** The tree is green and two APKs are built.

| Phase | Deliverable | State |
|---|---|---|
| 0 Audit | `ticker/AUDIT.md` (findings A1–F7 with file:line, severity, repro, fix) | ✅ done |
| 1 Never blank | backoff, per-source last-good, parser hardening, isolation | ✅ done, tested |
| 2 Broadcast polish | constant px/s ribbon, watchdog, bundled fonts, TV scale, overscan | ✅ done, tested |
| 3 D-pad / settings | focus trap, reorder, refresh interval, speed/position | ✅ done, tested |
| 4 Tests + verification + build | 88/88 tests, soaks, failure injection, 2 APKs | ✅ done (see §9) |

**Only one thing is still outside the sandbox:** clean-install + first-run on a
real device/emulator and the multi-hour device soak. That is **[USER TO SUPPLY]**
— the sandbox has no display and no `/dev/kvm` (no emulator).

**2026-09-04 supporter feedback round** (see `VERIFICATION.md` §8 + §8.1): shipped —
(1) "LIVE badge on a rerun" parser fix (leading live indicator only, word-bounded,
`FINAL`/`FT` detection, replay markers); (2) college sports by default
(NCAAF/NCAAB in `DEFAULT_LEAGUES`) + `guessLeague`/`leagueFromCategory` keywords;
(3) per-feed 📡 tabs + RSS/JSON category bucketing; (4) rss.app JSON
(`content_html`/`date_published`); (5) sport super-tabs (`SPORT_GROUPS`); (6)
favorites on TV (card ★ button + settings team checklist); (7) `[data-tv]` 10-foot
pass (root 20px, minmax(360px,1fr) grid, 5px focus ring, header stats hidden).

**2026-09-04 second round — D-pad, settings, watch apps** (see `VERIFICATION.md` §8.2):
(8) rewritten spatial D-pad nav (`tv.js`) — cross-axis overlap preference
("down means down", not diagonal), auto-scroll focused item into view, focus never
dies; (9) settings menu restructured into a **left rail + content pane** (the
standard TV pattern — Netflix/Leanback style), with Feeds / Scoreboards /
Ticker & display / Watch apps / Help sections; (10) **Watch apps** — assign any
installed app per league, then ▶ Watch on a game (or the W key) opens it;
web fallback opens the ESPN game page. Android side: `LineBridge.listLaunchableApps()`
/ `openApp()` / `openUrl()` + manifest `<queries>` (MAIN/LAUNCHER +
LEANBACK_LAUNCHER, no QUERY_ALL_PACKAGES). 88/88 tests.

**2026-09-04 third round — in-app updates + full UI polish** (see `VERIFICATION.md` §8.3):
(11) **in-app updates** (sideload, no Play Store) — `lib/version.mjs` (semver
compare + GitHub-release parse/status, unit-tested), an **Updates** settings
pane (current version, update banner, release notes, check/download/install
buttons), and an Android updater (`UpdateManager.kt`): download the
`coreline-release.apk` through a host-allowlisted URL (github.com /
*.github.com / release-assets.githubusercontent.com), write to `cache/updates/`,
then `ACTION_VIEW` via a scoped FileProvider (`file_paths.xml` exposes only
`cache/updates/`). `REQUEST_INSTALL_PACKAGES` declared so the system installer
can prompt for unknown-sources. `MainActivity.getVersion()` /
`installUpdate(url)` + `LineBridge` bindings; `BuildConfig.VERSION_NAME` feeds
the comparison. (12) **UI polish pass** — tactile button states, focus rings,
input accent colours, broadcast-style section labels, chyron edge fades, thin
scrollbars, tabular numerals, card/toggle shadows, reduced-motion support.
Local version bumped to **versionCode 5 / versionName 1.2.0** (ahead of
GitHub's 1.1.0). 88/88 tests.

**2026-09-04 fourth round — phone floating overlay** (see `VERIFICATION.md` §8.4):
(13) **ticker over other apps on phones/tablets** — `OverlayService.kt` draws
the chyron as a translucent, touch-through, always-on-top window above every
other app (foreground service, `TYPE_APPLICATION_OVERLAY`). It reuses the SAME
web app (`https://coreline.local/index.html?native=1&overlay=1`) so it shares
the parser, the `/api/proxy` data path, and the app's localStorage settings;
the overlay page strips everything but the chyron via `[data-overlay]` CSS.
Toggle lives in Settings → Ticker & display (phone/native only). Needs
`SYSTEM_ALERT_WINDOW` (Settings → display over other apps); on Android TV the
app refuses to start it (TV has no overlay windows). Stop via the notification
or the checkbox. `MainActivity`/`LineBridge` add `canDrawOverlays` /
`overlayActive` / `startOverlay` / `stopOverlay`.

**2026-09-04 fifth round — Android TV UI consistency** (see `VERIFICATION.md` §8.5):
(14) the TV (10-foot) pass was **inconsistent and blown out** — text sizes
scattered from 13.2px (health pill, `.when`) to 68px (hero score), checkboxes
(16px) smaller than their labels, square controls at 44/46/52/56px. Replaced
the ad-hoc `[data-tv]` overrides in `public/css/app.css` with ONE coherent
scale ladder on the 20px root — micro .8rem / body .95rem / control 1.1rem /
title 1.3rem / display 2rem; every square control 56px (48px in-card); hero
numerals tamed to 48/56px; checkboxes 26px (and `flex: 0 0 auto` so they can't
shrink); `.abbr` gets an ellipsis guard. Pinned by headless TV checks in
`tests/smoke-updates.mjs` (health ≥16px, team names == brand size, no abbr
overflow). Phone layout untouched.

---

## 3. Locked product decisions — DO NOT reopen

(From `ticker/HANDOVER.md`, which is authoritative. Restated so you don't have to flip back.)

| Decision | Choice |
|---|---|
| Product | **Android/TV v1** — the installable app is the product |
| Surfaces | **Phone and TV equally** — one APK, one responsive UI |
| Delivery | **Sideload APK** (Fire Stick / Shield). Not Play Store. |
| Feeds | **Public scoreboards + user-pasted RSS only.** No bundled pirate/IPTV sources. Ever. |
| Add-feed on TV | **Same-Wi-Fi QR / IP form** (owner won't type a URL on a Fire remote) |
| Phone layout | One responsive UI, tappable **and** focusable (not a separate phone app) |
| First 3 seconds | **Open → slate already crawling.** Never blank, never a settings wall. |
| Pair network | Same Wi-Fi, **no cloud relay** |

**Hard no:** no video playback, no streams, no IPTV playlists, no bundled illegal
sources. It is a TV *guide*; the user pastes their own RSS.

**Working rules from this engagement (still standing):**
- Preserve existing behavior/architecture; fix, don't rewrite. No gratuitous rewrites.
- No heavy frameworks unless already in the stack (there are none — vanilla JS ES modules + Kotlin WebView shell).
- Never fabricate data — use real score/RSS sources. If a source needs a key, mark `[USER TO SUPPLY]` and degrade gracefully.
- Respect source ToS/rate limits; the app self-throttles and caches.
- Verify current Android TV/leanback APIs and score-feed shapes against live docs; cite them; mark unknowns `[UNVERIFIED]`, never guess.
- Keep credentials out of logs, source, and reports. (None are used today.)

---

## 4. What this engagement changed (file map)

### New files
| Path | What it is |
|---|---|
| `AUDIT.md` | Phase 0 audit — authoritative findings A1–F7 + fix→requirement map + verification status (§5 of that file) |
| `VERIFICATION.md` | Phase 4 report: 88/88 tests, failure injection, soaks, browser smoke, APK build + manifest checks, manual/soak checklist, and the 2026-09-04 supporter-feedback + D-pad/settings/watch-apps + updates/polish + overlay + TV-consistency rounds (§8–8.5) |
| `lib/backoff.mjs` | `createBackoff()` — exp backoff + jitter, cap, `Retry-After` parser. Shared (server + client). |
| `lib/source-registry.mjs` | `SourceRegistry` — per-league (`league:id`) and per-feed (`feed:url`) keys; backoff state, `lastGood[]`, `lastError`, `health`, hydrate/dehydrate; `MAX_ITEMS_PER_SOURCE = 100`. |
| `lib/watch.mjs` | Watch integration: curated sports-app list, `sortAppsForPicker`, `espnWebUrl`, `watchChoiceFor`, `leagueIdForLabel` — pure logic for "open a game in an assigned app". |
| `lib/version.mjs` | In-app update logic: `compareVersions`, `versionFromCorelineTag`, `latestCorelineRelease`, `updateStatus` — pure, unit-tested. |
| `android/app/src/main/java/dev/corebuilds/line/UpdateManager.kt` | Sideload updater: host-allowlisted download → `cache/updates/` → FileProvider `ACTION_VIEW` to the system installer. |
| `android/app/src/main/res/xml/file_paths.xml` | FileProvider paths — exposes only `cache/updates/`. |
| `android/app/src/main/java/dev/corebuilds/line/OverlayService.kt` | Phone floating ticker: translucent touch-through `TYPE_APPLICATION_OVERLAY` window hosting the same WebView app (`?native=1&overlay=1`); foreground service w/ stop notification; TV refused. |
| `android/app/src/main/res/drawable/ic_notification.xml` | Ticker-strip glyph for the overlay foreground-service notification. |
| `public/js/ticker.js` | `Ticker` class — constant px/s `translate3d` ribbon, rAF loop, seamless wrap, ResizeObserver re-measure. Replaces the CSS-keyframe crawl. |
| `public/js/watchdog.js` | `startWatchdog()` — samples ribbon progress, fires stall callback, wakes on visibility/page/focus. |
| `tests/backoff.test.mjs`, `tests/source-registry.test.mjs`, `tests/parser-robustness.test.mjs`, `tests/client-slate-resilience.test.mjs`, `tests/ticker.test.mjs`, `tests/watchdog.test.mjs`, `tests/feedback.test.mjs`, `tests/watch.test.mjs`, `tests/version.test.mjs` | New unit suites (64 of the 88 tests; `feedback.test.mjs` pins the supporter complaints, `watch.test.mjs` pins the watch-app URL/assignment logic, `version.test.mjs` pins the in-app update semver/tag/status logic) |
| `tests/soak.mjs` | Standalone accelerated in-process soak (`node tests/soak.mjs`; not part of `npm test`) |
| `tests/mockfeeds.mjs` | Hostile fixture server on 127.0.0.1:8799 (`/good` 200 RSS, `/down` 502) for real-socket server soaks |
| `releases/CoreLine-debug-v1.2.0.apk` | **Installable, debug-signed** sideload APK (3.08 MB) |
| `releases/CoreLine-release-unsigned-v1.2.0.apk` | Release variant, unsigned (2.26 MB) — sign via CI keystore |

### Modified files
| Path | Change |
|---|---|
| `lib/client-slate.mjs` | Rewritten: per-source isolated fetches via registry, last-good fallback on failure, backoff skip, demo only when nothing at all, native `/api/proxy` path |
| `lib/parser.mjs` | Exports `MAX_ITEMS_PER_FEED = 100`; RSS/JSON capped; `stripTags` CDATA-safe; JSON `null`/non-object rows skipped |
| `lib/scoreboard.mjs` | Exports `DEFAULT_LEAGUES` + `LEAGUE_LABELS` (single source for defaults) |
| `server.mjs` | Uses `createBackoff` + `DEFAULT_LEAGUES`; `/api/rss` and `/api/slate` use `resilientFeed`; per-league scoreboard slots w/ backoff + last-good; slate adds `feeds[].stale` and `health` |
| `public/js/app.js` | Rewritten wiring: single-flight refresh, silent resume refresh, `prefers-reduced-motion` clamps speed ≤12 px/s, ticker/watchdog integration, health pill; sport super-tabs, per-feed 📡 tabs, favorites (card ★ + team picker), Watch buttons + `openGame`, drawer rail sections, `W` key; Updates panel (`renderUpdates` / `checkForUpdates` / `installUpdateFlow`) |
| `public/js/state.js` | Rewritten sanitizer/defaults/cache; adds `watchApps` (leagueId → app pkg or `web`) |
| `public/js/tv.js` | Spatial D-pad nav: cross-axis overlap preference, scroll-into-view, focus confinement in the drawer |
| `public/index.html` | Boot guard (old-WebView message), health pill, speed stepper, refresh/position selects, `crawl-mask` id; **no Google Fonts link**; drawer restructured into a rail + 5 sections (Feeds / Scoreboards / Ticker & display / Watch apps / Help) |
| `public/css/app.css` | Local `@font-face`s, TV/overscan/top-position styles, `.health` states, league-accent cards, JS-transform crawl styling, reduced-motion/TV scaling, stepper/reorder/boot-fallback CSS, and the global fix `[hidden] { display: none; }` (see A3) |
| `android/.../LineWebClient.kt` | Oversized fetched bodies → HTTP 502 (not silent truncation) |
| `android/.../MainActivity.kt` | `OnBackInvokedDispatcher` on API 33+, shared `handleBack()`; adds `listLaunchableApps()` / `openApp()` / `openUrl()` for the Watch feature; `getVersion()` / `installUpdate(url)` for in-app updates |
| `android/.../LineBridge.kt` | JS bridge: `listLaunchableApps`, `openApp`, `openUrl`, `getVersion`, `installUpdate`, `canDrawOverlays`, `overlayActive`, `startOverlay`, `stopOverlay` |
| `android/.../AndroidManifest.xml` | `<queries>` MAIN/LAUNCHER + LEANBACK_LAUNCHER + VIEW/https (package visibility for the app picker); `REQUEST_INSTALL_PACKAGES` + `FileProvider` (${applicationId}.fileprovider) for the sideload updater; `SYSTEM_ALERT_WINDOW` + `FOREGROUND_SERVICE(_SPECIAL_USE)` + `POST_NOTIFICATIONS` + `OverlayService` (`foregroundServiceType="specialUse"`) for the phone overlay |

### Fixed during this engagement (notable)
- **A3 (P0, visual):** the settings drawer's `hidden` attribute was overridden by its `display:flex` rule, so the dimmed overlay was **visible on first paint** over the whole board. Fix: `[hidden] { display: none; }` appended to `app.css`. Verified in jsdom and real Chromium.
- **A1 (P0, blank-screen risk):** ES-module-only UI + minSdk 24 (Android 7 / Fire OS 6) → old WebViews silently ignore module scripts → blank screen. Mitigated with an inline classic-script boot guard in `index.html` + documented support floor (Fire OS 7+ / WebView ≥ 61).
- **F4 (false alarm):** `fonts/outfit-var.woff2` IS a genuine variable font (fontTools: `fvar` table, `wght` 100–900). No change needed.

---

## 5. Architecture (deltas only — full picture in HANDOVER.md)

```
web:        browser → GET /api/slate → Node server (proxies ESPN + user RSS, SSRF)
Android/TV: WebView loads https://coreline.local/index.html?native=1[&tv=1]
            JS buildClientSlate() → GET /api/proxy?url=… → Kotlin HttpURLConnection
```

Resilience wiring (what changed):
- **Per-source isolation + backoff:** every league/feed slot in `SourceRegistry`
  tracks backoff + last-good. A dead feed is retried with exp backoff + jitter
  (15 s base → 10 min cap, honors `Retry-After`); healthy sources keep refreshing.
  Repeated failures *skip* the fetch entirely (`error:"backoff"`).
- **Last-good:** failed sources keep their last items on the ribbon (`stale:true`)
  so a flaky network never blanks the ticker. `health:{degraded,stale}` is surfaced
  as a subtle pill (“All sources live” / “N retrying” / “showing cached”).
- **Parser hardening:** malformed/empty/truncated XML and garbage JSON return `[]`,
  never throw. CDATA and numeric entities handled. 100-item cap per feed.
- **Ticker:** constant px/s, `translate3d`, rAF, seamless wrap. Speed slider now
  means px/s (not inverted seconds). `watchdog.js` restarts a stalled ribbon.
- **Defaults:** single source in `lib/scoreboard.mjs` (`mlb,nfl,nba,nhl,epl,mls,wnba`).

Settings persisted in `localStorage` key `coreline.v1`; slate cached `coreline.v1.slate`.

---

## 6. Data sources (all keyless, shapes verified live 2026-09-03)

| Source | Endpoint | Notes |
|---|---|---|
| ESPN | `https://site.api.espn.com/apis/site/v2/sports/{sport}/{league}/scoreboard` (e.g. `hockey/nhl`, `baseball/mlb`, `basketball/nba`, `football/nfl`, `soccer/eng.1`, `soccer/usa.1`, `basketball/wnba`) | **Undocumented endpoint** — no public ToS/rate contract. App self-throttles. If shape changes, adapter returns `[]` → demo/fallback. |
| MLB | `https://statsapi.mlb.com/api/v1/schedule?sportId=1&date=…&hydrate=…` | keyless |
| NHL | `https://api-web.nhle.com/v1/score/now` | returns bare HTTP **307 redirect on first hit — follow redirects** |

No API keys anywhere. `lib/rss.mjs` uses Node `Buffer` — **do not import it in the browser** (HANDOVER.md rule still applies).

---

## 7. Exact commands

### Web + tests
```bash
cd /home/user/CoreBuildsApps/ticker
node server.mjs          # serves on 0.0.0.0:8787
npm test                 # node --test tests/*.test.mjs  → 88/88
node tests/soak.mjs      # standalone soak (NOT in npm test)
```
Keyboard (web): D-pad move · Enter select · `S` settings · `T` ticker-only · `R` refresh · `F` fullscreen.

### Android build (the command that WORKS — do not drop the memory caps)
```bash
cd /home/user/CoreBuildsApps/ticker/android
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
export GRADLE_USER_HOME=/opt/gradle-home
./gradlew :app:assembleDebug --no-daemon \
  -Dorg.gradle.jvmargs="-Xmx896m -XX:MaxMetaspaceSize=320m -Dfile.encoding=UTF-8" \
  -Dorg.gradle.workers.max=1 -Dorg.gradle.parallel=false \
  -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.configuration-cache=false
```
`assembleRelease` works the same way (minify is off; release only signs when
`KEYSTORE_PATH`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` env vars are set —
otherwise you get `app-release-unsigned.apk`).

APKs land in `app/build/outputs/apk/{debug,release}/`; I also copied them to
`ticker/releases/`. **`app/build/` is excluded from workspace snapshots** — keep
the `releases/` copies.

### One-time environment setup (fresh sandbox → rebuild APK)
```bash
# JDK 21 (Gradle 8.7 / AGP 8.5.2 need 17+; only 11 was preinstalled)
sudo apt-get update && sudo apt-get install -y openjdk-21-jdk-headless
# Android SDK
sudo mkdir -p /opt/android-sdk && sudo chown -R "$(whoami)" /opt/android-sdk /opt/gradle-home
curl -sLo /tmp/cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q /tmp/cmdtools.zip -d /opt/android-sdk/cmdline-tools
mv /opt/android-sdk/cmdline-tools/cmdline-tools /opt/android-sdk/cmdline-tools/latest
yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk --licenses
/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk \
  "platforms;android-34" "build-tools;34.0.0" "platform-tools"
echo 'sdk.dir=/opt/android-sdk' > android/local.properties   # (gitignored)
# swap — prevents the sandbox OOM (see §8)
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
```

### Headless browser smoke (Playwright)
```bash
mkdir /tmp/domtest && cd /tmp/domtest && npm init -y && npm i playwright
sudo apt-get install -y libnss3 libnspr4 libatk1.0-0 libatk-bridge2.0-0 libatspi2.0-0 \
  libxdamage1 libxkbcommon0 libasound2 libx11-xcb1 libxcomposite1 libxrandr2 libgbm1 libcups2
npx playwright install chromium
# launch with: chromium.launch({ args: ['--no-sandbox'] })
# smoke target: http://localhost:8787/  (start `node server.mjs` first)
```
For DOM/cascade checks: **use jsdom 24** (`npm i jsdom@24`); jsdom 30 crashes on
Node 20 (`webidl.util.markAsUncloneable is not a function`).

---

## 8. Environment gotchas & dead ends (do NOT repeat)

1. **OOM:** `android/gradle.properties` sets `-Xmx2g`. On a 1.9 GB sandbox the
   first build **OOM-thrashed the whole box for ~25 min** (even `echo` timed out;
   load hit 20+). Fix that worked: the `-Dorg.gradle.jvmargs="-Xmx896m…"` override
   (command line beats gradle.properties) + 2 GB swap. After that, `assembleDebug`
   ran in **21 s** (cache warm). **Never run Gradle here without the caps.**
2. `apt-get update` without sudo fails (lock permission) — always `sudo`.
3. Playwright `--with-deps` fails (no candidates for `ttf-unifont` /
   `ttf-ubuntu-font-family`); the plain `sudo apt-get install` list in §7 works.
4. NHL `api-web.nhle.com/v1/score/now` → bare HTTP 307; follow redirects.
5. jsdom 30 is broken on Node 20 — use jsdom 24.
6. No `/dev/kvm` → **cannot run an Android emulator** in this sandbox. Device
   verification is the owner's.
7. `tests/soak.mjs` and `tests/mockfeeds.mjs` are **not** `*.test.mjs` on purpose
   (soak is standalone; mockfeeds is a fixture server).
8. Do not assume a future unit-test failure is a product bug until the harness is
   reconciled with the real `public/js/ticker.js` / `watchdog.js` / `app.js`
   semantics (the ticker/watchdog tests use a fixed-step fake-DOM timing model).

---

## 9. Verification results (all exact)

- **Tests:** `npm test` → **88/88 pass**. 24 baseline
  (client-slate, parser, scoreboard, ssrf) + 64 new (backoff, source-registry,
  parser-robustness, client-slate-resilience, ticker, watchdog, feedback,
  watch, version).
- **Accelerated in-process soak** (`node tests/soak.mjs`): 3,000 cycles ≈ 50 h →
  0 blank cycles, 1,000 flaky stale hits (last-good kept), heap growth **7.11 MB**.
- **Real-socket server soak:** 70 cycles / 91 s against good+down mock feeds →
  70/70 responses, 0 blank slates, 67 backoff hits, server VmRSS delta **0 kB**.
- **Failure injection:** dead feed never blanks a healthy feed; last-good retained
  on failure; repeated failure → backoff skip; malformed/empty/garbage bodies → `[]`.
- **Browser smoke (headless Chromium, 1280×720):** drawer `display:none` on first
  paint (A3 fix confirmed), ribbon transform advancing, health pill “All sources
  live”, bundled fonts loaded, crawl-mode big clock `flex`, **0 console errors**.
- **APK manifest (aapt2):** `dev.corebuilds.line` v1.2.0 (code 5), minSdk 24,
  targetSdk 34, permissions INTERNET/ACCESS_NETWORK_STATE/WAKE_LOCK +
  REQUEST_INSTALL_PACKAGES, label “Core Line”, `LEANBACK_LAUNCHER` + `tv_banner`
  + `uses-feature leanback` present; `FileProvider`
  (`dev.corebuilds.line.fileprovider`, exported=false, grantUriPermissions=true)
  wired to `file_paths.xml` (cache/updates only); all 28 web assets bundled
  (incl. `[hidden]` fix, `ticker.js`, `watchdog.js`, `backoff.mjs`,
  `source-registry.mjs`, `watch.mjs`, `version.mjs`).
- **Live API sample:** `leagues=mlb` + good/down mock feeds → 16 MLB events both
  calls; down feed went `502 → backoff`; health `degraded:1, stale:0`.

---

## 10. What remains (prioritized)

1. **[USER TO SUPPLY] Sideload + first-run + manual checklist** — `releases/CoreLine-debug-v1.2.0.apk` onto a Shield/Google TV/Fire TV (Fire OS 7+); run `VERIFICATION.md` §5 (first launch crawls offline, airplane-mode 2 min, malformed/empty feed injection, full D-pad walk incl. reorder + interval + position, back-behavior, ≥6 h memory soak, 10-foot legibility). Also exercise Settings → Updates (check + install flow needs a published `coreline-v*` release to be fully end-to-end). Report which device/emulator.
2. **[USER TO SUPPLY] Signed release** — land `ticker/android/github-workflow-core-line-apk.yml` at `.github/workflows/core-line-apk.yml` (owner copy, since the CI bot can't push `.github/workflows/*`); push a `coreline-v*` tag with the keystore in GitHub Secrets. Local release APK is currently unsigned by design.
3. **Tester round-trip** — send the 2026-09-04 fixes back: rerun the pasted feed, confirm the rerun no longer shows LIVE, confirm college games appear, try the 📡 feed tab + ★ favorites + sport super-tabs on the TV. Ask for the **exact title** of any item that still shows a wrong LIVE/FINAL badge so the parser rules can be tuned (a title alone can't always distinguish "NFL Live" the show from a live game).
4. **Repo hygiene — `.git` was lost from the workspace** (code is intact): re-`git init` or re-clone from `brevityA/CoreBuildsApps` and re-apply `ticker/` before pushing. The zip in `releases/../` ships the full `ticker/` tree, so nothing is missing.
5. **Optional tidy:** `HANDOVER.md`'s "Known leftover bugs" and "Suggested first tasks" are now stale (items 2/4/6/9 addressed, first-tasks done). Update them to point at `CLAUDE_HANDOVER.md` + `VERIFICATION.md`, or fold this file in. Low priority, owner-visible doc — don't do it silently.
6. **If a future sandbox rebuild is needed:** re-run the §7 environment setup (JDK 21, SDK, swap). The Gradle cache (`/opt/gradle-home`, ~900 MB) and SDK (`/opt/android-sdk`) live outside `/home/user`, so they do **not** persist in workspace snapshots.

---

## 11. Prompt to paste to the next agent

```
You are continuing Core Line. Read ticker/CLAUDE_HANDOVER.md first, then
ticker/HANDOVER.md. Do not reopen locked decisions. Do not add streams or
pirate sources.

Product: Android/TV sports + channel ticker (chyron). "Team vs Team · ESPN, TSN4, SN 3".
Sideload APK. Public scoreboards + user-pasted RSS only. Same-Wi-Fi QR to add feeds.
First launch already crawling — never blank, never a settings wall.

Status as of 2026-09-04 (audit + reliability + polish + feedback + D-pad + in-app
updates rounds, all shipped):
- Audit: ticker/AUDIT.md (A1–F7, each with file:line, severity, fix).
- Reliability: per-source backoff + last-good (lib/backoff.mjs,
  lib/source-registry.mjs), hardened parser (lib/parser.mjs), client/server
  isolation (lib/client-slate.mjs, server.mjs), frozen-ribbon watchdog.
- Polish: constant px/s ticker (public/js/ticker.js), bundled fonts, TV scale,
  overscan, league accents, D-pad + settings (reorder, interval, speed, position).
- Feedback round: LIVE-on-rerun parser fix, NCAAF/NCAAB by default, per-feed 📡
  tabs, sport super-tabs, card ★ + settings team-picker favorites, rss.app JSON,
  fuller [data-tv] 10-foot pass. See VERIFICATION.md §8–8.1.
- D-pad/settings/watch-apps round: spatial D-pad nav (tv.js), left-rail settings,
  Watch-apps assignment. See VERIFICATION.md §8.2.
- Updates + polish round: in-app updates (lib/version.mjs + UpdateManager.kt +
  FileProvider + Updates pane) and a full cosmetic polish pass; local version is
  1.2.0 (code 5). See VERIFICATION.md §8.3.
- Overlay round: phone floating ticker over other apps (OverlayService.kt,
  translucent touch-through window reusing the same WebView app). TV overlay /
  home-screen widget are NOT possible on Android TV — see VERIFICATION.md §8.4.
- Verified: npm test = 88/88; 50 h soak + real-socket soak = 0 blank, ~0 memory
  growth; headless-Chromium smoke clean. See ticker/VERIFICATION.md.
- Built: ticker/releases/CoreLine-debug-v1.2.0.apk (installable) and
  CoreLine-release-unsigned-v1.2.0.apk. Manifest/leanback/FileProvider/assets
  verified.

Remaining (owner actions, cannot be done in sandbox — no device, no /dev/kvm):
1. Sideload the debug APK and run VERIFICATION.md §5 manual/soak checklist
   (incl. Settings → Updates).
2. Land .github/workflows/core-line-apk.yml and tag coreline-v1.2.0 for a signed
   release — this also makes the in-app updater live for existing 1.1.0 devices.

Hard no: video playback, IPTV playlists, bundled illegal sources, native rewrite
of the board (WebView + shared JS parser IS the product).
```
