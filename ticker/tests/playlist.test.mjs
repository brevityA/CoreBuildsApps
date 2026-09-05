import test from 'node:test';
import assert from 'node:assert/strict';

import {
  parseM3U, stripDecorations, networkBugFor, qualityRank,
  buildChannelIndex, matchChannels, MAX_CHANNELS,
} from '../lib/playlist.mjs';

const ev = (channels, awayName = 'Toronto Maple Leafs', awayAbbr = 'TOR', homeName = 'Montreal Canadiens', homeAbbr = 'MTL') => ({
  channels,
  away: { name: awayName, abbr: awayAbbr },
  home: { name: homeName, abbr: homeAbbr },
});

test('parseM3U reads EXTINF name, tvg-name and group-title', () => {
  const out = parseM3U([
    '#EXTM3U',
    '#EXTINF:-1 tvg-id="tsn4" tvg-name="TSN 4" group-title="Canada",TSN4 HD',
    'http://a.example/tsn4.m3u8',
    '#EXTINF:-1 group-title="Sports",Fox Sports',
    'https://b.example/fox',
  ].join('\n'));
  assert.equal(out.ok, true);
  assert.equal(out.count, 2);
  assert.deepEqual(out.channels[0], { name: 'TSN4 HD', url: 'http://a.example/tsn4.m3u8', group: 'Canada' });
  assert.equal(out.channels[1].group, 'Sports');
});

test('parseM3U falls back to tvg-name when nothing follows the comma', () => {
  const out = parseM3U('#EXTINF:-1 tvg-name="ESPN Full",\nhttp://x.example/e');
  assert.equal(out.ok, true);
  assert.equal(out.channels[0].name, 'ESPN Full');
});

test('parseM3U handles CRLF, #EXTGRP, plain URL lines and skips junk', () => {
  const out = parseM3U([
    '#EXTM3U',
    '#EXTGRP:Regional',
    'http://plain.example/one',           // no EXTINF → fallback name from path
    'ftp://blocked.example/two',          // non-http → skipped
    '#EXTVLCOPT:http-user-agent=UA',      // ignored, and does not eat pending name
    'not a url at all',
    '#EXTINF:-1,Named',
    'http://named.example/three',
  ].join('\r\n'));
  assert.equal(out.ok, true);
  assert.equal(out.count, 2);
  assert.equal(out.channels[0].group, 'Regional');
  assert.equal(out.channels[0].name, 'one');
  assert.equal(out.channels[1].name, 'Named');
});

test('parseM3U dedupes by URL and caps the list', () => {
  const dup = '#EXTINF:-1,A\nhttp://d.example/x\n#EXTINF:-1,B\nhttp://d.example/x\n';
  assert.equal(parseM3U(dup).count, 1);

  const many = Array.from({ length: MAX_CHANNELS + 50 }, (_, i) =>
    `#EXTINF:-1,Ch ${i}\nhttp://c.example/${i}`).join('\n');
  const out = parseM3U(many);
  assert.equal(out.ok, true);
  assert.equal(out.count, MAX_CHANNELS);
});

test('parseM3U rejects empty or channel-less input', () => {
  assert.equal(parseM3U('').ok, false);
  assert.equal(parseM3U('#EXTM3U\n#nothing here').ok, false);
});

test('stripDecorations removes country prefix, quality tags and emoji noise', () => {
  assert.equal(stripDecorations('US| FOX SPORTS UHD'), 'FOX SPORTS');
  assert.equal(stripDecorations('CA: TSN4 HD'), 'TSN4');
  assert.equal(stripDecorations('US| CA: ESPN FHD 1080'), 'ESPN');
  assert.equal(stripDecorations('FOX ⚡'), 'FOX');
  assert.equal(stripDecorations('ESPN'), 'ESPN');
  assert.equal(stripDecorations('TS4: Something'), 'TS4: Something'); // TS4 not a country code
});

test('networkBugFor collapses decorations into the shared bug space', () => {
  assert.equal(networkBugFor('US| TSN4 HD'), 'TSN4');
  assert.equal(networkBugFor('fox sports 1'), 'FS1');
  assert.equal(networkBugFor('Sportsnet One'), 'SN 1');
  assert.equal(networkBugFor('CA: sn 3 fhd'), 'SN 3');
});

test('qualityRank prefers UHD over HD over untagged over SD', () => {
  assert.ok(qualityRank('X UHD') < qualityRank('X HD'));
  assert.ok(qualityRank('X 4K') < qualityRank('X FHD'));
  assert.ok(qualityRank('X HDR') < qualityRank('X FHD'));
  assert.ok(qualityRank('X HD') < qualityRank('X SD'));
  assert.ok(qualityRank('X HD') < qualityRank('X'));
});

