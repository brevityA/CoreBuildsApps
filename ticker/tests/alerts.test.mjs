/**
 * Score alerts (tests/alerts.test.mjs).
 *
 * The alert is the one place Core Line interrupts the viewer, so the rules
 * matter more than the message: fire on a score change, a lead change, the
 * start of a game and the final — for starred teams only, and never on the
 * first look at a slate.
 */

import test from 'node:test';
import assert from 'node:assert/strict';
import { signatureOf, snapshotOf, diffSlate, leaderOf } from '../lib/alerts.mjs';

const FAVS = new Set(['TOR', 'LAL']);

function ev(id, over = {}) {
  return {
    id,
    status: 'live',
    away: { abbr: 'TOR', score: 1 },
    home: { abbr: 'MTL', score: 0 },
    ...over,
  };
}

const snap = (...events) => snapshotOf(events);

test('leaderOf is null when level or unscored', () => {
  assert.equal(leaderOf({ away: 2, home: 2, awayAbbr: 'TOR', homeAbbr: 'MTL' }), null);
  assert.equal(leaderOf({ away: null, home: 1, awayAbbr: 'TOR', homeAbbr: 'MTL' }), null);
  assert.equal(leaderOf({ away: 3, home: 1, awayAbbr: 'TOR', homeAbbr: 'MTL' }), 'TOR');
  assert.equal(leaderOf({ away: 1, home: 4, awayAbbr: 'TOR', homeAbbr: 'MTL' }), 'MTL');
});

test('a score change on a starred team alerts', () => {
  const before = snap(ev('a', { away: { abbr: 'TOR', score: 1 }, home: { abbr: 'MTL', score: 0 } }));
  const after = snap(ev('a', { away: { abbr: 'TOR', score: 2 }, home: { abbr: 'MTL', score: 0 } }));
  const msgs = diffSlate(before, after, FAVS);
  assert.equal(msgs.length, 1);
  assert.match(msgs[0], /TOR 2–0 MTL/);
});

test('a lead change is called out as a lead change', () => {
  const before = snap(ev('a', { away: { abbr: 'TOR', score: 0 }, home: { abbr: 'MTL', score: 1 } }));
  const after = snap(ev('a', { away: { abbr: 'TOR', score: 2 }, home: { abbr: 'MTL', score: 1 } }));
  const msgs = diffSlate(before, after, FAVS);
  assert.equal(msgs.length, 1);
  assert.match(msgs[0], /^Lead change — TOR 2–1 MTL$/);
});

test('a game going final alerts once', () => {
  const before = snap(ev('a', { status: 'live' }));
  const after = snap(ev('a', { status: 'final' }));
  const msgs = diffSlate(before, after, FAVS);
  assert.equal(msgs.length, 1);
  assert.match(msgs[0], /^Final — TOR 1–0 MTL$/);
});

test('a game going live alerts', () => {
  const before = snap(ev('a', { status: 'upcoming', away: { abbr: 'TOR', score: null }, home: { abbr: 'MTL', score: null } }));
  const after = snap(ev('a', { status: 'live', away: { abbr: 'TOR', score: 0 }, home: { abbr: 'MTL', score: 0 } }));
  const msgs = diffSlate(before, after, FAVS);
  assert.equal(msgs.length, 1);
  assert.match(msgs[0], /is underway/);
});

test('an unchanged game is silent', () => {
  const before = snap(ev('a'));
  const after = snap(ev('a'));
  assert.deepEqual(diffSlate(before, after, FAVS), []);
});

test('teams the viewer does not follow are silent', () => {
  const before = snap(ev('a', { away: { abbr: 'KC', score: 0 }, home: { abbr: 'BUF', score: 0 } }));
  const after = snap(ev('a', { away: { abbr: 'KC', score: 7 }, home: { abbr: 'BUF', score: 0 } }));
  assert.deepEqual(diffSlate(before, after, FAVS), []);
});

test('no favourites means no alerts', () => {
  const before = snap(ev('a'));
  const after = snap(ev('a', { away: { abbr: 'TOR', score: 9 } }));
  assert.deepEqual(diffSlate(before, after, new Set()), []);
});

test('a brand-new event does not alert (it has no baseline)', () => {
  const before = snap();
  const after = snap(ev('a', { away: { abbr: 'TOR', score: 4 } }));
  assert.deepEqual(diffSlate(before, after, FAVS), []);
});

test('an event that disappears is not an alert', () => {
  const before = snap(ev('a'), ev('b'));
  const after = snap(ev('a'));
  assert.deepEqual(diffSlate(before, after, FAVS), []);
});

test('signatureOf falls back safely on a headline-only listing', () => {
  const sig = signatureOf({ id: 'x', status: 'upcoming', headline: 'Inter Miami vs LAFC' });
  assert.equal(sig.away, null);
  assert.equal(sig.awayAbbr, '');
  assert.equal(sig.status, 'upcoming');
});

test('several starred games produce several messages, in order', () => {
  const before = snap(
    ev('a', { away: { abbr: 'TOR', score: 0 }, home: { abbr: 'MTL', score: 0 } }),
    ev('b', { away: { abbr: 'LAL', score: 10 }, home: { abbr: 'BOS', score: 10 } }),
  );
  const after = snap(
    ev('a', { away: { abbr: 'TOR', score: 1 }, home: { abbr: 'MTL', score: 0 } }),
    ev('b', { away: { abbr: 'LAL', score: 12 }, home: { abbr: 'BOS', score: 10 } }),
  );
  const msgs = diffSlate(before, after, FAVS);
  assert.equal(msgs.length, 2);
  assert.match(msgs[0], /TOR/);
  assert.match(msgs[1], /LAL/);
});
