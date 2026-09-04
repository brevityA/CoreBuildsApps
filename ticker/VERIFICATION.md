# Core Line — Verification (Phase 4)

Verification performed 2026-09-03 against the polished tree (post-AUDIT fixes).
Environment: sandboxed Linux (2 vCPU, ~1.9 GB RAM, Node 20.20.2) plus headless
Chromium (Playwright) for browser smoke tests, and JDK 21 + Android SDK 34 for the APK build.

## 1. Automated tests

```
cd ticker && npm test
```

**88 tests, 88 passing** (up from the 24 baseline tests). New coverage:

| File | Covers (req) |
|---|---|
| `tests/backoff.test.mjs` | exponential backoff + jitter bounds, cap, success reset, `canTry`/`waitMs`, `Retry-After` parsing, JSON round-trip (req 4) |
| `tests/source-registry.test.mjs` | per-source last-good, degraded flag, health counts, item cap, hydrate/dehydrate (req 4, 5) |
| `tests/parser-robustness.test.mjs` | malformed/empty/truncated XML, garbage bytes, null/non-object JSON rows, CDATA extraction, numeric entities, adversarial titles, 100-item cap (req 5) |
| `tests/client-slate-resilience.test.mjs` | one dead feed never blanks the slate or blocks a healthy feed; failing feed keeps last-good items (`stale`); repeated failures are skipped via backoff; demo only when there is nothing at all (req 4, 5) |
| `tests/ticker.test.mjs` | constant px/s loop math, wrap at seq width, short-content padding, dt clamping, stop/restart keeps position (req 8) |
| `tests/watchdog.test.mjs` | stall → `onStall` after two unchanged samples, no false stalls while moving, wake on visibility, background pages ignored (req 6) |
| `tests/feedback.test.mjs` | pins the 2026-09-04 supporter complaints: live/final/replay status rules, college leagues in defaults, `leagueFromCategory`, RSS/JSON category bucketing, rss.app JSON shape |
| `tests/watch.test.mjs` | `lib/watch.mjs`: app-picker sort/dedupe, `leagueIdForLabel`, `espnWebUrl` (game page + search fallback), `watchChoiceFor` assignment |
| `tests/version.test.mjs` | `lib/version.mjs`: semver ordering, `coreline-v*` tag parsing, newest-release selection, update-status (newer / up-to-date / no-asset) |

## 2. Failure injection (req 14)

### 2.1 Client path (deterministic, stubbed fetch)
`tests/client-slate-resilience.test.mjs` injects:
- **500 error** on one feed → slate stays populated, `feeds[].ok=false`, `health.degraded=1`.
- **fail after a success** → the dead feed's last-good items remain on the ribbon (`stale:true`).
- **repeated failure** → the second cycle *skips* the source (`skipped:true`, error `backoff`) instead of re-fetching.
- **malformed/empty/garbage** bodies → `parseFeed` returns `[]` without throwing.

### 2.2 Server path (real sockets)
A mock feed server (`tests/mockfeeds.mjs`, 127.0.0.1:8799) served one healthy RSS route and one permanently-502 route, reached through `mock-*.test` hosts (SSRF blocks raw loopback). Against the live `server.mjs`:

- **Call 1** — `GET /api/slate?leagues=mlb&feeds=good|Good,down|Down`:
  `events: 16` (MLB board intact), `Good → ok:true`, `Down → ok:false, error:"feed returned 502"`, `health.degraded:1`. **Never blank.**
- **Call 2 (immediate)** — `Down → error:"backoff"` (not re-fetched; exponential backoff engaged).

### 2.3 Network drop
- Web app: `localFallback()` renders the demo slate + bundled sample feed with zero network; last-good slate is read from `localStorage` first (`readCachedSlate`).
- Android app: all UI assets ship inside the APK (`syncWebAssets`); the bundled sample feed is served from assets, so first launch renders the crawl with no network at all.

## 3. Soak / memory (req 6, 14)

