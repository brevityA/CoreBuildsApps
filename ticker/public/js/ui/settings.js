/**
 * The settings drawer.
 *
 * A fixed rail of sections on the left, the active section on the right — the
 * same two-pane grammar as the board, for the same reason: a vertical rail is
 * what a D-pad walks. Focusing a rail item activates its section, so walking
 * down the rail previews each one without pressing anything.
 *
 * applyChrome() is the single place where persisted settings are written back
 * into the DOM. Every control is set from state on open, so a drawer re-opened
 * after a settings change from the overlay or another tab can never show stale
 * values.
 */

import { LEAGUES } from '/lib/scoreboard.mjs';
import { isNativeShell } from '/lib/client-slate.mjs';
import { store, persist } from '../core/store.js';
import { on, emit } from '../core/bus.js';
import { $, $$, esc, clampInt, setHidden, setText } from '../core/dom.js';
import { nativeBridge } from '../core/bridge.js';
import { renderWatchApps } from '../data/watch.js';
import { renderChannelsPanel } from '../data/playlist.js';
import { renderUpdates, checkForUpdates, installUpdateFlow } from '../data/updates.js';
import { addFeed, removeFeed, moveFeed, moveLeague } from '../data/feeds.js';
import { renderTeamPicker } from './rail.js';
import { teamListFromEvents } from '../data/slate.js';
import { refresh, armRefreshTimer } from '../data/slate.js';

const SPEED_MIN = 20;
const SPEED_MAX = 120;
const OVERSCAN_MAX = 80;

/**
 * The default for --overscan is defined in tokens.css, because it differs by
 * form factor: a phone wants a small gutter, a television wants the 5% the
 * platform guidance specifies. Read it back from the cascade instead of
 * restating the number here, so there is one place it lives and a fresh
 * install follows whatever the stylesheet says for this screen.
 */
function cssOverscan() {
  try {
    const raw = getComputedStyle(document.documentElement).getPropertyValue('--overscan');
    const n = parseFloat(String(raw).trim());
    if (Number.isFinite(n)) return Math.round(n);
  } catch {
    // getComputedStyle is not guaranteed to work — headless harnesses with no
    // real cascade can throw rather than return. Fall through to the numbers
    // below, which mirror tokens.css.
  }
  return document.documentElement.hasAttribute('data-tv') ? 48 : 24;
}

/** The margin in effect: the viewer's calibration, else the platform default. */
export function currentOverscan() {
  const s = store.state;
  return s.overscan == null ? cssOverscan() : s.overscan;
}

let lastFocusBeforeDrawer = null;

export function initSettings() {
  on('feeds', renderFeeds);
}

export function applyChrome() {
  const root = document.documentElement;
  const s = store.state;
  root.dataset.theme = s.theme;
  root.dataset.mode = s.mode;
  root.dataset.position = s.position;
  // A null overscan means the viewer has never calibrated, so leave the
  // stylesheet's platform default in place rather than stamping a phone-sized
  // number over a television's 5%.
  if (s.overscan != null) root.style.setProperty('--overscan', `${s.overscan}px`);

  set($('sampleFeed'), 'checked', s.sampleFeed);
  set($('speed'), 'value', s.speed);
  set($('refreshSec'), 'value', String(s.refreshSec));
  set($('position'), 'value', s.position);
  set($('theme'), 'value', s.theme);
  set($('clockFmt'), 'value', s.clockFmt);
  set($('overscan'), 'value', currentOverscan());
  set($('favorites'), 'value', s.favorites);
  set($('showFinals'), 'checked', s.showFinals);
  set($('wakeLock'), 'checked', s.wakeLock);
  set($('alerts'), 'checked', s.alerts);
  setText('speedVal', `${s.speed} px/s`);
  setText('overscanVal', `${currentOverscan()} px`);

  renderLeagueToggles();
  renderFeeds();
  renderTeamPicker(teamListFromEvents());

  // Pairing and the floating ticker are Android-only; a browser has neither.
  setHidden('pairBox', !isNativeShell());
  const platform = isNativeShell() ? nativeBridge()?.overlayPlatform?.() : '';
  setHidden('overlayBlock', !isNativeShell() || platform === 'unsupported');
  if ($('overlayEnabled')) $('overlayEnabled').checked = Boolean(nativeBridge()?.overlayActive?.());
  updateOverlayHint(platform);
  renderChannelsPanel();
}

function set(el, prop, value) {
  if (el) el[prop] = value;
}

