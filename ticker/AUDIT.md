# Core Line — Baseline Audit (Phase 0)

- **Repo:** `brevityA/CoreBuildsApps`, path `ticker/` (self-contained; Node 20+, zero npm deps).
- **Audited commit:** `525acf1` (branch `main`), date of audit 2026-09-03 (user tz Australia/Sydney).
- **Method:** read every file in `ticker/` (JS `lib/`, `public/`, `server.mjs`, `tests/`, and the `android/` Kotlin shell); ran `npm test` (24/24 green); probed the live data sources (ESPN `site.api.espn.com`, NHL `api-web.nhle.com`, MLB `statsapi.mlb.com`) to confirm the parsers match reality.
- **Baseline behavior is preserved.** Every change made in later phases maps to a finding here (or a numbered requirement). Nothing that already works is rewritten gratuitously.

---

## 1. Inventory

### 1.1 Tech stack / SDK setup
| Layer | Stack | Notes |
|---|---|---|
| Web UI | Vanilla HTML/CSS/JS, **ES modules**, no framework, no build step | `public/index.html` loads `./js/app.js` as `type="module"` (`index.html:150`) |
| Shared parsers | `lib/*.mjs` (channels, teams, parser, scoreboard, client-slate, rss, ssrf) | imported by both the Node server and the browser/WebView |
| Web server | `server.mjs`, `node:http`, in-memory 45 s cache | `PORT=8787` default; serves `public/` + `lib/` + `/api/*` |
| Android shell | Kotlin, **no** leanback/AndroidX library — a fullscreen `WebView` + asset server + on-device proxy + LAN pair server | `MainActivity.kt`, `LineWebClient.kt`, `LineBridge.kt`, `PairServer.kt`, `SafeUrl.kt` |
| Android build | AGP 8.5.2, Kotlin 1.9.24, `compileSdk 34`, `minSdk 24`, `targetSdk 34`, Gradle 8.7 | `android/app/build.gradle.kts`; `syncWebAssets` copies `public/`+`lib/` into APK assets (`build.gradle.kts:57-69`) |
| CI | `android/github-workflow-core-line-apk.yml` (owner must land as `.github/workflows/core-line-apk.yml`) | runs `npm test` + `assembleDebug`; tags `coreline-v*` → signed release |

