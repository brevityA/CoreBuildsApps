/**
 * Core Line — composition root.
 *
 * This file owns no rendering and no fetching. It reads the URL, wires the
 * modules together through the bus, and maps input (clicks, remote keys) onto
 * intents. Everything else lives in core/, data/ and ui/.
 *
 * It used to be one 1,340-line file where every function could call every
 * other. That shape is why the TV pass had to be a second, parallel scale
 * ladder, and why the audit listed focus loss, drawer traps and slider bugs
 * as separate findings rather than one structural one.
 */

import { toTickerText } from '/lib/parser.mjs';
import { buildDemoSlate } from '/lib/scoreboard.mjs';
import { isNativeShell, hydrateClientSlateRegistry, getClientSlateRegistry } from '/lib/client-slate.mjs';
import { store, persist } from './core/store.js';
import { on, emit } from './core/bus.js';
import { $, setText, toggleAttr } from './core/dom.js';
import { nativeBridge } from './core/bridge.js';
import { cardFavTeams, favSet } from './core/format.js';
import {
  refresh as loadSlate, armRefreshTimer, visibleEvents, teamListFromEvents,
  paintHealth, applyFavorites,
} from './data/slate.js';
import { startPair, stopPair } from './data/pairing.js';
import { importPlaylist, clearPlaylist, hydrateNativePlaylist } from './data/playlist.js';
import { applyGuide, initGuide, refreshGuide, saveGuideUrl, clearGuideUrl } from './data/guide.js';
import { watchEvent, loadInstalledApps } from './data/watch.js';
import { addFeed } from './data/feeds.js';
import { initToasts } from './ui/toast.js';
import {
  createTicker, getTicker, setSpeed, startChyron, tickClock, renderCrawl, setBug,
} from './ui/chyron.js';
import { renderStats, renderMyTeams, renderFilters, renderTeamPicker, refocusTeam, setFilter } from './ui/rail.js';
import { renderBoard } from './ui/board.js';
import { initSearch, clearSearch, focusSearch } from './ui/search.js';
import { openGameDetail, closeGameDetail, openMatched, watchDetail, watchDetailWeb, getDetailEvent } from './ui/detail.js';
import {
  applyChrome, applyTicker, openDrawer, activateDrawerSection, wireSettings, initSettings,
  nudgeSpeed, nudgeOverscan, nudgeVpnDot, renderFeeds,
} from './ui/settings.js';
import { maybeShowOnboarding, nextStep, finish as finishOnboarding } from './ui/onboarding.js';
import { openCalibrate, closeCalibrate, isCalibrating } from './ui/calibrate.js';
import { noteScores } from './ui/alerts.js';
import { initTvNav } from './tv.js';
import { startWatchdog } from './watchdog.js';

/* ---- environment ------------------------------------------------------- */

const params = new URLSearchParams(location.search);
if (params.get('native') === '1') globalThis.CORELINE_NATIVE = true;
if (params.get('tv') === '1') globalThis.CORELINE_TV = true;
if (params.get('overlay') === '1') globalThis.CORELINE_OVERLAY = true;

// Boot guard for ancient WebViews (AUDIT A1): module support is detected by
// the fact that this file runs at all.
if (window.__CORELINE_BOOT) {
  window.__CORELINE_BOOT.ready = true;
  clearTimeout(window.__CORELINE_BOOT.t);
}

let wakeLock = null;

/* ---- render ------------------------------------------------------------ */

/**
 * Repaint everything the viewer can see.
 *
 * Called on new slate, on a settings change and on a filter change. Cheap
 * enough to do wholesale: the containers are small, and partial renders are
 * how a UI ends up showing yesterday's filter with today's games.
 */
function render() {
  if (globalThis.CORELINE_OVERLAY) {
    renderCrawl(visibleEvents());
    return;
  }
  const list = visibleEvents();

  renderStats();
  renderMyTeams();
  renderFilters();
  renderBoard(list);
  renderCrawl(list);
  renderTeamPicker(teamListFromEvents());

  const live = store.events.filter((e) => e.status === 'live').length;
  setBug(live);

  const next = list.find((e) => e.status === 'live') || list.find((e) => e.status === 'upcoming');
  if (next) setText('bigNext', toTickerText(next));
}

/* ---- bus wiring -------------------------------------------------------- */

function wireBus() {
  initToasts();
  initSettings();

  on('slate', () => {
    // Fold the TV guide in before anyone reads the slate: it names channels
    // the scoreboards left blank and adds the games they do not list.
    store.events = applyGuide(store.events);
    // Alerts first: they compare the new slate against the previous one, so
    // they have to run before anything else re-reads the store.
    noteScores(store.events);
    render();
  });
  on('state', () => {
    applyChrome();
    render();
  });
  on('filter', render);
  on('feeds', () => {
    renderFeeds();
    renderFilters();
  });
  on('speed', (px) => setSpeed(px));
  on('clock', tickClock);
  on('wakelock', syncWakeLock);
  on('detail:close', closeGameDetail);
  on('pair:stop', stopPair);
  on('pair:feed', ({ url, label }) => {
    if (addFeed(url, label)) loadSlate(true);
  });
}

