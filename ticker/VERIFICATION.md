# Core Line — Verification (Phase 4)

Verification performed 2026-09-03 against the polished tree (post-AUDIT fixes).
Environment: sandboxed Linux (2 vCPU, ~1.9 GB RAM, Node 20.20.2) plus headless
Chromium (Playwright) for browser smoke tests, and JDK 21 + Android SDK 34 for the APK build.

## 1. Automated tests

```
cd ticker && npm test
```

**77 tests, 77 passing** (up from the 24 baseline tests). New coverage:

| File | Covers (req) |
|---|---|
| `tests/backoff.test.mjs` | exponential backoff + jitter bounds, cap, success reset, `canTry`/`waitMs`, `Retry-After` parsing, JSON round-trip (req 4) |
| `tests/source-registry.test.mjs` | per-source last-good, degraded flag, health counts, item cap, hydrate/dehydrate (req 4, 5) |
| `tests/parser-robustness.test.mjs` | malformed/empty/truncated XML, garbage bytes, null/non-object JSON rows, CDATA extraction, numeric entities, adversarial titles, 100-item cap (req 5) |
| `tests/client-slate-resilience.test.mjs` | one dead feed never blanks the slate or blocks a healthy feed; failing feed keeps last-good items (`stale`); repeated failures are skipped via backoff; demo only when there is nothing at all (req 4, 5) |
| `tests/ticker.test.mjs` | constant px/s loop math, wrap at seq width, short-content padding, dt clamping, stop/restart keeps position (req 8) |
| `tests/watchdog.test.mjs` | stall → `onStall` after two unchanged samples, no false stalls while moving, wake on visibility, background pages ignored (req 6) |
| `tests/feedback.test.mjs` | pins the 2026-09-04 supporter complaints: live/final/replay status rules, college leagues in defaults, `leagueFromCategory`, RSS/JSON category bucketing, rss.app JSON shape |

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

- [ ] Sideload `releases/CoreLine-debug-v1.0.2.apk` on a Shield / Google TV / Fire TV (Fire OS 7+).
- [ ] First launch (no network): crawl is populated (demo slate + bundled sample) — **never a blank screen**.
- [ ] Airplane mode 2 min → health pill turns amber ("retrying") and the ribbon keeps moving with last-good content; re-enable network → pill returns green within one refresh interval.
- [ ] Inject a malformed feed (a URL that returns HTML or garbage) via Settings → Add; the other feeds and scoreboards keep scrolling.
- [ ] Add an empty feed; ribbon still shows the scoreboards.
- [ ] D-pad: open Settings (⚙), move through every control, OK on checkboxes and the −/+ speed steppers; reorder a league and a feed with ▲/▼; change refresh interval and ticker position (top/bottom); confirm a visible focus ring on every control and no dead ends.
- [ ] Press Back while the drawer is open (closes the drawer, not the app), then again (backgrounds the app).
- [ ] Multi-hour soak (≥ 6 h): note memory in `adb shell dumpsys meminfo dev.corebuilds.line` at T+0 and T+6 h; the ribbon must still be moving (watchdog restarts it if it ever stalls).
- [ ] Screen off → on (and daydream if enabled): the clock is correct on resume and the ribbon is moving.
- [ ] 10-foot check: ticker and card text legible from ~3 m; no content under overscan on a classic TV (adjust `--overscan` if needed).

## 6. Release build (req 15)

Both variants built in-sandbox with JDK 21 + Android SDK 34 (AGP 8.5.2 / Kotlin 1.9.24 / Gradle 8.7, memory capped at 896 MB heap + 2 GB swap):

- **`releases/CoreLine-debug-v1.0.2.apk`** (1.82 MB, debug-signed, installable) — the sideload artifact.
- **`releases/CoreLine-release-unsigned-v1.0.2.apk`** (1.29 MB) — release variant compiles; signing is skipped because `KEYSTORE_PATH` is unset (by design: `app/build.gradle.kts` only signs release when a keystore is provided).

APK manifest verification (aapt2):
```
package: dev.corebuilds.line  versionCode 3  versionName 1.0.2
sdkVersion 24   targetSdkVersion 34
permissions: INTERNET, ACCESS_NETWORK_STATE, WAKE_LOCK
label: "Core Line"
```

APK contents verified:
- All 26 web assets bundled (`index.html`, `css/app.css`, `js/*.js`, `lib/*.mjs`, `fonts/*.woff2`, bundled sample feed) — including the `[hidden]` CSS fix, `watchdog.js`, `ticker.js`, `backoff.mjs`, `source-registry.mjs`.
- `android.software.leanback` uses-feature + `CATEGORY_LEANBACK_LAUNCHER` + `@drawable/tv_banner` present → the app appears in the Android TV launcher.

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