### 3.1 Accelerated in-process soak — `node tests/soak.mjs`
Simulated **3,000 refresh cycles ≈ 50 hours** against four hostile sources (healthy / flapping / malformed / empty) with a controllable clock.

```
simulated cycles : 3000 (≈ 50 h)
blank cycles     : 0
flaky stale hits : 1000   (last-good kept on failure)
heap growth      : 7.11 MB (budget 10 MB)
PASS
```

### 3.2 Real-socket server soak
`GET /api/slate` every ~1.3 s for **70 cycles / 91 s** with one healthy + one permanently-down feed, sampling the Node server's `VmRSS`:

```
successful responses : 70/70
blank slates         : 0
down feed backoff hits : 67
server VmRSS delta   : 0 kB
```

### 3.3 Multi-hour device soak (manual — REQUIRED, not yet run)
The sandbox has no display/device; a true multi-hour lounge/bar soak must run on hardware. Checklist in §5.

## 4. Browser smoke test (headless Chromium)
Loaded the real app at `http://localhost:8787/` (1280×720 viewport), waited for the first refresh, then asserted:

- `#drawer` computed `display: none` on first paint (drawer-overlay P0 fix confirmed — it previously rendered `flex` through its `hidden` attribute).
- Ribbon `transform` advances over 1.2 s (`translate3d(-166px,…) → translate3d(-228px,…)`) — constant-speed loop alive.
- Health pill reads **"All sources live"**; live-game count populated from real scoreboards.
- Bundled fonts loaded: `document.fonts.check('700 16px "Barlow Condensed"') === true`, Outfit `true`.
- Ticker-only mode (`data-mode=crawl`) shows the big clock (`display:flex`).
- Settings drawer opens and closes (`display:flex` ↔ `none`).
- **Zero console errors / page errors.**

## 5. Manual / soak checklist (run on device)

- [ ] Sideload `releases/CoreLine-debug-v1.2.0.apk` on a Shield / Google TV / Fire TV (Fire OS 7+).
- [ ] First launch (no network): crawl is populated (demo slate + bundled sample) — **never a blank screen**.
- [ ] Airplane mode 2 min → health pill turns amber ("retrying") and the ribbon keeps moving with last-good content; re-enable network → pill returns green within one refresh interval.
- [ ] Inject a malformed feed (a URL that returns HTML or garbage) via Settings → Add; the other feeds and scoreboards keep scrolling.
- [ ] Add an empty feed; ribbon still shows the scoreboards.
- [ ] D-pad: open Settings (⚙), move through every control, OK on checkboxes and the −/+ speed steppers; reorder a league and a feed with ▲/▼; change refresh interval and ticker position (top/bottom); confirm a visible focus ring on every control and no dead ends.
- [ ] Press Back while the drawer is open (closes the drawer, not the app), then again (backgrounds the app).
- [ ] Multi-hour soak (≥ 6 h): note memory in `adb shell dumpsys meminfo dev.corebuilds.line` at T+0 and T+6 h; the ribbon must still be moving (watchdog restarts it if it ever stalls).
- [ ] Screen off → on (and daydream if enabled): the clock is correct on resume and the ribbon is moving.
- [ ] 10-foot check: ticker and card text legible from ~3 m; no content under overscan on a classic TV (adjust `--overscan` if needed).
- [ ] Settings → Updates: shows the installed version; “Check for updates” reaches the GitHub release feed; with a newer `coreline-v*` release published, “Download & install” hands the APK to the system installer (unknown-sources prompt appears).

## 6. Release build (req 15)

Both variants built in-sandbox with JDK 21 + Android SDK 34 (AGP 8.5.2 / Kotlin 1.9.24 / Gradle 8.7, memory capped at 896 MB heap + 2 GB swap):

- **`releases/CoreLine-debug-v1.2.0.apk`** (3.08 MB, debug-signed, installable) — the sideload artifact.
- **`releases/CoreLine-release-unsigned-v1.2.0.apk`** (2.26 MB) — release variant compiles; signing is skipped because `KEYSTORE_PATH` is unset (by design: `app/build.gradle.kts` only signs release when a keystore is provided).