/* ---- input ------------------------------------------------------------- */

const ACTIONS = {
  settings: () => openDrawer(true),
  'close-settings': () => openDrawer(false),
  refresh: () => loadSlate(true),
  mode: toggleMode,
  'add-feed': () => {
    const url = $('feedUrl')?.value;
    const label = $('feedLabel')?.value;
    if (addFeed(url, label)) {
      $('feedUrl').value = '';
      $('feedLabel').value = '';
      loadSlate(true);
    }
  },
  'pair-start': startPair,
  'pair-stop': stopPair,
  'remove-feed': (el) => emit('feed:remove', el.dataset.url),
  'feed-up': (el) => emit('feed:up', el.dataset.url),
  'feed-down': (el) => emit('feed:down', el.dataset.url),
  'league-up': (el) => emit('league:up', el.dataset.leagueId),
  'league-down': (el) => emit('league:down', el.dataset.leagueId),
  'speed-up': () => nudgeSpeed(4),
  'speed-down': () => nudgeSpeed(-4),
  'overscan-up': () => nudgeOverscan(4),
  'overscan-down': () => nudgeOverscan(-4),
  'vpn-dot-bright': () => nudgeVpnDot(5),
  'vpn-dot-dim': () => nudgeVpnDot(-5),
  calibrate: openCalibrate,
  'calibrate-done': () => { closeCalibrate(); },
  'toggle-team': (el) => { toggleTeam(el.dataset.abbr); refocusTeam(el.dataset.abbr); },
  'toggle-card-fav': (el) => toggleCardFav(el.dataset.id),
  watch: (el) => watchEvent(store.events.find((e) => e.id === el.dataset.id)),
  'watch-web': (el) => watchDetailWeb(store.events.find((e) => e.id === el.dataset.id)),
  'game-detail': (el) => openGameDetail(store.events.find((e) => e.id === el.dataset.id)),
  'detail-close': closeGameDetail,
  'open-channel': (el) => openMatched(el.dataset.mindex),
  'open-channels-settings': () => {
    closeGameDetail();
    openDrawer(true);
    activateDrawerSection('channels');
  },
  'playlist-import': importPlaylist,
  'playlist-clear': clearPlaylist,
  'guide-refresh': () => refreshGuide(true),
  'guide-save': saveGuideUrl,
  'guide-clear-url': clearGuideUrl,
  'drawer-section': (el) => activateDrawerSection(el.dataset.section),
  'check-updates': () => emit('check-updates'),
  'install-update': () => emit('install-update'),
  filter: (el) => setFilter(el.dataset.league),
  'fav-filter': (el) => focusTeam(el.dataset.abbr),
  'empty-clear': () => {
    clearSearch();
    setFilter('ALL');
  },
  'onboard-next': nextStep,
  'onboard-skip': () => { if (isCalibrating()) closeCalibrate(); else finishOnboarding(); },
  'onboard-add-feed': () => {
    const url = $('onboardFeedUrl')?.value;
    if (url) addFeed(url, $('onboardFeedUrl').dataset.label || 'RSS');
    finishOnboarding();
  },
};

function bindActions() {
  document.body.addEventListener('click', (event) => {
    const el = event.target.closest('[data-action]');
    if (!el) return;
    const action = ACTIONS[el.dataset.action];
    if (!action) return;
    // Nested controls resolve correctly because `closest` returns the
    // innermost [data-action]: the star and watch buttons inside a card win
    // over the card's own game-detail action.
    action(el);
  });

  // Click the scrim to dismiss the game detail.
  $('gameDetail')?.addEventListener('click', (event) => {
    if (event.target.id === 'gameDetail') closeGameDetail();
  });

  // A team logo that fails to load — a dead CDN, a VPN that blocks it, a
  // team with no art. The mark already falls back structurally (the monogram
  // is under the logo), so this only tidies up after the browsers that paint
  // a broken-image glyph, and drops the empty slot the compact form leaves.
  //
  // Capture phase on purpose: image `error` events do not bubble, but they do
  // propagate down, so one listener at the document covers every mark the
  // board will ever rebuild — including the ones the 12s rotation swaps in.
  document.addEventListener('error', (event) => {
    const img = event.target;
    if (img?.classList?.contains('mark__img')) img.closest('.mark')?.classList.add('is-broken');
  }, true);
}

