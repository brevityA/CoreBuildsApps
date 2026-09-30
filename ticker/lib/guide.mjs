/**
 * The TV guide (XMLTV) as a second listings source.
 *
 * The scoreboards know which games exist and what they score; they do not
 * know Sky, the BBC, or most of what ESPN Unlimited and the conference
 * networks carry. The viewer's own guide does — it lists what their
 * provider's channels are showing. This module decides what in that guide is
 * a live event, which scoreboard game each one is, and what is left over.
 *
 * Pure: no network, no DOM. The Android importer (GuideParser.kt) streams the
 * provider's file and hands this module a compact, already-windowed summary:
 *
 *   {
 *     v: 1, fetchedAt: <ms>,
 *     channels:   { "<xmltv id>": { name: "Sky Sports Main Event" }, … },
 *     programmes: [ { c: "<xmltv id>", s: <start ms>, e: <stop ms>,
 *                     t: "<title>", st: "<sub-title>", cat: ["Sport", …],
 *                     live: <bool>, rerun: <bool> }, … ]
 *   }
 */

import { normalizeChannel } from './channels.mjs';
import { stripDecorations } from './playlist.mjs';
import { teamFromName } from './parser.mjs';

export const GUIDE_VERSION = 1;
/** How far back a programme may have started and still be on now. */
export const WINDOW_BEFORE_MS = 4 * 3600e3;
/** The most guide-only games the board will take; the rest is noise. */
export const MAX_GUIDE_ONLY = 60;
/** How far a guide slot may start from the scoreboard's start and still be the same game. */
const START_SLACK_MS = 90 * 60e3;

/**
 * The window the guide is kept for: from four hours ago (so a game that is on
 * now is still in it) to the end of tomorrow, local time. Two days, never
 * more — providers' guides beyond that are unreliable, and people do not
 * update the app often enough for a week of listings to stay true.
 */
export function guideWindow(now = Date.now()) {
  const end = new Date(now);
  end.setHours(0, 0, 0, 0);
  end.setDate(end.getDate() + 2);
  return { start: now - WINDOW_BEFORE_MS, end: end.getTime() };
}

/* ---- What counts as a live event --------------------------------------- */

// Programmes about sport rather than of it. Matched as words, so "Newcastle"
// is not "news" and "Review" is not "preview"'s substring problem.
const NOT_AN_EVENT = /\b(?:highlights?|review|preview|news|magazine|classics?|replay|rewind|reloaded|best of|countdown|build-?up|post-?match|pre-?match|studio|talk(?:show)?|podcast|documentary|analysis|goals?(?: round-?up)?|round-?up|reaction|debate|extra|uncut|stories|story|the story of|looking back|flashback|special|weekly|daily|tonight show|show|quiz|film|movie)\b/i;
const LIVE_PREFIX = /^\s*(?:live\b[:\-–— ]*|\(live\)\s*)/i;
const SPORT_CATEGORY = /\b(?:sport|sports|football|soccer|rugby|cricket|tennis|golf|basketball|baseball|hockey|boxing|mma|ufc|wrestling|motorsport|formula|racing|cycling|athletics|darts|snooker|volleyball|lacrosse|nfl|nba|mlb|nhl|ncaa)\b/i;

/**
 * A matchup from a guide title: "Live Premier League: Arsenal v Chelsea",
 * "College Football: Harvard vs. Brown", "NFL: Chiefs @ Bills".
 * Returns null when there are not two sides. A leading "Live" and a
 * competition before the last colon are peeled off first.
 */
export function matchupFrom(text) {
  let s = String(text || '').replace(LIVE_PREFIX, '').trim();
  if (!s) return null;
  let competition = '';
  const colon = s.lastIndexOf(':');
  if (colon > 0 && colon < s.length - 1) {
    competition = s.slice(0, colon).replace(LIVE_PREFIX, '').trim();
    s = s.slice(colon + 1).trim();
  }
  // Trailing context a guide appends: "Arsenal v Chelsea (Kick-off 5.30pm)",
  // "Arsenal v Chelsea - Premier League".
  s = s.replace(/\s*\([^)]*\)\s*$/, '').trim();
  const parts = s.split(/\s+(?:v|vs\.?|versus|@|at)\s+/i);
  if (parts.length !== 2) return null;
  const [away, rawHome] = parts;
  const home = rawHome.replace(/\s+[-–—|]\s+.*$/, '').trim();
  if (!sideLooksReal(away) || !sideLooksReal(home)) return null;
  return { competition, away: away.trim(), home };
}

function sideLooksReal(side) {
  const s = String(side || '').trim();
  // A side is a name: 2–48 characters, at least one letter, not a sentence.
  return s.length >= 2 && s.length <= 48 && /[a-z]/i.test(s) && s.split(/\s+/).length <= 6;
}

/** The text a programme is judged on: title, then sub-title. */
function programmeText(p) {
  return [p?.t, p?.st].filter(Boolean).join(' — ');
}