function updateOverlayHint(platform) {
  const hint = $('overlayHint');
  if (!hint || !isNativeShell()) return;
  if (platform === 'unsupported') {
    hint.textContent = 'Floating ticker is not available on Fire TV. The operating system blocks overlay windows on all Fire TV devices.';
    if ($('overlayEnabled')) $('overlayEnabled').disabled = true;
  } else if (globalThis.CORELINE_TV) {
    hint.textContent = 'Draws the crawl on top of every app. On Google TV or Shield, grant Display over other apps if the toggle asks. Stock Android TV often has no screen for that permission.';
  } else {
    hint.textContent = 'Draws the crawl on top of every app, edge to edge. Stop it from the notification or untick this box.';
  }
}

export function openDrawer(open) {
  const drawer = $('drawer');
  if (!drawer) return;
  if (open && !$('gameDetail').hidden) emit('detail:close');
  drawer.hidden = !open;
  if (open) {
    lastFocusBeforeDrawer = document.activeElement;
    const first = $('drawerRail')?.querySelector('.chip--row');
    if (globalThis.CORELINE_TV) {
      activateDrawerSection(first?.dataset.section || 'feeds');
      (first || drawer.querySelector('[data-action="close-settings"]'))?.focus();
    } else {
      activateDrawerSection('feeds');
      $('feedUrl')?.focus();
    }
  } else {
    emit('pair:stop');
    if (lastFocusBeforeDrawer && document.contains(lastFocusBeforeDrawer)) {
      lastFocusBeforeDrawer.focus();
    }
  }
}

export function activateDrawerSection(name) {
  $$('#drawerRail .chip--row').forEach((b) => {
    const active = b.dataset.section === name;
    b.classList.toggle('is-active', active);
    b.setAttribute('aria-pressed', active ? 'true' : 'false');
  });
  $$('.drawer__section').forEach((s) => {
    s.classList.toggle('is-active', s.dataset.panel === name);
  });
  if (name === 'watch') renderWatchApps();
  if (name === 'channels') renderChannelsPanel();
  if (name === 'updates') {
    renderUpdates();
    checkForUpdates(false);
  }
}

export function renderFeeds() {
  const host = $('feedList');
  if (!host) return;
  const feeds = store.state.feeds;
  host.innerHTML = feeds.map((feed, i) => `
    <li>
      <div class="feed-list__text"><b>${esc(feed.label)}</b><span>${esc(feed.url)}</span></div>
      <div class="row-actions">
        <button class="btn--mini focusable" data-action="feed-up" data-url="${esc(feed.url)}" aria-label="Move ${esc(feed.label)} up" ${i === 0 ? 'disabled' : ''}>&#9650;</button>
        <button class="btn--mini focusable" data-action="feed-down" data-url="${esc(feed.url)}" aria-label="Move ${esc(feed.label)} down" ${i === feeds.length - 1 ? 'disabled' : ''}>&#9660;</button>
        <button class="btn--ghost focusable" data-action="remove-feed" data-url="${esc(feed.url)}">Remove</button>
      </div>
    </li>`).join('')
    || '<li><span class="hint hint--small">No custom feeds yet — add one above or keep the sample on.</span></li>';
}

export function renderLeagueToggles() {
  const host = $('leagueToggles');
  if (!host) return;
  // Every known league is listed, not just the enabled ones: a league that is
  // off and invisible can never be turned on.
  host.innerHTML = Object.keys(LEAGUES).map((id) => {
    const league = LEAGUES[id];
    if (!league) return '';
    const checked = store.state.leagues.includes(id);
    const pos = store.state.leagues.indexOf(id);
    return `
      <div class="league-toggle">
        <label class="check" style="--acc:${esc(league.accent)}">
          <input class="focusable" type="checkbox" data-league-id="${esc(id)}" ${checked ? 'checked' : ''}>
          <span>${esc(league.label)}</span>
        </label>
        ${checked ? `
        <div class="row-actions">
          <button class="btn--mini focusable" data-action="league-up" data-league-id="${esc(id)}" aria-label="Move ${esc(league.label)} up" ${pos === 0 ? 'disabled' : ''}>&#9650;</button>
          <button class="btn--mini focusable" data-action="league-down" data-league-id="${esc(id)}" aria-label="Move ${esc(league.label)} down" ${pos === store.state.leagues.length - 1 ? 'disabled' : ''}>&#9660;</button>
        </div>` : ''}
      </div>`;
  }).join('');

  host.querySelectorAll('input[data-league-id]').forEach((input) => {
    input.addEventListener('change', () => {
      const id = input.dataset.leagueId;
      store.state.leagues = input.checked
        ? [...new Set([...store.state.leagues, id])]
        : store.state.leagues.filter((x) => x !== id);
      persist();
      renderLeagueToggles();
      refresh();
    });
  });
}

export function nudgeSpeed(delta) {
  store.state.speed = clampInt(store.state.speed + delta, SPEED_MIN, SPEED_MAX, 52);
  set($('speed'), 'value', store.state.speed);
  setText('speedVal', `${store.state.speed} px/s`);
  persist();
  emit('speed', store.state.speed);
}

