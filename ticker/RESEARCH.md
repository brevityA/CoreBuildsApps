# Core Line — market, provenance and platform research

Researched 2026-09-28/29. This documents what Core Line is up against, where
its real risks sit, and which numbers govern a television layout. It exists so
the next structural decision is made against evidence rather than instinct.

Two limits on all of it, stated up front because they bound what the research
can prove:

- **No device and no emulator.** Nothing here has been seen on a real panel.
- **The sandbox has no outbound network.** Every live endpoint below was
  reached through search tooling only; the app's own data sources could not be
  probed from here. Where that matters, it says so.

---

## 1. The market: three camps, none of which is Core Line

### IPTV sports players

**ScoreBox** (Dark Forest Studios) is the closest thing to a direct competitor,
and Core Line's own source already references it for channel matching.

- One-time **$9.99**, no subscription, no account, no server, no analytics.
- 57 leagues, ~195 networks, 13 sports.
- Bring your own M3U/Xtream playlist; it cross-references the public broadcast
  schedule against the viewer's channel list.
- Plays video: ExoPlayer + FFmpeg on Android TV, AVKit or VLC on tvOS.
- Multiview (4-up), full EPG, per-network preferred feed, spoiler hiding,
  Google-account backup.

Two things about it matter more than the feature list.

The first is the **posture**: "Data Not Collected", no server, the viewer is
responsible for the content they are entitled to. Core Line already matches
this and should keep saying so out loud — it is a genuine differentiator
against every ad-funded scores app.

The second is the **recurring complaint in its own reviews**: *"The UI is quite
small for the television."* It appears twice in the Play listing. Their
changelog also advertises fixes for exactly the reliability problems Core
Line's own audit flagged — stuttering refreshes, slow startup on large
playlists, guides that do not retry. They are good, and they are beatable on
the two things Core Line just fixed: 10-foot typography and refresh
resilience.

### LED and hardware tickers

Glance LED, Fintic, Skybox Sports Network. Physical signs, hundreds of dollars,
a different buyer entirely. Worth noting only because reviewers prefer Fintic's
continuous scrolling to Glance's "page flipping" — a small confirmation that
for an ambient ticker, **continuous motion reads as premium and paging reads as
cheap**. Core Line crawls continuously.

### Kodi add-ons

The telling find. Forum threads asking *"is there a plugin that I can get
scrolling sports scores on the bottom of the screen?"* get the answer *"find an
RSS feed of sports scores and just replace the default with it"* — a hack, not
a product. "Sports Guru" is the one add-on that ships a live ticker, and it
requires installing Kodi first.

---

## 2. The gap

**Nobody ships the RSS reader for the sports slate.**

That is Core Line's origin — sports sites killed their own RSS feeds, so the
app builds one — and the research says the gap is still empty in 2026. The
adjacent products all solve a different problem: they are *players* that need a
playlist, or *hardware*, or *add-ons* that need Kodi.

So the positioning is not "a better ScoreBox". It is:

> Core Line is a broadcast chyron for your living room that reads any feed you
> point it at, tells you which channel each game is on, and gets out of the
> way.

Channel matching is the moat. ScoreBox does it against *your* IPTV playlist;
Core Line does it against broadcast networks and the viewer's own feed. No one
else in the ambient-ticker space does it at all.

---

## 3. Data provenance — the one finding that could force an architecture change

Core Line currently asks **ESPN first** and falls back to the leagues' own
feeds only when ESPN fails (`server.mjs`, `fetchLeagueServer`). The research
says that ordering is backwards.

### What the sources say about ESPN

`site.api.espn.com` is used by Core Line as its primary source. Four
independent write-ups of it agree:

- Undocumented and unsupported; no key; **can change or be removed without
  notice**.
- Publishes no rate limits.
- ESPN retired its official developer API in 2014, so there is no supported
  path for a third party at any price.
- More than one states plainly that *"utilizing these APIs might contravene
  ESPN's Terms of Service."*

For a distributed app that phones home from thousands of living rooms, that is
a real exposure and not a hypothetical one.

### What the sources say about the leagues

The first-party feeds are strictly better on provenance, with an honesty
caveat:

- **NHL** — `api-web.nhle.com/v1`. Active. The legacy `statsapi.web.nhl.com`
  was decommissioned in 2023 and no longer resolves; if any code path still
  names it, that is a live bug.
- **MLB** — `statsapi.mlb.com/api/v1`.
- **NBA** — `stats.nba.com`. Exists, but is known to block non-browser clients.
- **NFL** — no public API.