/**
 * Whether a programme is a live event the board should list: marked live, or
 * a new "Team v Team" broadcast, and never a repeat, highlights, news or a
 * magazine show. Conservative on purpose — a missed oddly titled event costs
 * less than a board full of studio shows.
 */
export function isLiveEvent(p) {
  if (!p) return false;
  const text = programmeText(p);
  if (!text || NOT_AN_EVENT.test(text.replace(LIVE_PREFIX, ''))) return false;
  const markedLive = Boolean(p.live) || LIVE_PREFIX.test(p.t || '');
  if (p.rerun && !markedLive) return false;
  const sporty = (p.cat || []).some((c) => SPORT_CATEGORY.test(c)) || markedLive;
  if (!sporty) return false;
  return markedLive || Boolean(matchupFrom(p.t) || matchupFrom(p.st));
}

/* ---- Guide channels → the viewer's playlist channels -------------------- */

const lower = (s) => String(s || '').trim().toLowerCase();

/**
 * For each guide channel, the playlist channels that carry it: by tvg-id,
 * which is how a guide and a playlist from the same provider are meant to
 * join, and failing that by name — the "terrible EPG" case, where the ids are
 * missing or do not agree but the names do.
 *
 * @returns {Map<string, number[]>} guide channel id → playlist indices
 */
export function mapGuideChannels(guide, playlist) {
  const out = new Map();
  if (!guide?.channels || !Array.isArray(playlist) || !playlist.length) return out;
  const byId = new Map();
  const byName = new Map();
  playlist.forEach((ch, i) => {
    if (ch.tvgId) push(byId, lower(ch.tvgId), i);
    for (const n of [ch.tvgName, ch.name]) {
      const key = nameKey(n);
      if (key) push(byName, key, i);
    }
  });
  for (const [id, meta] of Object.entries(guide.channels)) {
    const hits = byId.get(lower(id)) || byName.get(nameKey(meta?.name)) || [];
    if (hits.length) out.set(id, hits);
  }
  return out;
}

function nameKey(name) {
  const s = lower(stripDecorations(name || '')).replace(/[^a-z0-9+]+/g, '');
  return s.length >= 2 ? s : '';
}

function push(map, key, i) {
  const list = map.get(key);
  if (list) { if (!list.includes(i)) list.push(i); } else map.set(key, [i]);
}

/* ---- Guide programme ↔ scoreboard game ---------------------------------- */

// Short forms a UK guide uses for teams the scoreboard names in full.
const ALIASES = new Map([
  ['man utd', 'manchester united'], ['man united', 'manchester united'],
  ['man city', 'manchester city'], ['spurs', 'tottenham hotspur'],
  ['wolves', 'wolverhampton wanderers'], ['brighton', 'brighton & hove albion'],
  ['newcastle', 'newcastle united'], ['west ham', 'west ham united'],
  ['nottm forest', 'nottingham forest'], ["nott'm forest", 'nottingham forest'],
  ['psg', 'paris saint-germain'], ['inter', 'internazionale'],
]);

const GENERIC = new Set(['fc', 'afc', 'cf', 'sc', 'the', 'and', 'of', 'city', 'united', 'state', 'university', 'college']);

function teamKey(s) {
  const k = lower(s).replace(/&/g, ' & ').replace(/[^a-z0-9& ]+/g, ' ').replace(/\s+/g, ' ').trim();
  return ALIASES.get(k) || k;
}
const words = (k) => k.split(' ').filter((w) => w.length >= 3);

/**
 * Whether a side of a guide title names this scoreboard team: the full name,
 * the abbreviation, or a part of the name ("Chiefs", "Kansas City").
 *
 * Every word of the side must be in the team's name — generic words
 * included, or "Man City" would match Manchester United once "city" and
 * "united" were set aside — and at least one of them must be distinctive,
 * so a generic word alone ("City", "United") is never enough.
 */
export function sideMatchesTeam(side, team) {
  const s = teamKey(side);
  if (!s || !team) return false;
  const full = teamKey(team.name);
  const abbr = lower(team.abbr);
  if (s === full || (abbr.length >= 2 && s === abbr)) return true;
  const sw = words(s);
  const fw = words(full);
  if (!sw.length || !fw.length) return false;
  return sw.every((w) => fw.includes(w)) && sw.some((w) => !GENERIC.has(w));
}

/** The scoreboard game a guide programme is, or null. Both teams, either order, near in time. */
export function findEvent(events, p, matchup) {
  if (!matchup) return null;
  for (const ev of events) {
    if (!ev?.away || !ev?.home) continue;
    const start = ev.start ? Date.parse(ev.start) : NaN;
    if (Number.isFinite(start) && Math.abs(start - p.s) > START_SLACK_MS
      && !(start >= p.s && start <= p.e)) continue;
    const straight = sideMatchesTeam(matchup.away, ev.away) && sideMatchesTeam(matchup.home, ev.home);
    const flipped = sideMatchesTeam(matchup.away, ev.home) && sideMatchesTeam(matchup.home, ev.away);
    if (straight || flipped) return ev;
  }
  return null;
}

