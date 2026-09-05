/**
 * IPTV playlist (M3U) support — ScoreBox-style game→channel matching.
 *
 * Core Line stays a guide, never a player: this module only reads the
 * playlist the user supplies, figures out which of its channels carry a
 * game, and hands the stream URL to an external player app.
 *
 * Pure functions only — no network, no DOM. The server proxies the actual
 * M3U fetch (/api/playlist) with the same SSRF posture as RSS feeds.
 */

import { normalizeChannel } from './channels.mjs';

/** Hard cap so a pathological playlist can't melt the TV. */
export const MAX_CHANNELS = 4000;
/** Longest channel name we keep (decorated IPTV names are noisy). */
const MAX_NAME = 64;
/** Longest stream URL we keep. */
const MAX_URL = 500;

/** Two-letter country prefixes seen in IPTV names ("US| FOX", "CA: TSN4"). */
const COUNTRY_CODES = new Set([
  'US', 'CA', 'UK', 'GB', 'AU', 'NZ', 'DE', 'FR', 'IT', 'ES', 'NL', 'BE',
  'CH', 'AT', 'IE', 'PT', 'SE', 'NO', 'DK', 'FI', 'PL', 'CZ', 'SK', 'HU',
  'RO', 'GR', 'TR', 'IL', 'SA', 'AE', 'IN', 'PK', 'BD', 'LK', 'JP', 'KR',
  'CN', 'TW', 'HK', 'SG', 'MY', 'ID', 'TH', 'VN', 'PH', 'BR', 'MX', 'AR',
  'CL', 'CO', 'PE', 'UY', 'EC', 'CR', 'PA', 'DO', 'VE', 'ZA', 'NG', 'KE',
  'EG', 'MA', 'UA', 'RU', 'LT', 'LV', 'EE', 'HR', 'RS', 'SI', 'BG',
]);

/** Trailing quality/codec tags, strongest first (rank matches array order). */
const QUALITY_TAGS = [
  /\b(?:uhd|4k)\b/i,
  /\bhdr(?:10\+?)?\b/i,
  /\b(?:fhd|1080p?)\b/i,
  /\bhd\b/i,
];
const QUALITY_TAG_RE = /[\s\-|·>]*\b(?:uhd|4k|hdr(?:10\+?)?|fhd|hd|sd|hevc|h\.?26[45]|1080p?|720p?|576p?|480p?)\b\s*$/i;
/** Trailing emoji / symbol noise ("FOX ⚡", "TSN4 ✅"). */
const EMOJI_TAIL_RE = /[\u{1F000}-\u{1FAFF}\u{2600}-\u{27BF}\u{2B00}-\u{2BFF}\u{FE0F}\u{2190}-\u{21FF}\u{2B50}]+\s*$/u;
/** Generic words that must never count as a team-name match on their own. */
const GENERIC_TOKENS = new Set([
  'sports', 'sport', 'network', 'channel', 'channels', 'tv', 'television',
  'hd', 'fhd', 'uhd', 'sd', '4k', 'one', 'two', 'three', 'plus', 'east',
  'west', 'north', 'south', 'pacific', 'atlantic', 'central', 'world',
  'news', 'radio', 'events', 'event', 'live', 'stream', 'main', 'feed',
  'video', 'free', 'extra', 'premium', 'prime', 'fox', 'espn', 'sportsnet',
  'tsn', 'cbc', 'nbc', 'cbs', 'abc', 'bein', 'sky', 'dazn',
]);

/**
 * Parse M3U/M3U8 playlist text into a flat channel list.
 * Tolerant by design: real-world playlists are hand-mangled.
 *
 * @param {string} text raw playlist body
 * @param {{maxChannels?: number}} [opts]
 * @returns {{ok: boolean, count: number, channels: Array<{name:string,url:string,group:string}>, error?: string}}
 */
