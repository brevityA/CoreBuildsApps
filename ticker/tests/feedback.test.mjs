/**
 * Regression tests for the 2026-09-04 supporter feedback on the TV build:
 *   1. "you show a live nfl game its a rerun"  -> stray "live" word marked a
 *      listing LIVE; replay/rerun titles not handled; no FINAL detection.
 *   2. "espn game right now doesn't exist because both unranked" -> college
 *      sports were not in the default league set (and never guessable).
 *   3. "the built in tabs override a custom feed" -> per-feed tabs + explicit
 *      RSS/JSON category now bucket a feed into a league instead of RSS.
 */
import test from 'node:test';
import assert from 'node:assert/strict';

import { parseListing, parseFeed, parseJsonFeed, guessLeague, leagueFromCategory } from '../lib/parser.mjs';
import { DEFAULT_LEAGUES } from '../lib/scoreboard.mjs';

// --- 1. live / final / replay status -------------------------------------

test('leading LIVE prefix still marks a listing live', () => {
  assert.equal(parseListing('LIVE Chiefs vs Bills | CBS').status, 'live');
});

test('league prefix then LIVE still marks a listing live', () => {
  assert.equal(parseListing('NFL LIVE - Chiefs vs Bills | CBS').status, 'live');
});

test('a stray "live" mid-title does NOT mark a listing live (rerun bug)', () => {
  assert.equal(parseListing('Chiefs vs Bills live tonight on CBS').status, 'upcoming');
  assert.equal(parseListing('Classic: Chiefs vs Bills (live from 2019)').status, 'upcoming');
});

test('"watch live replay" is not live', () => {
  const e = parseListing('Watch live replay: Chiefs vs Bills | CBS');
  assert.notEqual(e.status, 'live');
});

test('replay/rerun/on-demand markers suppress a leading LIVE', () => {
  const e = parseListing('LIVE Chiefs vs Bills - full match replay | CBS');
  assert.notEqual(e.status, 'live');
});

test('leading FINAL marks a listing final', () => {
  assert.equal(parseListing('FINAL Chiefs 27-24 Bills | CBS').status, 'final');
  assert.equal(parseListing('FT - Chiefs 2-1 Bills | CBS').status, 'final');
});

test('final detection only matches the standalone word (not "Final Four" or "Finals")', () => {
  // "final" inside a longer word should not flip status.
  assert.equal(parseListing('Semifinal preview: Chiefs vs Bills').status, 'upcoming');
  // "NBA Finals" is a series name, not a finished game.
  assert.equal(parseListing('NBA Finals Game 1: Celtics vs Mavs').status, 'upcoming');
  assert.equal(parseListing('Final Four preview: Duke vs UNC').status, 'upcoming');
});

// --- 2. college sports ---------------------------------------------------

test('college football and basketball are in the default league set', () => {
  assert.ok(DEFAULT_LEAGUES.includes('ncaaf'));
  assert.ok(DEFAULT_LEAGUES.includes('ncaab'));
});

test('guessLeague recognises college keywords', () => {
  assert.equal(guessLeague('Georgia vs Alabama — college football'), 'NCAAF');
  assert.equal(guessLeague('Duke vs UNC — march madness'), 'NCAAB');
});

test('leagueFromCategory maps unambiguous categories', () => {
  assert.equal(leagueFromCategory('NCAAF'), 'NCAAF');
  assert.equal(leagueFromCategory('College Football'), 'NCAAF');
  assert.equal(leagueFromCategory('Women\'s College Basketball'), 'NCAAB');
  assert.equal(leagueFromCategory('NFL'), 'NFL');
  assert.equal(leagueFromCategory('Baseball'), 'MLB');
  assert.equal(leagueFromCategory('Hockey'), 'NHL');
});

test('leagueFromCategory returns null for generic/ambiguous text', () => {
  assert.equal(leagueFromCategory('football'), null);
  assert.equal(leagueFromCategory(''), null);
  assert.equal(leagueFromCategory('Top 25'), null);
});

// --- 3. category bucketing for pasted feeds ------------------------------

test('RSS <category>NCAAF</category> buckets the item as NCAAF', () => {
  const xml = `<rss><channel><item>
    <title>Georgia vs Alabama</title>
    <category>NCAAF</category>
  </item></channel></rss>`;
  const items = parseFeed(xml);
  assert.equal(items.length, 1);
  assert.equal(items[0].league, 'NCAAF');
});

test('JSON category field buckets the item, but an explicit league wins', () => {
  const items = parseJsonFeed(JSON.stringify([
    { title: 'Georgia vs Alabama', category: 'College Football' },
    { title: 'Some game', category: 'NCAAF', league: 'NFL' },
  ]));
  assert.equal(items[0].league, 'NCAAF');
  assert.equal(items[1].league, 'NFL'); // explicit league overrides category
});

test('rss.app JSON shape (items[].content_html / date_published) parses', () => {
  const items = parseJsonFeed(JSON.stringify({
    title: 'Sports wire',
    items: [
      {
        title: 'Miami vs KC - 3-4',
        content_html: '<p>Live on MLB.TV, ROYALS.TV, MARLINS.TV</p>',
        date_published: '2026-09-04T01:53:00Z',
      },
    ],
  }));
  assert.equal(items.length, 1);
  assert.ok(items[0].channels.includes('MLB.TV'));
  assert.ok(items[0].start); // date_published became start
});
