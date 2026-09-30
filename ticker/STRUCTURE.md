# Core Line — structure

How the front end is organised, and the rules that keep it that way.

Core Line is a web UI in a WebView. The Node server (`server.mjs`) and the
parsers in `lib/` are shared with the browser; `public/` is the app. This
document is about `public/`.

## Why it is split this way

The UI used to be one 1,340-line `public/js/app.js` in which every function
could call every other. That shape is what produced the audit's findings: the
drawer focus trap, focus loss on re-render, and the D-pad-unoperable slider
were three symptoms of one problem — there was no seam at which to fix
"rendering" or "focus" as a single concern.

The split mirrors the Core Builds Icon Pack, which has one file per screen and
one drawable per control. A web app has no resource system to enforce that, so
here it is a convention instead.

## Layers

```
public/
  index.html          app shell + overlays
  overlay.html        the crawl over other apps (OverlayService)
  scorebug.html       the scoreboard bug over other apps (ScoreBugWindow)
  css/
    app.css           manifest — the only file index.html links
    tokens.css        colour + metrics. The only place a literal may appear.
    base.css          reset, typography, the focus system
    chyron.css        the broadcast strip: bug, crawl, clock
    scorebug.css      the scoreboard bug's box (loaded by scorebug.html only)
    layout.css        app shell: icon rail, header, stage body, board
    components.css    buttons, chips, cards, fields
    screens.css       drawer, game detail, onboarding, calibration
    tv.css            the 10-foot pass (moves tokens, not components)
  js/
    app.js            composition root: wiring and input only
    core/             dom, bus, store, format, bridge
    data/             slate, feeds, updates, pairing, playlist, watch
    ui/               chyron, rail, board, search, detail, settings,
                      toast, alerts, onboarding, calibrate
    state.js          persisted settings (sanitised on read)
    ticker.js         constant px/s ribbon
    tv.js             D-pad geometric navigation
    watchdog.js       stalled-ribbon detection
    overlay.js        the crawl page; overlay-slate.js is its slate, shared
    scorebug.js       the scoreboard bug page, on the same shared slate
  lib/  (repo-level)  shared with the Node server; importable in tests
```

## The rules

**1. No literal outside `tokens.css`.** A component that needs 16px asks for
`--cb-space-md`. The old stylesheet had ~90 hard-coded values, which is why the
TV pass needed a second, parallel scale ladder to repair what the first one got
wrong.

**2. `tv.css` moves tokens, not components.** The 10-foot pass changes spacing,
type, radii and targets once, and every component follows. It does not override
forty component rules.

**3. Every colour has exactly one meaning slot.** Status colours — LIVE, UP,
FINAL, health — are *not* themeable. A red that means "in progress" in one
theme and "brand chrome" in another is two meanings for one hue. Themes remap
only the accent and the surface ramp.

**4. Modules do not import each other sideways.** `data/` never imports `ui/`,
and `ui/` never fetches. They meet at the bus (`core/bus.js`), which carries
three events — `slate`, `state`, `filter` — plus `toast` and `alert`. That is
the whole contract.

**5. `app.js` owns nothing but wiring.** It reads the URL, subscribes to the
bus, and maps clicks and remote keys onto intents. It contains no markup and no
fetching.

**6. Anything worth testing goes in `lib/`.** The browser modules import it
over `/lib/...`; `npm test` runs under Node with no DOM. That is why search
matching lives in `lib/query.mjs`, alert diffing in `lib/alerts.mjs` and
league-tag contrast in `lib/contrast.mjs` rather than in `ui/`.

## Design system

Ported from the icon pack so both apps draw from one brand:

| Icon pack | Core Line |
|---|---|
| `values/colors.xml` | `css/tokens.css` — brand palette |
| `values/dimens.xml` | `css/tokens.css` — metrics |
| `values/themes.xml` | `css/base.css` — chrome + focus |
| `bg_cta.xml` | `.btn` |
| `bg_ghost.xml` | `.btn--ghost` |
| `bg_chip_toggle.xml` | `.chip` |
| `bg_search.xml` | `.field` |
| `bg_card.xml` | `.card` / `.tile` |
| `bg_update.xml` | `.notice` |

Two techniques, both carried over with their reasons:

- **Cards** focus with `outline` + `outline-offset`: three opaque layers
  (ring, gap of true background, card) and no reflow, so the surface never
  jumps when focus arrives.
- **Buttons, chips and fields** focus with a stroke drawn *inside* their
  bounds, because a control that grows when focused moves its own label, and
  these are what a D-pad user crosses most often.

## The shell

A slim icon rail on the left is the menu (Refresh, Ticker only, Settings), the
way a TV launcher's is. Everything else is in the stage: a fixed header — the
top bar (Today, the Live/Up counts, source health, search, the clock) and the
sport pills — over a body that scrolls: the live banner, then every game live
or upcoming today as cards that lead with the channel. The chyron, when it is
on, spans the whole width under both.

The header does not scroll, so "Today", the counts and the pills are always
where the eye expects them. On a 1080p panel (960x540dp) the top bar, pills,
banner and the first row of three cards share one screen. Below 720px, off a
TV, the rail becomes a row across the top.

## Focus

`js/tv.js` is geometric D-pad navigation: it scores every visible `.focusable`
by distance along the pressed axis, with a penalty for poor alignment on the
cross axis. Two behaviours are deliberate:

- A range input takes **horizontal** arrows natively and **vertical** arrows
  for navigation. Exempting it entirely made the speed slider inoperable by
  remote; exempting it for nothing would strand the cursor.
- Every re-render is wrapped in `captureFocus`/`restoreFocus`, which identify
  the focused element by event id rather than by node, because the node is
  about to be replaced.
