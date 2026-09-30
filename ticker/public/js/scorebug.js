/**
 * The scoreboard bug's page: fetch the slate, cycle the live games, paint one.
 *
 * What to show and how it reads is `lib/scorebug.mjs`; where the window sits
 * and how it stays out of the remote's way is `ScoreBugWindow.kt`. This file
 * only joins them: it shares the crawl's slate (`overlay-slate.js`), rotates
 * every SCOREBUG_ROTATE_MS, and hides the bug entirely when nothing is on or
 * about to be — an empty box over a film is worse than no box.
 */

import { loadState } from './state.js';
import { startOverlaySlate } from './overlay-slate.js';
import { startLabel } from './core/format.js';
import { scoreBugQueue, scoreBugAt, scoreBugHtml, SCOREBUG_ROTATE_MS } from '/lib/scorebug.mjs';

const params = new URLSearchParams(location.search);
if (params.get('native') === '1') globalThis.CORELINE_NATIVE = true;
if (params.get('tv') === '1') document.documentElement.setAttribute('data-tv', '');

const bug = document.getElementById('bug');
const reducedMotion = typeof matchMedia !== 'undefined'
  && matchMedia('(prefers-reduced-motion: reduce)').matches;

let events = [];
let queue = [];
let index = 0;
let shownId = null;

function rebuild() {
  const state = loadState();
  queue = scoreBugQueue(events, { favorites: state.favorites });
  // Keep the game that is on screen when a refresh reorders the queue: a bug
  // that jumps to another match on every score update is unreadable.
  const keep = queue.findIndex((ev) => ev.id === shownId);
  index = keep >= 0 ? keep : 0;
  paint();
}

function paint() {
  const ev = scoreBugAt(queue, index);
  if (!ev) {
    bug.hidden = true;
    bug.innerHTML = '';
    shownId = null;
    return;
  }
  shownId = ev.id;
  bug.innerHTML = scoreBugHtml(ev, { position: index % queue.length, total: queue.length, startLabel });
  bug.hidden = false;
}

startOverlaySlate((next) => {
  events = next;
  rebuild();
}, { demoFallback: false });

// A reduced-motion viewer keeps the first game (favourites lead the queue)
// rather than having the bug change under them.
if (!reducedMotion) {
  setInterval(() => {
    if (queue.length < 2) return;
    index = (index + 1) % queue.length;
    paint();
  }, SCOREBUG_ROTATE_MS);
}

// Favourites and leagues are set in the board; pick up a change without a restart.
window.addEventListener('storage', (event) => {
  if (event.key === 'coreline.v1') rebuild();
});
