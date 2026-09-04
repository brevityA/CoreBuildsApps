/**
 * Turn RSS / Atom / JSON / free-text listings into ticker events.
 * Built for the format the living-room crowd actually uses:
 *   "Team vs Team  epn, tsn4, sn 3"
 */

import { extractChannels, splitChannelsBlob, normalizeChannel, peelChannels } from './channels.mjs';
import { abbreviate } from './teams.mjs';

/** Hard cap on items parsed from any single feed (bounded DOM, no runaway renders). */
export const MAX_ITEMS_PER_FEED = 100;

const VS_RE = /\s+(?:vs\.?|v\.|versus|@|at)\s+/i;
const SEP_RE = /\s*[-–—|:]\s*/;
const LEAGUE_PREFIX = /^(nhl|nba|nfl|mlb|wnba|mls|epl|ufc|f1|ncaaf|ncaab|cfb|cbb|soccer|football|hockey|baseball|basketball)\s*[-–—:|]\s*/i;
const LIVE_PREFIX = /^(?:\[?live\]?\b|in progress\b|now playing\b)\s*[-–—:|]?\s*/i;
const FINAL_PREFIX = /^(?:final\b|ft\b|full[- ]time)\s*[-–—:|]?\s*/i;
const REPLAY_RE = /\b(replay|re-?run|on[- ]demand|encore|highlights?)\b/i;
/** Like LEAGUE_PREFIX but the separator is optional — used only to peel a
 *  leading league word ("NFL LIVE - X vs Y") before testing LIVE/FINAL. */
const LEAGUE_WORD = /^(?:nhl|nba|nfl|mlb|wnba|mls|epl|ufc|f1|ncaaf|ncaab|cfb|cbb|soccer|football|hockey|baseball|basketball)\s*[-–—:|]?\s*/i;
const TIME_RE = /\b((?:1[0-2]|0?[1-9])(?::[0-5]\d)?\s*(?:am|pm)|(?:[01]?\d|2[0-3]):[0-5]\d)\b/i;

export function decodeXmlEntities(value) {
  return String(value || '')
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&#(\d+);/g, (_, n) => String.fromCharCode(Number(n)))
    .replace(/&#x([0-9a-f]+);/gi, (_, n) => String.fromCharCode(parseInt(n, 16)))
    // &amp; must be decoded LAST so "&amp;lt;" becomes the literal text
    // "&lt;" instead of being double-unescaped into "<".
    .replace(/&amp;/g, '&')
    .trim();
}

export function stripTags(html) {
  // CDATA sections are literal text and may contain `<`, `>`, or whole tag-like
  // shapes. Hoist them out first so the tag-stripper cannot eat their contents
  // (a real bug: `<![CDATA[Leafs <vs> Habs]]>` used to collapse to ">").
  const cdata = [];
  let source = String(html || '').replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, (_, inner) => {
    cdata.push(inner);
    return `\u0000${cdata.length - 1}\u0000`;
  });
  source = decodeXmlEntities(source.replace(/<[^>]+>/g, ' '));
  source = source.replace(/\u0000(\d+)\u0000/g, (_, i) => cdata[Number(i)] ?? '');
  return source.replace(/\s+/g, ' ').trim();
}

export function parseListing(title, extra = '') {
  const rawTitle = stripTags(title);
  const rawExtra = stripTags(extra);
  const combined = [rawTitle, rawExtra].filter(Boolean).join(' — ');

  let working = rawTitle.replace(LIVE_PREFIX, '');
  let league = null;
  const leagueMatch = working.match(LEAGUE_PREFIX);
  if (leagueMatch) {
    league = leagueMatch[1].toUpperCase();
    if (league === 'CFB') league = 'NCAAF';
    if (league === 'CBB') league = 'NCAAB';
    working = working.slice(leagueMatch[0].length);
  }

  let away = null;
  let home = null;
  let remainder = '';

  const vsParts = working.split(VS_RE);
  if (vsParts.length >= 2) {
    away = cleanTeam(vsParts[0]);
    const right = vsParts.slice(1).join(' vs ');
    const cut = right.split(SEP_RE);
    if (cut.length > 1) {
      home = cleanTeam(cut[0]);
      remainder = cut.slice(1).join(' ');
    } else {
      const peeled = peelChannels(right);
      home = cleanTeam(peeled.text || right);
      remainder = peeled.channels.join(', ');
    }
  }

  const channelPool = [remainder, rawExtra].filter(Boolean).join(' , ');
  let channels = splitChannelsBlob(channelPool);
  if (!channels.length) channels = extractChannels([remainder, rawExtra, rawTitle].filter(Boolean).join(' , '));

  // If the extra blob is "just" channels, don't also treat it as a headline.
  const extraLooksLikeChannels = rawExtra && splitChannelsBlob(rawExtra).length > 0
    && !VS_RE.test(rawExtra);

  const timeMatch = combined.match(TIME_RE);
  // A stray "live" anywhere in the title used to flip an item to LIVE — so a
  // "NFL Live" show, a "watch live replay", or any replay/rerun title showed a
  // red LIVE badge over a game that isn't live. Only a LEADING live indicator
  // (optionally after a league prefix) counts, and replay markers win.
  const afterLeague = rawTitle.replace(LEAGUE_WORD, '');
  const isReplay = REPLAY_RE.test(`${rawTitle} ${rawExtra}`);
  const isFinal = (FINAL_PREFIX.test(rawTitle) || FINAL_PREFIX.test(afterLeague))
    && !/final four/i.test(rawTitle);
  const isLive = !isReplay && (LIVE_PREFIX.test(rawTitle) || LIVE_PREFIX.test(afterLeague));

  const event = {
    id: slugId(rawTitle || combined),
    source: 'rss',
    league: league || guessLeague(combined, channels),
    status: isLive ? 'live' : isFinal ? 'final' : 'upcoming',
    start: null,
    detail: timeMatch ? timeMatch[1].toUpperCase() : '',
    away: away ? teamFromName(away) : null,
    home: home ? teamFromName(home) : null,
    channels,
    headline: away && home ? null : rawTitle,
    rawTitle,
    rawExtra: extraLooksLikeChannels ? '' : rawExtra,
  };

  return event;
}

