import { toTickerText, parseFeed } from '/lib/parser.mjs';
import { LEAGUES, LEAGUE_LABELS, SPORT_GROUPS, compareEvents, buildDemoSlate, mergeEvents } from '/lib/scoreboard.mjs';
import { WATCH_WEB, sortAppsForPicker, espnWebUrl, watchChoiceFor } from '/lib/watch.mjs';
import { matchChannels } from '/lib/playlist.mjs';
import { updateStatus as buildUpdateStatus } from '/lib/version.mjs';
import { buildClientSlate, isNativeShell, hydrateClientSlateRegistry, getClientSlateRegistry } from '/lib/client-slate.mjs';
import { isSafeFeedUrl } from '/lib/ssrf.mjs';
import { loadState, saveState, cacheSlate, readCachedSlate, REFRESH_CHOICES, SPEED_MIN, SPEED_MAX, DEFAULTS, savePlaylistChannels, readPlaylistChannels } from './state.js';
import { initTvNav } from './tv.js';
import { qrDataUrl } from './qr.js';
import { Ticker } from './ticker.js';
import { startWatchdog } from './watchdog.js';

const params = new URLSearchParams(location.search);
if (params.get('native') === '1') globalThis.CORELINE_NATIVE = true;
if (params.get('tv') === '1') globalThis.CORELINE_TV = true;
if (params.get('overlay') === '1') globalThis.CORELINE_OVERLAY = true;

// Boot guard for ancient WebViews (AUDIT.md A1): module support detected by
// the fact that this file runs at all.
if (window.__CORELINE_BOOT) {
  window.__CORELINE_BOOT.ready = true;
  clearTimeout(window.__CORELINE_BOOT.t);
}

const SAMPLE_FEED = { url: `${location.origin}/feeds/sample-sports.xml`, label: 'Sample' };
const LEAGUE_ORDER = ['ALL', 'LIVE', 'RSS', ...LEAGUE_LABELS];
const SPORT_LABELS = Object.fromEntries(
  SPORT_GROUPS.map((g) => [g.id, new Set(g.leagues.map((id) => LEAGUES[id]?.label).filter(Boolean))]),
);
const REFRESH_TIMEOUT_MS = 25_000;
const MAX_FEEDS = 20;

const $ = (id) => document.getElementById(id);

const state = loadState();

// Imported IPTV playlist (ScoreBox-style channel matching). The channel list
// is big, so it lives under its own storage key; state only keeps metadata.
let playlistChannels = readPlaylistChannels();
let detailEvent = null;
let detailMatches = [];
let lastFocusBeforeDetail = null;
let events = [];
let wakeLock = null;
let clockTimer = null;
let refreshTimer = null;
let refreshGen = 0;
let refreshing = false;
let pairTimer = null;
let lastFocusBeforeDrawer = null;
let installedApps = [];
let updateInfo = null;
let updateChecking = false;

const UPDATE_RELEASES_URL = 'https://api.github.com/repos/brevityA/CoreBuildsApps/releases?per_page=30';
const UPDATE_APK_NAME = 'coreline-release.apk';
let lastHealth = { demo: false, degraded: 0, stale: 0 };

let ticker = null;

init();

async function init() {
  document.documentElement.toggleAttribute('data-tv', Boolean(globalThis.CORELINE_TV));
  document.documentElement.toggleAttribute('data-overlay', Boolean(globalThis.CORELINE_OVERLAY));
  document.documentElement.dataset.position = state.position;
  hydrateClientSlateRegistry();
  applyChrome();
  bind();
  if (!globalThis.CORELINE_OVERLAY) initTvNav(document.getElementById('app'));

  ticker = new Ticker({
    track: $('crawl'),
    seqA: $('crawlA'),
    seqB: $('crawlB'),
    mask: $('crawl-mask'),
    speed: state.speed,
  });
  if (typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches) {
    ticker.setSpeed(Math.min(state.speed, 12)); // slow, still informative
  }

  startWatchdog({
    getProgress: () => ticker.progress(),
    isRunning: () => ticker.running,
    onStall: () => {
      console.warn('core-line: ribbon stalled — restarting loop');
      ticker.restart();
      tickClock();
    },
    onWake: () => {
      ticker.restart();
      refresh(false); // silent resume refresh (no toast spam on focus/page-show)
    },
  });

  tickClock();
  clockTimer = setInterval(tickClock, 1000);

  const cached = readCachedSlate();
  if (cached?.events?.length) {
    events = cached.events;
  } else {
    events = buildDemoSlate();
  }
  render();
  ticker.start();

  await refresh();
  armRefreshTimer();
  if (isNativeShell() && !globalThis.CORELINE_OVERLAY) loadInstalledApps();
  if ('serviceWorker' in navigator && !isNativeShell()) {
    navigator.serviceWorker.register('./sw.js').catch(() => {});
  }
}

function armRefreshTimer() {
  clearInterval(refreshTimer);
  refreshTimer = setInterval(refresh, state.refreshSec * 1000);
}