The caveat: **these are undocumented too.** They are the leagues' own public
data surfaces — the data behind nhl.com and mlb.com — which is a materially
weaker claim for a rights holder to make than ESPN's, but none of them publish
a licence or a stability guarantee. Swapping ESPN for the NHL does not make
Core Line licensed; it makes it *less exposed* and better sourced.

### What this means

The honest read is that **every source Core Line has is undocumented**, so the
mitigation is not "find a licensed one or die trying". It is:

1. **Prefer first-party.** For NHL and MLB, try the league first and fall back
   to ESPN. Both parsers already extract broadcast channels
   (`eventsFromNhl` reads `tvBroadcasts`, `eventsFromMlb` reads
   `broadcasts(all)`), so channel matching — the moat — survives the flip.
2. **Keep the abstraction honest.** Sources are already individually backed
   off and last-good cached. That is the behaviour that makes any single
   source's disappearance an inconvenience rather than an outage.
3. **Lean on the path that is unambiguously fine.** The RSS/JSON feed feature
   is the one source with no provenance question at all: the viewer supplies
   it, for data they have. It should be the recommended default for anyone who
   cares, and the docs should say why.

**Not done in this change**, deliberately. The flip is a two-line reorder, but
it can only be validated against the live endpoints, and this sandbox has no
outbound network. `tools/probe-sources.mjs` is committed alongside this
document: run it on a networked machine and it prints a side-by-side of every
source for every league — event counts, channel coverage, status split — so the
reorder happens on evidence.

---

## 4. Platform: the numbers that govern a TV layout

From the official Android TV design guidance. These are not preferences.

### Overscan

> "position the elements with a 5% margin of 48dp on the left and right sides,
> and 27dp on the top and bottom of a layout"

with the safer variant stated as **58dp sides / 28dp top and bottom**. Measured
at the 960×540 MDPI baseline, where 1dp = 1 CSS px.

The one rule the guidance is unambiguous about:

> "Don't adjust or clip background screen elements to the overscan safe area."

Backgrounds bleed to the full frame. Only content is inset.

### Columns and gutters

12 columns, 52dp wide, 20dp gutters, 58dp side margins, 4dp vertical spacing.

### Two-pane

> "A 2-pane layout performs better when the page shows hierarchical content."

Core Line's layout is a 300dp rail plus a board. That is the recommended
structure for a hierarchical, task-forward screen, and the icon pack it was
ported from uses the same 300dp.

### Test on hardware

> "Test your interface on actual televisions rather than relying on
> emulators."

The corroborating source puts it more sharply: safe-area violations that look
minor in development become unusable when navigation buttons fall off the
screen. Older panels crop hardest.

---

## 5. What changed because of this

Reading the spec against the implementation found three things.

- **The overscan default was invented, not specced.** It was 28px on a single
  axis, applied to the sides only, with the top inheriting an unrelated
  safe-area token. It is now the documented 5% — 48px sides, 27px top and
  bottom — with vertical derived from horizontal at the same 27:48 ratio, so
  calibration stays one control. A phone keeps a small gutter; a phone has no
  overscan to survive.
- **The default now lives in one place.** `state.overscan` is `null` until the
  viewer calibrates, meaning the stylesheet owns the platform-appropriate
  number and JavaScript only stamps one once it has been chosen. Previously a
  phone's 28 was written over a television's 5% on first boot.
- **The rail was eating the board's columns.** At 360px per card and a 340px
  rail, a 1080p panel (960dp wide, 864dp of content after the guards) had
  504dp of board — **one card per row**. A board one card wide is not a board.
  The rail is now 300dp per the icon pack and the card floor 260px, which fits
  two columns at 1080p and five at 4K.

---

## 6. Ranked next steps

1. **Get it on a real panel.** Everything above converges here. Both the
   platform guidance and the competitor reviews say the failure mode is
   invisible until it is on glass. This is the top caveat on the open PR.
2. **Decide the source order with `tools/probe-sources.mjs`.** Evidence first,
   then the reorder. Cheap, and it retires the largest legal exposure in the
   app.
3. **Match ScoreBox's posture in the listing.** No tracking, no account, no
   server, viewer supplies their own data. Say it where buyers decide.
4. **Exploit their weak spot.** Their reviews complain about small type on a
   television, twice. Core Line's 10-foot scale should be the first thing the
   store listing shows.
5. **Spoiler-free mode.** ScoreBox has it; for an ambient living-room ticker
   that is on while people are doing something else, hiding scores until asked
   is a genuinely core feature and not a gimmick.
6. **Do not chase multiview or EPG.** Both need a video pipeline. Core Line is
   a ticker and a channel launcher; adding a player means becoming a worse
   ScoreBox instead of a better Core Line.