The product is a **web UI wrapped in a WebView**. "Android TV" support = `LEANBACK_LAUNCHER` intent filter + `android.software.leanback` `required=false` (one APK for phone + TV) — correct per the official TV docs ([1](https://minimum-viable-product.github.io/marshmallow-docs/training/tv/start/start.html), [2](https://reintech.io/blog/kotlin-android-tv-developing-apps-for-big-screen)). D-pad handling is custom JS (`public/js/tv.js`), not leanback-library widgets.

### 1.2 Data sources & how scores/RSS are fetched and parsed
| Source | Fetch path | Parse | Poll |
|---|---|---|---|
| ESPN scoreboards (NFL/NBA/MLB/NHL/NCAAF/NCAAB/WNBA/EPL/MLS/UCL/UFC/F1) | `https://site.api.espn.com/apis/site/v2/sports/{sport}/{league}/scoreboard` (`lib/scoreboard.mjs:24-28`) | `eventsFromEspn` (`scoreboard.mjs:30`) | every 60 s client-side; 45 s cache server-side |
| NHL fallback | `https://api-web.nhle.com/v1/score/now` | `eventsFromNhl` (`scoreboard.mjs:56`) | only when ESPN NHL fails |
| MLB fallback | `https://statsapi.mlb.com/api/v1/schedule?sportId=1&date=…` | `eventsFromMlb` (`scoreboard.mjs:104`) | only when ESPN MLB fails |
| User RSS/Atom/JSON feeds | Web: server `fetchFeed` (`lib/rss.mjs:7`, Node `fetch`). Native: Kotlin proxy `LineWebClient.proxy` → `HttpURLConnection` (`LineWebClient.kt:44-92`) → same `parseFeed` | regex-based `parseFeed` (`lib/parser.mjs:98`); JSON via `parseJsonFeed` (`parser.mjs:129`) | every 60 s |
| Bundled sample | `public/feeds/sample-sports.xml` (served from assets on-device, so it works offline) | `parseFeed` | every 60 s |

Normalized event shape (documented in `HANDOVER.md`, implemented across `lib/parser.mjs` + `lib/scoreboard.mjs`): `{id, source, league, status(live|upcoming|final), start, detail, away{name,abbr,score,logo,winner}, home{…}, channels[], headline, rawTitle, feed, venue}`.

**Live-source check today:** ESPN NHL returned 7 scheduled events with the exact `status.type.state`/`competitions[].competitors[]` shape the parser expects; MLB returned `dates[].games[].broadcasts[]` with `type: "TV"` filtering working; NHL `score/now` answered with a 307 redirect (both fetch layers follow redirects — Node `fetch` by default, Kotlin manually up to 5 hops) and then `games[].tvBroadcasts[].network`. Parsers match reality.

**ESPN is not a documented public API** (it is the endpoint ESPN's own site uses). Treat as [UNVERIFIED for ToS]; there is no rate-limit contract, so the app must self-throttle (finding B1). NHL (`api-web.nhle.com`) and MLB (`statsapi.mlb.com`) publish public, documented stats APIs.

### 1.3 Ticker render/animation loop
- `app.js:renderCrawl()` (`app.js:202-217`) builds the item spans, writes them to **two identical copies** `#crawlA`/`#crawlB`, and a CSS animation `crawl` translates `#crawl` from `0` to `-50%` (`app.css:173`). Loop = two copies for a seamless seam.
- Clock: `tickClock()` every 1 s (`app.js:32`).
- Slate refresh: `refresh()` immediately + `setInterval(60_000)` (`app.js:40-41`); `refreshGen` guard prevents stale slates from applying (`app.js:4,13,22`).

### 1.4 State management
- `localStorage` key `coreline.v1` (`public/js/state.js:1`); settings: `leagues[]`, `feeds[{url,label}]`, `sampleFeed`, `speed`, `favorites`, `showFinals`, `wakeLock`, `theme`, `clockFmt`, `mode`, `leagueFilter`.
- Last slate cached at `coreline.v1.slate` (`state.js:31-44`).

### 1.5 Lifecycle / foreground handling
- Kotlin: `onResume/onPause` → `webView.onResume/onPause` (`MainActivity.kt:91-100`); `onBackPressed` closes drawer else `moveTaskToBack` (`MainActivity.kt:74-89`); `onDestroy` stops pair server, removes bridge, destroys WebView (`MainActivity.kt:102-107`).
- Keep-awake: `FLAG_KEEP_SCREEN_ON` set in `onCreate` and toggled by the JS bridge (`MainActivity.kt:34,67-72`); JS Wake Lock API attempted too (`app.js:231-241`).
- **No** daydream/screensaver integration; no JS visibility/`pageshow` handling; **no watchdog**.

### 1.6 Settings / configuration surface
`index.html:62-132` drawer: add feed URL/label, pair-from-phone (native only), remove feeds, league toggles, ticker **speed** slider, favorites text, show-finals, keep-awake, theme select, clock-format select.
- **Missing** (required by Req 12): refresh-interval control, ticker **position** (top/bottom), **reordering** leagues/feeds.
- Feed entry: `addFeed()` (`app.js:155-175`) validates only `new URL()` + http/https (no SSRF check at add time — blocked later at proxy/server).

---

## 2. Findings (severity-ranked)

Legend — **P0** blank/crash · **P1** degraded/self-healing gap · **P2** polish. "Req" = the numbered requirement this maps to.

### P0

| ID | File:line | Finding | Reproduction | Minimal fix | Req |
|---|---|---|---|---|---|
| A1 | `public/index.html:150`; `lib/*.mjs` | UI is ES-module-only while `minSdk = 24` (Android 7 / Fire OS 6). WebViews on Android 7.x can predate Chrome 61, which is when ES modules landed in WebView ([3](https://stackoverflow.com/questions/43890272/which-versions-of-ios-and-android-webviews-already-support-es6-modules)). On such devices the module script is silently ignored → **permanently blank screen**. | Sideload on Fire OS 6 / Android 7.0–7.1 with stock WebView; app shows only the window background. | (a) Add an inline classic-script boot guard in `index.html` that, if `window.__CORELINE_READY` is still unset after N s, paints a styled "update your System WebView / Fire OS 7+" message instead of blank; (b) document Fire OS 7+ / WebView ≥ 61 as the support floor. | 1, 4, 6 |
| A2 | `app.js:26-45` (`init`) | `init()` reads `$('sampleFeed').checked` etc. before any guard; if `localStorage` is present but corrupted (`loadState` catches) it is fine — **no crash found**. Marking as resolved-by-audit: first run falls to `buildDemoSlate()` + sample feed, never blank (`app.js:33-40`; `client-slate.mjs:64-69`). | — | No change needed (baseline is correct). | 7 |
| A3 | `index.html:82` (`<aside class="drawer" hidden>`) + `app.css` `.drawer{display:flex}` | The settings drawer's `hidden` attribute is overridden by the author `.drawer { display:flex }` rule (author origin beats the UA `[hidden]{display:none}`), so the full-screen dimmed drawer overlay was **visible from first paint** — covering the board with `rgba(0,0,0,.62)`. `openDrawer(false)` toggled `hidden` with no visual effect. Confirmed in jsdom (computed `display:flex` while `hidden`) and fixed; real-browser behavior re-verified with headless Chromium. | First launch shows the dimmed settings overlay over the board. | Add an author `[hidden] { display: none }` rule at the end of `app.css`. Crawl-mode `.bigclock` still shows because `[data-mode="crawl"] .bigclock` (0,2,0) outranks `[hidden]` (0,1,0). | 1, 7 |

### P1

| ID | File:line | Finding | Reproduction | Minimal fix | Req |
|---|---|---|---|---|---|
| B1 | `app.js:41` (`setInterval(refresh,60_000)`); `client-slate.mjs:22-79`; `server.mjs:77,91` | **No backoff/jitter** on failure. Every 60 s cycle re-fetches *all* sources at full rate; a dead feed is hit 1440×/day and a rate-limited ESPN endpoint is re-hit immediately. Server cache is a flat 45 s (`server.mjs:35`), not failure-aware. | Add a feed that 404s; watch logs — it is requested every 60 s forever. | Per-source scheduler: on failure double the wait (cap ~10 min) + jitter; on success reset; honor `Retry-After`. Healthy sources keep their own cadence. | 4 |
| B2 | `client-slate.mjs:54-78`; `app.js:13-15` | **No per-source last-good retention.** Whole-slate cache exists (`state.js:31`), but when one feed fails, its items are dropped from the next successful slate (only `[]` returned for the dead source) → content silently disappears/flickers. | Two feeds, one dies; the dead feed's items vanish next refresh. | Keep per-source last-good events (memory + `localStorage`), merge them when a source fails, mark the source "degraded". | 4, 5 |
| B3 | `app.js:2-3` (`refresh`) | **No client-side timeout/latch on refresh.** Native proxy has 10 s timeouts and server has 10 s timeouts, so bounded today, but nothing prevents a slow/hung cycle from overlapping the next 60 s tick (refresh work piles up). | Feed endpoint that streams bytes slowly under the 1.5 MB cap. | Single-flight latch + per-cycle timeout (e.g. 20 s); skip a cycle if the previous is still running. | 6 |
| C1 | `app.js:31-41`; `app.css:173` | **No watchdog.** If the WebView's compositor/animations stall after sleep/resume or a long soak, nothing detects the frozen ribbon and restarts the loop. | `adb shell` press power → resume; on some WebViews the CSS animation never resumes. | Watchdog samples the ribbon's `translateX` twice; if unchanged while visible/expected-to-move, re-render + restart the loop; also hook `visibilitychange`/`pageshow`/`resume`. | 6 |
| C5 | `app.js:234-240` (`renderFeeds`), `renderCrawl` | **Unbounded feed/item counts.** Client has no cap on `state.feeds` or items per feed; a huge feed (or many feeds) builds a very large `crawlA`/`crawlB` innerHTML each refresh and can starve a TV WebView. | Add 50 feeds → 2× multi-MB innerHTML rebuild every 60 s. | Cap feeds (≈20) and items per feed (≈100) at parse/merge time. | 6 |
| D1 | `app.js:143-147` (`openDrawer`); `tv.js:44-80` | **Drawer focus trap.** On TV, opening settings does not move focus (feedUrl is only focused when *not* TV), and background `.focusable`s remain "visible" to `nearest()` (the drawer is an overlay; background elements keep nonzero rects) → focus can land on hidden-behind-the-overlay controls. | On TV: press ⚙ → press down repeatedly → focus reaches league chips *under* the drawer. | Move focus to the close button on open; mark the stage inert (or exclude it in `nearest()`) while open; restore focus on close. | 11, 12 |
| D5 | `app.js:105-117` (`renderFilters`), `160-167` (`renderGrid`) | **Focus loss on re-render.** `innerHTML` rebuild of `#leagues`/`#grid` removes the focused element every refresh (60 s) and on every filter tap; focus falls to `<body>` and D-pad navigation resets to the first focusable. | On TV: focus a grid card, wait for refresh → focus ring disappears. | Remember the focused element's identity before rebuild; restore it (or nearest equivalent) after. | 11 |
| D7 | `tv.js:82-91`; `index.html:104` | **Speed slider is not D-pad operable.** `isEditing()` exempts `input[type=range]` (not in the exclude list) and the keydown handler routes arrows to `nearest()`, so ArrowLeft/Right *move focus* instead of adjusting the slider; OK does nothing on a range. Checkboxes work (OK → `.click()`), selects work natively. | On TV: open settings → focus "Ticker speed" → press OK/arrows → nothing changes. | Treat `input[type=range]` as editing (let native arrows adjust it) and/or add visible − / + stepper buttons. | 12 |
| E1 | `app.js:41` | **Refresh interval hardcoded**, not user-settable. | — | Add `refreshSec` setting (select: 15/30/60/120/300/600 s) and re-arm the interval on change. | 12 |
| E2 | `index.html:72-100` | **No reorder** for leagues or feeds. | — | Up/down buttons per league chip and feed row (D-pad operable). | 12 |
| E3 | `app.css:69-74,134-147` | **No ticker position option.** Chyron is hard-fixed as the bottom grid row. | — | `position: bottom|top` setting; swap the chyron grid row. | 12 |
| F2 | `scoreboard.mjs:9-22`; `app.js:170-198` | League `accent` colors are defined but never used in cards → weak sport/state hierarchy at 10 ft. | Look at MLB vs NFL cards: identical styling. | Apply league accent as a card tag/underline color; keep LIVE/FINAL state colors distinct. | 8, 9 |

### P2

| ID | File:line | Finding | Minimal fix | Req |
|---|---|---|---|---|
| B4 | `state.js:4` vs `server.mjs:57,82` | Default league list duplicated (drift risk). | Single exported `DEFAULT_LEAGUES` from `lib/scoreboard.mjs`. | 8 |
| B5 | `LineWebClient.kt:78-84` | Kotlin proxy stops at `MAX_BYTES` without signalling truncation (returns partial body) → truncated XML parsed silently to fewer items. | Return a 413-style error when `total == MAX_BYTES`. | 5 |
| B7 | `app.js:155-175` | `addFeed` doesn't SSRF-validate; invalid/private URLs are stored and retried forever (blocked only at fetch). | Validate with `isSafeFeedUrl` on add; skip+flag invalid at fetch. | 5 |
| C2 | `MainActivity.kt:74-89` | `onBackPressed` is deprecated (API 33+) and uses string-matching on JS return values. | Use `OnBackInvokedDispatcher` when available; keep legacy path. | 6 |
| C3 | `MainActivity.kt:34,67-72` | Keep-awake set unconditionally in `onCreate` then corrected by JS; no daydream integration. | Set only when needed; document daydream behavior. | 6 |
| D3 | `app.css:63-67` | Checkbox/select/range focus ring is only on the control itself (small target). | `:focus-within` ring on `.check` labels; bigger touch/DPAD target. | 11 |
| D4 | `tv.js:43-68` | `nearest()` is geometric with a 2.4 alignment weight; can pick a "sideways" target in dense grids. | Minor tuning; keep geometric search but bias primary axis more. | 11 |
| F1 | `app.css:173`, `state.js:7`, `app.js:78-81` | Ticker speed is "seconds per half-ribbon" so px/s varies with content length, and the slider reads inverted (higher = slower). | Constant px/s engine; relabel slider. | 8 |
| F3 | `app.css:14-16,100,186,192` | Ticker type (1.55 rem) and 72 px bar are small at 10 ft; `env(safe-area-inset-*)` doesn't cover classic-TV **overscan**. | `[data-tv]` scale-up + `--overscan` margin (user-tunable). | 8, 10 |
| F4 | `index.html:15` | Fonts come from Google Fonts CDN → FOUT/layout shift and offline fallback drift. Barlow Condensed + Outfit are OFL. | Bundle woff2 + `@font-face`; drop the CDN. | 8, 10 |
| F5 | `app.css:96-105` | `.tick::after` adds a trailing ◆ to every item (seam shows a dangling separator) and margins are static. | Consistent separator width; no dangling separator at seam. | 9 |
| F6 | `app.css:8-10` | `--faint #4b5563` and `--final #8b949e` are low-contrast at distance. | Bump to ≥ 4.5:1. | 8, 10 |
| F7 | `app.css:103-106,186-188` | `.game:hover/.is-focused` transform never fires on TV (no pointer; `is-focused` never set). | Focus styling via `:focus` for `[data-tv]`. | 11 |
| A4 | `app.js:215-216`; `app.css:173` | Mid-animation `innerHTML` swap of `crawlA`/`crawlB` can seam-jump when item widths change. | Covered by the constant-speed loop (Phase 1/2). | 8 |

### Baseline that already works (preserve — do not rewrite)
- Parser breadth & correctness: supporter line `Team vs team epn, tsn4, sn 3` → `ESPN, TSN4, SN 3`; channel alias table; entity decoding without double-unescape; prose-not-channels; 24 green tests.
- SSRF guard (http/https only, RFC1918/link-local/metadata blocked, IPv6 unique-local only when host contains `:`), applied at both Node server and Kotlin proxy.
- Demo-slate fallback so the crawl is never empty; bundled sample feed; offline `localFallback()`.
- `refreshGen` stale-response guard; whole-slate `localStorage` cache; per-feed isolation in `Promise.all` (try/catch per source).
- Native shell: asset server at `https://coreline.local`, on-device proxy, LAN pair server (code-gated, no proxy on that port), QR encoder now local (`public/js/qr.js`), manifest has both `LAUNCHER` + `LEANBACK_LAUNCHER`.
- Size/timeout caps on feeds (1.5 MB / 10 s), redirect caps (5 hops), cache cap (200 keys) on the server.

---

## 3. Fix → requirement mapping (what Phase 1–3 will implement)

| Finding | Change | Location |
|---|---|---|
| A1 | Boot guard vs old WebView + support-floor docs | `public/index.html` |
| B1 | `Backoff` (exp + jitter, cap, Retry-After) + per-source scheduler | new `lib/backoff.mjs`; `client-slate.mjs`; `server.mjs` |
| B2 | Per-source last-good merge (memory + `localStorage`) | `client-slate.mjs`; `state.js`; `app.js` |
| B3 | Single-flight latch + cycle timeout | `app.js` |
| C1 | Watchdog (ribbon-stall detection) + resume hooks | new `public/js/watchdog.js`; `app.js` |
| C5 | Feed/item caps | `client-slate.mjs`/`parser` merge |
| D1/D5/D7 | Drawer focus trap + focus restore on re-render + range D-pad | `tv.js`, `app.js`, `index.html`, `app.css` |
| E1/E2/E3 | Settings: refresh interval, position, reorder | `state.js`, `app.js`, `index.html`, `app.css` |
| F1 | Constant px/s ribbon (JS-driven `translate3d`) | `app.js`, `app.css` |
| F2/F3/F4/F5/F6/F7 | League accents, TV type scale, overscan, bundled fonts, separators, contrast | `app.css`, `index.html`, assets |
| B4/B5/B7 | Defaults single-source; proxy truncation flag; add-feed SSRF | `lib/scoreboard.mjs`, `LineWebClient.kt`, `app.js` |

---

## 4. Knowledge-cutoff / verification notes

- **Knowledge cutoff:** my training data ends before 2026; the above Android TV/leanback and WebView facts were verified against the sources cited (official TV docs [1][2], Chromium WebView ES-module history [3]). ESPN/NHL/MLB endpoints were **verified live today** (see §1.2).
- **[UNVERIFIED]** ESPN `site.api.espn.com` is an undocumented endpoint (no public ToS/rate-limit contract). The app self-throttles regardless. If it ever changes shape, `eventsFromEspn` returns `[]` and the app degrades to demo/fallback — by design.
- **[UNVERIFIED]** Exact D-pad/range-input behavior on Fire OS WebView variants — flagged for the manual test checklist (Phase 4).
- **[USER TO SUPPLY]** No API keys are used by any source today (all three are keyless public endpoints). If a keyed source is added later, it must be marked `[USER TO SUPPLY]` and degrade gracefully.
- **Credentials:** none are stored or logged anywhere (verified — no keys in `lib/`, `server.mjs`, Kotlin, or docs).

---

## 5. Phase 4 verification status (2026-09-03)

See `VERIFICATION.md` for full detail. Summary:

- **Tests:** 88/88 pass (`npm test`) — 24 baseline + backoff, source-registry, parser-robustness, client-slate-resilience, ticker, watchdog, feedback, watch, and version suites.
- **Failure injection:** dead-feed isolation, last-good retention, backoff skip, malformed/empty/garbage bodies, real-socket 502 route — all verified without a single blank slate.
- **Soak:** 3,000-cycle (~50 h) in-process soak — 0 blank, 7.11 MB heap growth; 70-cycle real-socket server soak — 70/70 responses, 0 kB VmRSS growth.
- **Browser smoke (headless Chromium):** drawer hidden on first paint (A3 fixed), ribbon moving, fonts loaded, 0 console errors.
- **Release build:** `releases/CoreLine-debug-v1.2.0.apk` and `CoreLine-release-unsigned-v1.2.0.apk` built with JDK 21 + SDK 34; manifest/leanback/FileProvider/asset contents verified (versionCode 5). Clean-install on a device/emulator remains [USER TO SUPPLY].
- **Correction:** `fonts/outfit-var.woff2` is a real variable font (fontTools: `fvar`, wght 100–900) — F4 was a false alarm.