function bind() {
  document.body.addEventListener('click', (event) => {
    const btn = event.target.closest('[data-action]');
    if (!btn) return;
    const action = btn.dataset.action;
    if (action === 'settings') openDrawer(true);
    if (action === 'close-settings') openDrawer(false);
    if (action === 'refresh') refresh(true);
    if (action === 'mode') toggleMode();
    if (action === 'add-feed') addFeed();
    if (action === 'pair-start') startPair();
    if (action === 'pair-stop') stopPair();
    if (action === 'remove-feed') {
      state.feeds = state.feeds.filter((f) => f.url !== btn.dataset.url);
      persist();
      renderFeeds();
      refresh();
    }
    if (action === 'feed-up' || action === 'feed-down') moveFeed(btn.dataset.url, action === 'feed-up' ? -1 : 1);
    if (action === 'league-up' || action === 'league-down') moveLeague(btn.dataset.leagueId, action === 'league-up' ? -1 : 1);
    if (action === 'speed-up') nudgeSpeed(4);
    if (action === 'speed-down') nudgeSpeed(-4);
    if (action === 'toggle-team') toggleTeam(btn.dataset.abbr);
    if (action === 'toggle-card-fav') toggleCardFav(btn.dataset.id);
    if (action === 'watch') watchEventById(btn.dataset.id);
    if (action === 'watch-web') openWebForEvent(btn.dataset.id);
    if (action === 'game-detail') openGameDetail(btn.dataset.id);
    if (action === 'detail-close') closeGameDetail();
    if (action === 'open-channel') openMatchedChannel(Number(btn.dataset.mindex));
    if (action === 'open-channels-settings') {
      closeGameDetail();
      openDrawer(true);
      activateDrawerSection('channels');
    }
    if (action === 'playlist-import') importPlaylist();
    if (action === 'playlist-clear') clearPlaylist();
    if (action === 'drawer-section') activateDrawerSection(btn.dataset.section);
    if (action === 'check-updates') checkForUpdates(true);
    if (action === 'install-update') installUpdateFlow();
    if (action === 'filter') {
      state.leagueFilter = btn.dataset.league;
      persist();
      render();
    }
  });

  $('sampleFeed').addEventListener('change', () => {
    state.sampleFeed = $('sampleFeed').checked;
    persist();
    refresh();
  });
  $('speed').addEventListener('input', () => {
    state.speed = clampInt($('speed').value, SPEED_MIN, SPEED_MAX);
    onSpeedChanged();
  });
  $('favorites').addEventListener('change', () => {
    state.favorites = $('favorites').value;
    persist();
    render();
  });
  $('showFinals').addEventListener('change', () => {
    state.showFinals = $('showFinals').checked;
    persist();
    render();
  });
  $('wakeLock').addEventListener('change', () => {
    state.wakeLock = $('wakeLock').checked;
    persist();
    syncWakeLock();
  });
  $('theme').addEventListener('change', () => {
    state.theme = $('theme').value;
    persist();
    applyChrome();
  });
  $('clockFmt').addEventListener('change', () => {
    state.clockFmt = $('clockFmt').value;
    persist();
    tickClock();
  });
  $('refreshSec').addEventListener('change', () => {
    state.refreshSec = Number($('refreshSec').value);
    persist();
    armRefreshTimer();
  });
  $('position').addEventListener('change', () => {
    state.position = $('position').value;
    persist();
    document.documentElement.dataset.position = state.position;
  });

  $('overlayEnabled')?.addEventListener('change', () => {
    const on = $('overlayEnabled').checked;
    const platform = nativeBridge()?.overlayPlatform?.();
    if (on) {
      if (platform === 'unsupported') {
        $('overlayEnabled').checked = false;
        toast('Floating ticker is not supported on Fire TV');
        return;
      }
      const started = nativeBridge()?.startOverlay?.() === true;
      $('overlayEnabled').checked = nativeBridge()?.overlayActive?.() === true;
      if (!started) {
        if (globalThis.CORELINE_TV) {
          toast('Enable “Display over other apps” for Core Line in Settings, then retick');
        } else {
          toast('Allow “display over other apps” for Core Line, then retick');
        }
      }
    } else {
      nativeBridge()?.stopOverlay?.();
    }
    state.overlay = Boolean(nativeBridge()?.overlayActive?.());
    persist();
  });

  window.addEventListener('keydown', (event) => {
    if (event.target.matches('input, textarea, select')) return;
    const key = event.key.toLowerCase();
    if (key === 's') openDrawer(true);
    if (key === 't') toggleMode();
    if (key === 'r') refresh(true);
    if (key === 'w') {
      // While Game Detail is open, W targets the game in the modal — not a
      // background one.
      if (detailEvent) {
        if (detailEvent.away && detailEvent.home) watchEventById(detailEvent.id);
        return;
      }
      const featured = events.find((e) => e.status === 'live' && e.away && e.home)
        || events.find((e) => e.status === 'upcoming' && e.away && e.home);
      if (featured) watchEventById(featured.id);
    }
    if (key === 'f') document.documentElement.requestFullscreen?.().catch(() => {});
  });

  $('drawer').addEventListener('click', (event) => {
    if (event.target.id === 'drawer') openDrawer(false);
  });

  // Game Detail modal: click the backdrop to close (D-pad Back does the same).
  const gd = $('gameDetail');
  if (gd) {
    gd.addEventListener('click', (event) => {
      if (event.target.id === 'gameDetail') closeGameDetail();
    });
  }

  // TV sidebar behaviour: focusing a rail item selects its section, so
  // D-pad up/down through the rail shows each section as you pass it.
  $('drawerRail').addEventListener('focusin', (event) => {
    const btn = event.target.closest('[data-section]');
    if (btn) activateDrawerSection(btn.dataset.section);
  });
}

function onSpeedChanged() {
  document.documentElement.style.setProperty('--crawl-s', `${state.speed}s`); // kept for any CSS fallback
  $('speed').value = state.speed;
  $('speedVal').textContent = `${state.speed} px/s`;
  ticker?.setSpeed(state.speed);
  persist();
}

function nudgeSpeed(delta) {
  state.speed = clampInt(state.speed + delta, SPEED_MIN, SPEED_MAX);
  onSpeedChanged();
}

function clampInt(value, min, max) {
  const n = Number(value);
  if (!Number.isFinite(n)) return min;
  return Math.max(min, Math.min(max, Math.round(n)));
}

function moveFeed(url, delta) {
  const i = state.feeds.findIndex((f) => f.url === url);
  const j = i + delta;
  if (i < 0 || j < 0 || j >= state.feeds.length) return;
  const [item] = state.feeds.splice(i, 1);
  state.feeds.splice(j, 0, item);
  persist();
  renderFeeds();
  refresh();
}

function moveLeague(id, delta) {
  const i = state.leagues.indexOf(id);
  const j = i + delta;
  if (i < 0 || j < 0 || j >= state.leagues.length) return;
  const [item] = state.leagues.splice(i, 1);
  state.leagues.splice(j, 0, item);
  persist();
  renderLeagueToggles();
  refresh();
}

