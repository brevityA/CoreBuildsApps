/**
 * The chyron: LIVE bug, crawling listings, clock.
 *
 * The crawl is two identical copies of the same sequence translated by JS at
 * a constant px/s (see ticker.js). That is why the separator must be part of
 * every item including the last: the seam between copy A and copy B has to
 * look like any other item boundary, or the ribbon stutters once per loop.
 *
 * The clock ticks on its own 1s timer and is deliberately *not* driven by the
 * ribbon's rAF loop — a stalled compositor must not freeze the clock.
 */

import { toTickerText } from '/lib/parser.mjs';
import { Ticker } from '../ticker.js';
import { $, esc, setText } from '../core/dom.js';
import { clockOptions } from '../core/format.js';
import { store } from '../core/store.js';

const SEPARATOR = '◆';
const IDLE_ITEM = {
  headline: 'Add an RSS feed in settings — Team vs Team · ESPN, TSN4, SN 3',
  channels: [],
  status: 'upcoming',
};

let ticker = null;
let clockTimer = null;

export function createTicker() {
  if (ticker) return ticker;
  ticker = new Ticker({
    track: $('crawl'),
    seqA: $('crawlA'),
    seqB: $('crawlB'),
    mask: $('crawlMask'),
    speed: store.state.speed,
  });
  // Respect the OS setting by crawling slowly rather than stopping: a frozen
  // ribbon is a broken ribbon, but a crawl is exactly the kind of motion the
  // setting is about.
  if (typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches) {
    ticker.setSpeed(Math.min(store.state.speed, 12));
  }
  return ticker;
}

export function getTicker() {
  return ticker;
}

export function setSpeed(px) {
  ticker?.setSpeed(px);
}

export function startChyron() {
  ticker?.start();
  tickClock();
  clearInterval(clockTimer);
  clockTimer = setInterval(tickClock, 1000);
}

export function tickClock() {
  const opts = clockOptions(store.state.clockFmt);
  const now = new Date();
  setText('clock', now.toLocaleTimeString([], opts));
  setText('bigTime', now.toLocaleTimeString([], opts));
  setText('bigDate', now.toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric' }));
}

export function setBug(liveCount) {
  // Idle, the bug reads as the wordmark — CORE over LINE. It used to fall back
  // to "LINE", which since the bug gained its own name line read LINE / LINE.
  setText('bugLive', liveCount ? `${liveCount} LIVE` : 'CORE');
  $('chyron')?.classList.toggle('is-live', liveCount > 0);
}

export function renderCrawl(list) {
  if (!ticker) return;
  const items = list.length ? list : [IDLE_ITEM];
  ticker.setItems(items.map(tickHtml).join(''));
}

export function tickHtml(ev) {
  const kind = ev.status === 'live' ? 'LIVE' : ev.status === 'final' ? 'FINAL' : 'UP';
  const klass = ev.status === 'live' ? 'tick__k' : ev.status === 'final' ? 'tick__k tick__k--final' : 'tick__k tick__k--up';
  const channels = (ev.channels || []).join('  ');
  const body = ev.away && ev.home
    ? `${esc(ev.away.abbr)}${ev.status !== 'upcoming' && ev.away.score != null ? ` ${esc(ev.away.score)}-${esc(ev.home.score)} ` : ' vs '}${esc(ev.home.abbr)}`
    : esc(ev.headline || ev.rawTitle || toTickerText(ev));
  const detail = ev.detail ? ` ${esc(ev.detail)}` : '';
  return `<span class="tick"><span class="${klass}">${kind}</span> ${body}${detail}`
    + `${channels ? ` <span class="tick__chs">${esc(channels)}</span>` : ''}`
    + `<span class="tick__sep">${SEPARATOR}</span></span>`;
}
