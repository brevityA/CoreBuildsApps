import { loadState, readCachedSlate } from './state.js';
import { buildClientSlate, hydrateClientSlateRegistry, isNativeShell } from '/lib/client-slate.mjs';
import { compareEvents, buildDemoSlate, mergeEvents } from '/lib/scoreboard.mjs';
import { parseFeed } from '/lib/parser.mjs';
import { matchesFavorite } from '/lib/favorites.mjs';
import { Ticker } from './ticker.js';
import { startWatchdog } from './watchdog.js';

const params = new URLSearchParams(location.search);
if (params.get('native') === '1') globalThis.CORELINE_NATIVE = true;
if (params.get('tv') === '1') document.documentElement.setAttribute('data-tv', '');

const SAMPLE = { url: `${location.origin}/feeds/sample-sports.xml`, label: 'Sample' };
const $ = (id) => document.getElementById(id);

let state = loadState();
let events = [];
let refreshing = false;
let ticker = null;

hydrateClientSlateRegistry();
reportEdge();
window.addEventListener('storage', (event) => {
  if (event.key === 'coreline.v1') {
    state = loadState();
    reportEdge();
  }
});
setInterval(() => {
  state = loadState();
  reportEdge();
}, 2000);

ticker = new Ticker({
  track: $('crawl'),
  seqA: $('crawlA'),
  seqB: $('crawlB'),
  mask: $('crawl-mask'),
  speed: state.speed,
});
if (typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches) {
  ticker.setSpeed(Math.min(state.speed, 12));
}
startWatchdog({
  getProgress: () => ticker.progress(),
  isRunning: () => ticker.running,
  onStall: () => ticker.restart(),
  onWake: () => {
    ticker.restart();
    refresh();
  },
});

const cached = readCachedSlate();
events = cached?.events?.length ? cached.events : buildDemoSlate();
paint();
ticker.start();
tickClock();
setInterval(tickClock, 1000);
refresh();
setInterval(refresh, Math.max(15, state.refreshSec || 60) * 1000);

function reportEdge() {
  const edge = state.position === 'top' ? 'top' : 'bottom';
  try { globalThis.CoreLineNative?.setOverlayEdge?.(edge); } catch { /* no bridge yet */ }
}

function tickClock() {
  const opts = state.clockFmt === '24'
    ? { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }
    : { hour: 'numeric', minute: '2-digit' };
  $('clock').textContent = new Date().toLocaleTimeString([], opts);
}

function visible() {
  return events
    .filter((ev) => state.showFinals || ev.status !== 'final')
    .sort((a, b) => {
      const af = matchesFavorite(a, state.favorites) ? 0 : 1;
      const bf = matchesFavorite(b, state.favorites) ? 0 : 1;
      if (af !== bf) return af - bf;
      return compareEvents(a, b);
    });
}

function paint() {
  const list = visible();
  const live = list.filter((ev) => ev.status === 'live').length;
  $('bugLive').textContent = live ? `${live} LIVE` : 'LINE';
  $('chyron').classList.toggle('is-live', live > 0);
  const html = (list.length ? list : [{ headline: 'Waiting for a slate', channels: [], status: 'upcoming' }])
    .map(itemHtml)
    .join('');
  ticker.setItems(html);
}

function itemHtml(ev) {
  const kind = ev.status === 'live' ? 'LIVE' : ev.status === 'final' ? 'FINAL' : 'UP';
  const klass = ev.status === 'live' ? 'k' : ev.status === 'final' ? 'k final' : 'k up';
  const channels = (ev.channels || []).join('  ');
  const body = ev.away && ev.home
    ? `${esc(ev.away.abbr || ev.away.name)}${ev.status !== 'upcoming' && ev.away.score != null ? ` ${esc(ev.away.score)}-${esc(ev.home.score)} ` : ' vs '}${esc(ev.home.abbr || ev.home.name)}`
    : esc(ev.headline || ev.rawTitle || 'Listing');
  const detail = ev.detail ? ` ${esc(ev.detail)}` : '';
  return `<span class="tick"><span class="${klass}">${kind}</span> ${body}${detail}${channels ? ` <span class="chs">${esc(channels)}</span>` : ''}</span>`;
}

async function refresh() {
  if (refreshing) return;
  refreshing = true;
  try {
    const fresh = freshCache();
    const data = fresh || await loadSlate();
    if (data?.events) {
      events = data.events;
      paint();
    }
  } catch {
    const cachedSlate = readCachedSlate();
    if (cachedSlate?.events?.length) {
      events = cachedSlate.events;
      paint();
    } else {
      events = await localFallback();
      paint();
    }
  } finally {
    refreshing = false;
  }
}

function freshCache() {
  try {
    const raw = localStorage.getItem('coreline.v1.slate');
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    const age = Date.now() - Number(parsed.at || 0);
    const limit = Math.max(15, state.refreshSec || 60) * 1000;
    if (!parsed.payload?.events?.length || age > limit) return null;
    return parsed.payload;
  } catch {
    return null;
  }
}

async function loadSlate() {
  const feeds = [...(state.feeds || []), ...(state.sampleFeed ? [SAMPLE] : [])].slice(0, 20);
  if (isNativeShell()) return buildClientSlate({ leagues: state.leagues, feeds });
  const query = new URLSearchParams({
    leagues: (state.leagues || []).join(','),
    feeds: feeds.map((f) => `${encodeURIComponent(f.url)}|${encodeURIComponent(f.label)}`).join(','),
  });
  const res = await fetch(`/api/slate?${query}`);
  if (!res.ok) throw new Error(`slate ${res.status}`);
  return res.json();
}

async function localFallback() {
  try {
    const xml = await fetch('./feeds/sample-sports.xml').then((r) => r.text());
    return mergeEvents([buildDemoSlate(), parseFeed(xml, { source: 'rss', label: 'Sample' })]);
  } catch {
    return buildDemoSlate();
  }
}

function esc(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