function applyChrome() {
  document.documentElement.dataset.theme = state.theme;
  document.documentElement.dataset.mode = state.mode;
  document.documentElement.dataset.position = state.position;
  $('sampleFeed').checked = state.sampleFeed;
  $('speed').value = state.speed;
  $('speedVal').textContent = `${state.speed} px/s`;
  $('favorites').value = state.favorites;
  $('showFinals').checked = state.showFinals;
  $('wakeLock').checked = state.wakeLock;
  $('theme').value = state.theme;
  $('clockFmt').value = state.clockFmt;
  $('refreshSec').value = String(state.refreshSec);
  $('position').value = state.position;
  renderLeagueToggles();
  renderFeeds();
  if ($('pairBox')) $('pairBox').hidden = !isNativeShell();
  if ($('overlayBlock')) {
    $('overlayBlock').hidden = !isNativeShell();
  }
  if ($('overlayEnabled')) {
    $('overlayEnabled').checked = Boolean(nativeBridge()?.overlayActive?.());
  }
  // Update overlay hint based on platform capabilities
  const overlayHint = $('overlayHint');
  if (overlayHint && isNativeShell()) {
    const platform = nativeBridge()?.overlayPlatform?.();
    if (platform === 'unsupported') {
      overlayHint.textContent = 'Floating ticker is not available on Fire TV. The operating system blocks overlay windows on all Fire TV devices.';
      if ($('overlayEnabled')) $('overlayEnabled').disabled = true;
    } else if (globalThis.CORELINE_TV) {
      overlayHint.textContent = 'Draws the crawl on top of every app. If the toggle does not work, grant the permission via Settings → Apps → Special app access → Display over other apps, or run: adb shell appops set dev.corebuilds.line SYSTEM_ALERT_WINDOW allow';
    } else {
      overlayHint.textContent = 'Draws the crawl on top of every app, edge to edge. Stop it from the notification or untick this box.';
    }
  }
  renderChannelsPanel();
  if (!globalThis.CORELINE_OVERLAY) syncWakeLock();
}

function persist() {
  saveState(state);
}

function openDrawer(open) {
  const drawer = $('drawer');
  if (open && !$('gameDetail').hidden) closeGameDetail(); // one modal at a time
  drawer.hidden = !open;
  if (open) {
    lastFocusBeforeDrawer = document.activeElement;
    document.body.classList.add('drawer-open');
    const rail = $('drawerRail');
    const firstRail = rail?.querySelector('.rail-item');
    if (globalThis.CORELINE_TV) {
      activateDrawerSection(firstRail?.dataset.section || 'feeds');
      (firstRail || drawer.querySelector('[data-action="close-settings"]'))?.focus();
    } else {
      activateDrawerSection('feeds');
      $('feedUrl').focus();
    }
  } else {
    document.body.classList.remove('drawer-open');
    stopPair();
    if (lastFocusBeforeDrawer && document.contains(lastFocusBeforeDrawer)) {
      lastFocusBeforeDrawer.focus();
    }
  }
}

function activateDrawerSection(name) {
  document.querySelectorAll('#drawerRail .rail-item').forEach((b) => {
    b.classList.toggle('is-active', b.dataset.section === name);
    b.setAttribute('aria-pressed', b.dataset.section === name ? 'true' : 'false');
  });
  document.querySelectorAll('.drawer-section').forEach((s) => {
    s.classList.toggle('is-active', s.dataset.panel === name);
  });
  if (name === 'watch') renderWatchApps();
  if (name === 'channels') renderChannelsPanel();
  if (name === 'updates') {
    renderUpdates();
    if (!updateInfo && !updateChecking) checkForUpdates(false);
  }
}

function currentVersion() {
  try {
    const v = nativeBridge()?.getVersion?.();
    return typeof v === 'string' && v ? v : null;
  } catch {
    return null;
  }
}

