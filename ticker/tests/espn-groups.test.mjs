import test from 'node:test';
import assert from 'node:assert/strict';

import { espnScoreboardUrls, loadEspnLeague } from '../lib/scoreboard.mjs';

const payload = (...ids) => ({
  events: ids.map((id) => ({
    id,
    name: `A at B ${id}`,
    shortName: `A @ B`,
    date: '2026-09-26T19:00Z',
    status: { type: { state: 'pre', completed: false } },
    competitions: [{ competitors: [
      { homeAway: 'home', team: { abbreviation: `H${id}`, displayName: `Home ${id}` } },
      { homeAway: 'away', team: { abbreviation: `A${id}`, displayName: `Away ${id}` } },
    ], broadcasts: [{ names: ['ESPN2'] }] }],
  })),
});

test('college football asks for FBS and FCS separately', () => {
  // One groups=90 request is ~1.7MB, over the 1.5MB feed cap on both paths.
  const urls = espnScoreboardUrls('ncaaf');
  assert.equal(urls.length, 2);
  assert.match(urls[0], /college-football\/scoreboard\?groups=80$/);
  assert.match(urls[1], /college-football\/scoreboard\?groups=81$/);
});

test('a league without groups is still one plain request', () => {
  const urls = espnScoreboardUrls('nfl');
  assert.deepEqual(urls, ['https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard']);
  assert.deepEqual(espnScoreboardUrls('nope'), []);
});

test('groups are merged and a game listed twice appears once', async () => {
  const load = async (url) => (url.endsWith('=80') ? payload('1', '2') : payload('2', '3'));
  const events = await loadEspnLeague('ncaaf', load);
  assert.deepEqual(events.map((e) => e.id).sort(), ['1', '2', '3']);
});

test('one group failing still shows the other', async () => {
  const load = async (url) => {
    if (url.endsWith('=81')) throw new Error('http 503');
    return payload('1');
  };
  const events = await loadEspnLeague('ncaaf', load);
  assert.deepEqual(events.map((e) => e.id), ['1']);
});

test('every group failing is an error, so fallback and backoff still run', async () => {
  const load = async () => { throw new Error('http 503'); };
  await assert.rejects(loadEspnLeague('ncaaf', load), /503/);
});
