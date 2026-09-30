/**
 * Niche sports and niche channels — the 2026-09-29 supporter feedback.
 *
 * "It's easy to get the main sports on the main channels. It's the niche
 * sports and niche channels that's hard — ESPN unlimited, ESPN+, SEC+ etc. If
 * someone can pull those you get all college sports."
 *
 * He was right, and the mechanism was measurable: the matcher's Tier 1
 * network comparison is `networkBugFor(playlist name)` against the bug the
 * slate publishes. ESPN publishes "ESPN+"/"SECN"/"ACCN"/"BTN"; a provider
 * ships those as "US| ESPN+ 1 HD", "US| SEC Network+ HD", "US| ACC Network
 * Extra". Before this change the first collapsed to "ESPN+ 1" and matched
 * nothing, the second and third matched nothing at all, and a card could not
 * even *show* "SEC Network+" — while "US| ESPNU HD" and "US| ESPN2 HD"
 * matched fine. Mainstream worked, the tiers did not, which is exactly the
 * shape of his complaint.
 */
import test from 'node:test';
import assert from 'node:assert/strict';

import { normalizeChannel, extractChannels, splitChannelsBlob } from '../lib/channels.mjs';
import { parseM3U, networkBugFor, matchChannels } from '../lib/playlist.mjs';
import { SPORT_GROUPS, LEAGUES } from '../lib/scoreboard.mjs';

/* ---- the collapse ------------------------------------------------------- */

test('a numbered streaming-tier feed collapses to the tier the slate names', () => {
  // "ESPN+ 1"…"ESPN+ 12" are event feeds of one channel, not channels.
  for (const n of [1, 2, 7, 12]) {
    assert.equal(networkBugFor(`US| ESPN+ ${n} HD`), 'ESPN+');
    assert.equal(networkBugFor(`US| SEC+ ${n}`), 'SEC+');
  }
  assert.equal(networkBugFor('US| ESPN+ 1'), 'ESPN+');
});

test('numbered real channels keep their identity', () => {
  // The guard on the collapse: a number that IS the channel must survive.
  assert.equal(networkBugFor('US| ESPN2 HD'), 'ESPN2');
  assert.equal(networkBugFor('US| ESPN 2 HD'), 'ESPN2');
  assert.equal(networkBugFor('CA| TSN4 HD'), 'TSN4');
  assert.equal(networkBugFor('CA| SN 3 HD'), 'SN 3');
  assert.equal(networkBugFor('US| ESPNU 2 HD'), 'ESPNU');
  assert.equal(networkBugFor('US| F1 HD'), 'F1');
});

test('the tier brands providers actually write all resolve', () => {
  const cases = [
    ['US| ESPN+ 1 HD', 'ESPN+'],
    ['US| ESPN Plus 4K', 'ESPN+'],
    ['ESPN Unlimited', 'ESPN+'],
    ['US| SEC Network+ HD', 'SEC+'],
    ['US| SEC Network Plus', 'SEC+'],
    ['US| SECN+ HD', 'SEC+'],
    ['US| ACC Network Extra', 'ACC+'],
    ['US| ACCNX', 'ACC+'],
    ['US| Big Ten Network HD', 'BTN'],
    ['US| Big Ten+ 2', 'BTN+'],
    ['US| ESPN3', 'ESPN3'],
    ['US| FloSports HD', 'FLO'],
    ['US| Stadium HD', 'STAD'],
    ['US| truTV', 'TRU'],
  ];
  for (const [playlistName, slateBug] of cases) {
    assert.equal(networkBugFor(playlistName), slateBug, `${playlistName} should be ${slateBug}`);
  }
});

test('the mainstream national feed still resolves, unchanged', () => {
  // The fix must not cost the case that already worked.
  for (const [playlistName, slateBug] of [
    ['US| ESPNU HD', 'ESPNU'],
    ['US| Fox Sports 1 HD', 'FS1'],
    ['US| CBS Sports Network', 'CBSSN'],
    ['US| Peacock', 'PEACOCK'],
    ['CA| TSN1', 'TSN1'],
  ]) {
    assert.equal(networkBugFor(playlistName), slateBug);
  }
});

/* ---- what a card is allowed to show ------------------------------------- */

