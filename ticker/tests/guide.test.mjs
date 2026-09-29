import test from 'node:test';
import assert from 'node:assert/strict';

import {
  GUIDE_VERSION, guideWindow, matchupFrom, isLiveEvent, mapGuideChannels,
  sideMatchesTeam, findEvent, mergeGuide, maskUrl,
} from '../lib/guide.mjs';

const H = 3600e3;
const NOW = Date.parse('2026-09-26T18:00:00Z');
const prog = (o) => ({ c: 'sky.me', s: NOW - H, e: NOW + H, t: '', st: '', cat: ['Sport'], live: false, rerun: false, ...o });

/* ---- matchups ---- */

test('matchupFrom reads the title styles guides actually use', () => {
  const cases = [
    ['Live Premier League: Arsenal v Chelsea', 'Arsenal', 'Chelsea', 'Premier League'],
    ['Arsenal v Chelsea', 'Arsenal', 'Chelsea', ''],
    ['College Football: Harvard vs. Brown', 'Harvard', 'Brown', 'College Football'],
    ['NFL: Kansas City Chiefs @ Buffalo Bills', 'Kansas City Chiefs', 'Buffalo Bills', 'NFL'],
    ['LIVE: Celtic versus Rangers (Kick-off 12.00)', 'Celtic', 'Rangers', ''],
    ['Wrexham v Stoke City - Championship', 'Wrexham', 'Stoke City', ''],
  ];
  for (const [title, away, home, comp] of cases) {
    const m = matchupFrom(title);
    assert.ok(m, title);
    assert.equal(m.away, away, title);
    assert.equal(m.home, home, title);
    assert.equal(m.competition, comp, title);
  }
});

test('matchupFrom returns null when there are not two sides', () => {
  for (const t of ['Wimbledon', 'Premier League Football', 'Match of the Day', '', 'v', 'A v B v C']) {
    assert.equal(matchupFrom(t), null, t);
  }
});

/* ---- live-event filter ---- */

test('live events pass: marked live, a Live prefix, or a new matchup', () => {
  assert.equal(isLiveEvent(prog({ t: 'Premier League', live: true })), true);
  assert.equal(isLiveEvent(prog({ t: 'Live Premier League: Arsenal v Chelsea' })), true);
  assert.equal(isLiveEvent(prog({ t: 'Arsenal v Chelsea' })), true);
  assert.equal(isLiveEvent(prog({ t: 'College Football', st: 'Harvard vs Brown' })), true);
});

test('studio, news, highlights and repeats do not', () => {
  const noise = [
    prog({ t: 'Premier League Highlights' }),
    prog({ t: 'Sky Sports News', live: true }),
    prog({ t: 'Match of the Day' }),
    prog({ t: 'NFL Review: Chiefs v Bills' }),
    prog({ t: 'Arsenal v Chelsea', rerun: true }),
    prog({ t: 'Classic Ashes: England v Australia' }),
    prog({ t: 'Arsenal v Chelsea', cat: ['Drama'] }),
  ];
  for (const p of noise) assert.equal(isLiveEvent(p), false, `${p.t} ${p.rerun ? '(rerun)' : ''}`);
  // A repeat that is marked live is a live event re-aired as it happens.
  assert.equal(isLiveEvent(prog({ t: 'Arsenal v Chelsea', rerun: true, live: true })), true);
});

test('the filter matches words, not substrings', () => {
  // "Newcastle" contains "news"; "Showdown" contains "show".
  assert.equal(isLiveEvent(prog({ t: 'Newcastle v Sunderland' })), true);
  assert.equal(isLiveEvent(prog({ t: 'Live Boxing: Friday Showdown', live: true })), true);
});

/* ---- channels ---- */

const PLAYLIST = [
  { name: 'UK| Sky Sports Main Event HD', url: 'http://p.example/live/u/p/1.ts', tvgId: 'SkyMainEvent.uk', tvgName: '' },
  { name: 'US| ESPN2 HD', url: 'http://p.example/live/u/p/2.ts', tvgId: '', tvgName: 'ESPN2' },
  { name: 'US| ESPN2 FHD', url: 'http://p.example/live/u/p/3.ts', tvgId: 'espn2.us', tvgName: '' },
];

