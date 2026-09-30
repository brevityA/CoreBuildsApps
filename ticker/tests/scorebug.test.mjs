import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import {
  scoreBugConfig,
  scoreBugQueue,
  scoreBugAt,
  scoreBugStatus,
  scoreBugHtml,
  SCOREBUG_POSITIONS,
  SCOREBUG_OPACITY,
} from '../lib/scorebug.mjs';

/**
 * The scoreboard bug: the pure half executed, the seams around it asserted
 * the way vpn-ui.test.mjs asserts the dot's — a control wired to an id that
 * does not exist, or a JS call to a bridge method Android never exposes,
 * fails silently in production and loudly here.
 */

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..');
const read = (rel) => readFileSync(join(root, rel), 'utf8');

const NOW = Date.parse('2026-09-30T20:00:00Z');
const game = (id, status, away, home, extra = {}) => ({
  id,
  status,
  source: 'espn',
  league: 'NHL',
  detail: status === 'live' ? 'P2 04:11' : '',
  start: new Date(NOW + (extra.inMin ?? 0) * 60e3).toISOString(),
  channels: ['TSN4'],
  away: { abbr: away, name: `${away} Team`, score: status === 'live' ? 1 : null, logo: null },
  home: { abbr: home, name: `${home} Team`, score: status === 'live' ? 2 : null, logo: null },
  ...extra,
});

/* ---- Queue ---------------------------------------------------------------- */

test('live games fill the bug, the viewer’s teams first, finals never', () => {
  const events = [
    game('a', 'live', 'BOS', 'NYR'),
    game('b', 'final', 'TOR', 'MTL'),
    game('c', 'live', 'EDM', 'CGY'),
    game('d', 'upcoming', 'VAN', 'SEA', { inMin: 30 }),
  ];
  const q = scoreBugQueue(events, { favorites: 'CGY', now: NOW });
  assert.deepEqual(q.map((e) => e.id), ['c', 'a'], 'favourite first, no final, no upcoming while games are live');
});

test('a scoreboard game outranks a feed item that only says LIVE', () => {
  const rss = { ...game('r', 'live', 'AAA', 'BBB'), source: 'rss' };
  const q = scoreBugQueue([rss, game('s', 'live', 'CCC', 'DDD')], { now: NOW });
  assert.deepEqual(q.map((e) => e.id), ['s', 'r']);
});

test('with nothing live it shows the next games within the lookahead, soonest first', () => {
  const events = [
    game('late', 'upcoming', 'A', 'B', { inMin: 13 * 60 }), // past 12 h
    game('soon', 'upcoming', 'C', 'D', { inMin: 20 }),
    game('sooner', 'upcoming', 'E', 'F', { inMin: 5 }),
    game('headline', 'upcoming', null, null, { away: null, home: null, inMin: 1 }),
  ];
  const q = scoreBugQueue(events, { now: NOW });
  assert.deepEqual(q.map((e) => e.id), ['sooner', 'soon']);
});

test('nothing on and nothing soon: an empty queue, so the window shows nothing', () => {
  assert.deepEqual(scoreBugQueue([game('f', 'final', 'A', 'B')], { now: NOW }), []);
  assert.deepEqual(scoreBugQueue(null, { now: NOW }), []);
  assert.equal(scoreBugAt([], 3), null);
});

test('the rotation wraps in both directions', () => {
  const q = ['x', 'y', 'z'];
  assert.equal(scoreBugAt(q, 0), 'x');
  assert.equal(scoreBugAt(q, 4), 'y');
  assert.equal(scoreBugAt(q, -1), 'z');
});

/* ---- Markup --------------------------------------------------------------- */