export function nudgeOverscan(delta) {
  const px = clampInt(currentOverscan() + delta, 0, OVERSCAN_MAX, 0);
  store.state.overscan = px;
  document.documentElement.style.setProperty('--overscan', `${px}px`);
  set($('overscan'), 'value', px);
  setText('overscanVal', `${px} px`);
  setText('calibrateValue', `${px} px`);
  persist();
}

/** Wire every control in the drawer. Called once from app.js. */
export function wireSettings() {
  $('sampleFeed')?.addEventListener('change', (e) => {
    store.state.sampleFeed = e.target.checked;
    persist();
    refresh();
  });

  $('speed')?.addEventListener('input', (e) => {
    store.state.speed = clampInt(e.target.value, SPEED_MIN, SPEED_MAX, 52);
    setText('speedVal', `${store.state.speed} px/s`);
    persist();
    emit('speed', store.state.speed);
  });

  $('overscan')?.addEventListener('input', (e) => {
    const px = clampInt(e.target.value, 0, OVERSCAN_MAX, 0);
    store.state.overscan = px;
    document.documentElement.style.setProperty('--overscan', `${px}px`);
    setText('overscanVal', `${px} px`);
    persist();
  });

  $('refreshSec')?.addEventListener('change', (e) => {
    store.state.refreshSec = Number(e.target.value);
    persist();
    armRefreshTimer();
  });

  $('position')?.addEventListener('change', (e) => {
    store.state.position = e.target.value === 'top' ? 'top' : 'bottom';
    document.documentElement.dataset.position = store.state.position;
    persist();
    try { nativeBridge()?.setOverlayEdge?.(store.state.position); } catch { /* overlay not up */ }
  });

  $('theme')?.addEventListener('change', (e) => {
    store.state.theme = e.target.value;
    document.documentElement.dataset.theme = store.state.theme;
    persist();
  });

  $('clockFmt')?.addEventListener('change', (e) => {
    store.state.clockFmt = e.target.value === '24' ? '24' : '12';
    persist();
    emit('clock');
  });

  $('favorites')?.addEventListener('change', (e) => {
    store.state.favorites = e.target.value;
    persist();
    emit('state');
  });

  $('showFinals')?.addEventListener('change', (e) => {
    store.state.showFinals = e.target.checked;
    persist();
    emit('state');
  });

  $('alerts')?.addEventListener('change', (e) => {
    store.state.alerts = e.target.checked;
    persist();
  });

  $('wakeLock')?.addEventListener('change', (e) => {
    store.state.wakeLock = e.target.checked;
    persist();
    emit('wakelock');
  });

  $('overlayEnabled')?.addEventListener('change', (e) => {
    const bridge = nativeBridge();
    if (e.target.checked) {
      if (bridge?.overlayPlatform?.() === 'unsupported') {
        e.target.checked = false;
        emit('toast', 'Floating ticker is not supported on Fire TV');
        return;
      }
      const started = bridge?.startOverlay?.() === true;
      e.target.checked = bridge?.overlayActive?.() === true;
      if (!started) {
        emit('toast', globalThis.CORELINE_TV
          ? 'Enable “Display over other apps” for Core Line in Settings, then retick'
          : 'Allow “display over other apps” for Core Line, then retick');
      }
    } else {
      bridge?.stopOverlay?.();
    }
    store.state.overlay = Boolean(bridge?.overlayActive?.());
    persist();
  });

  // Focusing a rail item activates its section, so walking the rail with the
  // D-pad previews each section as you pass it.
  $('drawerRail')?.addEventListener('focusin', (event) => {
    const btn = event.target.closest('[data-section]');
    if (btn) activateDrawerSection(btn.dataset.section);
  });

  // Click the scrim to dismiss.
  $('drawer')?.addEventListener('click', (event) => {
    if (event.target.id === 'drawer') openDrawer(false);
  });

  on('install-update', installUpdateFlow);
  on('check-updates', () => checkForUpdates(true));
  on('feed:add', ({ url, label }) => {
    addFeed(url, label);
    const a = $('feedUrl'); const b = $('feedLabel');
    if (a) a.value = '';
    if (b) b.value = '';
    refresh(true);
  });
  on('feed:remove', (url) => { removeFeed(url); refresh(); });
  on('feed:up', (url) => { moveFeed(url, -1); refresh(); });
  on('feed:down', (url) => { moveFeed(url, 1); refresh(); });
  on('league:up', (id) => { moveLeague(id, -1); refresh(); });
  on('league:down', (id) => { moveLeague(id, 1); refresh(); });
}

export { addFeed, removeFeed, moveFeed, moveLeague };