test('matchChannels: network match beats team-named channel', () => {
  const chans = [
    { name: 'ESPN HD', url: 'http://p/e1', group: '' },
    { name: 'Toronto Maple Leafs TV', url: 'http://p/t1', group: '' },
  ];
  const got = matchChannels(ev(['ESPN']), chans);
  assert.equal(got.length, 2);
  assert.equal(got[0].reason, 'network');
  assert.equal(got[0].name, 'ESPN HD');
  assert.equal(got[1].reason, 'team');
});

test('matchChannels: quality tiebreak inside a network tier', () => {
  const chans = [
    { name: 'US| FOX SD', url: 'http://p/sd', group: '' },
    { name: 'CA: FOX UHD', url: 'http://p/uhd', group: '' },
  ];
  const got = matchChannels(ev(['FOX']), chans);
  assert.equal(got[0].name, 'CA: FOX UHD');
});

test('matchChannels: preferred channel jumps the queue and is flagged', () => {
  const chans = [
    { name: 'US| TSN4 UHD', url: 'http://p/uhd', group: '' },
    { name: 'TSN4 SD', url: 'http://p/sd', group: '' },
  ];
  const got = matchChannels(ev(['TSN4']), chans, { preferred: { TSN4: 'TSN4 SD' } });
  assert.equal(got[0].name, 'TSN4 SD');
  assert.equal(got[0].preferred, true);
  assert.equal(got[1].preferred, false);
});

test('matchChannels: full team name, abbr and city tiers', () => {
  const chans = [
    { name: 'Toronto CityTV', url: 'http://p/city', group: '' },        // city tier only
    { name: 'Philadelphia Phillies Feed', url: 'http://p/philly', group: '' }, // no match
    { name: 'Maple Leafs Home', url: 'http://p/leafs', group: '' },     // city? no — 'maple' token, full? no
  ];
  // 'Toronto Maple Leafs' away: full name must hit a channel containing it
  const withFull = [{ name: 'Toronto Maple Leafs Game', url: 'http://p/full', group: '' }];
  assert.equal(matchChannels(ev([]), withFull)[0].reason, 'team');
  // city tier
  const city = matchChannels(ev([]), chans);
  assert.ok(city.some((m) => m.name === 'Toronto CityTV' && m.reason === 'team'));
  // abbr tier — 'TOR' token (after 4K is stripped)
  const abbrChans = [{ name: 'TOR 4K', url: 'http://p/tor', group: '' }];
  const abbrGot = matchChannels(ev([]), abbrChans);
  assert.equal(abbrGot.length, 1);
  assert.equal(abbrGot[0].reason, 'team');
  // no false positive on unrelated channel
  assert.ok(!city.some((m) => m.url === 'http://p/philly'));
});

test('matchChannels: dedupes a channel matched via two bugs, honours limit', () => {
  const chans = [{ name: 'ESPN HD', url: 'http://p/e', group: '' }];
  const got = matchChannels(ev(['ESPN', 'ESPN2']), chans); // 'ESPN HD' → bug ESPN only
  assert.equal(got.length, 1);

  const many = Array.from({ length: 12 }, (_, i) => ({ name: `ESPN feed ${i}`, url: `http://p/${i}`, group: '' }))
    .map((c) => ({ ...c, name: c.name.replace(/feed \d/, 'HD') }));
  // 'ESPN HD' normalizes to ESPN for all → 12 candidates, limited to 6
  assert.equal(matchChannels(ev(['ESPN']), many, { limit: 6 }).length, 6);
});

test('matchChannels: empty inputs are safe', () => {
  assert.deepEqual(matchChannels(ev(['ESPN']), []), []);
  assert.deepEqual(matchChannels(ev([]), [{ name: 'ESPN', url: 'u', group: '' }]), []);
  assert.deepEqual(matchChannels(null, [{ name: 'ESPN', url: 'u', group: '' }]), []);
});

test('buildChannelIndex is reusable across calls (import once, match per game)', () => {
  const chans = [
    { name: 'US| FOX UHD', url: 'http://p/1', group: '' },
    { name: 'FOX', url: 'http://p/2', group: '' },
  ];
  const index = buildChannelIndex(chans);
  const got = matchChannels(ev(['FOX']), chans, { index });
  assert.equal(got.length, 2);
  assert.equal(got[0].name, 'US| FOX UHD'); // quality tiebreak still applies
});