test('a live game reads like a broadcast bug: LIVE, league, clock, abbreviations, scores, channel', () => {
  const html = scoreBugHtml(game('a', 'live', 'TOR', 'MTL'), { position: 0, total: 3 });
  assert.match(html, /class="sb is-live"/);
  assert.match(html, />LIVE</);
  assert.match(html, />NHL</);
  assert.match(html, />P2 04:11</);
  assert.match(html, /sb__abbr">TOR</);
  assert.match(html, /sb__abbr">MTL</);
  assert.match(html, /sb__score">1</);
  assert.match(html, /sb__score">2</);
  assert.match(html, />TSN4</);
  assert.match(html, />1<\/span>\/<span|1\/3/);
});

test('an upcoming game says when, and shows no 0–0', () => {
  const html = scoreBugHtml(game('u', 'upcoming', 'VAN', 'SEA'), { startLabel: () => '7:00 PM' });
  assert.match(html, /class="sb is-next"/);
  assert.match(html, /Next · 7:00 PM/);
  assert.doesNotMatch(html, /sb__score/);
  assert.match(html, /sb__sep">@</);
  assert.equal(scoreBugStatus(game('u', 'upcoming', 'A', 'B')), 'Next');
});

test('every remote value is inert', () => {
  const evil = '<img src=x onerror=alert(1)>';
  const ev = game('e', 'live', evil, 'MTL', {
    league: evil,
    detail: evil,
    channels: [evil],
  });
  ev.away.score = evil;
  const html = scoreBugHtml(ev, { position: 0, total: 1 });
  assert.doesNotMatch(html, /<img src=x/);
  assert.doesNotMatch(html, /<[^>]*onerror=/, 'an attribute survived escaping');
  assert.match(html, /&lt;img src=x onerror=alert\(1\)&gt;/, 'the text is still shown, as text');
});

/* ---- Config --------------------------------------------------------------- */

test('the config is sanitised, with the panel’s overscan folded into the margin', () => {
  assert.deepEqual(scoreBugConfig({}), { position: 'top_center', opacity: SCOREBUG_OPACITY.default, marginDp: 16 });
  assert.deepEqual(
    scoreBugConfig({ scoreBugPosition: 'top_right', scoreBugOpacity: 5 }, { overscanPx: 64, devicePixelRatio: 2 }),
    { position: 'top_right', opacity: SCOREBUG_OPACITY.min, marginDp: 48 },
  );
  assert.equal(scoreBugConfig({ scoreBugPosition: 'bottom_right' }).position, 'top_center', 'top edge only');
  assert.equal(scoreBugConfig({}, { overscanPx: -500 }).marginDp, 16, 'a corrupt overscan never eats the breathing room');
});

/* ---- Seams ---------------------------------------------------------------- */

const settingsJs = read('public/js/ui/settings.js');
const indexHtml = read('public/index.html');
const stateJs = read('public/js/state.js');
const appJs = read('public/js/app.js');
const lineBridge = read('android/app/src/main/java/dev/corebuilds/line/LineBridge.kt');
const mainActivity = read('android/app/src/main/java/dev/corebuilds/line/MainActivity.kt');
const configKt = read('android/app/src/main/java/dev/corebuilds/line/ScoreBugConfig.kt');
const windowKt = read('android/app/src/main/java/dev/corebuilds/line/ScoreBugWindow.kt');
const serviceKt = read('android/app/src/main/java/dev/corebuilds/line/OverlayService.kt');

test('the scoreboard controls exist and sit inside the native overlay block', () => {
  const blockStart = indexHtml.indexOf('id="overlayBlock"');
  const nextSection = indexHtml.indexOf('Favorite teams');
  const block = indexHtml.slice(blockStart, nextSection);
  for (const id of ['scoreBugEnabled', 'scoreBugPosition', 'scoreBugOpacity', 'scoreBugOpacityVal']) {
    assert.ok(block.includes(`id="${id}"`), `${id} is missing or outside the overlay block`);
  }
  for (const pos of SCOREBUG_POSITIONS) {
    assert.ok(block.includes(`value="${pos}"`), `no option for ${pos}`);
  }
  for (const action of ['score-bug-dim', 'score-bug-bright']) {
    assert.ok(block.includes(`data-action="${action}"`), `no ${action} button`);
    assert.ok(appJs.includes(`'${action}'`), `app.js does not handle ${action}`);
  }
});

test('every scoreboard bridge call is implemented on both sides of the Kotlin bridge', () => {
  const calls = [...settingsJs.matchAll(/\?\.(\w*[sS]coreBug\w*)\?\.\(/g)].map((m) => m[1]);
  assert.ok(new Set(calls).size >= 4, `expected start/stop/set/active, saw ${[...new Set(calls)]}`);
  for (const name of new Set(calls)) {
    assert.match(lineBridge, new RegExp(`fun ${name}\\(`), `LineBridge.kt has no ${name}`);
    assert.match(mainActivity, new RegExp(`fun ${name}\\(`), `MainActivity.kt has no ${name}`);
  }
});

test('the settings the drawer writes are the settings state sanitises', () => {
  for (const key of ['scoreBug', 'scoreBugPosition', 'scoreBugOpacity']) {
    assert.match(stateJs, new RegExp(`${key}:`), `${key} has no default`);
    assert.match(stateJs, new RegExp(`out\\.${key} =`), `${key} is never sanitised on load`);
  }
});

test('the native config accepts exactly the positions and range the web side sends', () => {
  for (const pos of SCOREBUG_POSITIONS) assert.ok(configKt.includes(`"${pos}"`), `ScoreBugConfig.kt does not know ${pos}`);
  assert.match(configKt, new RegExp(`coerceIn\\(${SCOREBUG_OPACITY.min}, ${SCOREBUG_OPACITY.max}\\)`));
  assert.match(configKt, new RegExp(`opacity: Int = ${SCOREBUG_OPACITY.default}`));
});

test('the window stays out of the remote’s way, like the dot’s', () => {
  for (const flag of ['FLAG_NOT_FOCUSABLE', 'FLAG_NOT_TOUCHABLE', 'TYPE_APPLICATION_OVERLAY']) {
    assert.ok(windowKt.includes(flag), `ScoreBugWindow is missing ${flag}`);
  }
  assert.match(windowKt, /Gravity\.TOP/);
  assert.doesNotMatch(windowKt, /Gravity\.(START|END)\b/, 'absolute gravity only: RTL must not mirror the bug');
  assert.match(windowKt, /scorebug\.html\?native=1/);
});

test('a system restart never puts the crawl up unasked', () => {
  // START_STICKY redelivers a null intent. It used to fall through to
  // showTicker() whenever any surface was already running.
  const nullBranch = serviceKt.slice(serviceKt.indexOf('null -> {'), serviceKt.indexOf('else -> showTicker()'));
  assert.ok(nullBranch.length > 0, 'no explicit null-action branch');
  assert.match(nullBranch, /intent == null && !dotRunning/);
  assert.match(serviceKt, /ACTION_STOP -> \{[^}]*stopScores\(\)/, 'Stop in the notification must take the bug down too');
});