export function parseFeed(xmlOrJson, meta = {}) {
  const text = String(xmlOrJson || '').trim();
  if (!text) return [];

  if (text.startsWith('{') || text.startsWith('[')) {
    return parseJsonFeed(text, meta);
  }

  const items = [];
  const itemBlocks = [...text.matchAll(/<(item|entry)\b[^>]*>([\s\S]*?)<\/\1>/gi)];
  for (const block of itemBlocks) {
    const body = block[2];
    const title = firstTag(body, 'title');
    const description = firstTag(body, 'description')
      || firstTag(body, 'summary')
      || firstTag(body, 'content')
      || firstTag(body, 'content:encoded');
    const category = allTags(body, 'category').join(', ');
    const pub = firstTag(body, 'pubDate') || firstTag(body, 'updated') || firstTag(body, 'published');
    const event = parseListing(title, [description, category].filter(Boolean).join(', '));
    const catLeague = leagueFromCategory(category);
    if (catLeague && (!event.league || event.league === 'RSS')) event.league = catLeague;
    event.source = meta.source || 'rss';
    event.feed = meta.label || meta.source || 'RSS';
    if (pub) {
      const ms = Date.parse(pub);
      if (!Number.isNaN(ms)) event.start = new Date(ms).toISOString();
    }
    items.push(event);
  }
  return items.slice(0, MAX_ITEMS_PER_FEED);
}

export function parseJsonFeed(text, meta = {}) {
  let data;
  try {
    data = JSON.parse(text);
  } catch {
    return [];
  }
  const rows = Array.isArray(data)
    ? data
    : data.items || data.events || data.games || data.entries || data.results || [];
  if (!Array.isArray(rows)) return [];
  const limited = rows.slice(0, MAX_ITEMS_PER_FEED);

  return limited.map((row, index) => {
    if (row == null) return null; // skip null rows instead of crashing (AUDIT B-group)
    if (typeof row === 'string') {
      const event = parseListing(row);
      event.source = meta.source || 'rss';
      event.feed = meta.label || 'RSS';
      event.id = event.id || `json-${index}`;
      return event;
    }
    if (typeof row !== 'object') return null; // numbers, booleans, etc.
    const title = row.title || row.name || row.headline || row.event || '';
    const extra = row.channels
      ? (Array.isArray(row.channels) ? row.channels.join(', ') : String(row.channels))
      : row.description || row.summary || row.content_html || row.content_text || row.networks || row.tv || '';
    const event = parseListing(title, extra);
    if (Array.isArray(row.channels)) {
      event.channels = unique([
        ...row.channels.map(normalizeChannel),
        ...event.channels,
      ]);
    }
    if (row.home || row.away) {
      event.home = teamFromName(row.home?.name || row.home || event.home?.name);
      event.away = teamFromName(row.away?.name || row.away || event.away?.name);
      if (row.home?.score != null) event.home.score = String(row.home.score);
      if (row.away?.score != null) event.away.score = String(row.away.score);
    }
    if (row.league) event.league = String(row.league).toUpperCase();
    if (row.category || row.categories) {
      const catLeague = leagueFromCategory(String(row.category || row.categories));
      if (catLeague && (!event.league || event.league === 'RSS')) event.league = catLeague;
    }
    if (row.status) event.status = normalizeStatus(row.status);
    if (row.start || row.date || row.time || row.date_published || row.published) {
      const ms = Date.parse(row.start || row.date || row.time || row.date_published || row.published);
      if (!Number.isNaN(ms)) event.start = new Date(ms).toISOString();
    }
    event.source = meta.source || 'rss';
    event.feed = meta.label || row.feed || 'RSS';
    event.id = row.id || event.id || `json-${index}`;
    return event;
  }).filter(Boolean);
}

