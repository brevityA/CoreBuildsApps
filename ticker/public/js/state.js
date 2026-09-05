import { DEFAULT_LEAGUES, LEAGUES } from '/lib/scoreboard.mjs';

const KEY = 'coreline.v1';

export const REFRESH_CHOICES = [15, 30, 60, 120, 300, 600];
export const SPEED_MIN = 20;
export const SPEED_MAX = 120;

export const DEFAULTS = {
  leagues: [...DEFAULT_LEAGUES],
  feeds: [],
  sampleFeed: true,
  speed: 52,
  favorites: '',
  showFinals: true,
  wakeLock: true,
  theme: 'core',
  clockFmt: '12',
  mode: 'board',
  leagueFilter: 'ALL',
  refreshSec: 60,
  position: 'bottom',
  watchApps: {},
  playlist: { url: '', importedAt: 0, count: 0 },
  preferredChannels: {},
  overlay: false,
};

/**
 * Coerce a possibly-corrupt/legacy stored state into the current shape.
 * Never throws; unknown keys are dropped, known keys are type-checked and
 * clamped so the render loop can always trust them.
 */
export function sanitizeState(raw) {
  const out = { ...DEFAULTS, ...(raw && typeof raw === 'object' ? raw : {}) };

  const leagueIds = Object.keys(LEAGUES);
  if (Array.isArray(out.leagues)) {
    out.leagues = [...new Set(out.leagues.filter((id) => leagueIds.includes(id)))];
  } else {
    out.leagues = [...DEFAULT_LEAGUES];
  }

  if (!Array.isArray(out.feeds)) out.feeds = [];
  out.feeds = out.feeds
    .filter((f) => f && typeof f === 'object' && typeof f.url === 'string')
    .map((f) => ({ url: String(f.url), label: String(f.label || 'RSS').slice(0, 24) }))
    .slice(0, 20);

  out.sampleFeed = Boolean(out.sampleFeed);
  out.showFinals = Boolean(out.showFinals);
  out.wakeLock = Boolean(out.wakeLock);
  // overlay: accept booleans, or string "true"/"false" from legacy persisted data
  if (out.overlay === true || out.overlay === 'true') {
    out.overlay = true;
  } else {
    out.overlay = false;
  }

  out.speed = clampInt(out.speed, SPEED_MIN, SPEED_MAX, DEFAULTS.speed);
  out.refreshSec = REFRESH_CHOICES.includes(Number(out.refreshSec)) ? Number(out.refreshSec) : DEFAULTS.refreshSec;
  out.position = out.position === 'top' ? 'top' : 'bottom';

  out.favorites = typeof out.favorites === 'string' ? out.favorites : '';
  out.clockFmt = out.clockFmt === '24' ? '24' : '12';
  out.mode = out.mode === 'crawl' ? 'crawl' : 'board';
  out.theme = ['core', 'broadcast', 'stadium', 'mono'].includes(out.theme) ? out.theme : 'core';
  out.leagueFilter = typeof out.leagueFilter === 'string' ? out.leagueFilter : 'ALL';

  // watchApps: leagueId → installed app package id (or the string 'web').
  if (!out.watchApps || typeof out.watchApps !== 'object' || Array.isArray(out.watchApps)) {
    out.watchApps = {};
  }
  out.watchApps = Object.fromEntries(
    Object.entries(out.watchApps)
      .filter(([k, v]) => typeof k === 'string' && typeof v === 'string')
      .map(([k, v]) => [k, v.slice(0, 200)]),
  );

  // playlist: metadata only — the channel list itself is too big for the
  // main state blob and lives under its own key (see savePlaylistChannels).
  if (!out.playlist || typeof out.playlist !== 'object' || Array.isArray(out.playlist)) {
    out.playlist = { ...DEFAULTS.playlist };
  }
  out.playlist = {
    url: typeof out.playlist.url === 'string' ? out.playlist.url.slice(0, 500) : '',
    importedAt: Number.isFinite(Number(out.playlist.importedAt)) ? Number(out.playlist.importedAt) : 0,
    count: clampInt(out.playlist.count, 0, 1_000_000, 0),
  };

  // preferredChannels: network bug ("TSN4") → channel name ("US| TSN4 UHD").
  if (!out.preferredChannels || typeof out.preferredChannels !== 'object' || Array.isArray(out.preferredChannels)) {
    out.preferredChannels = {};
  }
  out.preferredChannels = Object.fromEntries(
    Object.entries(out.preferredChannels)
      .filter(([k, v]) => typeof k === 'string' && typeof v === 'string')
      .map(([k, v]) => [k.slice(0, 24), v.slice(0, 64)])
      .slice(0, 50),
  );

  return out;
}

function clampInt(value, min, max, fallback) {
  const n = Number(value);
  if (!Number.isFinite(n)) return fallback;
  return Math.max(min, Math.min(max, Math.round(n)));
}

export function loadState() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return { ...DEFAULTS, watchApps: { ...DEFAULTS.watchApps } };
    return sanitizeState(JSON.parse(raw));
  } catch {
    return { ...DEFAULTS, watchApps: { ...DEFAULTS.watchApps } };
  }
}

export function saveState(state) {
  try {
    localStorage.setItem(KEY, JSON.stringify(state));
  } catch {
    /* quota / private mode — non-fatal */
  }
}

export function cacheSlate(payload) {
  try {
    localStorage.setItem(`${KEY}.slate`, JSON.stringify({ at: Date.now(), payload }));
  } catch {
    /* quota */
  }
}

/**
 * Imported playlist channels live under their own key: a 4k-channel list
 * would crowd the main state blob against the localStorage quota.
 * Returns false when storage refused the write (quota) — caller keeps the
 * channels in memory for this session.
 */
export function savePlaylistChannels(channels) {
  try {
    const clean = (Array.isArray(channels) ? channels : [])
      .filter((c) => c && typeof c.name === 'string' && typeof c.url === 'string')
      .map((c) => ({ name: c.name.slice(0, 64), url: c.url.slice(0, 500), group: String(c.group || '').slice(0, 40) }))
      .slice(0, 4000);
    localStorage.setItem(`${KEY}.channels`, JSON.stringify(clean));
    return true;
  } catch {
    return false;
  }
}

export function readPlaylistChannels() {
  try {
    const raw = localStorage.getItem(`${KEY}.channels`);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((c) => c && typeof c.name === 'string' && typeof c.url === 'string')
      .map((c) => ({ name: String(c.name).slice(0, 64), url: String(c.url).slice(0, 500), group: String(c.group || '').slice(0, 40) }))
      .slice(0, 4000);
  } catch {
    return [];
  }
}

export function readCachedSlate() {
  try {
    const raw = localStorage.getItem(`${KEY}.slate`);
    return raw ? JSON.parse(raw).payload : null;
  } catch {
    return null;
  }
}