/** GitHub release bodies arrive as markdown; flatten to plain panel text. */
function cleanNotes(body) {
  return String(body || '')
    .replace(/^#+\s*/gm, '')
    .replace(/^\s*[-*]\s+/gm, '· ')
    .replace(/[*_`~]/g, '')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/\s{2,}/g, ' ')
    .trim();
}

function renderUpdates() {
  const el = $('updatePanel');
  if (!el) return;
  const native = isNativeShell();
  const current = currentVersion();
  const rows = [];

  if (current) {
    rows.push(`<div class="update-row"><span class="update-label">Current version</span><span class="update-value">${esc(current)}</span></div>`);
  } else {
    rows.push(`<div class="update-row"><span class="update-label">Build</span><span class="update-value">${native ? 'TV app' : 'Web build'}</span></div>`);
  }

  if (updateChecking) {
    rows.push(`<div class="update-note checking">Checking for updates…</div>`);
  } else if (updateInfo && updateInfo.ok) {
    if (updateInfo.newer) {
      rows.push(`<div class="update-banner">
        <div class="update-banner-title">Update ${esc(updateInfo.latest)} is available</div>
        ${updateInfo.notes ? `<div class="update-notes">${esc(cleanNotes(updateInfo.notes).slice(0, 300))}</div>` : ''}
      </div>`);
      if (native) {
        rows.push(`<button class="primary focusable" data-action="install-update">Download &amp; install ${esc(updateInfo.latest)}</button>`);
      } else {
        rows.push(`<div class="update-note">Updates install on the Android TV app (Settings → Updates).</div>`);
      }
    } else {
      rows.push(`<div class="update-note ok">You’re up to date${updateInfo.latest ? ` (latest ${esc(updateInfo.latest)})` : ''}.</div>`);
    }
  } else if (updateInfo && !updateInfo.ok) {
    rows.push(`<div class="update-note error">${esc(updateInfo.error || 'Update check failed.')}</div>`);
  } else {
    rows.push(`<div class="update-note">Not checked yet.</div>`);
  }

  rows.push(`<button class="ghost focusable" data-action="check-updates" ${updateChecking ? 'disabled' : ''}>Check for updates</button>`);
  el.innerHTML = rows.join('');
}

async function checkForUpdates(manual = false) {
  if (updateChecking) return;
  updateChecking = true;
  renderUpdates();
  try {
    const url = isNativeShell()
      ? `/api/proxy?url=${encodeURIComponent(UPDATE_RELEASES_URL)}`
      : UPDATE_RELEASES_URL;
    const signal = typeof AbortSignal?.timeout === 'function'
      ? AbortSignal.timeout(20000)
      : (() => { const ac = new AbortController(); setTimeout(() => ac.abort(), 20000); return ac.signal; })();
    const res = await fetch(url, { headers: { Accept: 'application/json' }, signal });
    if (!res.ok) throw new Error('http ' + res.status);
    const releases = await res.json();
    updateInfo = buildUpdateStatus(releases, currentVersion() || '0', UPDATE_APK_NAME);
  } catch (err) {
    updateInfo = { ok: false, error: 'Could not reach the update server' };
  }
  updateChecking = false;
  renderUpdates();
  if (manual) {
    toast(updateInfo?.newer ? 'Update available' : updateInfo?.ok ? 'You are up to date' : 'Update check failed');
  }
}

function installUpdateFlow() {
  const url = updateInfo?.apkUrl;
  if (!url) return;
  const bridge = nativeBridge();
  if (bridge?.installUpdate) {
    try {
      if (bridge.installUpdate(url)) {
        toast('Downloading update — the installer opens when ready');
        return;
      }
    } catch { /* fall back to browser */ }
  }
  if (bridge?.openUrl) {
    try {
      bridge.openUrl(`https://github.com/brevityA/CoreBuildsApps/releases/tag/${encodeURIComponent(updateInfo.tag || 'coreline-v' + updateInfo.latest)}`);
      return;
    } catch { /* ignore */ }
  }
  toast('Could not start the update');
}

/** Open the app (or web page) assigned to a game's league. */
async function watchEventById(id) {
  const ev = events.find((e) => e.id === id);
  if (!ev) return;
  const choice = watchChoiceFor(state.watchApps, ev.league);
  const url = espnWebUrl(ev);
  const bridge = nativeBridge();
  if (choice !== WATCH_WEB && bridge?.openApp) {
    try {
      const ok = bridge.openApp(choice);
      toast(ok ? 'Opening game in app…' : 'Could not open that app');
      return;
    } catch {
      /* fall through to web */
    }
  }
  if (bridge?.openUrl) {
    try { bridge.openUrl(url); return; } catch { /* ignore */ }
  }
  try { window.open(url, '_blank', 'noopener'); } catch { /* ignore */ }
  toast('Opening in browser');
}

/** Open a game's ESPN page directly (web fallback from Game Detail). */
function openWebForEvent(id) {
  const ev = events.find((e) => e.id === id);
  if (!ev) return;
  const url = espnWebUrl(ev);
  const bridge = nativeBridge();
  if (bridge?.openUrl) {
    try { if (bridge.openUrl(url)) return; } catch { /* ignore */ }
  }
  try { window.open(url, '_blank', 'noopener'); } catch { /* ignore */ }
  toast('Opening in browser');
}

/* ------------------------------------------------------------------ *
 * IPTV playlist — ScoreBox-style channel matching + handoff.
 * Core Line matches games to the user's own channels and opens them in
 * an external player; it never plays video itself.
 * ------------------------------------------------------------------ */

function renderChannelsPanel() {
  const input = $('playlistUrl');
  if (!input) return;
  if (state.playlist.url && document.activeElement !== input) input.value = state.playlist.url;
  const n = playlistChannels.length;
  const when = state.playlist.importedAt ? ` · imported ${new Date(state.playlist.importedAt).toLocaleDateString()}` : '';
  $('playlistStatus').textContent = n
    ? `${n} channels${when}${n !== state.playlist.count ? ' (cached)' : ''}`
    : 'No playlist imported yet.';
  const clearBtn = $('playlistClearBtn');
  if (clearBtn) clearBtn.hidden = !n && !state.playlist.url;
}

async function importPlaylist() {
  const url = $('playlistUrl')?.value.trim();
  if (!url) { toast('Paste your M3U link first'); return; }
  const btn = $('playlistImportBtn');
  if (btn) btn.disabled = true;
  $('playlistStatus').textContent = 'Importing…';
  try {
    const res = await fetch(`/api/playlist?url=${encodeURIComponent(url)}`);
    const data = await res.json().catch(() => ({ ok: false, error: 'bad response' }));
    if (!data.ok) {
      toast(`Import failed: ${data.error || 'unknown error'}`);
      return;
    }
    playlistChannels = data.channels || [];
    const stored = savePlaylistChannels(playlistChannels);
    state.playlist = { url, importedAt: Date.now(), count: data.count || playlistChannels.length };
    persist();
    toast(`Imported ${data.count || playlistChannels.length} channels${stored ? '' : ' — storage full, kept for this session'}`);
  } catch (err) {
    toast(`Import failed: ${err?.message || 'network error'}`);
  } finally {
    if (btn) btn.disabled = false;
    renderChannelsPanel();
  }
}

function clearPlaylist() {
  playlistChannels = [];
  savePlaylistChannels([]);
  state.playlist = { ...DEFAULTS.playlist };
  state.preferredChannels = {};
  persist();
  renderChannelsPanel();
  toast('Playlist removed');
}

function openGameDetail(id) {
  const ev = events.find((e) => e.id === id);
  if (!ev) return;
  detailEvent = ev;
  detailMatches = playlistChannels.length
    ? matchChannels(ev, playlistChannels, { limit: 6, preferred: state.preferredChannels })
    : [];
  lastFocusBeforeDetail = document.activeElement;
  $('gameDetailPanel').innerHTML = gameDetailHtml(ev, detailMatches, playlistChannels.length > 0);
  const overlay = $('gameDetail');
  overlay.hidden = false;
  overlay.querySelector('.focusable')?.focus();
}

function closeGameDetail() {
  const overlay = $('gameDetail');
  if (overlay.hidden) return;
  overlay.hidden = true;
  detailEvent = null;
  detailMatches = [];
  if (lastFocusBeforeDetail && document.contains(lastFocusBeforeDetail)) {
    lastFocusBeforeDetail.focus();
  } else {
    document.querySelector('.stage .focusable')?.focus();
  }
  lastFocusBeforeDetail = null;
}

function gameDetailHtml(ev, matches, hasPlaylist) {
  const badge = ev.status === 'live' ? 'live' : ev.status === 'final' ? 'final' : 'upcoming';
  const label = ev.status === 'live' ? (ev.detail || 'LIVE') : ev.status === 'final' ? 'FINAL' : (ev.detail || 'UPCOMING');
  const pills = (ev.channels || []).map((c) => `<span class="pill">${esc(c)}</span>`).join('');
  const teams = ev.away && ev.home
    ? `<div class="teams">${teamLine(ev.away, ev)}${teamLine(ev.home, ev)}</div>`
    : (ev.headline ? `<p class="hint">${esc(ev.headline)}</p>` : '');

  let channelBlock;
  if (!hasPlaylist) {
    channelBlock = `
      <p class="hint">Import your IPTV playlist to see which of your channels carry this game.</p>
      <button class="ghost focusable" data-action="open-channels-settings">Set up in Settings → Channels</button>`;
  } else if (!matches.length) {
    channelBlock = `
      <p class="hint">No channels matched ${esc((ev.channels || []).join(', ') || 'this game')}. Your provider may carry it under a different network name.</p>`;
  } else {
    channelBlock = matches.map((m, i) => `
      <button class="gd-row focusable" data-action="open-channel" data-mindex="${i}">
        <span class="gd-ch">${esc(m.name)}</span>
        ${m.group ? `<span class="gd-why">${esc(m.group)}</span>` : ''}
        <span class="gd-why">${m.reason === 'network' ? 'NETWORK' : 'TEAM'}</span>
        ${m.preferred ? '<span class="gd-star" title="Preferred channel">★</span>' : ''}
        <span class="gd-open">Open ▸</span>
      </button>`).join('');
  }

  return `
    <header class="gd-head">
      <div class="gd-meta">
        <span class="badge ${badge}">${esc(label)}</span>
        <span>${esc(ev.league || ev.feed || '')}</span>
      </div>
      <button class="icon-btn focusable" data-action="detail-close" aria-label="Close">✕</button>
    </header>
    ${teams}
    ${pills ? `<div class="gd-pills">${pills}</div>` : ''}
    <div class="gd-actions">
      ${ev.away && ev.home ? `<button class="watch-btn focusable" data-action="watch" data-id="${esc(ev.id)}">▶ Watch</button>` : ''}
      <button class="ghost focusable" data-action="watch-web" data-id="${esc(ev.id)}">Web page</button>
    </div>
    <div class="gd-section">Your channels</div>
    ${channelBlock}
    <div class="gd-foot">${esc(ev.venue || ev.feed || '')}</div>
  `;
}

/** Open a matched playlist channel in an external player; remember the choice. */
function openMatchedChannel(i) {
  const m = detailMatches[i];
  if (!m) return;
  if (m.bug) {
    state.preferredChannels[m.bug] = m.name;
    persist();
  }
  const bridge = nativeBridge();
  if (bridge?.openStream) {
    try {
      const ok = bridge.openStream(m.url);
      if (ok) { toast('Opening in player…'); return; }
      toast('No player found — try TiviMate or VLC');
    } catch { /* fall through to web */ }
  }
  try { window.open(m.url, '_blank', 'noopener'); toast('Opening stream link'); } catch { /* ignore */ }
  toast('Could not open stream');
}

function toggleMode() {
  state.mode = state.mode === 'crawl' ? 'board' : 'crawl';
  persist();
  applyChrome();
}

function addFeed(rawUrl, rawLabel) {
  const url = (rawUrl ?? $('feedUrl').value).trim();
  const label = (rawLabel ?? $('feedLabel').value).trim().slice(0, 24) || 'RSS';
  if (!url) return toast('Paste a feed URL first');
  if (state.feeds.length >= MAX_FEEDS) return toast(`At most ${MAX_FEEDS} feeds`);
  const safety = isSafeFeedUrl(url);
  if (!safety.ok) return toast(safety.reason);
  if (state.feeds.some((f) => f.url === safety.url)) return toast('Already added');
  state.feeds.push({ url: safety.url, label });
  if (!rawUrl) {
    $('feedUrl').value = '';
    $('feedLabel').value = '';
  }
  persist();
  renderFeeds();
  refresh(true);
  toast(`Added ${label}`);
}

function nativeBridge() {
  return globalThis.CoreLineNative || null;
}

function startPair() {
  const bridge = nativeBridge();
  if (!bridge?.startPair) {
    toast('Pairing is in the Android app — same Wi-Fi as the TV');
    return;
  }
  let info;
  try {
    info = JSON.parse(bridge.startPair());
  } catch {
    toast('Could not start pairing');
    return;
  }
  if (!info.ok || !info.url) {
    toast(info.error || 'No Wi-Fi address yet');
    return;
  }
  $('pairCard').hidden = false;
  $('pairUrl').textContent = info.url;
  $('pairCode').textContent = info.code || '';
  const qr = $('pairQr');
  const dataUrl = qrDataUrl(info.url, 6);
  if (dataUrl) {
    qr.src = dataUrl;
    qr.style.display = 'block';
  } else {
    qr.style.display = 'none';
  }
  clearInterval(pairTimer);
  pairTimer = setInterval(pollPairInbox, 1000);
}

function stopPair() {
  clearInterval(pairTimer);
  pairTimer = null;
  try { nativeBridge()?.stopPair?.(); } catch { /* ignore */ }
  if ($('pairCard')) $('pairCard').hidden = true;
}

function pollPairInbox() {
  const bridge = nativeBridge();
  if (!bridge?.takeInbox) return;
  let payload = '';
  try { payload = bridge.takeInbox() || ''; } catch { return; }
  if (!payload) return;
  try {
    const feed = JSON.parse(payload);
    if (feed.url) {
      addFeed(feed.url, feed.label);
      stopPair();
    }
  } catch { /* ignore malformed */ }
}

function renderFeeds() {
  $('feedList').innerHTML = state.feeds.map((feed, i) => `
    <li>
      <div class="feed-meta"><b>${esc(feed.label)}</b><span>${esc(feed.url)}</span></div>
      <div class="row-actions">
        <button class="ghost focusable" data-action="feed-up" data-url="${esc(feed.url)}" aria-label="Move ${esc(feed.label)} up" ${i === 0 ? 'disabled' : ''}>▲</button>
        <button class="ghost focusable" data-action="feed-down" data-url="${esc(feed.url)}" aria-label="Move ${esc(feed.label)} down" ${i === state.feeds.length - 1 ? 'disabled' : ''}>▼</button>
        <button class="ghost focusable" data-action="remove-feed" data-url="${esc(feed.url)}">Remove</button>
      </div>
    </li>
  `).join('') || '<li class="hint">No custom feeds yet — add one above or keep the sample on.</li>';
}

function renderLeagueToggles() {
  // Show every known league (not just the enabled ones) so a league can be
  // added later — e.g. college football, which was invisible before because it
  // wasn't in the default set and never appeared in this list to enable.
  const ids = Object.keys(LEAGUES);
  $('leagueToggles').innerHTML = ids.map((id) => {
    const league = LEAGUES[id];
    if (!league) return '';
    const checked = state.leagues.includes(id);
    const pos = state.leagues.indexOf(id);
    return `
      <div class="league-toggle">
        <label class="check">
          <input class="focusable" type="checkbox" data-league-id="${id}" ${checked ? 'checked' : ''}>
          <span>${league.label}</span>
        </label>
        ${checked ? `
        <div class="row-actions">
          <button class="ghost focusable" data-action="league-up" data-league-id="${id}" aria-label="Move ${league.label} up" ${pos === 0 ? 'disabled' : ''}>▲</button>
          <button class="ghost focusable" data-action="league-down" data-league-id="${id}" aria-label="Move ${league.label} down" ${pos === state.leagues.length - 1 ? 'disabled' : ''}>▼</button>
        </div>
        ` : ''}
      </div>
    `;
  }).join('');
  $('leagueToggles').querySelectorAll('input').forEach((input) => {
    input.addEventListener('change', () => {
      const id = input.dataset.leagueId;
      state.leagues = input.checked
        ? [...new Set([...state.leagues, id])]
        : state.leagues.filter((x) => x !== id);
      persist();
      renderLeagueToggles();
      refresh();
    });
  });
}

async function refresh(manual = false) {
  if (refreshing) return; // single-flight (AUDIT.md B3)
  refreshing = true;
  if (manual) toast('Refreshing slate…');
  const gen = ++refreshGen;
  try {
    const feeds = [
      ...state.feeds,
      ...(state.sampleFeed ? [SAMPLE_FEED] : []),
    ].slice(0, MAX_FEEDS);

    const work = isNativeShell()
      ? buildClientSlate({ leagues: state.leagues, feeds })
      : fetchSlateFromServer(state.leagues, feeds);
    const data = await Promise.race([work, timeout(REFRESH_TIMEOUT_MS)]);
    if (!data) throw new Error('refresh timed out');
    if (gen !== refreshGen) return; // superseded by a newer refresh

    events = data.events || [];
    cacheSlate(data);
    updateHealth(data);
    $('brandSub').textContent = data.demo
      ? 'Demo slate · live scoreboards unreachable'
      : `Updated ${new Date(data.generatedAt || Date.now()).toLocaleTimeString()}`;
    render();
    if (manual) toast(data.demo ? 'Showing demo slate' : `Loaded ${events.length} listings`);
  } catch (err) {
    if (gen !== refreshGen) return;
    const cached = readCachedSlate();
    if (cached?.events?.length) {
      events = cached.events;
      render();
      if (manual) toast('Could not refresh — showing last slate');
    } else {
      events = await localFallback();
      render();
      if (manual) toast('Offline slate');
    }
    updateHealth({ demo: false, health: { degraded: 1, stale: 0 } });
    console.warn('core-line refresh failed', err);
  } finally {
    refreshing = false;
  }
}

function timeout(ms) {
  return new Promise((resolve) => setTimeout(() => resolve(null), ms));
}

function updateHealth(data) {
  const demo = Boolean(data?.demo);
  const degraded = Number(data?.health?.degraded || 0) + Number(data?.feeds?.filter((f) => !f.ok).length || 0) + Number(data?.sources?.filter((s) => !s.ok).length || 0);
  const stale = Number(data?.health?.stale || 0) + Number(data?.feeds?.filter((f) => f.stale).length || 0);
  lastHealth = { demo, degraded: degraded > 0 ? degraded : 0, stale };
  paintHealth();
}

function paintHealth() {
  const el = $('health');
  if (!el) return;
  const { demo, degraded, stale } = lastHealth;
  let klass = 'ok';
  let text = 'All sources live';
  if (demo) {
    klass = 'demo';
    text = 'Demo slate — scoreboards unreachable';
  } else if (degraded > 0 && stale > 0) {
    klass = 'degraded';
    text = `${degraded} source${degraded === 1 ? '' : 's'} retrying · showing last-good`;
  } else if (degraded > 0) {
    klass = 'degraded';
    text = `${degraded} source${degraded === 1 ? '' : 's'} retrying`;
  }
  el.className = `health ${klass}`;
  el.title = text;
  el.setAttribute('aria-label', text);
  el.textContent = text;
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
  } catch { /* ignore */ }
  return mergeEvents([buildDemoSlate(), rss]);
}

