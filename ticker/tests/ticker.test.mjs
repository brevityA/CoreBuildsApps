import test from 'node:test';
import assert from 'node:assert/strict';

import { Ticker } from '../public/js/ticker.js';

// Minimal DOM stand-ins so the loop math is testable without a browser.
function fakeSeq(width) {
  return {
    innerHTML: '',
    style: {},
    getBoundingClientRect: () => ({ width }),
    offsetWidth: width,
  };
}

globalThis.requestAnimationFrame = () => 1;
globalThis.cancelAnimationFrame = () => {};
globalThis.ResizeObserver = class { observe() {} disconnect() {} };

function makeTicker({ seqW = 800, maskW = 500, speed = 50 } = {}) {
  const seqA = fakeSeq(seqW);
  const seqB = fakeSeq(seqW);
  const track = { style: {} };
  const mask = { clientWidth: maskW };
  const t = new Ticker({ track, seqA, seqB, mask, speed });
  return { t, seqA, seqB, track };
}

// Simulate a running loop: mark running and drive frames by hand.
function arm(t, offset = 0) {
  t.running = true;
  t.offset = offset;
  t._last = 0;
}

test('setItems writes both copies and measures width', () => {
  const { t, seqA, seqB } = makeTicker({ seqW: 800 });
  t.setItems('<span class="tick">X</span>');
  assert.equal(seqA.innerHTML, '<span class="tick">X</span>');
  assert.equal(seqB.innerHTML, '<span class="tick">X</span>');
  assert.equal(t.seqWidth, 800);
});

test('short content is padded to at least the mask width (no blank edge)', () => {
  const { t, seqA, seqB } = makeTicker({ seqW: 300, maskW: 640 });
  t.measure();
  assert.equal(t.seqWidth, 640);
  assert.equal(seqA.style.minWidth, '640px');
  assert.equal(seqB.style.minWidth, '640px');
});

test('offset advances at constant px/s (frame dt clamped to 100 ms)', () => {
  const { t, track } = makeTicker({ speed: 50 });
  arm(t);
  t._tick(1000); // 1000 ms since last → clamped to 0.1 s → +5 px
  assert.equal(t.offset, 5);
  assert.equal(track.style.transform, 'translate3d(-5px,0,0)');
  t._tick(1100); // 100 ms later → another +5 px
  assert.equal(t.offset, 10);
});

test('offset wraps at seqWidth (seamless loop)', () => {
  const { t } = makeTicker({ speed: 120 });
  t.seqWidth = 100;
  arm(t, 95);
  t._tick(1000); // +12 px (120 * 0.1) → 107 → wraps to 7
  assert.equal(t.offset, 7);
});

test('progress() is the watchdog readout and moves with the loop', () => {
  const { t } = makeTicker({ speed: 10 });
  arm(t);
  t._tick(1000); // +1 px
  assert.equal(t.progress(), 1);
});

test('stop halts the loop; restart resumes from current offset', () => {
  const { t } = makeTicker({ speed: 50 });
  arm(t, 40);
  t.stop();
  assert.equal(t.running, false);
  t.restart();
  assert.equal(t.running, true);
  assert.equal(t.offset, 40, 'restart keeps position — no teleport');
});

test('background-tab time jumps are clamped (no teleport after resume)', () => {
  const { t } = makeTicker({ speed: 50 });
  arm(t);
  t._tick(60_000); // 60 s jump clamped to 0.1 s → +5 px, not +3000 px
  assert.equal(t.offset, 5);
});

// ---- Compositor path (Web Animations API) ---------------------------------
// The WebView runs the crawl as one infinite transform animation, so the main
// thread does nothing per frame. A stand-in `animate()` records what was asked
// for; its `currentTime` is what a real animation would report.