/* ---- Merge --------------------------------------------------------------- */

/**
 * Fold the guide into the slate.
 *
 * - A scoreboard game the guide also lists gains `guideChannels` — the
 *   viewer's playlist channels airing it, exact by guide id — and, when the
 *   scoreboard named no channel, the guide's channel names as its channels.
 * - A guide live event no scoreboard has becomes a game of its own
 *   (`source: 'epg'`), one per event however many channels carry it.
 *
 * Only channels the viewer actually has count: a programme on a channel
 * missing from their playlist is not something they can watch.
 *
 * @returns {{events: object[], matched: number, added: number}}
 */
export function mergeGuide(events, guide, playlist, { now = Date.now(), maxGuideOnly = MAX_GUIDE_ONLY } = {}) {
  const list = Array.isArray(events) ? events : [];
  if (!guide || guide.v !== GUIDE_VERSION || !Array.isArray(guide.programmes)) {
    return { events: list, matched: 0, added: 0 };
  }
  const carriers = mapGuideChannels(guide, playlist);
  if (!carriers.size) return { events: list, matched: 0, added: 0 };
  const { start: wStart, end: wEnd } = guideWindow(now);

  const extra = new Map(); // scoreboard event id → { channels:Set, playlist:Set }
  const guideOnly = new Map(); // dedupe key → event
  let matched = 0;

  for (const p of guide.programmes) {
    if (!p || !carriers.has(p.c)) continue;
    if (!(p.e > now && p.s < wEnd && p.e > wStart)) continue; // ended, or outside the two days
    if (!isLiveEvent(p)) continue;
    const matchup = matchupFrom(p.t) || matchupFrom(p.st);
    const chName = normalizeChannel(stripDecorations(guide.channels[p.c]?.name || p.c));
    const idx = carriers.get(p.c);

    const ev = findEvent(list, p, matchup);
    if (ev) {
      let rec = extra.get(ev.id);
      if (!rec) { rec = { channels: new Set(), playlist: new Set() }; extra.set(ev.id, rec); matched += 1; }
      rec.channels.add(chName);
      idx.forEach((i) => rec.playlist.add(i));
      continue;
    }

    const key = matchup
      ? `${teamKey(matchup.away)}|${teamKey(matchup.home)}|${Math.round(p.s / 9e5)}`
      : `${lower(p.t)}|${lower(p.st)}|${Math.round(p.s / 9e5)}`;
    let g = guideOnly.get(key);
    if (!g) {
      if (guideOnly.size >= maxGuideOnly) continue;
      g = guideEvent(p, matchup, now);
      guideOnly.set(key, g);
    }
    if (!g.channels.includes(chName)) g.channels.push(chName);
    for (const i of idx) if (!g._playlist.includes(i)) g._playlist.push(i);
  }

  const withChannels = (ev, chans, idxs) => {
    const guideChannels = idxs.map((i) => playlist[i]).filter(Boolean);
    const channels = ev.channels?.length ? ev.channels : [...chans];
    return { ...ev, channels, guideChannels };
  };

  const merged = list.map((ev) => {
    const rec = extra.get(ev.id);
    return rec ? withChannels(ev, rec.channels, [...rec.playlist]) : ev;
  });
  const added = [...guideOnly.values()].map((g) => {
    const { _playlist, ...ev } = g;
    return withChannels(ev, ev.channels, _playlist);
  });
  return { events: [...merged, ...added], matched, added: added.length };
}

function guideEvent(p, matchup, now) {
  const live = p.s <= now && p.e > now;
  const title = String(p.t || '').replace(LIVE_PREFIX, '').trim();
  const ev = {
    id: `epg:${p.c}:${p.s}`,
    source: 'epg',
    league: 'TV',
    status: live ? 'live' : 'upcoming',
    detail: '',
    start: new Date(p.s).toISOString(),
    end: new Date(p.e).toISOString(),
    channels: [],
    headline: [title, p.st].filter(Boolean).join(' — '),
    rawTitle: p.t || '',
    _playlist: [],
  };
  if (matchup) {
    ev.away = teamFromName(matchup.away);
    ev.home = teamFromName(matchup.home);
    if (matchup.competition) ev.detail = matchup.competition;
  }
  return ev;
}

/* ---- Privacy ------------------------------------------------------------- */

/**
 * A playlist or guide URL safe to show or report: the host only.
 * Provider URLs carry the account — `get.php?username=…&password=…`,
 * `/live/<user>/<pass>/…`, or `user:pass@host` — so the path, query and
 * userinfo never leave the device and are never drawn on screen.
 */
export function maskUrl(url) {
  try {
    const u = new URL(String(url));
    return `${u.protocol}//${u.hostname}${u.port ? `:${u.port}` : ''}/…`;
  } catch {
    return '(invalid link)';
  }
}