function visibleEvents() {
  const favs = state.favorites.split(/[,\s]+/).map((s) => s.trim().toUpperCase()).filter(Boolean);
  return events
    .filter((ev) => state.showFinals || ev.status !== 'final')
    .filter((ev) => matchesFilter(ev, state.leagueFilter))
    .sort((a, b) => {
      const af = isFav(a, favs) ? 0 : 1;
      const bf = isFav(b, favs) ? 0 : 1;
      if (af !== bf) return af - bf;
      return compareEvents(a, b);
    });
}

function matchesFilter(ev, filter) {
  if (!filter || filter === 'ALL') return true;
  if (filter === 'LIVE') return ev.status === 'live';
  if (filter === 'RSS') return ev.source === 'rss';
  if (filter.startsWith('sport:')) return SPORT_LABELS[filter]?.has(ev.league) ?? false;
  if (filter.startsWith('feed:')) return ev.feed === filter.slice(5);
  return ev.league === filter;
}

function isFav(ev, favs) {
  if (!favs.length) return false;
  const bag = [ev.home?.abbr, ev.away?.abbr, ev.home?.name, ev.away?.name].filter(Boolean).map((s) => String(s).toUpperCase());
  return favs.some((f) => bag.some((b) => b.includes(f)));
}

function favSet() {
  return new Set(String(state.favorites || '').split(/[,\s]+/).map((s) => s.trim().toUpperCase()).filter(Boolean));
}