function waapiTicker({ seqW = 800, maskW = 500, speed = 50 } = {}) {
  const made = [];
  const track = {
    style: {},
    animate(keyframes, options) {
      const anim = { keyframes, options, currentTime: 0, cancelled: false, cancel() { this.cancelled = true; } };
      made.push(anim);
      return anim;
    },
  };
  const seqA = fakeSeq(seqW);
  const t = new Ticker({ track, seqA, seqB: fakeSeq(seqW), mask: { clientWidth: maskW }, speed });
  return { t, track, seqA, made, live: () => made.filter((a) => !a.cancelled) };
}

test('compositor: start builds one linear infinite animation over one copy', () => {
  const { t, live } = waapiTicker({ seqW: 800, speed: 50 });
  t.start();
  const [anim] = live();
  assert.equal(live().length, 1);
  assert.equal(anim.options.duration, 16_000, '800 px at 50 px/s');
  assert.equal(anim.options.iterations, Infinity);
  assert.equal(anim.options.easing, 'linear');
  assert.equal(anim.keyframes.at(-1).transform, 'translate3d(-800px,0,0)');
});

test('compositor: no per-frame main-thread loop', () => {
  let frames = 0;
  const saved = globalThis.requestAnimationFrame;
  globalThis.requestAnimationFrame = () => { frames += 1; return 1; };
  try {
    const { t } = waapiTicker();
    t.start();
    assert.equal(frames, 0);
  } finally {
    globalThis.requestAnimationFrame = saved;
  }
});

test('compositor: progress reads the animation, wrapping at one copy', () => {
  const { t, live } = waapiTicker({ seqW: 800, speed: 50 });
  t.start();
  live()[0].currentTime = 2_000; // 2 s at 50 px/s
  assert.equal(t.progress(), 100);
  live()[0].currentTime = 17_000; // one full copy (16 s) plus 1 s
  assert.equal(t.progress(), 50);
});

test('compositor: a width change rebuilds from the same position (no teleport)', () => {
  const { t, seqA, live } = waapiTicker({ seqW: 800, speed: 50 });
  t.start();
  live()[0].currentTime = 4_000; // 200 px in
  seqA.getBoundingClientRect = () => ({ width: 1000 });
  t.measure();
  const [anim] = live();
  assert.equal(live().length, 1, 'the old animation is cancelled');
  assert.equal(anim.options.duration, 20_000, '1000 px at 50 px/s');
  assert.equal(anim.currentTime, 4_000, 'still 200 px in');
  assert.equal(t.progress(), 200);
});

test('compositor: a speed change keeps position and changes px/s', () => {
  const { t, live } = waapiTicker({ seqW: 800, speed: 50 });
  t.start();
  live()[0].currentTime = 4_000; // 200 px
  t.setSpeed(100);
  const [anim] = live();
  assert.equal(anim.options.duration, 8_000);
  assert.equal(anim.currentTime, 2_000, '200 px at the new speed');
});

test('compositor: stop holds the ribbon in place; restart resumes there', () => {
  const { t, track, live } = waapiTicker({ seqW: 800, speed: 50 });
  t.start();
  live()[0].currentTime = 3_000; // 150 px
  t.stop();
  assert.equal(live().length, 0);
  assert.equal(track.style.transform, 'translate3d(-150px,0,0)');
  t.start();
  assert.equal(live()[0].currentTime, 3_000);
});

test('compositor: setSpeed(0) holds the ribbon where it is', () => {
  const { t, track, live } = waapiTicker({ seqW: 800, speed: 50 });
  t.start();
  live()[0].currentTime = 4_000; // 200 px in
  t.setSpeed(0);
  assert.equal(live().length, 0, 'nothing left playing');
  assert.equal(track.style.transform, 'translate3d(-200px,0,0)', 'snapped back to the start');
});

test('compositor: an empty strip plays nothing until it has a width', () => {
  const { t, seqA, live } = waapiTicker({ seqW: 0, maskW: 0 });
  t.start();
  assert.equal(live().length, 0);
  seqA.getBoundingClientRect = () => ({ width: 600 });
  t.measure();
  assert.equal(live().length, 1);
});