export function toTickerText(event) {
  const channels = (event.channels || []).join(', ');
  const status = (event.status || '').toUpperCase();
  const prefix = status === 'LIVE' ? 'LIVE ' : status === 'FINAL' ? 'FINAL ' : '';
  if (event.away && event.home) {
    const away = event.away.abbr || event.away.name;
    const home = event.home.abbr || event.home.name;
    const hasScore = event.away.score != null && event.home.score != null && event.status !== 'upcoming';
    const matchup = hasScore
      ? `${away} ${event.away.score}-${event.home.score} ${home}`
      : `${away} vs ${home}`;
    const when = event.status === 'upcoming' ? (event.detail || '') : (event.detail || '');
    return [prefix + matchup, when, channels].filter(Boolean).join('  ·  ').replace(/\s+/g, ' ').trim();
  }
  return [prefix + (event.headline || event.rawTitle || ''), channels].filter(Boolean).join('  ·  ');
}

export function guessLeague(text, channels = []) {
  const blob = `${text} ${channels.join(' ')}`.toLowerCase();
  if (/\bncaaf|college football|cfb\b/.test(blob)) return 'NCAAF';
  if (/\bncaab|college basketball|march madness|cbb\b/.test(blob)) return 'NCAAB';
  if (/\bnhl|hockey|maple leafs|canadiens|oilers\b/.test(blob)) return 'NHL';
  if (/\bnba\b|lakers|celtics|knicks|warriors\b/.test(blob)) return 'NBA';
  if (/\bnfl\b|chiefs|bills|cowboys|super bowl\b/.test(blob)) return 'NFL';
  if (/\bmlb|yankees|dodgers|blue jays|world series\b/.test(blob)) return 'MLB';
  if (/\bwnba\b/.test(blob)) return 'WNBA';
  if (/\bepl|premier league|arsenal|chelsea|liverpool\b/.test(blob)) return 'EPL';
  if (/\bmls|inter miami\b/.test(blob)) return 'MLS';
  if (/\bufc|mma\b/.test(blob)) return 'UFC';
  if (/\bf1|formula 1|grand prix\b/.test(blob)) return 'F1';
  if (channels.some((c) => /^TSN|^SN/.test(c))) return 'SN';
  return 'RSS';
}

/**
 * Map an explicit category/league string (RSS `<category>`, JSON `category`
 * field) to a known league id. Only unambiguous matches; returns null when the
 * text is generic (e.g. bare "football") so we never mis-bucket a feed.
 */
export function leagueFromCategory(text) {
  const t = String(text || '').toLowerCase();
  if (!t) return null;
  if (/\bncaaf\b|college football|cfb/.test(t)) return 'NCAAF';
  if (/\bncaab\b|college basketball|march madness|cbb/.test(t)) return 'NCAAB';
  if (/\bwnba\b|women.{0,4}basketball/.test(t)) return 'WNBA';
  if (/\bnhl\b|hockey/.test(t)) return 'NHL';
  if (/\bnba\b/.test(t)) return 'NBA';
  if (/\bnfl\b/.test(t)) return 'NFL';
  if (/\bmlb\b|baseball/.test(t)) return 'MLB';
  if (/\bmls\b|major league soccer/.test(t)) return 'MLS';
  if (/\bepl\b|premier league/.test(t)) return 'EPL';
  if (/\bufc\b|mma/.test(t)) return 'UFC';
  if (/\bf1\b|formula 1|grand prix/.test(t)) return 'F1';
  return null;
}

function firstTag(xml, name) {
  const re = new RegExp(`<${name}(?:\\s[^>]*)?>([\\s\\S]*?)</${name}>`, 'i');
  const match = xml.match(re);
  return match ? stripTags(match[1]) : '';
}

function allTags(xml, name) {
  const re = new RegExp(`<${name}(?:\\s[^>]*)?>([\\s\\S]*?)</${name}>`, 'gi');
  return [...xml.matchAll(re)].map((m) => stripTags(m[1])).filter(Boolean);
}

function cleanTeam(value) {
  return String(value || '')
    .replace(/\[[^\]]*\]/g, '')
    .replace(/\([^)]*\)/g, '')
    .replace(/\s{2,}/g, ' ')
    .trim();
}

export function teamFromName(name) {
  if (!name) return null;
  if (typeof name === 'object') return name;
  const trimmed = cleanTeam(name);
  if (!trimmed) return null;
  return { name: trimmed, abbr: abbreviate(trimmed), score: null, logo: null, winner: false };
}

function normalizeStatus(value) {
  const v = String(value || '').toLowerCase();
  if (['in', 'live', 'inprogress', 'in_progress'].includes(v)) return 'live';
  if (['post', 'final', 'ended', 'complete'].includes(v)) return 'final';
  return 'upcoming';
}

function slugId(text) {
  return String(text || '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 64) || `item-${Math.random().toString(36).slice(2, 8)}`;
}

function unique(list) {
  const seen = new Set();
  const out = [];
  for (const item of list) {
    if (!item || seen.has(item)) continue;
    seen.add(item);
    out.push(item);
  }
  return out;
}
