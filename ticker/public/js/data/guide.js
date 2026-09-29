/**
 * The TV guide: when to refresh it, and folding it into the slate.
 *
 * Android app only — the import runs in the shell (Importer.kt) and the
 * judgement runs in lib/guide.mjs. This module holds the parsed guide for the
 * session, keeps it fresh (on open when it is 6 hours old or from another
 * day, then every 6 hours), and re-merges the slate when it changes.
 *
 * Guide links carry the provider account: they are stored like the playlist
 * link, and only ever shown or reported as a host (maskUrl).
 */

import { mergeGuide, mapGuideChannels, isLiveEvent, maskUrl, GUIDE_VERSION } from '/lib/guide.mjs';
import { store, persist } from '../core/store.js';
import { emit } from '../core/bus.js';
import { $, setHidden, setText } from '../core/dom.js';
import { canImportNatively, runNativeJob, readNativeJson } from './native-import.js';

const STALE_MS = 6 * 3600e3;
const CHECK_MS = 30 * 60e3;

let guide = null;
/** The slate as the scoreboards sent it, before the guide was folded in. */
let baseEvents = [];
let refreshing = false;
let timer = null;

export function guideSupported() {
  return canImportNatively();
}

/** The links the next refresh will use: the viewer's own, else the playlist's. */
export function guideUrls() {
  const g = store.state.guide;
  return g.url ? [g.url] : g.playlistUrls;
}

/**
 * The slate with the guide folded in. Called with every new slate; keeps the
 * unmerged copy so a guide refresh can re-merge without a network round trip.
 */
export function applyGuide(events) {
  baseEvents = Array.isArray(events) ? events : [];
  if (!guide || !store.playlistChannels.length) return baseEvents;
  try {
    return mergeGuide(baseEvents, liveGuide(), store.playlistChannels).events;
  } catch (err) {
    console.warn('core-line: guide merge failed', err?.name || 'error');
    return baseEvents;
  }
}

/**
 * The guide cut to live events on the viewer's channels, recomputed only when
 * the guide or the playlist changes. The slate refreshes every minute; the
 * guide changes every six hours, and re-judging 12,000 listings each minute
 * is work a TV box feels.
 */
let prepared = { guide: null, playlist: null, value: null };
function liveGuide() {
  if (prepared.guide === guide && prepared.playlist === store.playlistChannels) return prepared.value;
  const carriers = mapGuideChannels(guide, store.playlistChannels);
  const value = { ...guide, programmes: guide.programmes.filter((p) => carriers.has(p?.c) && isLiveEvent(p)) };
  prepared = { guide, playlist: store.playlistChannels, value };
  return value;
}

/** Re-merge after the guide or the playlist changed. */
export function remergeGuide() {
  store.events = baseEvents;
  emit('slate', { guide: true });
}

export function guideIsDue(now = Date.now()) {
  const at = store.state.guide.fetchedAt;
  if (!at) return true;
  return now - at >= STALE_MS || new Date(at).toDateString() !== new Date(now).toDateString();
}

/** Boot: read the last guide from disk, refresh it when due, then keep it fresh. */
export function initGuide() {
  if (!guideSupported()) return;
  loadFromDisk();
  if (guideUrls().length && guideIsDue()) refreshGuide(false);
  clearInterval(timer);
  timer = setInterval(() => {
    if (guideUrls().length && guideIsDue()) refreshGuide(false);
  }, CHECK_MS);
}

function loadFromDisk() {
  const data = readNativeJson('guide');
  guide = data && data.v === GUIDE_VERSION && Array.isArray(data.programmes) ? data : null;
  return guide;
}

export async function refreshGuide(manual = true) {
  if (!guideSupported()) return;
  const urls = guideUrls();
  if (!urls.length) {
    if (manual) emit('toast', 'Add a guide link first, or import a playlist that names one');
    return;
  }
  if (refreshing) return;
  refreshing = true;
  renderGuidePanel();
  try {
    const res = await runNativeJob((b) => b.startGuideImport(JSON.stringify(urls)));
    if (res.state === 'busy') {
      if (manual) emit('toast', res.message);
      return;
    }
    const prev = store.state.guide;
    if (res.state === 'done' && loadFromDisk()) {
      store.state.guide = { ...prev, fetchedAt: guide.fetchedAt || Date.now(), count: guide.programmes.length, error: '' };
      persist();
      remergeGuide();
      if (manual) emit('toast', `Guide updated · ${guide.programmes.length} sport listings`);
    } else {
      // Keep the last good guide on screen; say why this one failed.
      store.state.guide = { ...prev, error: res.message || 'The guide could not be read.' };
      persist();
      if (manual) emit('toast', `Guide failed: ${store.state.guide.error}`);
    }
  } finally {
    refreshing = false;
    renderGuidePanel();
  }
}

/** The playlist named guide links (url-tvg): remember them, and fetch if nothing else is set. */
export function notePlaylistGuideUrls(urls) {
  const list = (Array.isArray(urls) ? urls : []).filter((u) => /^https?:\/\//i.test(String(u))).slice(0, 4);
  store.state.guide = { ...store.state.guide, playlistUrls: list };
  persist();
  if (guideSupported() && list.length && !store.state.guide.url) refreshGuide(false);
}

export function saveGuideUrl() {
  const input = $('guideUrl');
  const url = input?.value.trim() || '';
  if (!/^https?:\/\//i.test(url)) { emit('toast', 'Paste an http(s) XMLTV link'); return; }
  store.state.guide = { ...store.state.guide, url: url.slice(0, 500), fetchedAt: 0, error: '' };
  persist();
  if (input) input.value = '';
  refreshGuide(true);
}

export function clearGuideUrl() {
  store.state.guide = { ...store.state.guide, url: '', fetchedAt: 0, error: '' };
  persist();
  if (store.state.guide.playlistUrls.length) refreshGuide(true);
  else renderGuidePanel();
}

/** Forget the guide entirely (the playlist was removed). */
export function dropGuide() {
  guide = null;
  store.state.guide = { url: store.state.guide.url, playlistUrls: [], fetchedAt: 0, count: 0, error: '' };
  persist();
  remergeGuide();
}

export function renderGuidePanel() {
  if (!$('guideBlock')) return;
  const supported = guideSupported();
  setHidden('guideControls', !supported);
  setHidden('guideWebNote', supported);
  if (!supported) return;

  const g = store.state.guide;
  const urls = guideUrls();
  setText('guideSource', g.url
    ? `Using your link: ${maskUrl(g.url)}`
    : urls.length ? `Using the playlist's guide: ${urls.map(maskUrl).join(', ')}`
      : 'No guide yet. Import a playlist that names one, or paste an XMLTV link.');
  let status = '';
  if (refreshing) status = 'Updating the guide…';
  else if (g.fetchedAt) {
    status = `${g.count} sport listings · updated ${new Date(g.fetchedAt).toLocaleString([], { weekday: 'short', hour: 'numeric', minute: '2-digit' })}`;
    if (g.error) status += ` · last try failed: ${g.error}`;
  } else if (g.error) status = `Failed: ${g.error}`;
  setText('guideStatus', status);
  setHidden('guideClearUrlBtn', !g.url);
  const btn = $('guideRefreshBtn');
  if (btn) btn.disabled = refreshing || !urls.length;
}
