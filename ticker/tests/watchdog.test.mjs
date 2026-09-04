import test from 'node:test';
import assert from 'node:assert/strict';

import { startWatchdog } from '../public/js/watchdog.js';

// Fake browser globals (watchdog is DOM-timer logic; no real browser needed).
function installGlobals() {
  const listeners = { doc: {}, win: {} };
  globalThis.document = {
    hidden: false,
    addEventListener: (e, fn) => { listeners.doc[e] = fn; },
    removeEventListener: (e) => { delete listeners.doc[e]; },
  };
  globalThis.window = {
    addEventListener: (e, fn) => { listeners.win[e] = fn; },
    removeEventListener: (e) => { delete listeners.win[e]; },
  };
  let intervalCb = null;
  globalThis.setInterval = (cb) => { intervalCb = cb; return 7; };
  globalThis.clearInterval = () => { intervalCb = null; };
  return { listeners, tick: () => intervalCb && intervalCb() };
}

test('stalls fire onStall after two unchanged samples', () => {
  const { tick } = installGlobals();
  let progress = 10; // frozen
  let stalls = 0;
  startWatchdog({
    getProgress: () => progress,
    isRunning: () => true,
    onStall: () => { stalls += 1; },
    onWake: () => {},
  });
  tick(); // baseline sample
  tick(); // unchanged #1
  tick(); // unchanged #2 → stall
  assert.equal(stalls, 1);
});

test('a moving ribbon never fires onStall', () => {
  const { tick } = installGlobals();
  let progress = 0;
  let stalls = 0;
  startWatchdog({
    getProgress: () => progress,
    isRunning: () => true,
    onStall: () => { stalls += 1; },
    onWake: () => {},
  });
  progress += 5; tick();
  progress += 5; tick();
  progress += 5; tick();
  assert.equal(stalls, 0);
});

test('visibilitychange → visible fires onWake', () => {
  const { listeners } = installGlobals();
  let wakes = 0;
  startWatchdog({
    getProgress: () => 0,
    isRunning: () => true,
    onStall: () => {},
    onWake: () => { wakes += 1; },
  });
  document.hidden = true;
  listeners.doc.visibilitychange?.();
  document.hidden = false;
  listeners.doc.visibilitychange?.();
  assert.equal(wakes, 1);
});

test('hidden pages are ignored (no false stalls while backgrounded)', () => {
  const { tick } = installGlobals();
  document.hidden = true;
  let stalls = 0;
  startWatchdog({
    getProgress: () => 0,
    isRunning: () => true,
    onStall: () => { stalls += 1; },
    onWake: () => {},
  });
  tick(); tick(); tick();
  assert.equal(stalls, 0);
});
