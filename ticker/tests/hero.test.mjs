import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { HERO_ROTATE_MS, heroCandidates, pickHero, shouldRotate } from '../lib/hero.mjs';

const game = (id, over = {}) => ({ id, status: 'live', away: { abbr: 'A' }, home: { abbr: 'H' }, ...over });

// A slate in feed order: two real games, then a headline that is merely live.
const SLATE = [
  game('nba-1'),
  { id: 'rss-1', status: 'live', away: null, home: null, source: 'rss', title: 'Something happened' },
  game('mls-1', { source: 'espn' }),
  { id: 'nba-2', status: 'upcoming', away: { abbr: 'A' }, home: { abbr: 'H' } },
];

test('a banner picks a game, never a headline', () => {
  const ids = heroCandidates(SLATE).map((e) => e.id);
  assert.deepEqual(ids, ['nba-1', 'mls-1']);
  // The rss item is `status: 'live'` with no teams — the sample feed's shape,
  // and the reason the hero used to show a text headline over a real game.
  assert.ok(!ids.includes('rss-1'));
});

test('a headline can still hold the banner when nothing else is on', () => {
  const quiet = [
    { id: 'nba-2', status: 'upcoming', away: { abbr: 'A' }, home: { abbr: 'H' } },
    { id: 'rss-1', status: 'live', source: 'rss', away: { abbr: 'R' }, home: { abbr: 'S' } },
  ];
  assert.deepEqual(heroCandidates(quiet).map((e) => e.id), ['rss-1']);
});

test('nothing live means no banner, not an error', () => {
  assert.equal(pickHero([]), null);
  assert.equal(pickHero(SLATE.filter((e) => e.status !== 'live')), null);
  assert.equal(pickHero(SLATE, 7), pickHero(SLATE, 1)); // wraps rather than throwing
});

test('the running order is stable and it wraps both ways', () => {
  assert.equal(pickHero(SLATE, 0).id, 'nba-1');
  assert.equal(pickHero(SLATE, 1).id, 'mls-1');
  assert.equal(pickHero(SLATE, 2).id, 'nba-1', 'did not wrap forward');
  assert.equal(pickHero(SLATE, -1).id, 'mls-1', 'did not wrap backward');
  assert.equal(pickHero(SLATE, NaN).id, 'nba-1');
});

test('the banner does not move out from under a viewer', () => {
  assert.equal(shouldRotate({ list: SLATE, focused: true }), false);
  // ...even though there is more than one game, which is what would otherwise
  // let it move. Focus wins.
  assert.equal(shouldRotate({ list: SLATE }), true);
});

test('it holds still for a viewer who asked for less motion', () => {
  assert.equal(shouldRotate({ list: SLATE, reducedMotion: true }), false);
});

test('it does not repaint a banner nobody can see', () => {
  assert.equal(shouldRotate({ list: SLATE, hidden: true }), false);
});

test('one game is not a rotation, and no arguments is not a crash', () => {
  assert.equal(shouldRotate({ list: [game('nba-1')] }), false);
  assert.equal(shouldRotate(), false);
});

test('the hold is long enough to read a score from a couch', () => {
  // This is a TV three metres away, not a carousel. Anything under ~8s turns
  // the banner into something a viewer has to chase.
  assert.ok(HERO_ROTATE_MS >= 8000, `${HERO_ROTATE_MS}ms is too short to read`);
});

/* ---- The seams in the board --------------------------------------------- */

const boardJs = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), '..', 'public/js/ui/board.js'),
  'utf8',
);

test('the board paints through this module rather than its own sort', () => {
  assert.match(boardJs, /from '\/lib\/hero\.mjs'/, 'board does not import the rules');
  assert.match(boardJs, /pickHero\(list, index\)/, 'board does not use the shared selection');
  assert.match(boardJs, /function renderHero\(list, index = 0\)/, 'hero cannot be pointed at a game');
});

test('the clock is armed and torn down with the render', () => {
  assert.match(boardJs, /clearInterval\(heroTimer\)/, 'a stale timer would outlive its slate');
  assert.match(boardJs, /setInterval\(tickHero, HERO_ROTATE_MS\)/);
  // The tick must read focus at tick time — a render is up to 12s stale by
  // then, and the viewer may have walked onto the banner since.
  assert.match(boardJs, /hero\.contains\(document\.activeElement\)/);
  assert.match(boardJs, /document\.hidden/);
  assert.match(boardJs, /prefers-reduced-motion/);
});
