/**
 * The slate: fetching, filtering, and source health.
 *
 * Everything that talks to the network lives here. The UI modules never fetch;
 * they render whatever is in the store and emit intents.
 *
 * Three behaviours are load-bearing and were hard-won (AUDIT B1-B3, C5):
 *
 *   - Single flight. A refresh that is already running is skipped, so a slow
 *     cycle can never overlap the next tick.
 *   - Generation guard. A slow response that lands after a newer refresh has
 *     started is discarded rather than applied.
 *   - Never blank. On failure the app falls back to the cached slate, then to
 *     the bundled sample feed, then to the demo slate. The crawl always has
 *     something to say.
 */

import { buildClientSlate, isNativeShell } from '/lib/client-slate.mjs';
import { compareEvents, buildDemoSlate, mergeEvents, LEAGUES, LEAGUE_LABELS, SPORT_GROUPS } from '/lib/scoreboard.mjs';
import { parseFeed } from '/lib/parser.mjs';
import { matchesQuery } from '/lib/query.mjs';
import { cacheSlate, readCachedSlate } from '../state.js';
import { store, persist } from '../core/store.js';
import { emit } from '../core/bus.js';
import { favSet, isFav } from '../core/format.js';
import { $, timeout, setText } from '../core/dom.js';

export const MAX_FEEDS = 20;
const REFRESH_TIMEOUT_MS = 25_000;

const SAMPLE_FEED = { url: `${location.origin}/feeds/sample-sports.xml`, label: 'Sample' };

export const LEAGUE_ORDER = ['ALL', 'LIVE', 'RSS', ...LEAGUE_LABELS];

/** sport-group id -> the set of league labels it covers. */
export const SPORT_LABELS = Object.fromEntries(
  SPORT_GROUPS.map((g) => [g.id, new Set(g.leagues.map((id) => LEAGUES[id]?.label).filter(Boolean))]),
);

let refreshTimer = null;
let refreshGen = 0;
let refreshing = false;
let lastHealth = { demo: false, degraded: 0, stale: 0 };

export function armRefreshTimer() {
  clearInterval(refreshTimer);
  refreshTimer = setInterval(() => refresh(false), store.state.refreshSec * 1000);
}

export function activeFeeds() {
  return [
    ...store.state.feeds,
    ...(store.state.sampleFeed ? [SAMPLE_FEED] : []),
  ].slice(0, MAX_FEEDS);
}

export async function refresh(manual = false) {
  if (refreshing) return;
  refreshing = true;
  emit('refresh:start', { manual });
  if (manual) emit('toast', 'Refreshing slate…');

  const gen = ++refreshGen;
  try {
    const feeds = activeFeeds();
    const work = isNativeShell()
      ? buildClientSlate({ leagues: store.state.leagues, feeds })
      : fetchSlateFromServer(store.state.leagues, feeds);

    const data = await Promise.race([work, timeout(REFRESH_TIMEOUT_MS)]);
    if (!data) throw new Error('refresh timed out');
    if (gen !== refreshGen) return; // superseded

    store.events = data.events || [];
    cacheSlate(data);
    updateHealth(data);
    setText('brandSub', data.demo
      ? 'Demo slate · live scoreboards unreachable'
      : `Updated ${new Date(data.generatedAt || Date.now()).toLocaleTimeString()}`);

    emit('slate', { manual, demo: Boolean(data.demo) });
    if (manual) {
      emit('toast', data.demo ? 'Showing demo slate' : `Loaded ${store.events.length} listings`);
    }
  } catch (err) {
    if (gen !== refreshGen) return;
    const cached = readCachedSlate();
    if (cached?.events?.length) {
      store.events = cached.events;
      emit('slate', { manual, stale: true });
      if (manual) emit('toast', 'Could not refresh — showing last slate');
    } else {
      store.events = await localFallback();
      emit('slate', { manual, offline: true });
      if (manual) emit('toast', 'Offline slate');
    }
    updateHealth({ demo: false, health: { degraded: 1, stale: 0 } });
    console.warn('core-line refresh failed', err);
  } finally {
    refreshing = false;
    emit('refresh:end');
  }
}

