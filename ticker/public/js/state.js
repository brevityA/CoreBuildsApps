import { DEFAULT_LEAGUES, LEAGUES } from '/lib/scoreboard.mjs';
import { VPN_CORNERS, VPN_OPACITY } from '/lib/vpn.mjs';
import { SCOREBUG_POSITIONS, SCOREBUG_OPACITY } from '/lib/scorebug.mjs';
import { resolveTicker } from '/lib/chrome.mjs';

const KEY = 'coreline.v1';

export const REFRESH_CHOICES = [15, 30, 60, 120, 300, 600];
export const SPEED_MIN = 20;
export const SPEED_MAX = 120;
export const OVERSCAN_MAX = 80;

export const DEFAULTS = {
  leagues: [...DEFAULT_LEAGUES],
  feeds: [],
  sampleFeed: true,
  speed: 52,
  favorites: '',
  showFinals: true,
  wakeLock: true,
  theme: 'core',
  // True-black surfaces for an OLED panel. Independent of `theme`: it
  // remaps only the surface ramp, so any accent can sit on it. On by default
  // (supporter feedback, 2026-09-29): OLED sets are what this audience is
  // buying, and on a backlit panel #000 is only slightly deeper than the
  // house near-black, so the default costs an LCD almost nothing.
  oled: true,
  // The scrolling ticker (chyron). Off for a fresh install; an install from
  // before this key existed keeps it on — see resolveTicker() in
  // lib/chrome.mjs. Crawl mode and the phone overlay always show it.
  ticker: false,
  clockFmt: '12',
  mode: 'board',
  leagueFilter: 'ALL',
  refreshSec: 60,
  position: 'bottom',
  watchApps: {},
  playlist: { url: '', importedAt: 0, count: 0 },
  // The TV guide (XMLTV), Android app only. `url` is one the viewer typed;
  // empty means use the guide links the playlist itself names
  // (`playlistUrls`, from its #EXTM3U url-tvg). Both carry the provider
  // account, so the UI only ever shows their host (maskUrl in lib/guide.mjs).
  guide: { url: '', playlistUrls: [], fetchedAt: 0, count: 0, error: '' },
  preferredChannels: {},
  overlay: false,
  // The scoreboard bug: one game at a time in a box at the top of the screen,
  // over other apps (Android). Shape only; whether it is up is asked of the
  // shell, like the dot.
  scoreBug: false,
  scoreBugPosition: 'top_center',
  scoreBugOpacity: SCOREBUG_OPACITY.default,
  // The VPN status dot: an always-on-top indicator of whether this device's
  // traffic is inside a tunnel. Shape only — the state itself is read live
  // from the Android shell, never persisted.
  vpnDot: false,
  vpnDotCorner: 'bottom_right',
  vpnDotOpacity: VPN_OPACITY.default,
  vpnDotHideWhenOk: false,
  vpnDotBlink: true,
  // How much of the frame the panel crops (classic TV overscan). Stored in px
  // and applied as the app shell's side padding; calibrated once per set.
  // null means the viewer has never calibrated, and the stylesheet's own
  // platform default is left in place — a phone gutter and a TV's 5% are
  // different numbers and only the stylesheet knows which one applies.
  overscan: null,
  // Announce score changes for starred teams.
  alerts: true,
  // First-run onboarding has been dismissed.
  onboarded: false,
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

  // vpnDot mirrors the overlay flag's tolerance: booleans, or the string
  // "true" that an older persisted blob may carry.
  out.vpnDot = out.vpnDot === true || out.vpnDot === 'true';
  out.scoreBug = out.scoreBug === true || out.scoreBug === 'true';
  out.scoreBugPosition = SCOREBUG_POSITIONS.includes(out.scoreBugPosition) ? out.scoreBugPosition : DEFAULTS.scoreBugPosition;
  out.scoreBugOpacity = clampInt(out.scoreBugOpacity, SCOREBUG_OPACITY.min, SCOREBUG_OPACITY.max, SCOREBUG_OPACITY.default);
  out.vpnDotCorner = VPN_CORNERS.includes(out.vpnDotCorner) ? out.vpnDotCorner : DEFAULTS.vpnDotCorner;
  out.vpnDotOpacity = clampInt(out.vpnDotOpacity, VPN_OPACITY.min, VPN_OPACITY.max, VPN_OPACITY.default);
  out.vpnDotHideWhenOk = Boolean(out.vpnDotHideWhenOk);
  out.vpnDotBlink = out.vpnDotBlink !== false;

  out.speed = clampInt(out.speed, SPEED_MIN, SPEED_MAX, DEFAULTS.speed);
  out.overscan = out.overscan == null
    ? null
    : clampInt(out.overscan, 0, OVERSCAN_MAX, null);
  out.alerts = out.alerts !== false;
  out.onboarded = Boolean(out.onboarded);
  out.refreshSec = REFRESH_CHOICES.includes(Number(out.refreshSec)) ? Number(out.refreshSec) : DEFAULTS.refreshSec;
  out.position = out.position === 'top' ? 'top' : 'bottom';

  out.favorites = typeof out.favorites === 'string' ? out.favorites : '';
  out.clockFmt = out.clockFmt === '24' ? '24' : '12';
  out.mode = out.mode === 'crawl' ? 'crawl' : 'board';
  out.theme = ['core', 'broadcast', 'stadium', 'mono'].includes(out.theme) ? out.theme : 'core';
  out.oled = Boolean(out.oled);
  out.ticker = resolveTicker(raw);
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

  if (!out.guide || typeof out.guide !== 'object' || Array.isArray(out.guide)) {
    out.guide = { ...DEFAULTS.guide };
  }
  const httpUrl = (u) => typeof u === 'string' && /^https?:\/\//i.test(u.trim());
  out.guide = {
    url: httpUrl(out.guide.url) ? out.guide.url.trim().slice(0, 500) : '',
    playlistUrls: (Array.isArray(out.guide.playlistUrls) ? out.guide.playlistUrls : [])
      .filter(httpUrl).map((u) => u.trim().slice(0, 500)).slice(0, 4),
    fetchedAt: Number.isFinite(Number(out.guide.fetchedAt)) ? Number(out.guide.fetchedAt) : 0,
    count: clampInt(out.guide.count, 0, 1_000_000, 0),
    error: typeof out.guide.error === 'string' ? out.guide.error.slice(0, 160) : '',
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
      .map(cleanChannel)
      .slice(0, 4000);
    localStorage.setItem(`${KEY}.channels`, JSON.stringify(clean));
    return true;
  } catch {
    return false;
  }
}

/** One stored channel. tvgId/tvgName are what the TV guide joins on. */
export function cleanChannel(c) {
  return {
    name: String(c.name).slice(0, 64),
    url: String(c.url).slice(0, 500),
    group: String(c.group || '').slice(0, 40),
    tvgId: String(c.tvgId || '').slice(0, 128),
    tvgName: String(c.tvgName || '').slice(0, 64),
  };
}

export function readPlaylistChannels() {
  try {
    const raw = localStorage.getItem(`${KEY}.channels`);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((c) => c && typeof c.name === 'string' && typeof c.url === 'string')
      .map(cleanChannel)
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