APK manifest verification (aapt2):
```
package: dev.corebuilds.line  versionCode 5  versionName 1.2.0
sdkVersion 24   targetSdkVersion 34
permissions: INTERNET, ACCESS_NETWORK_STATE, WAKE_LOCK, REQUEST_INSTALL_PACKAGES
label: "Core Line"
```

APK contents verified:
- All 28 web assets bundled (`index.html`, `css/app.css`, `js/*.js`, `lib/*.mjs`, `fonts/*.woff2`, bundled sample feed) — including the `[hidden]` CSS fix, `watchdog.js`, `ticker.js`, `backoff.mjs`, `source-registry.mjs`, `watch.mjs`, `version.mjs`.
- `android.software.leanback` uses-feature + `CATEGORY_LEANBACK_LAUNCHER` + `@drawable/tv_banner` present → the app appears in the Android TV launcher.
- `FileProvider` authority `dev.corebuilds.line.fileprovider` (exported=false, grantUriPermissions=true) backed by `file_paths.xml` exposing only `cache/updates/` → the sideload updater can hand the downloaded APK to the system installer.

**Clean install + first run on a real Android TV target (or emulator) remains [USER TO SUPPLY]** — say which device/emulator when reporting back; the sandbox has no display. The production signing path is the CI workflow `ticker/android/github-workflow-core-line-apk.yml` (land at `.github/workflows/core-line-apk.yml`): `npm test` then `assembleDebug` on push/PR, and a **signed release** on a `coreline-v*` tag with the keystore from GitHub Secrets.

## 7. Notes / corrections to earlier findings

- **Font (F4):** `fonts/outfit-var.woff2` is a genuine variable font — verified via fontTools: `fvar` table present, `wght` axis 100–900. The earlier "Latin 400 only" note was a false alarm; the `@font-face { font-weight: 100 900 }` declaration is correct and no change is needed.
- **Drawer P0 (A3):** fixed with a global `[hidden] { display: none }` rule appended to `app.css`; confirmed in jsdom and re-verified in real Chromium.

## 8. Supporter feedback round (2026-09-04)

A supporter ("Tetelestai3-16") ran the TV build and reported: (1) looks mobile-optimized, not TV; (2) favorite teams can't be added on TV; (3) custom RSS is overridden by built-in league tabs and games "outside the criteria" don't show; (4) an ESPN game that was live didn't exist in-app because both teams were unranked; (5) a listing was shown LIVE when it was a rerun; (6) wants sport-level grouping (football → NFL+college, basketball → college+WNBA+FIBA).

Fixes shipped this round (77/77 tests):

| Complaint | Root cause | Fix |
|---|---|---|
| LIVE badge on a rerun | `parseListing` flipped any title containing "live" to LIVE; no FINAL/replay handling | Status now requires a **leading** live indicator (optionally after a league word), word-bounded (`Liverpool`/`NBA Live` no longer trigger); added `FINAL`/`FT`/`full time` detection (word-bounded, not "Finals"/"Final Four"); `replay`/`rerun`/`on demand`/`encore`/`highlights` markers suppress LIVE. `lib/parser.mjs` |
| Unranked college game missing | NCAAF/NCAAB were not in `DEFAULT_LEAGUES`, so college scoreboards were never fetched (ESPN returns unranked games — verified live: `IDHO @ UTAH`, `UAPB @ MIZ`) | Added `ncaaf`, `ncaab` to `DEFAULT_LEAGUES` (client + server); `guessLeague` recognises college keywords; settings toggles now list **all** leagues (checked = enabled) so any league can be added later. |
| Custom feed overridden by tabs | items auto-bucketed by league heuristics; no way to view a feed whole | Each custom feed gets its own filter chip (`feed:<label>`, shown as 📡) — verified: a "Hockey Wire" feed chip filters to its 99 items; RSS `<category>`/JSON `category` now buckets items to a league via `leagueFromCategory`. |
| rss.app JSON feeds | parser ignored `content_html`/`date_published` | `parseJsonFeed` now reads `content_html`/`content_text` for channels/description and `date_published`/`published` for start time. |
| Looks mobile, not TV | 10-foot type too small | `[data-tv]` now sets root `font-size: 20px` (+~25% rem scale) and `--chyron-h: 96px`; verified in headless Chromium at 1920×1080 (`?tv=1`): root 20px, tick 41px, no overflow, 0 console errors. |

