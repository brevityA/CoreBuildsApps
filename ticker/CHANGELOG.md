# Changelog

All notable changes to **Core Line**. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[SemVer](https://semver.org/spec/v2.0.0.html).

The repo-root `CHANGELOG.md` is the icon pack's. Core Line keeps its own here.

## [Unreleased]

## [1.4.4] — 2026-10-01

### Changed

- **The crawl no longer ties up the TV.** The scrolling strip, on the board
  and floating over other apps, now moves as one animation the graphics side
  runs by itself, instead of being pushed along a pixel at a time sixty times
  a second. With the crawl on, the app's main thread went from busy about half
  the time to the same as with it off (2.46 s → 0.24 s per 5 s, measured at a
  6× CPU slowdown), which leaves it free for the remote.
- **A refresh only redraws what changed.** Every refresh used to throw away
  and rebuild all the cards on the board, logos included; now a card is only
  rebuilt when its score, clock or channel changed, so the card you are on
  keeps its focus. A refresh with nothing new went from 1.0–1.9 s of work to
  0.4–0.6 s at the same slowdown, and layout from 250–450 ms to 32–45 ms.

## [1.4.3] — 2026-10-01

### Fixed

- **The floating ticker turns on again (Android).** Ticking Settings →
  Overlays → Floating ticker did nothing: the app read the ticker's edge from
  the page on the wrong thread, Android refused the call, and the overlay
  service was never started. The edge is now kept natively and the ticker
  starts at once. The switch also no longer flips back off with a "Display
  over other apps" message when the permission is already granted.

## [1.4.2] — 2026-09-30

### Added

- **A scoreboard at the top of the screen, over other apps (Android).**
  Settings → Overlays → Scoreboard at the top. A broadcast-style score box
  shows one game at a time over whatever you are watching: the league, the
  clock, both teams with their marks, the score and the channel. It cycles
  through the live games every 10 seconds, with your starred teams first. When
  nothing is live it shows the next game within 12 hours ("Next · 7:00 PM").
  When nothing is on or coming up, it disappears rather than sit empty. It
  goes top centre, top left or top right, with its own brightness setting.
  **On by default:** it comes up with Core Line whenever "Display over other
  apps" is already allowed. The app never opens that permission screen on its
  own; ticking the switch is how you grant it. Unticking the switch turns it
  off for good, and Stop in the notification hides it until the next launch.
  It is built the way the VPN dot is: its own small window, which never takes
  focus or touches, so the remote works exactly as if it were not there. It
  runs on its own switch, independent of the crawl and the dot, and the
  notification's Stop takes it down with them. It uses the crawl's slate
  (`overlay-slate.js`, now shared), so the two cannot disagree about a score.
  Not on Fire TV, which blocks overlay windows.
- **Your TV guide as a second listings source (Android app).** Supporter
  feedback (2026-09-29): Sky, the BBC, CFL on TSN and ESPN's conference
  networks carry games the scoreboards never list, and "if you do EPG it will
  need a way to refresh that data". Settings → Channels → TV guide uses the
  XMLTV link the playlist names (`url-tvg`), or one the viewer pastes. It
  refreshes on open when 6 hours old or from another day, then every 6 hours,
  plus a Refresh button; today and tomorrow only. Three jobs:
  - *Match my channels:* a scoreboard game the guide lists gets the exact
    channels airing it, shown first in Game Detail as `GUIDE` rows.
  - *Fix bad names:* guide channels join the playlist by `tvg-id`, and by
    cleaned name when the ids are missing or disagree.
  - *Add missing games:* live events only the guide has get their own cards
    and an "On TV" tab — as a matchup when the title parses ("Arsenal v
    Chelsea", "Chiefs @ Bills"), otherwise as the title. Repeats, highlights,
    news and studio shows are left out; at most 60 are added.

  The importer (`Importer.kt`, `GuideParser.kt`) streams the file and keeps
  only two days of sport — past 12,000 listings, the soonest. A generated
  120 MB guide parses in 1.9 s with a ~44 MB heap delta, 300 MB in 3.5 s (JVM
  on a build machine, not a TV box). Files that would exhaust a TV's memory —
  entity declarations, a single line or text node the size of the file, a
  gzip bomb — fail the import with a named reason instead. Judgement and
  merging live in `lib/guide.mjs` (`tests/guide.test.mjs`).

- **A TV-first home screen.** Supporter feedback (2026-09-29): "you can only
  see one event when opening", with a reference layout — settings on the left,
  a banner of what is live, pills for the sports, and everything upcoming and
  live below. The 300dp rail that carried the brand, counts, health, My Teams
  and the league list is now an 80dp icon rail (Refresh, Ticker only,
  Settings), and the stage gets a fixed header — a one-line top bar (Today,
  the Live/Up counts, a health dot, search, a clock of its own now the ticker
  is off by default) and the sport pills in a scrolling row — over a body that
  scrolls. The banner is one match line, away | score | home, with the
  status, channels and Watch on the line above it. Cards lead with the
  channel as a footer band, show the local start time instead of "UP", and
  lose their second Watch button (Watch is one OK away in Game Detail).
  Measured at 960x540 — a 1080p panel — the header, pills, banner and the
  first row of three cards share one screen, where before only the banner
  and a one-card-wide board did; 1920 fits five cards a row. The section
  labels step aside on that panel. On a phone the rail becomes a row across
  the top and the banner shows marks and score. `tests/layout.test.mjs`
  checks that every id the app looks up still exists after the move.

### Fixed

- **The new header and cards work without colour or a tooltip.** On TV panels
  under 1280px the health dot changes shape as well as colour (round when all
  sources are live, a diamond when one is degraded, a hollow ring on the demo
  slate). The demo-slate warning is announced by screen readers. A card's "+N"
  reads its other channels aloud ("2 more channels: ABC, ESPN+").
- **Playlist import works on the TV.** The APK never served `/api/playlist`
  (the WebView client answers 404 for it), and a provider playlist is past the
  1.5 MB proxy cap anyway, so Import failed on every Android install. The shell
  now downloads and parses it (`PlaylistParser.kt`, held to the same answer as
  `parseM3U` by `tests/fixtures/playlist-parity.m3u`), skips movie and series
  entries before the 4000-channel cap, and keeps `tvg-id`/`tvg-name`. A
  provider link is shown only as its host after import, and no error message
  quotes one — including the dev server's, which could pass a fetch error
  through.

## [1.4.1] — 2026-09-30

### Added

- **The scrolling ticker can be turned off, and a fresh install starts with it
  off.** Settings → Ticker & display → Scrolling ticker. With it off, the
  chyron's row collapses and the board takes the height, keeping the overscan
  guard at the bottom edge. A hidden ticker is also a stopped one, so it costs
  nothing and the frozen-ribbon watchdog does not restart it. An install from
  before this setting keeps its ticker on, because that is what the viewer has
  been looking at; crawl mode and the phone's floating overlay always show it.
  `lib/chrome.mjs`, `tests/chrome.test.mjs`.

- **A VPN status dot over every other app.** Android TV has no status bar, so
  once a stream is playing there is no way to tell whether the VPN is still up
  — you have to leave the game and open its app to find out. Settings → Ticker
  & display now offers a small dot in the corner of the screen: green when a
  tunnel is up and Core Line's traffic is inside it, amber when a tunnel is up
  but this app is outside it (per-app VPN), red when there is no tunnel. Corner,
  brightness, hide-while-healthy and pulse-on-fault are the viewer's choice, it
  respects the panel's calibrated overscan, and it survives a reboot. It runs
  on the same foreground service as the ticker but independently of it — either
  surface can be on alone. Detection is `NetworkCapabilities.TRANSPORT_VPN` on
  the active network; the dot names the *state*, never the provider, because
  `getOwnerUid()` is only readable by the VPN's own app, and it does not treat
  `NET_CAPABILITY_VALIDATED` as a colour, because local VPNs report validation
  with no upstream connection at all. `lib/vpn.mjs`, `tests/vpn.test.mjs`,
  `VpnState.kt`/`VpnDotView.kt`/`VpnDotWindow.kt`/`BootReceiver.kt`, and
  `tests/vpn-ui.test.mjs` for the markup/model/bridge seams. Research:
  `docs/research/core-line-vpn-dot-android-tv-2026-09-29.md`.

- **Team logos.** Every scoreboard source has carried a `logo` on each
  competitor since it was written, and no view ever drew one — the hero, the
  cards and the game detail all printed an abbreviation and nothing else. They
  are now rendered from whichever provider served the event — ESPN (which
  covers the NBA, NFL, MLB, college and soccer) and the NHL — in two shapes: a
  large mark over
  the abbreviation in the hero and the game detail (the team's name is spelled
  out beside it, so covering the monogram costs nothing), and a small mark
  *beside* the abbreviation on the cards, because a 26px logo is a colour cue
  rather than text and the letters are what a viewer reads from a couch.

  The fallback is structural rather than a handler: the monogram is painted
  first and the logo over it, so a dead CDN, a blocked host, a refused URL or a
  document with no JavaScript all land on a readable mark. There is no inline
  `onerror=` — see the policy below — only one capture-phase `error` listener
  for the whole app, which drops the empty slot a failed image leaves in the
  compact form. `lib/logos.mjs`, `lib/team-rows.mjs`, `tests/logos.test.mjs`.

  Corrections from the review that followed, each verified against the live
  endpoints rather than recalled:

  - **The plate now follows the artwork.** One plate colour for every source
    was not a guarantee, it was half a guarantee. ESPN draws its scoreboard
    marks for a dark UI; the NHL publishes only `assets.nhle.com/.../_light.svg`,
    the *dark-ink* variant drawn for a light background — so on a dark plate the
    league this app's supporter names first would have rendered as a smudge.
    `logoPlate()` reads the variant out of the URL and the CSS paints a light or
    dark plate to match, in both mark forms.
  - **Scores are escaped.** The two row builders this change merged into one
    place interpolated `team.score` raw. A score reads like a number, so it is
    easy to miss that it arrived from a third party — a hostile or malformed
    payload could put markup in the document, in an app that now ships a CSP
    partly as defence against exactly that. The rows moved to
    `lib/team-rows.mjs` so a test can *execute* them and assert a hostile score
    comes out inert, which reading the source for `esc(` never could.
  - **`mlbstatic.com` is no longer allowlisted**, because nothing ever built a
    URL on it: `eventsFromMlb` sets `logo: null` and MLB reaches ESPN first. An
    allowlist entry nothing uses is surface with no benefit, and it made the
    "ESPN, the NHL and MLB" claim read as true when the statsapi fallback
    supplies no logo at all. That claim is now the accurate one above.
  - **A logo URL can no longer carry credentials or a non-standard port.** It
    was accepted before, and the case was pinned by a test that asserted
    nothing (`ESPN.endsWith('.png') ? X : null` — always `X`), which is how it
    survived.
  - **`data-oled` is declared statically on `<html>`.** It used to be applied
    only by script, so the page painted the house near-black and then snapped to
    true black. Invisible while the default was off; a flash on every boot once
    it flipped to on, on the panels this feature exists for. `applyChrome()`
    still removes it for a viewer who turned the option off.

- **A Content-Security-Policy, and the decision written down.** The page now
  ships a meta CSP rather than inheriting the browser's defaults. `script-src
  'self'` is the load-bearing part, and it is why the boot guard moved out of
  `index.html` into `js/boot.js`: keeping two inline `<script>` tags would have
  meant allowing inline script, and any injected handler anywhere would then
  run. `style-src` allows `'unsafe-inline'` because the app is built on inline
  custom-property accents — declared in the policy's own comment rather than
  discovered later. `img-src` names the logo CDNs, matching `LOGO_DOMAINS`, and
  the test asserts they agree. `connect-src` stays open to http/https on
  purpose: feeds are user input, so `lib/ssrf.mjs` is the control there, not a
  directive that would break the app's main feature.

  Both documents carry one — `index.html` and `overlay.html`, the strip that
  floats over other apps — because a CSP does not inherit between documents.

- **The banner rotates through what is live.** The hero tile used to be pinned
  to the same game until it ended — on a Saturday with six games on, the other
  five were only ever cards. It now moves through the slate every 12s, real
  games ahead of feed listings. It deliberately stands still while a viewer has
  focus on it (rotating then replaces the node they are on, and the focus ring
  falls to the body mid-read), while the app is not visible, and when the
  viewer has asked for reduced motion. The running order and the "may I move?"
  rule are in `lib/hero.mjs` and unit-tested; the board only paints the pick.

- **True black for OLED panels.** The house near-black (`#0B0B0D`) is a
  deliberate choice on a backlit panel, where pure black is a hole. On an OLED
  it is the opposite problem: every one of those pixels is still emitting, and
  a full-screen field of it in a dark room reads as grey haze against a bezel
  that is genuinely off. Settings → Appearance now offers *True black (OLED)*,
  **on by default** — OLED sets are what this audience is buying, and on a
  backlit panel `#000` costs almost nothing over the house near-black —
  which drops the shell and the board's field to `#000000` and steps the
  surfaces above it up the way Material's dark ramp does, so elevation — the
  only thing keeping a focused card from dissolving into the field — survives.
  It is applied as a `data-oled` attribute beside `data-theme` rather than as
  a fifth theme, because it says nothing about the accent: broadcast red and
  Core midnight both have to work on it. `tests/oled.test.mjs` parses the ramp
  out of `tokens.css` and compares luminances, so raising the house ramp in
  some later release cannot silently put the haze back.

- **A College pill in the sport filter.** "Whatever college sport is on" was
  two pills to check by hand. `sport:college` groups NCAA football and
  basketball into one filter, so football Saturdays and March Madness both
  answer to a single tap. It is a grouping in `SPORT_GROUPS`, not a new league
  — the pills, counts and labels are all derived from that one table.

### Fixed

- **FCS college football shows up — including games on ESPN2.** A supporter
  reported that Harvard at Brown on ESPN2 (2026-09-25) never appeared. ESPN's
  college-football scoreboard returns only the top division (FBS) unless it is
  asked for a group, so every FCS game was missing, whatever channel it was
  on. Asking for all of Division I in one request (`groups=90`) returns about
  1.7MB, over the 1.5MB feed cap on both the server and the Android proxy, so
  the slate now asks for FBS (`groups=80`) and FCS (`groups=81`) separately —
  about 1MB each — and merges them. On Saturday 2026-09-26 that is 116 games
  where it was 65. One half failing still shows the other.
  `espnScoreboardUrls()` / `loadEspnLeague()` in `lib/scoreboard.mjs`, used by
  both `server.mjs` and `lib/client-slate.mjs`; `tests/espn-groups.test.mjs`.

- **Channels whose whole name *is* a streaming tier now match.** The Tier 1
  "network bug" comparison in the guide matched a playlist entry's channel name
  against the badge the slate prints, which worked for broadcast networks and
  silently failed for the tiers that only exist as a suffix: `ESPN+ 1`,
  `SEC Network+`, `ACC Network Extra`, `Big Ten Network`, `Longhorn Network`.
  Those rows simply never appeared under their network. The comparison now
  collapses an entry to its tier (`ESPN+ 1` → `ESPN+`, `ESPN Unlimited` →
  `ESPN+`) and expands the shorthand back to its brand before matching, with a
  guard so that real channels that merely look numbered (`ESPN2`, `TSN4`,
  `SN 3`, `F1`) are left exactly as they are. Dot-carrying names like `MLB.TV`
  also survive the splitter now; they were being trimmed off the end of a
  comma-separated list. `tests/niche-channels.test.mjs`.
- **Team names show in the banner on a 1080p TV and on a phone.** A 1080p
  panel reports 960 CSS px, so the board beside the rail is 544dp. With scores
  on, the banner's Watch column took its content width and the team column got
  4px: "Philadelphia Eagles" and "Chicago Bears" were not drawn at all. On a
  phone they were 0px, because the phone layout for the banner sat in
  `layout.css`, which loads before `components.css` and so never applied.
  Below 1280 on a TV, and on a phone, the banner now stacks: teams across the
  full width (280px of name at 960), then one row of channels and Watch. The
  venue leaves that row, since beside three channels it came down to one
  letter; Game Detail still shows it. At 960x540 the banner is
  267px, so the first card stays above the chyron. With the banner now
  rotating, this is the frame a viewer sees most.

## [1.4.0] — 2026-09-29

A structural release. The front end had grown into a single 1,340-line
`public/js/app.js` and a 1,000-line `public/css/app.css`, and the audit's
findings — drawer focus trap, focus lost on re-render, a slider no remote could
operate — were three symptoms of that one shape. Both files are now split by
concern, and the design system is ported from the Core Builds Icon Pack so the
two apps draw from one brand.

**Install:** Downloader code `7375676`, which follows the floating `coreline`
release, or the `coreline-v1.4.0` release once it is tagged. Installed copies
offer the update from Settings → Updates.

### Added

- **Search across the whole slate.** The board, the hero and the crawl all
  filter together. Matching is generous on purpose: "leafs" finds Toronto and
  "tsn" finds the game on TSN, because that is how people type. Multiple terms
  are ANDed. `lib/query.mjs`, covered by `tests/query.test.mjs`.
- **My Teams rail.** Starred teams get their own rail section with a live
  count each, so a favourite is addressable without scanning the board.
- **Score alerts.** A score change, a lead change, the start of a game or the
  final for a starred team raises one brief alert. The crawl scrolls past once
  and is gone; for the two or three teams someone actually follows that was the
  wrong trade-off. One alert per cycle — three stacked toasts on a TV is noise.
  `lib/alerts.mjs`, covered by `tests/alerts.test.mjs`.
- **Overscan calibration.** Classic TV panels crop 2.5–5% of the frame and the
  amount differs by set, so the margin is now calibrated on the device against
  corner marks (`Settings → Ticker & display → Calibrate overscan`) instead of
  guessed once.
- **First-run onboarding.** Two skippable questions — favourite teams, and a
  feed. Nothing blocks: the bundled sample feed already fills the crawl.
- **Start times in the viewer's own timezone** on upcoming game cards.
- **Three distinct empty states** (no listings, no search matches, nothing on
  this tab), each offering the action that undoes it.
- `ticker/STRUCTURE.md` — the module map and the rules that keep it that way.

### Changed

- **Overscan now follows the platform spec instead of a guess.** The margin was
  28px on one axis, applied to the sides only, with the top inheriting an
  unrelated safe-area token. Android's TV guidance specifies a 5% margin at the
  960×540 baseline — 48dp on the sides, 27dp top and bottom — so `--overscan`
  is now 48px there, with `--overscan-y` derived from `--overscan-x` at that
  same 27:48 ratio. One slider still drives both. A phone keeps a small gutter,
  because a phone has no overscan to survive.
- **The overscan default lives in one place.** `state.overscan` is `null` until
  the viewer calibrates, so the stylesheet owns the number appropriate to the
  form factor. Previously JavaScript stamped a phone's 28 over a television's
  5% on first boot, before anyone had chosen anything.
- **The rail no longer eats the board's columns.** At a 360px card floor and a
  340px rail, a 1080p panel — 960dp wide, 864dp of content after the guards —
  left the board 504dp and fitted exactly one card per row. The rail is 300dp,
  as it is in the icon pack, and the card floor 260px: two columns at 1080p,
  five at 4K.
- **Two-pane TV layout.** A fixed-width left rail carries the brand, counts,
  source health, My Teams, the league nav and the actions; the right pane holds
  the board. The chyron spans both, for the reason the icon pack gives its
  update bar: it is the one thing on the screen wider than the rail and more
  urgent than the grid. Below 1100px the rail becomes a strip above the board.
- **The design system is now the icon pack's.** `tokens.css` carries the brand
  palette from `colors.xml` and the metrics from `dimens.xml` — 8px grid, one
  type scale with a 12px floor, 48px minimum targets, the 3px-ring / 3px-gap
  focus treatment. `components.css` is a line-by-line port of `bg_cta.xml`,
  `bg_ghost.xml`, `bg_chip_toggle.xml`, `bg_search.xml`, `bg_card.xml` and
  `bg_update.xml`, keeping their reasons.
- **The 10-foot pass scales tokens instead of patching components.** The old
  `tv.css` overrode ~40 component rules with hard-coded rem values, which is
  how a UI ends up with two parallel scale ladders that disagree.
- **Themes no longer restyle status.** LIVE, UP and FINAL were being recoloured
  per theme — in Broadcast red, the accent and the LIVE badge were the same red.
  Themes now remap only the accent and the surface ramp; status is fixed, like
  a traffic light.
- **The chyron separator is a real element with symmetric margins**, not a
  `::after` with a one-sided margin. A trailing separator on every item is what
  makes the seam seamless.
- **The phone overlay uses the shared stylesheet.** `overlay.html` carried its
  own copy of the strip's CSS, which is how the two drifted apart; it now links
  the same `tokens.css`, `base.css` and `chyron.css` and shares `tickHtml`.
- `public/js/app.js` is 1,340 → 352 lines and contains only wiring and input.

### Fixed

- **The D-pad stays inside onboarding and calibration.** Only Game Detail
  kept focus inside itself, so Down from a team chip on the very first screen
  walked onto a game card hidden behind the dialog. Every overlay now holds
  the D-pad until it closes (`MODALS` in `public/js/tv.js`).
- **Closing an overlay puts focus somewhere visible.** Onboarding and
  calibration left focus on a hidden node, so nothing was highlighted until
  an arrow was pressed. Focus returns to where it was, or to the league rail,
  and never to a text field: on a TV that raises the on-screen keyboard.
- **Card controls stay on the card.** A start time such as "9/29 - 6:30 PM
  EDT" pushed the star and Watch buttons past the card edge on a 1080p board.
  The status badge now truncates; the two 48px targets never shrink.
- **League tags are readable.** They used the raw brand colour as text, and
  NFL, MLB, EPL and UCL navies land near 1.3:1 on the card. The text is now
  lifted to 4.5:1 or better while keeping its hue, and the marker keeps the
  true colour (`lib/contrast.mjs`, covered by `tests/contrast.test.mjs`).
- **Ghost buttons have their size back.** `.btn--ghost` is a modifier of
  `.btn`, but ten buttons (Skip, Clear filters, pairing, Calibrate overscan,
  Remove playlist, feed Remove, Check for updates, and two in Game Detail)
  used it alone and rendered as small browser-default buttons.
- **A 1080p TV gets the two-pane layout.** A 1080p panel reports 960 CSS px
  (density 2), and the narrow layout's `max-width: 1100px` query caught it:
  the rail, counts and chyron filled the 540px frame and not one card was
  above the fold. TV mode now always keeps the rail beside the board; the
  strip is for phones and narrow windows only.
- **The rail keeps its shape at 960×540.** Its children shrank to fit, which
  flattened the source-health pill and left the league list about one and a
  half chips tall. They keep their height and the rail scrolls as one column.
- **Onboarding fits the screen.** On a 1080p TV the teams step was 772px on a
  540px frame, with Next and Skip below the panel edge. Only the body scrolls
  now; the actions stay put.
- **Onboarding lists every team on the slate, grouped by league.** It showed
  the first 24 alphabetically, which on a live slate of 91 teams stopped at
  DEN. They now follow the viewer's league order.
- **The sample game no longer claims to be live.** The bundled sample feed
  carried a "LIVE Chiefs vs Bills" listing with no score or clock. It was
  counted in the LIVE total, badged LIVE on the board, and took the hero over a
  real game in progress. The title no longer says LIVE, so all seven sample
  items are listings (`UP`); a test now fails if the sample ever parses as live
  or final. Separately, a scoreboard game is featured before any feed listing.
- **The idle chyron bug reads CORE / LINE.** With nothing live it fell back to
  "LINE" above the new "LINE" name line — LINE / LINE, on the TV and in the
  phone overlay. Red is kept for "N LIVE"; idle, the kicker takes the accent.
- **Long venue names stay on the card.** Live ESPN data carries names like
  "Empower Field at Mile High", which ran 32px past a 266px card at 720p. The
  venue truncates and keeps the full name in its tooltip; the channels never
  give way.
- **`/api/health` reports the real version.** It said `1.0.0` whatever was
  running; it now reads `package.json`.

- **The speed slider works with a D-pad.** It was exempt from key handling
  entirely, so arrows moved focus instead of the value and the control could
  not be operated by remote at all. Horizontal arrows now drive the slider and
  vertical arrows navigate. There are also −/+ buttons. (AUDIT D7)
- **Focus survives re-render.** Every render is wrapped in
  `captureFocus`/`restoreFocus`, which identify the focused element by event id
  rather than by node, because the node is about to be replaced. (AUDIT D5)
- **Escape closes the topmost layer** — calibration, onboarding, game detail,
  then the drawer — instead of only the drawer.
- **Feeds are SSRF-validated when added**, not only when fetched, so an unsafe
  URL never enters persisted state and is never retried. (AUDIT B7)
- Missing hero and card interior styles (`.teams`, `.hero-meta`, `.hero-side`,
  `.pills`, `.game-teams`) reinstated after the stylesheet split.
