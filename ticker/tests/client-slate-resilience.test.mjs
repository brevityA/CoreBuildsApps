import test from 'node:test';
import assert from 'node:assert/strict';

import { SourceRegistry } from '../lib/source-registry.mjs';

test('registry returns lastGood on failure after prior success', () => {
  const reg = new SourceRegistry();
  const items = [{ id: 'g1', title: 'Game 1' }];
  reg.recordSuccess('league:nfl', items);
  reg.recordFailure('league:nfl', new Error('timeout'), null);
  assert.deepEqual(reg.getLastGood('league:nfl'), items);
});

test('registry returns empty array when no lastGood exists', () => {
  const reg = new SourceRegistry();
  reg.recordFailure('feed:bad', new Error('404'), null);
  assert.deepEqual(reg.getLastGood('feed:bad'), []);
});

test('shouldSkip returns false initially and true after failure', () => {
  const reg = new SourceRegistry();
  assert.equal(reg.shouldSkip('league:nba'), false);
  reg.recordFailure('league:nba', new Error('500'), null);
  assert.equal(reg.shouldSkip('league:nba'), true);
});

test('shouldSkip resets to false after success', () => {
  const reg = new SourceRegistry();
  reg.recordFailure('feed:x', new Error('err'), null);
  assert.equal(reg.shouldSkip('feed:x'), true);
  reg.recordSuccess('feed:x', [{ id: '1' }]);
  assert.equal(reg.shouldSkip('feed:x'), false);
});

test('health transitions: unknown -> degraded -> ok -> stale', () => {
  const reg = new SourceRegistry();
  assert.equal(reg.getHealth('k'), 'unknown');
  reg.recordFailure('k', 'err', null);
  assert.equal(reg.getHealth('k'), 'degraded');
  reg.recordSuccess('k', [{ id: '1' }]);
  assert.equal(reg.getHealth('k'), 'ok');
  reg.recordFailure('k', 'err2', null);
  assert.equal(reg.getHealth('k'), 'stale');
});

test('summary aggregates multiple sources correctly', () => {
  const reg = new SourceRegistry();
  reg.recordSuccess('league:nfl', [{ id: '1' }]);
  reg.recordSuccess('league:nba', [{ id: '2' }]);
  reg.recordFailure('feed:rss1', 'err', null);
  reg.recordSuccess('league:mlb', []);
  reg.recordFailure('league:nhl', 'err', null);
  const s = reg.summary();
  assert.equal(s.total, 5);
  assert.equal(s.ok, 3);
  assert.equal(s.degraded, 2);
});

test('persist and restore round-trip via dehydrate/hydrate', () => {
  const reg = new SourceRegistry();
  reg.recordSuccess('league:nfl', [{ id: 'g1' }]);
  reg.recordFailure('feed:dead', 'gone', null);
  const json = reg.dehydrate();

  const reg2 = new SourceRegistry();
  reg2.hydrate(json);
  assert.deepEqual(reg2.getLastGood('league:nfl'), [{ id: 'g1' }]);
  assert.equal(reg2.getHealth('feed:dead'), 'degraded');
  assert.equal(reg2.getHealth('league:nfl'), 'ok');
});

test('multiple failures increase backoff window', () => {
  const reg = new SourceRegistry();
  reg.recordFailure('k', 'err1', null);
  const skip1 = reg.shouldSkip('k');
  reg.recordFailure('k', 'err2', null);
  const skip2 = reg.shouldSkip('k');
  assert.equal(skip1, true);
  assert.equal(skip2, true);
});
