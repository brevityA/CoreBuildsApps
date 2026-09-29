/**
 * Slate search (tests/query.test.mjs).
 *
 * The board search is generous on purpose: it matches team names and channel
 * names, not just abbreviations, because "leafs" and "tsn" are how people
 * type. These tests pin that behaviour so a future optimisation that narrows
 * the haystack cannot quietly break it.
 */

import test from 'node:test';
import assert from 'node:assert/strict';
import { matchesQuery, queryText } from '../lib/query.mjs';

const game = {
  id: 'nhl-1',
  league: 'NHL',
  status: 'live',
  detail: '3rd 04:12',
  venue: 'Scotiabank Arena',
  channels: ['TSN4', 'SN 3'],
  away: { abbr: 'TOR', name: 'Toronto Maple Leafs', score: 3 },
  home: { abbr: 'MTL', name: 'Montreal Canadiens', score: 2 },
};

const headline = {
  id: 'rss-1',
  source: 'rss',
  league: null,
  status: 'upcoming',
  headline: 'Inter Miami vs LAFC',
  feed: 'My Feed',
  channels: ['Apple TV+'],
};

test('an empty or whitespace query matches everything', () => {
  assert.equal(matchesQuery(game, ''), true);
  assert.equal(matchesQuery(game, '   '), true);
  assert.equal(matchesQuery(game, null), true);
  assert.equal(matchesQuery(game, undefined), true);
});

test('matches a team abbreviation, either case', () => {
  assert.equal(matchesQuery(game, 'TOR'), true);
  assert.equal(matchesQuery(game, 'tor'), true);
  assert.equal(matchesQuery(game, 'mtl'), true);
});

test('matches a full team name, not just the abbreviation', () => {
  // The whole point of the generous haystack: nobody types "TOR" for Toronto.
  assert.equal(matchesQuery(game, 'leafs'), true);
  assert.equal(matchesQuery(game, 'Canadiens'), true);
  assert.equal(matchesQuery(game, 'maple'), true);
});

test('matches a broadcast channel', () => {
  assert.equal(matchesQuery(game, 'tsn'), true);
  assert.equal(matchesQuery(game, 'TSN4'), true);
  assert.equal(matchesQuery(game, 'sn 3'), true);
});

test('matches league, venue and period detail', () => {
  assert.equal(matchesQuery(game, 'nhl'), true);
  assert.equal(matchesQuery(game, 'scotiabank'), true);
});

test('matches a headline-only listing and its feed label', () => {
  assert.equal(matchesQuery(headline, 'miami'), true);
  assert.equal(matchesQuery(headline, 'LAFC'), true);
  assert.equal(matchesQuery(headline, 'apple tv'), true);
  assert.equal(matchesQuery(headline, 'my feed'), true);
});

test('multiple terms are ANDed, not ORed', () => {
  assert.equal(matchesQuery(game, 'leafs tsn'), true);
  assert.equal(matchesQuery(game, 'leafs espn'), false, 'one term misses — must not match');
  assert.equal(matchesQuery(game, 'toronto boston'), false);
});

test('a non-match is a non-match', () => {
  assert.equal(matchesQuery(game, 'lakers'), false);
  assert.equal(matchesQuery(game, 'nfl'), false);
});

test('an event with no teams or channels is still searchable', () => {
  const bare = { id: 'x', status: 'upcoming', headline: 'Practice report' };
  assert.equal(matchesQuery(bare, 'practice'), true);
  assert.equal(matchesQuery(bare, 'zzz'), false);
  assert.equal(typeof queryText(bare), 'string');
});

test('queryText is stable and lowercased', () => {
  const text = queryText(game);
  assert.equal(text, text.toLowerCase());
  assert.ok(text.includes('toronto maple leafs'));
  assert.ok(text.includes('tsn4'));
});
