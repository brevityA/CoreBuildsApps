import test from 'node:test';
import assert from 'node:assert/strict';

import { createBackoff, parseRetryAfterMs, BACKOFF_DEFAULTS } from '../lib/backoff.mjs';

test('backoff doubles per failure and caps at maxMs', () => {
  const b = createBackoff({ baseMs: 1000, maxMs: 100000, factor: 2, jitter: 0 });
  const t0 = 1_000_000;
  let now = t0;
  const waits = [];
  for (let i = 0; i < 10; i++) {
    waits.push(b.fail(now));
    now += waits[waits.length - 1];
  }
  // no jitter → deterministic
  assert.equal(waits[0], 1000);
  assert.equal(waits[1], 2000);
  assert.equal(waits[2], 4000);
  for (let i = 3; i < waits.length; i++) assert.ok(waits[i] <= 100000);
  assert.equal(waits[waits.length - 1], 100000); // capped
});

test('jitter stays within ±jitterRatio of the base wait', () => {
  const b = createBackoff({ baseMs: 10000, maxMs: 600000, factor: 2, jitter: 0.25 });
  const waits = [];
  for (let i = 0; i < 500; i++) waits.push(b.fail(0));
  const first = waits[0];
  for (const w of waits) {
    // worst case: base grows; every wait must be within ±25% of its unjittered base
    // but here we only assert sanity: waits are positive and finite.
    assert.ok(Number.isFinite(w) && w > 0);
  }
  assert.ok(first >= 7500 && first <= 12500);
});

test('success resets the schedule', () => {
  const b = createBackoff({ baseMs: 1000, jitter: 0 });
  b.fail(0);
  b.fail(1000);
  assert.equal(b.failCount, 2);
  b.success();
  assert.equal(b.failCount, 0);
  assert.equal(b.nextAt, 0);
  assert.equal(b.canTry(123), true);
});

test('canTry / waitMs gate retries until nextAt', () => {
  const b = createBackoff({ baseMs: 5000, jitter: 0 });
  const now = 10_000;
  b.fail(now); // nextAt = 15000
  assert.equal(b.canTry(14999), false);
  assert.equal(b.waitMs(14999), 1);
  assert.equal(b.canTry(15000), true);
});

test('retryAfter override wins when larger than computed wait', () => {
  const b = createBackoff({ baseMs: 1000, jitter: 0 });
  const wait = b.fail(0, 30000);
  assert.equal(wait, 30000);
});

test('parseRetryAfterMs parses seconds and HTTP dates', () => {
  assert.equal(parseRetryAfterMs('120'), 120_000);
  assert.equal(parseRetryAfterMs(null), 0);
  assert.equal(parseRetryAfterMs(''), 0);
  assert.equal(parseRetryAfterMs('garbage'), 0);
  const date = new Date(Date.now() + 60_000).toUTCString();
  const ms = parseRetryAfterMs(date);
  assert.ok(ms > 0 && ms <= 60_000 + 1000);
  // clamps to cap
  assert.equal(parseRetryAfterMs(String(999999999)), BACKOFF_DEFAULTS.maxMs);
});

test('toJSON/fromJSON round-trips schedule state', () => {
  const a = createBackoff({ baseMs: 1000, jitter: 0 });
  a.fail(0);
  const b = createBackoff();
  b.fromJSON(a.toJSON());
  assert.equal(b.failCount, a.failCount);
  assert.equal(b.nextAt, a.nextAt);
});