test('guide channels map to playlist channels by id, then by name', () => {
  const guide = { channels: {
    'skymainevent.uk': { name: 'Sky Sports Main Event' }, // id differs only in case
    'ESPN2.guide': { name: 'ESPN2' }, // no id match; name matches tvg-name
    'bbc1.uk': { name: 'BBC One' }, // the viewer does not have it
  } };
  const m = mapGuideChannels(guide, PLAYLIST);
  assert.deepEqual(m.get('skymainevent.uk'), [0]);
  // Both of the viewer's ESPN2 streams carry it: one by tvg-name, one by name.
  assert.deepEqual(m.get('ESPN2.guide'), [1, 2]);
  assert.equal(m.has('bbc1.uk'), false);
});

/* ---- team matching ---- */

test('a side matches a team by full name, abbreviation, or a distinctive word', () => {
  const chiefs = { name: 'Kansas City Chiefs', abbr: 'KC' };
  assert.equal(sideMatchesTeam('Kansas City Chiefs', chiefs), true);
  assert.equal(sideMatchesTeam('Chiefs', chiefs), true);
  assert.equal(sideMatchesTeam('KC', chiefs), true);
  assert.equal(sideMatchesTeam('Bills', chiefs), false);
  assert.equal(sideMatchesTeam('Man Utd', { name: 'Manchester United', abbr: 'MUN' }), true);
});

test('a generic word alone never matches', () => {
  // "City" and "United" are in half the league.
  assert.equal(sideMatchesTeam('City', { name: 'Manchester City', abbr: 'MNC' }), false);
  assert.equal(sideMatchesTeam('United', { name: 'Manchester United', abbr: 'MUN' }), false);
  assert.equal(sideMatchesTeam('Man City', { name: 'Manchester United', abbr: 'MUN' }), false);
  assert.equal(sideMatchesTeam('Man City', { name: 'Manchester City', abbr: 'MNC' }), true);
  assert.equal(sideMatchesTeam('Kansas City', { name: 'Kansas City Chiefs', abbr: 'KC' }), true);
});

const GAME = {
  id: 'espn:1', source: 'espn', league: 'NCAAF', status: 'upcoming', start: new Date(NOW + 0.5 * H).toISOString(),
  channels: ['ESPN2'], away: { name: 'Harvard Crimson', abbr: 'HARV' }, home: { name: 'Brown Bears', abbr: 'BRWN' },
};

test('findEvent needs both teams, either order, and a start near the slot', () => {
  const p = prog({ t: 'College Football', st: 'Harvard vs Brown' });
  assert.equal(findEvent([GAME], p, matchupFrom(p.st)), GAME);
  assert.equal(findEvent([GAME], p, matchupFrom('Brown v Harvard')), GAME);
  assert.equal(findEvent([GAME], p, matchupFrom('Harvard v Yale')), null);
  const tomorrow = prog({ s: NOW + 24 * H, e: NOW + 27 * H, t: 'Harvard v Brown' });
  assert.equal(findEvent([GAME], tomorrow, matchupFrom(tomorrow.t)), null, 'same teams, a different day');
});

/* ---- merge ---- */

const GUIDE = {
  v: GUIDE_VERSION,
  fetchedAt: NOW,
  channels: { 'espn2.us': { name: 'ESPN2' }, 'SkyMainEvent.uk': { name: 'Sky Sports Main Event' } },
  programmes: [
    prog({ c: 'espn2.us', t: 'College Football', st: 'Harvard vs. Brown', s: NOW, e: NOW + 3 * H }),
    prog({ c: 'SkyMainEvent.uk', t: 'Live Premier League: Arsenal v Chelsea', live: true, s: NOW - 0.5 * H, e: NOW + 1.5 * H }),
    prog({ c: 'SkyMainEvent.uk', t: 'Premier League Highlights', s: NOW + 2 * H, e: NOW + 3 * H }),
    prog({ c: 'SkyMainEvent.uk', t: 'Live Premier League: Spurs v Wolves', live: true, s: NOW - 5 * H, e: NOW - 3 * H }),
  ],
};

