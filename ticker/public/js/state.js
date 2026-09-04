const KEY = 'coreline.v1';

export const DEFAULTS = {
  leagues: ['mlb', 'nfl', 'nba', 'nhl', 'epl', 'mls', 'wnba'],
  feeds: [],
  sampleFeed: true,
  speed: 80,
  favorites: '',
  showFinals: true,
  wakeLock: true,
  theme: 'core',
  clockFmt: '12',
  mode: 'board',
  leagueFilter: 'ALL',
  refreshSec: 60,
  position: 'bottom',
};

const VALID_REFRESH = [15, 30, 60, 120, 300, 600];
const VALID_POSITION = ['bottom', 'top'];

function sanitize(raw) {
  const s = { ...DEFAULTS, ...raw };
  if (!Array.isArray(s.leagues)) s.leagues = DEFAULTS.leagues;
  if (!Array.isArray(s.feeds)) s.feeds = [];
  s.feeds = s.feeds.filter((f) => f && typeof f.url === 'string').slice(0, 20);
  s.speed = Math.max(1, Math.min(200, Number(s.speed) || DEFAULTS.speed));
  if (!VALID_REFRESH.includes(s.refreshSec)) s.refreshSec = DEFAULTS.refreshSec;
  if (!VALID_POSITION.includes(s.position)) s.position = DEFAULTS.position;
  return s;
}

export function loadState() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return { ...DEFAULTS };
    return sanitize(JSON.parse(raw));
  } catch {
    return { ...DEFAULTS };
  }
}

export function saveState(state) {
  localStorage.setItem(KEY, JSON.stringify(state));
}

export function cacheSlate(payload) {
  try {
    localStorage.setItem(`${KEY}.slate`, JSON.stringify({ at: Date.now(), payload }));
  } catch { /* quota */ }
}

export function readCachedSlate() {
  try {
    const raw = localStorage.getItem(`${KEY}.slate`);
    return raw ? JSON.parse(raw).payload : null;
  } catch {
    return null;
  }
}