Open items deferred for a product decision: **sport-level super-tabs** (football/basketball), **favorite-teams on TV** (D-pad can't type into the text input today), and any further TV layout direction.

### 8.1 Second pass (same day, after owner decisions)

Owner approved: sport super-tabs, favorites via card-star + checklist, and a fuller 10-foot pass. All shipped and verified (77/77 tests, headless Chromium):

- **Sport super-tabs** — `SPORT_GROUPS` in `lib/scoreboard.mjs`; filter chips `Football` (= NFL+NCAAF), `Basketball` (= NBA+NCAAB+WNBA), `Baseball`, `Hockey`, `Soccer` (= EPL+MLS+UCL), `MMA`, `Racing`, shown before league chips when that sport has events. Verified: `Football 42`, `Basketball 63` chips appear; clicking Basketball filters cards to NBA/WNBA/NCAAB only.
- **Favorites on TV** — (a) every game card has a ★ button (focusable; OK on it toggles both teams, filled when favorited); (b) settings has a `teamPicker` checklist of teams from the **current tab** (capped at 100, D-pad operable) that toggles favorites without typing. Verified: card ★ → `favorites: "KC, BUF"`; picker chip → appended.
- **10-foot pass** — `[data-tv]` now: root `font-size: 20px`, grid `minmax(360px,1fr)` (fewer cards/row), 5px focus ring, bigger chips/badges/scores, header `.stats` counts hidden. Verified at 1920×1080 `?tv=1`: stats `display:none`, cards ~419px wide, focused outline `5px solid`, 0 console errors.

### 8.2 Third pass — D-pad, settings, watch apps (2026-09-04)

Owner asked: "make the d-pad selection better, research other Android TV apps,
make the settings menu very refined, allow any sport application to be assigned
to open a game." Researched official TV navigation guidance (always keep an item
focused; uniform focus indication; D-pad must reach every control; multi-pane
layouts for easy reachability) and the standard TV settings pattern (left rail +
content pane — Netflix/Leanback). Shipped and verified (88/88 tests, headless
Chromium 1920×1080 `?tv=1` + 390×844 mobile, 0 console errors):

- **D-pad nav rewritten** (`public/js/tv.js`): candidates must lie in the pressed
  direction; **cross-axis overlap preference** (the item directly above/below or
  in-row wins over a diagonal neighbour); focused item auto-scrolls into view
  (`scrollIntoView` + CSS `scroll-margin`/`scroll-padding`). Verified: `ArrowDown`
  moves rail→rail, `ArrowRight` enters the active section's content, focus never
  drops to `<body>`.
- **Refined settings menu** (`public/index.html`, `public/css/app.css`): the
  drawer is now a **left rail (Feeds / Scoreboards / Ticker & display / Watch
  apps / Help) + right content pane** on TV (grid `208px 1fr`); focusing a rail
  item switches its section (Netflix-style). On phone the rail is horizontal
  pills. Verified: rail focus switches sections; 12 league toggles reachable.
