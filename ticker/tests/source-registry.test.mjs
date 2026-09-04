import test from 'node:test';
import assert from 'node:assert/strict';

import { SourceRegistry } from '../lib/source-registry.mjs';

test('new registry: shouldSkip is false for unknown key', () => {
  const reg = new SourceRegistry();
  assert.equal(reg.shouldSkip('league:nfl'), false);
});

test('recordSuccess stores lastGood', () => {
  const reg = new SourceRegistry();
  const items = [{ id: '1' }, { id: '2' }];
  reg.recordSuccess('league:nfl', items);
  assert.deepEqual(reg.getLastGood('league:nfl'), items);
  assert.equal(reg.getHealth('league:nfl'), 'ok');
});

test('recordFailure returns lastGood and marks stale', () => {
  const reg = new SourceRegistry();
  reg.recordSuccess('feed:x', [{ id: 'a' }]);
  reg.recordFailure('feed:x', new Error('timeout'), null);
  assert.deepEqual(reg.getLastGood('feed:x'), [{ id: 'a' }]);
  assert.equal(reg.getHealth('feed:x'), 'stale');
  assert.equal(reg.isStale('feed:x'), true);
});

test('recordFailure with no lastGood marks degraded', () => {
  const reg = new SourceRegistry();
  reg.recordFailure('feed:y', new Error('404'), null);
  assert.deepEqual(reg.getLastGood('feed:y'), []);
  assert.equal(reg.getHealth('feed:y'), 'degraded');
});

test('lastGood capped at 100 items', () => {
  const reg = new SourceRegistry();
  const big = Array.from({ length: 150 }, (_, i) => ({ id: String(i) }));
  reg.recordSuccess('feed:big', big);
  assert.equal(reg.getLastGood('feed:big').length, 100);
});

test('summary counts correctly', () => {
  const reg = new SourceRegistry();
  reg.recordSuccess('a', []);
  reg.recordSuccess('b', [{ id: '1' }]);
  reg.recordFailure('c', 'err', null);
  const s = reg.summary();
  assert.equal(s.total, 3);
  assert.equal(s.ok, 2);
  assert.equal(s.degraded, 1);
});

test('dehydrate / hydrate round-trip', () => {
  const reg = new SourceRegistry();
  reg.recordSuccess('league:mlb', [{ id: 'g1' }]);
  reg.recordFailure('feed:dead', 'gone', null);
  const json = reg.dehydrate();
  const reg2 = new SourceRegistry();
  reg2.hydrate(json);
  assert.deepEqual(reg2.getLastGood('league:mlb'), [{ id: 'g1' }]);
  assert.equal(reg2.getHealth('feed:dead'), 'degraded');
});

test('hydrate with null is safe', () => {
  const reg = new SourceRegistry();
  reg.hydrate(null);
  assert.equal(reg.summary().total, 0);
});

test('clear removes all entries', () => {
  const reg = new SourceRegistry();
  reg.recordSuccess('a', []);
  reg.recordSuccess('b', []);
  reg.clear();
  assert.equal(reg.summary().total, 0);
  assert.deepEqual(reg.getLastGood('a'), []);
});

test('getHealth returns unknown for untracked key', () => {
  const reg = new SourceRegistry();
  assert.equal(reg.getHealth('nonexistent'), 'unknown');
});

test('success after failure resets health to ok', () => {
  const reg = new SourceRegistry();
  reg.recordFailure('k', 'err', null);
  assert.equal(reg.getHealth('k'), 'degraded');
  reg.recordSuccess('k', [{ id: '1' }]);
  assert.equal(reg.getHealth('k'), 'ok');
  assert.equal(reg.isStale('k'), false);
});
