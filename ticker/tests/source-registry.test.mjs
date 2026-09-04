import test from 'node:test';
import assert from 'node:assert/strict';

import { SourceRegistry, MAX_ITEMS_PER_SOURCE } from '../lib/source-registry.mjs';

test('recordSuccess stores last-good and clears the error', () => {
  const r = new SourceRegistry();
  const key = r.keyForFeed('https://example.com/feed.xml');
  r.recordSuccess(key, 'Example', [{ id: 'a' }, { id: 'b' }]);
  assert.equal(r.lastGood(key).length, 2);
  assert.equal(r.isDegraded(key), false);
  assert.equal(r.canTry(key), true);
});

test('recordFailure keeps last-good and marks degraded + backoff', () => {
  const r = new SourceRegistry();
  const key = r.keyForFeed('https://example.com/feed.xml');
  r.recordSuccess(key, 'Example', [{ id: 'a' }]);
  r.recordFailure(key, 'Example', 'http 500');
  assert.equal(r.isDegraded(key), true);
  assert.equal(r.lastGood(key).length, 1);
  assert.equal(r.lastError(key), 'http 500');
  // freshly failed → cannot retry immediately
  assert.equal(r.canTry(key), false);
});

test('canTry restores after the backoff window', () => {
  const r = new SourceRegistry();
  const key = r.keyForFeed('https://example.com/feed.xml');
  // A big Retry-After overrides the computed wait, giving a deterministic window.
  r.recordFailure(key, 'Example', 'boom', 100_000);
  const now = Date.now();
  assert.equal(r.canTry(key, now), false);
  assert.equal(r.canTry(key, now + 50_000), false);
  assert.equal(r.canTry(key, now + 101_000), true);
});

test('health() counts degraded sources', () => {
  const r = new SourceRegistry();
  const a = r.keyForFeed('https://a.example/feed.xml');
  const b = r.keyForFeed('https://b.example/feed.xml');
  r.recordSuccess(a, 'A', [{ id: 1 }]);
  r.recordFailure(b, 'B', 'down');
  const h = r.health();
  assert.equal(h.total, 2);
  assert.equal(h.degraded, 1);
});

test('last-good is capped at MAX_ITEMS_PER_SOURCE', () => {
  const r = new SourceRegistry();
  const key = r.keyForFeed('https://example.com/big.xml');
  const events = Array.from({ length: 500 }, (_, i) => ({ id: String(i) }));
  r.recordSuccess(key, 'Big', events);
  assert.equal(r.lastGood(key).length, MAX_ITEMS_PER_SOURCE);
});

test('hydrate/dehydrate round-trips last-good events', () => {
  const a = new SourceRegistry();
  a.recordSuccess(a.keyForFeed('https://x.example/feed.xml'), 'X', [{ id: 'keep' }]);
  const json = a.dehydrate();

  const b = new SourceRegistry();
  b.hydrate(json);
  const key = b.keyForFeed('https://x.example/feed.xml');
  assert.equal(b.lastGood(key).length, 1);
  assert.equal(b.lastGood(key)[0].id, 'keep');
  // hydrate is not degraded (no failure recorded yet)
  assert.equal(b.isDegraded(key), false);
});

test('hydrate survives corrupt input', () => {
  const r = new SourceRegistry();
  assert.doesNotThrow(() => r.hydrate('not-json{'));
  assert.doesNotThrow(() => r.hydrate(null));
});
