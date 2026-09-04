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