test('a tier brand on a listing becomes one pill, not two', () => {
  // "SEC Network+" contains "SEC Network" as a prefix. Longest-first matching
  // plus consuming the hit is what keeps this to a single label; without it
  // the card read "SEC+ SECN".
  assert.deepEqual(extractChannels('Georgia vs Florida | SEC Network+'), ['SEC+']);
  assert.deepEqual(extractChannels('LIVE Duke vs UNC | ACC Network Extra'), ['ACC+']);
  assert.deepEqual(extractChannels('Big Ten Saturday | Big Ten Network'), ['BTN']);
});

test('ordinary multi-channel listings are untouched', () => {
  assert.deepEqual(extractChannels('Chiefs vs Bills | ESPN, TSN4, SN 3'), ['ESPN', 'TSN4', 'SN 3']);
  assert.deepEqual(extractChannels('Leafs vs Habs | Sportsnet Ontario'), ['SN ONT']);
});

test('a curated label is trusted even when it carries a dot', () => {
  // Regression: the length/dot filter meant "MLB.TV" — a curated alias —
  // could never reach a card however it was written.
  assert.deepEqual(splitChannelsBlob('MLB.TV, SEC Network+'), ['MLB.TV', 'SEC+']);
  assert.deepEqual(extractChannels('Yankees vs Red Sox | MLB.TV'), ['MLB.TV']);
});

test('guessed labels are still filtered, so junk stays out', () => {
  // The filter was there for a reason: a brand smashed together with a team
  // name must not become a pill.
  const out = splitChannelsBlob('Some.Random.Token, Another Dotted.Thing');
  assert.deepEqual(out, []);
});

/* ---- end to end: the niche feed is found -------------------------------- */

test('a game on ESPN+ finds the numbered ESPN+ feed in the playlist', () => {
  // The payoff. This is the path a college football Saturday actually takes.
  const { ok, channels } = parseM3U([
    '#EXTM3U',
    '#EXTINF:-1 tvg-id="espn1" group-title="US Sports",US| ESPN HD',
    'http://p.example/espn.m3u8',
    '#EXTINF:-1 group-title="US Sports",US| ESPN+ 1 HD',
    'http://p.example/espnplus1.m3u8',
    '#EXTINF:-1 group-title="US Sports",US| ESPN+ 2 HD',
    'http://p.example/espnplus2.m3u8',
    '#EXTINF:-1 group-title="US Sports",US| SEC Network+ HD',
    'http://p.example/secplus.m3u8',
  ].join('\n'));
  assert.equal(ok, true);

  const college = {
    channels: ['ESPN+'],
    away: { name: 'Georgia Bulldogs', abbr: 'UGA' },
    home: { name: 'Florida Gators', abbr: 'FLA' },
  };
  const matches = matchChannels(college, channels);
  assert.equal(matches.length, 2, 'both ESPN+ event feeds should match the tier');
  assert.ok(matches.every((m) => m.reason === 'network'));
  assert.ok(matches.every((m) => m.bug === 'ESPN+'));
  assert.ok(!matches.some((m) => /SEC Network\+/.test(m.name)), 'the SEC+ feed is a different channel');

  const secGame = {
    channels: ['SEC+'],
    away: { name: 'Auburn Tigers', abbr: 'AUB' },
    home: { name: 'LSU Tigers', abbr: 'LSU' },
  };
  const secMatches = matchChannels(secGame, channels);
  assert.equal(secMatches.length, 1);
  assert.equal(secMatches[0].name, 'US| SEC Network+ HD');
});

/* ---- the College pill --------------------------------------------------- */

test('there is a College pill, and it covers both college leagues', () => {
  const college = SPORT_GROUPS.find((g) => g.id === 'sport:college');
  assert.ok(college, 'sport:college is missing from SPORT_GROUPS');
  assert.equal(college.label, 'College');
  assert.deepEqual([...college.leagues].sort(), ['ncaab', 'ncaaf']);
});

test('every league a sport group names exists', () => {
  // A typo here is silent: the rail counts `LEAGUES[id]?.label` and a missing
  // id simply contributes nothing, so the pill shows a lower number forever.
  for (const group of SPORT_GROUPS) {
    for (const id of group.leagues) {
      assert.ok(LEAGUES[id], `${group.id} names unknown league "${id}"`);
    }
    assert.ok(group.leagues.length > 0, `${group.id} has no leagues`);
  }
});

test('every sport group label is what the rail will show', () => {
  const labels = SPORT_GROUPS.map((g) => g.label);
  assert.equal(new Set(labels).size, labels.length, 'two sport groups share a label');
  assert.ok(labels.every((l) => l.length > 0 && l.length <= 14), 'a pill label is too long for the rail');
});