test('mergeGuide attaches the viewer channel to a scoreboard game and adds what it lacks', () => {
  const { events, matched, added } = mergeGuide([GAME], GUIDE, PLAYLIST, { now: NOW });
  assert.equal(matched, 1);
  assert.equal(added, 1, 'Arsenal v Chelsea; not the highlights, not the finished game');
  const game = events.find((e) => e.id === 'espn:1');
  assert.deepEqual(game.guideChannels.map((c) => c.url), ['http://p.example/live/u/p/3.ts']);
  assert.deepEqual(game.channels, ['ESPN2'], 'the scoreboard named a channel, so it is kept');
  const epl = events.find((e) => e.source === 'epg');
  assert.equal(epl.status, 'live');
  assert.equal(epl.away.name, 'Arsenal');
  assert.equal(epl.home.name, 'Chelsea');
  assert.equal(epl.detail, 'Premier League');
  assert.deepEqual(epl.guideChannels.map((c) => c.tvgId), ['SkyMainEvent.uk']);
  assert.equal(epl.channels.length, 1);
  assert.equal(GAME.guideChannels, undefined, 'the input is not mutated');
});

test('a scoreboard game with no channel takes the guide channel names', () => {
  const bare = { ...GAME, channels: [] };
  const { events } = mergeGuide([bare], GUIDE, PLAYLIST, { now: NOW });
  assert.deepEqual(events[0].channels, ['ESPN2']);
});

test('one event on several channels is one card', () => {
  const guide = { ...GUIDE, programmes: [
    prog({ c: 'espn2.us', t: 'Live: Arsenal v Chelsea', live: true }),
    prog({ c: 'SkyMainEvent.uk', t: 'Live Premier League: Arsenal v Chelsea', live: true }),
  ] };
  const { events, added } = mergeGuide([], guide, PLAYLIST, { now: NOW });
  assert.equal(added, 1);
  assert.equal(events[0].channels.length, 2);
  assert.equal(events[0].guideChannels.length, 2);
});

test('guide-only games are capped', () => {
  const programmes = Array.from({ length: 200 }, (_, i) => prog({ c: 'espn2.us', t: `Team${i} v Other${i}`, s: NOW + i * 60e3, e: NOW + i * 60e3 + 2 * H }));
  const { added } = mergeGuide([], { ...GUIDE, programmes }, PLAYLIST, { now: NOW, maxGuideOnly: 60 });
  assert.equal(added, 60);
});

test('a guide the viewer cannot watch, or a stale shape, changes nothing', () => {
  assert.equal(mergeGuide([GAME], GUIDE, [], { now: NOW }).events[0], GAME);
  assert.equal(mergeGuide([GAME], { ...GUIDE, v: 99 }, PLAYLIST, { now: NOW }).events[0], GAME);
  assert.equal(mergeGuide([GAME], null, PLAYLIST, { now: NOW }).events.length, 1);
});

/* ---- window ---- */

test('the window runs from four hours ago to the end of tomorrow, local time', () => {
  const prev = process.env.TZ;
  try {
    for (const tz of ['UTC', 'America/New_York', 'Europe/London', 'Australia/Sydney']) {
      process.env.TZ = tz;
      const { start, end } = guideWindow(NOW);
      assert.equal(start, NOW - 4 * H, tz);
      const endLocal = new Date(end);
      assert.equal(endLocal.getHours(), 0, tz);
      assert.equal(endLocal.getMinutes(), 0, tz);
      assert.ok(end - NOW > 24 * H && end - NOW <= 48 * H + H, `${tz}: ${(end - NOW) / H}h`);
    }
    // Across the spring-forward night the window still ends at local midnight.
    process.env.TZ = 'Europe/London';
    const dst = Date.parse('2026-03-28T12:00:00Z');
    assert.equal(new Date(guideWindow(dst).end).getHours(), 0);
  } finally {
    if (prev === undefined) delete process.env.TZ; else process.env.TZ = prev;
  }
});

/* ---- privacy ---- */

test('maskUrl shows the host and nothing that could hold the account', () => {
  assert.equal(maskUrl('http://prov.example:8080/get.php?username=joe&password=hunter2&type=m3u_plus'), 'http://prov.example:8080/…');
  assert.equal(maskUrl('http://prov.example/live/joe/hunter2/1.ts'), 'http://prov.example/…');
  assert.equal(maskUrl('https://joe:hunter2@prov.example/xmltv.php'), 'https://prov.example/…');
  for (const u of ['http://prov.example/get.php?username=joe&password=hunter2', 'https://joe:hunter2@prov.example/x']) {
    assert.doesNotMatch(maskUrl(u), /joe|hunter2/);
  }
  assert.equal(maskUrl('not a url'), '(invalid link)');
});