- **Watch apps** (new `lib/watch.mjs`, `public/js/state.js`, `public/js/app.js`,
  `LineBridge.kt`, `MainActivity.kt`, `AndroidManifest.xml`): assign any installed
  app per league in Settings → Watch apps; `▶ Watch` on the hero card / `▶` on
  each game card / the `W` key opens the assigned app (or the ESPN game page in
  the browser). Android 11+ package visibility handled with `<queries>` for
  `MAIN`/`LAUNCHER` + `LEANBACK_LAUNCHER` (no `QUERY_ALL_PACKAGES`); launch uses
  `getLeanbackLaunchIntentForPackage` → `getLaunchIntentForPackage` fallback.
  Verified: APK manifest contains the `<queries>` block; `lib/watch.mjs` covered
  by `tests/watch.test.mjs` (URL building, assignment, picker sort).
  [UNVERIFIED on-device] the curated package-id list (`SPORTS_APPS`) drifts across
  storefronts — the full installed-app list is what the picker actually uses.

Rebuilt both APKs with the changes (now `releases/CoreLine-debug-v1.2.0.apk`,
3.08 MB, and `CoreLine-release-unsigned-v1.2.0.apk`, 2.26 MB).

### 8.3 Fourth pass — in-app updates + full UI polish (2026-09-04)

Owner asked: (1) check the current GitHub version; (2) polish the UI — "leave
nothing untouched, make the UI as clean as possible"; (3) add in-app updates
(the app is sideloaded; no Play Store).

**GitHub state checked:** `main` ticker is versionCode 4 / versionName 1.1.0
(`coreline-v1.1.0` release, asset `coreline-release.apk`); local is now ahead at
**versionCode 5 / versionName 1.2.0**. CI: `.github/workflows/core-line-apk.yml`
builds on ticker pushes/PRs and releases on a `coreline-v*` tag equal to the
Gradle `versionName` and an ancestor of `main`.

Shipped and verified (88/88 tests, headless Chromium smoke, full Android build):

- **In-app update logic** (`lib/version.mjs` + `tests/version.test.mjs`): pure
  `compareVersions`, `versionFromCorelineTag`, `latestCorelineRelease`,
  `updateStatus`. Verified against the real GitHub releases JSON — newest
  `coreline-v*` release with a `coreline-release.apk` asset is picked.
- **Updates settings pane** (`public/index.html`, `public/js/app.js`,
  `public/css/app.css`): Settings → Updates shows the installed version (via
  `LineBridge.getVersion()` → `BuildConfig.VERSION_NAME`), a “Check for updates”
  action (GitHub releases API through the native `/api/proxy` so it passes
  `SafeUrl`), an update banner with flattened release notes, and
  “Download & install” on native. Verified headless: web build reports “Update
  1.1.0 is available”, TV hint shown, 0 console errors.
- **Android updater** (`UpdateManager.kt`, `MainActivity.kt`, `LineBridge.kt`,
  `AndroidManifest.xml`, `file_paths.xml`, `app/build.gradle.kts`): download the
  release APK over a host-allowlisted URL (github.com / *.github.com /
  release-assets.githubusercontent.com / objects.githubusercontent.com, redirects
  followed) → `cache/updates/` (80 MB cap) → `FileProvider` `ACTION_VIEW` to the
  system installer. `REQUEST_INSTALL_PACKAGES` declared; `androidx.core:core-ktx`
  added. Verified via aapt2: versionCode 5, permission present, FileProvider
  present and scoped to `cache/updates/`. [UNVERIFIED on-device] the actual
  unknown-sources prompt — no emulator/device in the sandbox.
- **UI polish pass** (`public/css/app.css`): tactile press states, focus rings,
  input accent colour, broadcast-style uppercase section labels with accent bar,
  chyron edge fades into the bug/clock, tabular numerals, thin scrollbars, card
  and badge shadows, reduced-motion support. Verified: headless smoke at
  1440×900 / 390×844 / 1920×1080 `?tv=1` all render with 0 console errors.


### 8.4 Fifth pass — phone floating overlay (2026-09-04)

Owner asked to "allow the ticker to display as a widget or over the screen."
Platform reality (researched live, cited):

- **TV home-screen widget** — not supported. Launchers decide widget support and
  Google's TV launcher (and Fire TV) deliberately omit it; only niche
  third-party launchers add it (StackOverflow 76549233).
