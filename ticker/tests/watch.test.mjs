/**
 * "Watch" integration (lib/watch.mjs) — assign an app per league, build the
 * web fallback URL. Pure logic, no network.
 */
import test from 'node:test';
import assert from 'node:assert/strict';

import {
  WATCH_WEB, SPORTS_APPS, sortAppsForPicker, leagueIdForLabel, espnWebUrl, watchChoiceFor,
} from '../lib/watch.mjs';

test('sortAppsForPicker puts curated sports apps first, dedupes, then alpha', () => {
  const apps = [
    { pkg: 'com.random.zoo', label: 'Zoo' },
    { pkg: 'com.dazn', label: 'DAZN' },
    { pkg: 'com.random.apple', label: 'Apple' },
    { pkg: 'com.dazn', label: 'DAZN duplicate' },
    null,
    { pkg: '', label: 'empty pkg' },
  ];
  const out = sortAppsForPicker(apps);
  const pkgs = out.map((a) => a.pkg);
  assert.equal(pkgs[0], 'com.dazn'); // curated first
  assert.equal(pkgs.filter((p) => p === 'com.dazn').length, 1); // deduped
  assert.ok(!pkgs.includes('')); // empties dropped
  const rest = pkgs.slice(1).sort();
  assert.deepEqual(pkgs.slice(1), rest); // alpha after curated
});

test('SPORTS_APPS entries are well-formed and unique', () => {
  const pkgs = SPORTS_APPS.map((a) => a.pkg);
  assert.equal(new Set(pkgs).size, pkgs.length);
  for (const a of SPORTS_APPS) assert.ok(a.pkg && a.label);
});

test('leagueIdForLabel maps display labels back to league ids', () => {
  assert.equal(leagueIdForLabel('NFL'), 'nfl');
  assert.equal(leagueIdForLabel('NCAAF'), 'ncaaf');
  assert.equal(leagueIdForLabel('Basketball'), null); // sport group, not a league
});

test('espnWebUrl builds a canonical ESPN game URL for espn events', () => {
  const url = espnWebUrl({ source: 'espn', league: 'NFL', id: '4012345' });
  assert.equal(url, 'https://www.espn.com/nfl/game/_/gameId/4012345');
});

test('espnWebUrl falls back to a search URL for non-numeric ids', () => {
  const url = espnWebUrl({ league: 'NHL', id: 'nhl-123', away: { name: 'Maple Leafs' }, home: { name: 'Canadiens' } });
  assert.ok(url.startsWith('https://www.espn.com/search/_/q/'));
  assert.ok(url.includes('Maple'));
});

test('espnWebUrl uses soccer match route for soccer leagues', () => {
  const url = espnWebUrl({ league: 'EPL', id: '4012345' });
  assert.equal(url, 'https://www.espn.com/soccer/match/_/gameId/4012345');
});

test('espnWebUrl uses UFC fightcenter route for MMA', () => {
  const url = espnWebUrl({ league: 'UFC', id: '4012345' });
  assert.equal(url, 'https://www.espn.com/mma/fightcenter/_/fightId/4012345');
});

test('espnWebUrl uses F1 race results route for racing', () => {
  const url = espnWebUrl({ league: 'F1', id: '4012345' });
  assert.equal(url, 'https://www.espn.com/f1/race/_/raceId/4012345');
});

test('watchChoiceFor defaults to web and honours per-league assignment', () => {
  assert.equal(watchChoiceFor({}, 'NFL'), WATCH_WEB);
  assert.equal(watchChoiceFor({ nfl: 'com.espn.score_center' }, 'NFL'), 'com.espn.score_center');
  // falls back to the label key, and 'web' wins explicitly
  assert.equal(watchChoiceFor({ NFL: 'com.dazn' }, 'NFL'), 'com.dazn');
  assert.equal(watchChoiceFor({ nfl: WATCH_WEB, NFL: 'com.dazn' }, 'NFL'), WATCH_WEB);
});