async function fetchSlateFromServer(leagues, feeds) {
  const query = new URLSearchParams({
    leagues: leagues.join(','),
    feeds: feeds.map((f) => `${encodeURIComponent(f.url)}|${encodeURIComponent(f.label)}`).join(','),
  });
  const res = await fetch(`/api/slate?${query}`);
  if (!res.ok) throw new Error(`slate ${res.status}`);
  return res.json();
}

async function localFallback() {
  let rss = [];
  try {
    const xml = await fetch('./feeds/sample-sports.xml').then((r) => r.text());
    rss = parseFeed(xml, { source: 'rss', label: 'Sample' });
  } catch { /* no bundled feed reachable */ }
  return mergeEvents([buildDemoSlate(), rss]);
}

/* ---- source health ---------------------------------------------------- */

export function updateHealth(data) {
  const demo = Boolean(data?.demo);
  const bad = (list) => Number(list?.filter((x) => x && !x.ok).length || 0);
  const staleOf = (list) => Number(list?.filter((x) => x && x.stale).length || 0);
  const degraded = Number(data?.health?.degraded || 0) + bad(data?.feeds) + bad(data?.sources);
  const stale = Number(data?.health?.stale || 0) + staleOf(data?.feeds);
  lastHealth = { demo, degraded: Math.max(degraded, 0), stale };
  paintHealth();
}

export function paintHealth() {
  const el = $('health');
  if (!el) return;
  const { demo, degraded, stale } = lastHealth;
  let klass = 'is-ok';
  let text = 'All sources live';
  if (demo) {
    klass = 'is-demo';
    text = 'Demo slate — scoreboards unreachable';
  } else if (degraded > 0 && stale > 0) {
    klass = 'is-degraded';
    text = `${degraded} source${degraded === 1 ? '' : 's'} retrying · showing last-good`;
  } else if (degraded > 0) {
    klass = 'is-degraded';
    text = `${degraded} source${degraded === 1 ? '' : 's'} retrying`;
  }
  el.className = `health ${klass}`;
  el.title = text;
  el.setAttribute('aria-label', text);
  el.textContent = text;
}

/* ---- filtering -------------------------------------------------------- */

/** Does an event survive the current league tab? */
export function matchesFilter(ev, filter) {
  if (!filter || filter === 'ALL') return true;
  if (filter === 'LIVE') return ev.status === 'live';
  if (filter === 'RSS') return ev.source === 'rss';
  if (filter.startsWith('sport:')) return SPORT_LABELS[filter]?.has(ev.league) ?? false;
  if (filter.startsWith('feed:')) return ev.feed === filter.slice(5);
  return ev.league === filter;
}

/**
 * The events the board should show right now: finals filtered by setting,
 * then the league tab, then the search box, then sorted — favourites first,
 * then by league order, then by status (compareEvents).
 */
export function visibleEvents() {
  const favs = favSet(store.state);
  const filter = store.state.leagueFilter;
  return store.events
    .filter((ev) => store.state.showFinals || ev.status !== 'final')
    .filter((ev) => matchesFilter(ev, filter))
    .filter((ev) => matchesQuery(ev, store.query))
    .sort((a, b) => {
      const af = isFav(a, favs) ? 0 : 1;
      const bf = isFav(b, favs) ? 0 : 1;
      if (af !== bf) return af - bf;
      return compareEvents(a, b);
    });
}

/** Every event on the crawl, regardless of the board's tab. */
export function crawlEvents() {
  const list = store.events.filter((ev) => store.state.showFinals || ev.status !== 'final');
  return list.length ? list : [];
}

/** Abbreviations present in the current view, for the favourites picker. */
export function teamListFromEvents() {
  // Follows the active filter so the picker stays short enough to walk with a
  // remote — college sports alone would otherwise list hundreds.
  const seen = new Map();
  for (const ev of visibleEvents()) {
    for (const team of [ev.away, ev.home]) {
      if (!team?.abbr) continue;
      const key = String(team.abbr).toUpperCase();
      if (!seen.has(key)) seen.set(key, { abbr: key, name: team.name || key });
    }
  }
  return [...seen.values()].sort((a, b) => a.abbr.localeCompare(b.abbr)).slice(0, 100);
}

export function applyFavorites(set) {
  store.state.favorites = [...set].join(', ');
  persist();
  const input = $('favorites');
  if (input) input.value = store.state.favorites;
  emit('state');
}

export { buildDemoSlate, isNativeShell };
