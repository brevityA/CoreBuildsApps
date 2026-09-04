import test from 'node:test';
import assert from 'node:assert/strict';

import { createBackoff, parseRetryAfter } from '../lib/backoff.mjs';

test('initial state: shouldSkip is false', () => {
  const bo = createBackoff();
  assert.equal(bo.shouldSkip(), false);
  assert.equal(bo.attempt, 0);
});

test('fail() sets shouldSkip for future polls', () => {
  const bo = createBackoff({ baseMs: 1000, capMs: 60_000 });
  const now = Date.now();
  bo.fail(null, now);
  assert.equal(bo.attempt, 1);
  assert.equal(bo.shouldSkip(now), true);
  assert.equal(bo.shouldSkip(now + 2000), false);
});

test('succeed() resets backoff', () => {
  const bo = createBackoff({ baseMs: 1000, capMs: 60_000 });
  bo.fail(null, 1000);
  bo.fail(null, 1000);
  assert.equal(bo.attempt, 2);
  bo.succeed();
  assert.equal(bo.attempt, 0);
  assert.equal(bo.shouldSkip(), false);
});

test('exponential growth with cap', () => {
  const bo = createBackoff({ baseMs: 1000, capMs: 10_000 });
  const now = Date.now();
  const d1 = bo.fail(null, now);
  assert.ok(d1 >= 1000 && d1 <= 1250);
  const d2 = bo.fail(null, now + d1);
  assert.ok(d2 >= 2000 && d2 <= 2500);
  const d3 = bo.fail(null, now + d1 + d2);
  assert.ok(d3 >= 4000 && d3 <= 5000);
  const d4 = bo.fail(null, now + d1 + d2 + d3);
  assert.ok(d4 >= 8000 && d4 <= 10_000);
  const d5 = bo.fail(null, now + d1 + d2 + d3 + d4);
  assert.ok(d5 <= 12_500);
});

test('reset() clears state', () => {
  const bo = createBackoff();
  bo.fail(null, 1000);
  bo.reset();
  assert.equal(bo.attempt, 0);
  assert.equal(bo.shouldSkip(), false);
});

test('toJSON / hydrate round-trip', () => {
  const bo = createBackoff({ baseMs: 1000, capMs: 60_000 });
  bo.fail(null, 5000);
  const json = bo.toJSON();
  assert.equal(json.attempt, 1);
  assert.ok(json.nextAt > 5000);
  const bo2 = createBackoff();
  bo2.hydrate(json);
  assert.equal(bo2.attempt, 1);
  assert.equal(bo2.nextAt, json.nextAt);
});

test('hydrate with null is safe', () => {
  const bo = createBackoff();
  bo.hydrate(null);
  assert.equal(bo.attempt, 0);
});

test('Retry-After: seconds', () => {
  const now = 1000;
  assert.equal(parseRetryAfter('120', now), 120_000);
});

test('Retry-After: HTTP-date', () => {
  const date = new Date(Date.now() + 30_000).toUTCString();
  const ms = parseRetryAfter(date);
  assert.ok(ms >= 29_000 && ms <= 31_000);
});

test('Retry-After: empty', () => {
  assert.equal(parseRetryAfter(''), 0);
  assert.equal(parseRetryAfter(null), 0);
  assert.equal(parseRetryAfter(undefined), 0);
});

test('fail() honors Retry-After when longer than backoff', () => {
  const bo = createBackoff({ baseMs: 1000, capMs: 60_000 });
  const now = Date.now();
  const delay = bo.fail('30', now);
  assert.ok(delay >= 30_000);
});
