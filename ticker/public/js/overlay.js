import { loadState } from './state.js';
import { compareEvents } from '/lib/scoreboard.mjs';
import { matchesFavorite } from '/lib/favorites.mjs';
import { startOverlaySlate } from './overlay-slate.js';
import { Ticker } from './ticker.js';
import { startWatchdog } from './watchdog.js';
import { tickHtml } from './ui/chyron.js';

const params = new URLSearchParams(location.search);
if (params.get('native') === '1') globalThis.CORELINE_NATIVE = true;
if (params.get('tv') === '1') document.documentElement.setAttribute('data-tv', '');

const $ = (id) => document.getElementById(id);

let state = loadState();
let events = [];
let ticker = null;

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
  mask: $('crawlMask'),
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
    slate.refresh();
  },
});

const slate = startOverlaySlate((next) => {
  events = next;
  paint();
});
ticker.start();
tickClock();
setInterval(tickClock, 1000);

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
  $('bugLive').textContent = live ? `${live} LIVE` : 'CORE';
  $('chyron').classList.toggle('is-live', live > 0);
  const items = list.length ? list : [{ headline: 'Waiting for a slate', channels: [], status: 'upcoming' }];
  ticker.setItems(items.map(tickHtml).join(''));
}
