/**
 * Watch handoff.
 *
 * Core Line is a guide, not a player. This module decides where a game opens:
 * the app the viewer assigned to that league, or the web page if they picked
 * "Web browser" or nothing at all.
 */

import { WATCH_WEB, sortAppsForPicker, espnWebUrl, watchChoiceFor } from '/lib/watch.mjs';
import { LEAGUES } from '/lib/scoreboard.mjs';
import { store, persist } from '../core/store.js';
import { emit } from '../core/bus.js';
import { $, esc, setText } from '../core/dom.js';
import { nativeBridge } from '../core/bridge.js';

export function renderWatchApps() {
  const el = $('watchList');
  if (!el) return;
  const leagues = store.state.leagues.length ? store.state.leagues : Object.keys(LEAGUES);
  el.innerHTML = leagues.map((id) => {
    const league = LEAGUES[id];
    if (!league) return '';
    const choice = watchChoiceFor(store.state.watchApps, league.label);
    const installedPkgs = new Set(store.installedApps.map((a) => a.pkg));
    const opts = [
      `<option value="${WATCH_WEB}" ${choice === WATCH_WEB ? 'selected' : ''}>Web browser</option>`,
      // Keep a stored choice that is neither 'web' nor a known installed app,
      // so the picker preserves it rather than silently resetting.
      (choice !== WATCH_WEB && !installedPkgs.has(choice))
        ? `<option value="${esc(choice)}" selected>${esc(choice)}</option>`
        : '',
      ...store.installedApps.map((a) =>
        `<option value="${esc(a.pkg)}" ${choice === a.pkg ? 'selected' : ''}>${esc(a.label)}</option>`),
    ].filter(Boolean).join('');
    return `
      <div class="watch-row">
        <span class="watch-league" style="--acc:${esc(league.accent)}">${esc(league.label)}</span>
        <select class="field focusable" data-watch-league="${esc(id)}" aria-label="App to open ${esc(league.label)} games">${opts}</select>
      </div>`;
  }).join('') || '<p class="hint">Enable leagues under Scoreboards to assign watch apps.</p>';

  el.querySelectorAll('select').forEach((s) => {
    s.addEventListener('change', () => {
      store.state.watchApps[s.dataset.watchLeague] = s.value;
      persist();
    });
  });

  setText('watchAppsStatus', store.installedApps.length
    ? `Detected ${store.installedApps.length} installed apps on this device.`
    : 'App list loads from the TV shell — install apps like ESPN, Fox Sports, or DAZN to pick them here.');
}

export async function loadInstalledApps() {
  try {
    const raw = nativeBridge()?.listLaunchableApps?.();
    if (!raw) return;
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return;
    store.installedApps = sortAppsForPicker(parsed);
    renderWatchApps();
  } catch {
    /* non-native (web) has no app list — keep Web browser only */
  }
}

/** Open the app (or web page) assigned to a game's league. */
export async function watchEvent(ev) {
  if (!ev) return;
  const choice = watchChoiceFor(store.state.watchApps, ev.league);
  const url = espnWebUrl(ev);
  const bridge = nativeBridge();
  if (choice !== WATCH_WEB && bridge?.openApp) {
    try {
      const ok = bridge.openApp(choice);
      emit('toast', ok ? 'Opening game in app…' : 'Could not open that app');
      return;
    } catch { /* fall through to web */ }
  }
  if (bridge?.openUrl) {
    try { bridge.openUrl(url); return; } catch { /* ignore */ }
  }
  try { window.open(url, '_blank', 'noopener'); } catch { /* ignore */ }
  emit('toast', 'Opening in browser');
}

/** Open a game's ESPN page directly (web fallback from Game Detail). */
export function openWebForEvent(ev) {
  if (!ev) return;
  const url = espnWebUrl(ev);
  const bridge = nativeBridge();
  if (bridge?.openUrl) {
    try { if (bridge.openUrl(url)) return; } catch { /* ignore */ }
  }
  try { window.open(url, '_blank', 'noopener'); } catch { /* ignore */ }
  emit('toast', 'Opening in browser');
}
