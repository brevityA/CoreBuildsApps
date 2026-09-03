import test from 'node:test';
import assert from 'node:assert/strict';

let rafEnabled = false;
globalThis.requestAnimationFrame = (cb) => rafEnabled ? setTimeout(cb, 0) : -1;
globalThis.cancelAnimationFrame = (id) => clearTimeout(id);

import { Ticker } from '../public/js/ticker.js';

function makeContainer() {
  const trackA = { offsetWidth: 1000 };
  const trackB = { offsetWidth: 1000 };
  const container = {
    children: [trackA, trackB],
    querySelector(sel) {
      if (sel === '#crawlA') return trackA;
      if (sel === '#crawlB') return trackB;
      return null;
    },
    style: {},
  };
  return { container, trackA, trackB };
}

test('constructor sets default speed', () => {
  const { container } = makeContainer();
  const t = new Ticker(container);
  assert.equal(t.speed, 80);
  assert.equal(t.offset, 0);
});

test('constructor accepts custom speed', () => {
  const { container } = makeContainer();
  const t = new Ticker(container, { speed: 120 });
  assert.equal(t.speed, 120);
});

test('speed setter clamps: Number(0)||80 gives 80, negative gives 1', () => {
  const { container } = makeContainer();
  const t = new Ticker(container);
  t.speed = 0;
  assert.equal(t.speed, 80);
  t.speed = -50;
  assert.equal(t.speed, 1);
});

test('speed setter defaults NaN to 80', () => {
  const { container } = makeContainer();
  const t = new Ticker(container);
  t.speed = 'garbage';
  assert.equal(t.speed, 80);
});

test('measure reads offsetWidth', () => {
  const { container, trackA } = makeContainer();
  const t = new Ticker(container);
  trackA.offsetWidth = 2000;
  t.measure();
  assert.equal(t._trackWidth, 2000);
});

test('measure with no trackA sets width to 0', () => {
  const container = {
    children: [],
    querySelector() { return null; },
    style: {},
  };
  const t = new Ticker(container);
  t.measure();
  assert.equal(t._trackWidth, 0);
});

test('reset sets offset to 0 and applies transform', () => {
  const { container } = makeContainer();
  const t = new Ticker(container);
  t._offset = 500;
  t.reset();
  assert.equal(t.offset, 0);
  assert.equal(container.style.transform, 'translate3d(0px, 0, 0)');
});

test('_apply sets translate3d on container', () => {
  const { container } = makeContainer();
  const t = new Ticker(container);
  t._offset = 123.5;
  t._apply();
  assert.equal(container.style.transform, 'translate3d(-123.5px, 0, 0)');
});

test('_tick advances offset based on speed and delta time', () => {
  const { container } = makeContainer();
  const t = new Ticker(container, { speed: 100 });
  t._running = true;
  t._trackWidth = 10000;
  t._tick(1000);
  assert.equal(t.offset, 0);
  t._running = false;
  t._lastTime = 1000;
  t._running = true;
  t._tick(1500);
  t._running = false;
  assert.ok(t.offset > 0);
  assert.ok(Math.abs(t.offset - 50) < 0.01);
});

test('_tick wraps offset when exceeding trackWidth', () => {
  const { container } = makeContainer();
  const t = new Ticker(container, { speed: 100 });
  t._running = true;
  t._trackWidth = 200;
  t._offset = 190;
  t._lastTime = 1000;
  t._tick(1200);
  t._running = false;
  assert.ok(t.offset < 200);
  assert.ok(t.offset >= 0);
});

test('_tick skips dt >= 1 second (tab was backgrounded)', () => {
  const { container } = makeContainer();
  const t = new Ticker(container, { speed: 100 });
  t._running = true;
  t._trackWidth = 10000;
  t._lastTime = 1000;
  t._tick(3000);
  t._running = false;
  assert.equal(t.offset, 0);
});

test('_tick does nothing when not running', () => {
  const { container } = makeContainer();
  const t = new Ticker(container, { speed: 100 });
  t._running = false;
  t._trackWidth = 1000;
  t._lastTime = 1000;
  t._tick(2000);
  assert.equal(t.offset, 0);
});