function applyFavorites(set) {
  state.favorites = [...set].join(', ');
  persist();
  $('favorites').value = state.favorites;
  render(); // re-sorts by favorite + refreshes the team picker
}

function teamListFromEvents() {
  // Follow the active filter tab so the picker stays short enough to walk
  // with a remote (college sports alone would otherwise list hundreds).
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

function renderTeamPicker() {
  const el = $('teamPicker');
  if (!el) return;
  const set = favSet();
  const teams = teamListFromEvents();
  el.innerHTML = teams.length
    ? teams.map((t) => `
      <button class="team-chip focusable ${set.has(t.abbr) ? 'on' : ''}" data-action="toggle-team" data-abbr="${esc(t.abbr)}" aria-pressed="${set.has(t.abbr)}" title="${esc(t.name)}">
        ${set.has(t.abbr) ? '★' : '☆'} ${esc(t.abbr)}
      </button>`).join('')
    : '<span class="hint">No teams yet — add a feed or wait for the next refresh.</span>';
}

function toggleTeam(abbr) {
  const key = String(abbr).toUpperCase();
  const set = favSet();
  if (set.has(key)) set.delete(key); else set.add(key);
  applyFavorites(set);
  // keep the just-toggled chip focused after the picker re-renders
  const chip = $('teamPicker')?.querySelector(`[data-abbr="${CSS.escape(key)}"]`);
  if (chip) chip.focus();
}

function renderWatchApps() {
  const el = $('watchList');
  if (!el) return;
  const leagues = state.leagues.length ? state.leagues : Object.keys(LEAGUES);
  el.innerHTML = leagues.map((id) => {
    const league = LEAGUES[id];
    if (!league) return '';
    const choice = watchChoiceFor(state.watchApps, league.label);
    const installedPkgs = new Set(installedApps.map((a) => a.pkg));
    const opts = [
      `<option value="${WATCH_WEB}" ${choice === WATCH_WEB ? 'selected' : ''}>Web browser</option>`,
      // If the stored choice is neither 'web' nor a known installed app,
      // still include it as a selected option so the picker preserves it.
      (choice !== WATCH_WEB && !installedPkgs.has(choice))
        ? `<option value="${esc(choice)}" selected>${esc(choice)}</option>`
        : '',
      ...installedApps.map((a) =>
        `<option value="${esc(a.pkg)}" ${choice === a.pkg ? 'selected' : ''}>${esc(a.label)}</option>`),
    ].filter(Boolean).join('');
    return `
      <div class="watch-row">
        <span class="watch-league" style="--acc:${esc(league.accent)}">${league.label}</span>
        <select class="focusable" data-watch-league="${id}" aria-label="App to open ${league.label} games">${opts}</select>
      </div>`;
  }).join('') || '<p class="hint">Enable leagues under Scoreboards to assign watch apps.</p>';
  el.querySelectorAll('select').forEach((s) => {
    s.addEventListener('change', () => {
      state.watchApps[s.dataset.watchLeague] = s.value;
      persist();
    });
  });
  const status = $('watchAppsStatus');
  if (status) {
    status.textContent = installedApps.length
      ? `Detected ${installedApps.length} installed apps on this device.`
      : 'App list loads from the TV shell — install apps like ESPN, Fox Sports, or DAZN to pick them here.';
  }
}

async function loadInstalledApps() {
  try {
    const raw = nativeBridge()?.listLaunchableApps?.();
    if (!raw) return;
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return;
    installedApps = sortAppsForPicker(parsed);
    renderWatchApps();
  } catch {
    /* non-native (web) has no app list — keep Web browser only */
  }
}

function cardFavTeams(ev) {
  return [ev.away?.abbr, ev.home?.abbr].filter(Boolean).map((s) => String(s).toUpperCase());
}

function toggleCardFav(id) {
  const ev = events.find((e) => e.id === id);
  if (!ev) return;
  const teams = cardFavTeams(ev);
  if (!teams.length) return;
  const set = favSet();
  const allFav = teams.every((t) => set.has(t));
  if (allFav) teams.forEach((t) => set.delete(t));
  else teams.forEach((t) => set.add(t));
  applyFavorites(set);
}

function accentFor(ev) {
  for (const league of Object.values(LEAGUES)) {
    if (league.label === ev.league) return league.accent;
  }
  return 'var(--line)';
}

function render() {
  const focusSnap = captureFocus();
  const list = visibleEvents();
  const live = events.filter((e) => e.status === 'live').length;
  const up = events.filter((e) => e.status === 'upcoming').length;
  const rss = events.filter((e) => e.source === 'rss').length;
  $('statLive').textContent = live;
  $('statUp').textContent = up;
  $('statRss').textContent = rss;
  $('chyron').classList.toggle('is-live', live > 0);
  $('bugLive').textContent = live ? `${live} LIVE` : 'LINE';

  renderFilters();
  renderHero(list);
  renderGrid(list);
  renderCrawl(list);
  const next = list.find((e) => e.status === 'live') || list.find((e) => e.status === 'upcoming');
  if ($('bigNext') && next) {
    $('bigNext').textContent = toTickerText(next);
  }
  $('empty').hidden = list.length > 0;
  $('grid').hidden = list.length === 0;
  restoreFocus(focusSnap);
  renderTeamPicker();
}

function captureFocus() {
  const el = document.activeElement;
  if (!el || !el.classList || !el.classList.contains('focusable')) return null;
  const container = el.closest('#leagues, #grid, #hero');
  if (!container) return null;
  const nodes = [...container.querySelectorAll('.focusable')];
  return { id: container.id, idx: nodes.indexOf(el) };
}

function restoreFocus(snap) {
  if (!snap || snap.idx < 0) return;
  const container = document.getElementById(snap.id);
  if (!container) return;
  const nodes = [...container.querySelectorAll('.focusable')];
  const target = nodes[Math.min(snap.idx, nodes.length - 1)];
  if (target) target.focus();
}

function renderFilters() {
  const counts = { ALL: events.length, LIVE: events.filter((e) => e.status === 'live').length, RSS: events.filter((e) => e.source === 'rss').length };
  for (const label of LEAGUE_LABELS) {
    counts[label] = events.filter((e) => e.league === label).length;
  }
  const chips = ['ALL', 'LIVE', 'RSS'].map((label) => filterChip(label, label, counts[label]));
  // Sport super-tabs ("Football" = NFL + NCAAF, …) sit before the league tabs.
  for (const g of SPORT_GROUPS) {
    const n = g.leagues.reduce((sum, id) => sum + (counts[LEAGUES[id]?.label] || 0), 0);
    if (n) chips.push(filterChip(g.id, g.label, n));
  }
  for (const label of LEAGUE_LABELS) {
    if (counts[label]) chips.push(filterChip(label, label, counts[label]));
  }
  // Each custom feed gets its own tab so a pasted feed can be viewed whole,
  // instead of being scattered across auto-detected league tabs (AUDIT A-group).
  const feedChips = state.feeds
    .map((f) => f.label)
    .filter((label) => events.some((e) => e.feed === label));
  for (const label of feedChips) {
    chips.push(filterChip(`feed:${label}`, `📡 ${label}`, events.filter((e) => e.feed === label).length));
  }
  $('leagues').innerHTML = chips.join('');
}

function filterChip(filterKey, display, count) {
  return `
    <button class="league-chip focusable" data-action="filter" data-league="${esc(filterKey)}" aria-pressed="${state.leagueFilter === filterKey}">
      ${esc(display)}${filterKey === 'ALL' ? '' : ` ${count || ''}`}
    </button>
  `;
}

function renderHero(list) {
  const featured = list.find((e) => e.status === 'live' && e.away && e.home);
  const hero = $('hero');
  if (!featured) {
    hero.hidden = true;
    hero.innerHTML = '';
    return;
  }
  hero.hidden = false;
  hero.innerHTML = `
    <article class="hero-card focusable" tabindex="0" data-action="game-detail" data-id="${esc(featured.id)}" style="--acc:${esc(accentFor(featured))}">
      <div>
        <div class="hero-meta">
          <span class="badge live">LIVE</span>
          <span>${esc(featured.league)}</span>
          <span>${esc(featured.detail || '')}</span>
        </div>
        <div class="teams">
          ${teamLine(featured.away, featured)}
          ${teamLine(featured.home, featured)}
        </div>
      </div>
      <div class="hero-side">
        <div class="pills">${(featured.channels || []).map((c) => `<span class="pill">${esc(c)}</span>`).join('')}</div>
        <button class="watch-btn focusable" data-action="watch" data-id="${esc(featured.id)}">▶ Watch</button>
        <div class="when">${esc(featured.venue || featured.feed || '')}</div>
      </div>
    </article>
  `;
}

function teamLine(team, event) {
  const win = event.status === 'final' && team.winner;
  return `
    <div class="team-row">
      <div class="abbr">${esc(team.abbr || '—')}</div>
      <div class="team-name">${esc(team.name || '')}</div>
      <div class="score ${win ? 'win' : ''}">${team.score ?? ''}</div>
    </div>
  `;
}

function renderGrid(list) {
  const cards = list.filter((e) => e.away && e.home);
  const headlines = list.filter((e) => !(e.away && e.home));
  $('grid').innerHTML = [
    ...cards.map(gameCard),
    ...headlines.map(headlineCard),
  ].join('');
}

function gameCard(ev) {
  const badge = ev.status === 'live' ? 'live' : ev.status === 'final' ? 'final' : 'upcoming';
  const label = ev.status === 'live' ? (ev.detail || 'LIVE') : ev.status === 'final' ? 'FINAL' : (ev.detail || 'UP');
  const favs = favSet();
  const teams = cardFavTeams(ev);
  const favOn = teams.length > 0 && teams.every((t) => favs.has(t));
  return `
    <article class="game focusable" tabindex="0" data-action="game-detail" data-id="${esc(ev.id)}" style="--acc:${esc(accentFor(ev))}">
      <div class="game-top">
        <span class="league-tag">${esc(ev.league || ev.feed || 'RSS')}</span>
        <div class="game-top-right">
          <span class="badge ${badge}">${esc(label)}</span>
          <button class="fav-star focusable ${favOn ? 'on' : ''}" data-action="toggle-card-fav" data-id="${esc(ev.id)}" aria-label="${favOn ? 'Unfavorite' : 'Favorite'} these teams" aria-pressed="${favOn}">★</button>
          <button class="watch-mini focusable" data-action="watch" data-id="${esc(ev.id)}" aria-label="Watch in app" title="Watch in app">▶</button>
        </div>
      </div>
      <div class="game-teams">
        <div class="gt ${ev.away?.winner ? 'win' : ''}"><span class="who">${esc(ev.away.abbr)}</span><span class="sc">${ev.away.score ?? ''}</span></div>
        <div class="gt ${ev.home?.winner ? 'win' : ''}"><span class="who">${esc(ev.home.abbr)}</span><span class="sc">${ev.home.score ?? ''}</span></div>
      </div>
      <div class="game-foot">
        <div class="channels">${(ev.channels || []).map((c) => `<span class="ch">${esc(c)}</span>`).join('') || '<span class="when">No channel listed</span>'}</div>
      </div>
    </article>
  `;
}

function headlineCard(ev) {
  return `
    <article class="game focusable" tabindex="0" data-action="game-detail" data-id="${esc(ev.id)}" style="--acc:${esc(accentFor(ev))}">
      <div class="game-top">
        <span class="league-tag">${esc(ev.league || ev.feed || 'RSS')}</span>
        <span class="badge ${ev.status}">${esc(ev.status.toUpperCase())}</span>
      </div>
      <div class="gt"><span class="who">${esc(ev.headline || ev.rawTitle || 'Listing')}</span></div>
      <div class="channels">${(ev.channels || []).map((c) => `<span class="ch">${esc(c)}</span>`).join('')}</div>
    </article>
  `;
}

function renderCrawl(list) {
  const html = (list.length ? list : [{ headline: 'Add an RSS feed in settings — Team vs Team · ESPN, TSN4, SN 3', channels: [], status: 'upcoming' }])
    .map((ev) => {
      const kind = ev.status === 'live' ? 'LIVE' : ev.status === 'final' ? 'FINAL' : 'UP';
      const klass = ev.status === 'live' ? 'k' : ev.status === 'final' ? 'k final' : 'k up';
      const channels = (ev.channels || []).join('  ');
      const body = ev.away && ev.home
        ? `${esc(ev.away.abbr)}${ev.status !== 'upcoming' && ev.away.score != null ? ` ${esc(ev.away.score)}-${esc(ev.home.score)} ` : ' vs '}${esc(ev.home.abbr)}`
        : esc(ev.headline || ev.rawTitle || toTickerText(ev));
      return `<span class="tick"><span class="${klass}">${kind}</span> ${body}${channels ? ` <span class="chs">${esc(channels)}</span>` : ''}</span>`;
    })
    .join('');
  ticker.setItems(html);
}

function tickClock() {
  const now = new Date();
  const opts = state.clockFmt === '24'
    ? { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }
    : { hour: 'numeric', minute: '2-digit' };
  $('clock').textContent = now.toLocaleTimeString([], opts);
  if ($('bigTime')) {
    $('bigTime').textContent = now.toLocaleTimeString([], { ...opts, second: undefined });
    $('bigDate').textContent = now.toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric' });
  }
}

async function syncWakeLock() {
  try { nativeBridge()?.setKeepAwake?.(Boolean(state.wakeLock)); } catch { /* no native bridge */ }
  try {
    if (state.wakeLock && navigator.wakeLock) {
      wakeLock = await navigator.wakeLock.request('screen');
    } else {
      await wakeLock?.release();
      wakeLock = null;
    }
  } catch { /* unsupported / denied */ }
}

function toast(message) {
  const el = $('toast');
  el.hidden = false;
  el.textContent = message;
  clearTimeout(toast._t);
  toast._t = setTimeout(() => { el.hidden = true; }, 2200);
}

function esc(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

// Expose a few internals for the manual/soak test harness (window-only).
window.__CORELINE__ = {
  getState: () => state,
  getEvents: () => events,
  getRegistry: () => getClientSlateRegistry(),
  refresh,
};