export function parseM3U(text, { maxChannels = MAX_CHANNELS } = {}) {
  const raw = String(text || '');
  if (!raw.trim()) return { ok: false, count: 0, channels: [], error: 'empty playlist' };

  const channels = [];
  const seenUrls = new Set();
  let pendingName = '';
  let group = '';

  const lines = raw.split(/\r?\n/);
  for (const line of lines) {
    const s = line.trim();
    if (!s) continue;

    if (s.startsWith('#EXTINF')) {
      pendingName = extinfName(s);
      const title = attr(s, 'group-title');
      if (title) group = title.slice(0, 40);
      continue;
    }
    if (s.startsWith('#EXTGRP:')) {
      group = s.slice('#EXTGRP:'.length).trim().slice(0, 40);
      continue;
    }
    if (s.startsWith('#')) continue; // #EXTM3U, #EXTVLCOPT, #KODIPROP, …

    // A non-comment line is a candidate URL.
    if (!/^https?:\/\//i.test(s)) continue;
    if (seenUrls.has(s)) continue;
    seenUrls.add(s);

    const name = (pendingName || fallbackName(s, channels.length + 1)).slice(0, MAX_NAME).trim();
    pendingName = '';
    channels.push({
      name,
      url: s.slice(0, MAX_URL),
      group,
    });
    if (channels.length >= maxChannels) break;
  }

  if (!channels.length) return { ok: false, count: 0, channels: [], error: 'no channels found in playlist' };
  return { ok: true, count: channels.length, channels };
}

/** Display name from an #EXTINF line: text after the last comma, else tvg-name. */
function extinfName(line) {
  const comma = line.lastIndexOf(',');
  if (comma !== -1 && comma < line.length - 1) {
    const tail = line.slice(comma + 1).trim();
    if (tail) return collapse(tail);
  }
  const tvg = attr(line, 'tvg-name');
  return tvg ? collapse(tvg) : '';
}

/** value of a `key="value"` attribute, tolerant of missing quotes. */
function attr(line, key) {
  const re = new RegExp(`${key}\\s*=\\s*"([^"]*)"`, 'i');
  const m = line.match(re);
  if (m) return m[1];
  const loose = line.match(new RegExp(`${key}\\s*=\\s*([^\\s,"]+)`, 'i'));
  return loose ? loose[1] : '';
}

function fallbackName(url, n) {
  try {
    const u = new URL(url);
    const last = u.pathname.split('/').filter(Boolean).pop() || u.hostname;
    return decodeURIComponent(last).replace(/\.(m3u8?|ts|mp4)$/i, '') || `Channel ${n}`;
  } catch {
    return `Channel ${n}`;
  }
}

function collapse(s) {
  return String(s).replace(/\s+/g, ' ').trim();
}

/**
 * Strip IPTV naming decorations so "US| FOX SPORTS UHD ⚡" → "FOX SPORTS".
 * Country prefixes are only stripped for known codes ("TS4:" stays intact).
 */
export function stripDecorations(raw) {
  let name = collapse(String(raw || ''));
  name = name.replace(EMOJI_TAIL_RE, '').trim();
  for (let i = 0; i < 2; i++) {
    const m = name.match(/^([A-Za-z]{2})\s*[|:]\s*(.+)$/);
    if (!m || !COUNTRY_CODES.has(m[1].toUpperCase())) break;
    name = collapse(m[2]);
  }
  for (let i = 0; i < 2; i++) {
    const next = name.replace(QUALITY_TAG_RE, '').trim();
    if (next === name || !next) break;
    name = next;
  }
  return name;
}

/** Lower rank = better-looking stream (UHD beats HD beats nothing). */
export function qualityRank(rawName) {
  const s = String(rawName || '');
  for (let i = 0; i < QUALITY_TAGS.length; i++) {
    if (QUALITY_TAGS[i].test(s)) return i;
  }
  return /\bsd\b/i.test(s) ? 5 : 4;
}

/** The network "bug" a playlist channel name collapses to ("US| TSN4 HD" → "TSN4"). */
export function networkBugFor(rawName) {
  return normalizeChannel(stripDecorations(rawName));
}

/**
 * Build a lookup index over a parsed channel list.
 * @returns {{byNetwork: Map<string, number[]>, byToken: Map<string, number[]>}}
 */
export function buildChannelIndex(channels) {
  const byNetwork = new Map();
  const byToken = new Map();
  (channels || []).forEach((ch, i) => {
    const bug = networkBugFor(ch.name);
    if (bug && bug.length >= 2) push(byNetwork, bug, i);
    for (const token of tokenize(stripDecorations(ch.name))) push(byToken, token, i);
  });
  return { byNetwork, byToken };
}

function push(map, key, i) {
  const list = map.get(key);
  if (list) list.push(i);
  else map.set(key, [i]);
}

// Indexes are memoized per channel-list identity: the list is imported once
// and matched on every Game Detail open — don't rebuild a 4k-channel index
// each time.
const indexCache = new WeakMap();

function indexFor(channels) {
  if (!Array.isArray(channels)) return buildChannelIndex([]);
  let index = indexCache.get(channels);
  if (!index) {
    index = buildChannelIndex(channels);
    indexCache.set(channels, index);
  }
  return index;
}

function tokenize(stripped) {
  return collapse(stripped.toLowerCase())
    .split(/[^a-z0-9+]+/)
    .filter((t) => t.length >= 3 && !GENERIC_TOKENS.has(t));
}

/**
 * Match a game event to playlist channels, best first.
 *
 * Tier bases (lower = better): network bug 0 (preferred −50) > full team
 * name 20 > team abbr 40 > city 60. Ties inside a tier prefer higher-quality
 * streams and shorter names.
 *
 * @param {object} ev event with `channels` (bugs) and `away`/`home` teams
 * @param {Array<{name:string,url:string}>} channels parsed playlist channels
 * @param {{index?: object, limit?: number, preferred?: Record<string,string>}} [opts]
 * @returns {Array<{name:string,url:string,group:string,reason:'network'|'team',score:number,preferred:boolean,bug:string}>}
 */
export function matchChannels(ev, channels, opts = {}) {
  const { index = indexFor(channels), limit = 6, preferred = {} } = opts;
  if (!ev || !channels || !channels.length) return [];

  const best = new Map(); // channel index → match record

  const consider = (i, base, reason, bug) => {
    const ch = channels[i];
    const score = base + qualityRank(ch.name) * 2 + Math.min(ch.name.length / 200, 0.5);
    const prev = best.get(i);
    if (prev && prev.score <= score) return;
    best.set(i, {
      name: ch.name,
      url: ch.url,
      group: ch.group || '',
      reason,
      score,
      preferred: Boolean(bug && preferred[bug] === ch.name),
      bug: bug || '',
    });
  };

  // Tier 1 — network bug exact match ("FS1", "TSN4", "SN 3").
  const bugs = [...new Set((ev.channels || []).map(String).filter(Boolean))];
  for (const bug of bugs) {
    for (const i of index.byNetwork.get(bug) || []) {
      const base = preferred[bug] === channels[i].name ? -50 : 0;
      consider(i, base, 'network', bug);
    }
  }

  // Tier 2/3/4 — channels named for the teams or city.
  const teams = [ev.away, ev.home].filter(Boolean);
  const fullNames = teams.map((t) => collapse(String(t.name || '').toLowerCase())).filter((n) => n.length >= 5);
  const abbrs = teams.map((t) => collapse(String(t.abbr || '').toLowerCase())).filter((a) => a.length >= 3);
  const cities = teams
    .map((t) => collapse(String(t.name || '').toLowerCase()).split(' ')[0])
    .filter((c) => c.length >= 4 && !GENERIC_TOKENS.has(c));

  if (fullNames.length || abbrs.length || cities.length) {
    const candidateIdx = new Set();
    const probeTokens = new Set([
      ...fullNames.flatMap((n) => n.split(' ').filter((w) => w.length >= 4 && !GENERIC_TOKENS.has(w))),
      ...abbrs,
      ...cities,
    ]);
    for (const tok of probeTokens) {
      for (const i of index.byToken.get(tok) || []) candidateIdx.add(i);
    }

    for (const i of candidateIdx) {
      const strippedName = collapse(stripDecorations(channels[i].name).toLowerCase());
      for (const full of fullNames) {
        if (strippedName.includes(full)) { consider(i, 20, 'team', ''); break; }
      }
      if (best.get(i)?.reason === 'team') continue;
      for (const abbr of abbrs) {
        if (tokenize(stripDecorations(channels[i].name)).includes(abbr)) { consider(i, 40, 'team', ''); break; }
      }
      if (best.get(i)?.reason === 'team') continue;
      for (const city of cities) {
        if (strippedName.includes(city)) { consider(i, 60, 'team', ''); break; }
      }
    }
  }

  return [...best.values()]
    .sort((a, b) => a.score - b.score)
    .slice(0, Math.max(0, limit));
}