function bindKeys() {
  window.addEventListener('keydown', (event) => {
    const tag = (event.target.tagName || '').toLowerCase();
    const typing = tag === 'input' || tag === 'textarea' || tag === 'select';

    if (event.key === '/' && !typing) {
      event.preventDefault();
      focusSearch();
      return;
    }
    if (typing) return;

    switch (event.key.toLowerCase()) {
      case 's':
        openDrawer(true);
        break;
      case 't':
        toggleMode();
        break;
      case 'r':
        loadSlate(true);
        break;
      case 'w': {
        // While Game Detail is open, W targets the game in the modal, not a
        // background one.
        const open = getDetailEvent();
        if (open) { watchDetail(); break; }
        const featured = store.events.find((e) => e.status === 'live' && e.away && e.home)
          || store.events.find((e) => e.status === 'upcoming' && e.away && e.home);
        if (featured) watchEvent(featured);
        break;
      }
      case 'f':
        document.documentElement.requestFullscreen?.().catch(() => {});
        break;
      default:
        break;
    }
  });
}

/* ---- behaviours -------------------------------------------------------- */

function toggleMode() {
  store.state.mode = store.state.mode === 'crawl' ? 'board' : 'crawl';
  persist();
  document.documentElement.dataset.mode = store.state.mode;
}

function focusTeam(abbr) {
  // Reuses the search box rather than inventing a second filter mechanism:
  // the query is visible, clearable, and works the same everywhere else.
  const key = String(abbr).toUpperCase();
  store.query = store.query.trim().toUpperCase() === key ? '' : key;
  const input = $('search');
  if (input) input.value = store.query;
  renderMyTeams();
  render();
}

function toggleTeam(abbr) {
  const key = String(abbr).toUpperCase();
  const set = favSet(store.state);
  if (set.has(key)) set.delete(key); else set.add(key);
  applyFavorites(set);
}

function toggleCardFav(id) {
  const ev = store.events.find((e) => e.id === id);
  if (!ev) return;
  const teams = cardFavTeams(ev);
  if (!teams.length) return;
  const set = favSet(store.state);
  if (teams.every((t) => set.has(t))) teams.forEach((t) => set.delete(t));
  else teams.forEach((t) => set.add(t));
  applyFavorites(set);
}

async function syncWakeLock() {
  try { nativeBridge()?.setKeepAwake?.(Boolean(store.state.wakeLock)); } catch { /* no bridge */ }
  try {
    if (store.state.wakeLock && navigator.wakeLock) {
      wakeLock = await navigator.wakeLock.request('screen');
    } else {
      await wakeLock?.release();
      wakeLock = null;
    }
  } catch { /* unsupported or denied */ }
}

/* ---- boot -------------------------------------------------------------- */

async function init() {
  toggleAttr('data-tv', Boolean(globalThis.CORELINE_TV));
  toggleAttr('data-overlay', Boolean(globalThis.CORELINE_OVERLAY));

  hydrateClientSlateRegistry();
  applyChrome();
  wireBus();
  wireSettings();
  initSearch();
  bindActions();
  bindKeys();
  if (!globalThis.CORELINE_OVERLAY) initTvNav($('app'));

  createTicker();
  startWatchdog({
    getProgress: () => getTicker()?.progress() ?? 0,
    isRunning: () => Boolean(getTicker()?.running),
    onStall: () => {
      console.warn('core-line: ribbon stalled — restarting loop');
      getTicker()?.restart();
      tickClock();
    },
    onWake: () => {
      // Restart only a ticker that is meant to be on: restart() also starts
      // one that the viewer has turned off.
      if (!document.documentElement.hasAttribute('data-no-ticker')) getTicker()?.restart();
      loadSlate(false); // silent resume refresh — no toast spam on focus
    },
  });

  // Paint immediately from cache or the demo slate so the crawl is never
  // empty while the first network round-trip is in flight.
  store.events = buildDemoSlate();
  render();
  startChyron();
  applyTicker(); // stops the loop startChyron began if the ticker is off

  // The guide and the shell's copy of the playlist are read before the first
  // slate lands, so that slate is already merged rather than the next one.
  if (isNativeShell() && !globalThis.CORELINE_OVERLAY) {
    hydrateNativePlaylist();
    initGuide();
  }

  await loadSlate();
  armRefreshTimer();

  if (isNativeShell() && !globalThis.CORELINE_OVERLAY) loadInstalledApps();
  if (!globalThis.CORELINE_OVERLAY) syncWakeLock();
  if ('serviceWorker' in navigator && !isNativeShell()) {
    navigator.serviceWorker.register('./sw.js').catch(() => {});
  }
  if (!globalThis.CORELINE_OVERLAY) maybeShowOnboarding();

  paintHealth();
}

// Expose a few internals for the manual/soak test harness (window-only).
window.__CORELINE__ = {
  getState: () => store.state,
  getEvents: () => store.events,
  getRegistry: () => getClientSlateRegistry(),
  refresh: loadSlate,
  render,
};

init();