- **TV ticker over other apps** — not supported. Android TV has no
  SYSTEM_ALERT_WINDOW for a sideloaded app, and PIP is reserved for video. The
  supported idle path is a Daydream **screen saver**, which on many Google TV /
  Fire OS devices can only be set via a one-time ADB
  `settings put secure screensaver_components …` (AerialViews README).
- **Phone home widget** — possible but RemoteViews only (no animated crawl).
- **Phone floating overlay** — feasible: translucent always-on-top window
  hosting the existing WebView renderer. **Built this.**

Shipped and verified (88/88 tests, headless overlay smoke, full Android build):

- **OverlayService.kt** — foreground service (`foregroundServiceType=specialUse`
  + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`), translucent `TYPE_APPLICATION_OVERLAY`
  (pre-26: `TYPE_PHONE`) window, `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL |
  FLAG_NOT_TOUCHABLE`, full-width ~56dp strip, transparent WebView loading the
  same app with `?native=1&overlay=1`. Ongoing notification with a Stop action.
- **Reuse, not rewrite** — the overlay is the same `index.html`; it shares the
  parser, the `/api/proxy` data path, and (same origin) the app's localStorage,
  so speed/theme/position/feeds all carry over. `[data-overlay]` CSS strips the
  topbar/leagues/stage/drawer and renders only the chyron.
- **Control** — Settings → Ticker & display gains a "Floating ticker over other
  apps (phone)" checkbox (native + non-TV only). Toggling on calls
  `LineBridge.startOverlay()`, which opens the system "display over other apps"
  screen if permission is missing, and refuses on TV (`isTelevision()`).
- **Verified**: aapt2 shows `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS` and the `OverlayService`
  with `foregroundServiceType=0x40000000 (specialUse)`. Headless Chromium
  `?native=1&overlay=1` at 390×64: `data-overlay` set, topbar/stage/drawer hidden,
  chyron visible, body background transparent, ticks render, no JS errors.
  [UNVERIFIED on-device] the actual overlay window over another app and the
  unknown-sources/overlay permission prompts — no device/emulator in the sandbox.

### 8.5 Sixth pass — Android TV UI consistency (2026-09-04)

Owner: "Fix the UI … it is too inconsistent on Android TV, blown out."

Measured the TV (1920×1080 `?tv=1`) before fixing: text sizes were scattered
from **13.2px** (health pill, `.when`) up to **68px** (hero `.score`), the
checkboxes were **16px** (smaller than their own labels), and the square
controls came in four sizes (44/46/52/56px). The root cause: the 10-foot pass
had a 20px root (1.25× phone) plus a pile of ad-hoc `[data-tv]` rem overrides
that each assumed a different multiplier (1.47×–1.77×).

Fix (in `public/css/app.css`, `[data-tv]` block rewritten): ONE coherent scale
ladder on the 20px root —

- micro **.8rem (16px)**: health, brand-sub, kicker, league-tag, `.when`
- body **.95rem (19px)**: hint/fineprint, `.ch`, update-notes
- control **1.1rem (22px)**: rail-item, ghost, team-chip, check labels, inputs
- title **1.3rem (26px)**: block h3, watch-league, chip
- display **2rem (40px)**: brand-name, drawer h2, `.gt .who`/`.sc`, tick, clock

plus: every square control 56px (48px in-card); hero numerals tamed to
abbr 48px / score 56px (with an `.abbr` ellipsis guard so a long abbreviation
can't overflow its 88px column); checkboxes 26px with `flex: 0 0 auto`;
focus ring 6px.

Verified headless (pinned in `tests/smoke-updates.mjs`): health ≥16px,
`.when` ≥16px, chyron tick 40px, card team names == brand size (40px == 40px),
abbr ≤ score, no abbreviation overflow; 88/88 unit tests; desktop/mobile/TV/
overlay smoke all 0 console errors. Phone layout untouched (all changes under
`[data-tv]`).
