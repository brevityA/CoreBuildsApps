#!/usr/bin/env node
/**
 * Probe every score source side by side.
 *
 *     node tools/probe-sources.mjs
 *
 * Core Line asks ESPN first and only falls back to the leagues' own feeds when
 * ESPN fails. ESPN's endpoint is undocumented, unsupported, and repeatedly
 * flagged as a possible breach of their terms; the league feeds are the
 * leagues' own public data. Reversing that order is a two-line change, but only
 * worth making if the league feeds carry everything the ESPN ones do — above
 * all the broadcast channels, which are what channel matching runs on.
 *
 * That cannot be checked from a sandbox with no outbound network, so it is
 * checked here instead. Run this on a networked machine and read the table.
 *
 * Exit code is 0 regardless; this is a report, not a gate.
 */

import {
  espnScoreboardUrl, eventsFromEspn, eventsFromNhl, eventsFromMlb, LEAGUES,
} from '../lib/scoreboard.mjs';

const UA = 'CoreLine/1.0 (+https://github.com/brevityA/CoreBuildsApps)';
const TIMEOUT_MS = 15000;

/** Endpoints known to exist per league. An empty list means ESPN is the only option. */
const FIRST_PARTY = {
  nhl: () => 'https://api-web.nhle.com/v1/score/now',
  mlb: () => {
    const today = new Date().toISOString().slice(0, 10);
    return `https://statsapi.mlb.com/api/v1/schedule?sportId=1&date=${today}&hydrate=team,linescore,broadcasts(all)`;
  },
  // Deliberately empty: neither league publishes a usable public feed.
  // NBA's stats.nba.com blocks non-browser clients; the NFL has none at all.
  nba: null,
  nfl: null,
};

async function getJson(url) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const res = await fetch(url, {
      headers: { 'user-agent': UA, accept: 'application/json' },
      signal: controller.signal,
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
  } finally {
    clearTimeout(timer);
  }
}

function describe(events) {
  return {
    events: events.length,
    channels: events.filter((e) => e.channels?.length).length,
    live: events.filter((e) => e.status === 'live').length,
    final: events.filter((e) => e.status === 'final').length,
    upcoming: events.filter((e) => e.status === 'upcoming').length,
    venues: events.filter((e) => e.venue).length,
    sample: events.find((e) => e.channels?.length) || null,
  };
}

function row(label, res) {
  if (!res) {
    console.log(`  ${label.padEnd(14)} ${'(no first-party feed)'.padEnd(34)}`);
    return;
  }
  if (res.error) {
    console.log(`  ${label.padEnd(14)} FAILED: ${res.error}`);
    return;
  }
  const d = res.data;
  const pct = d.events ? Math.round((d.channels / d.events) * 100) : 0;
  console.log(
    `  ${label.padEnd(14)} events=${String(d.events).padStart(3)}`
    + `  channels=${String(d.channels).padStart(3)} (${String(pct).padStart(3)}%)`
    + `  live=${String(d.live).padStart(2)} final=${String(d.final).padStart(2)}`
    + ` upcoming=${String(d.upcoming).padStart(2)}  venue=${String(d.venues).padStart(3)}`,
  );
  if (d.sample) {
    console.log(`  ${''.padEnd(14)} e.g. ${d.sample.away.abbr} @ ${d.sample.home.abbr}`
      + `  [${d.sample.channels.slice(0, 4).join(', ')}]`
      + (d.sample.venue ? `  @ ${d.sample.venue}` : ''));
  }
}

async function probe(label, url, parse) {
  try {
    return { data: describe(parse(await getJson(url))) };
  } catch (err) {
    return { error: err?.name === 'AbortError' ? `timed out after ${TIMEOUT_MS}ms` : (err?.message || 'fetch failed') };
  }
}

console.log(`\nCore Line source probe — ${new Date().toISOString()}\n`);
console.log('  "channels" is the number that matters: channel matching runs on it.\n');

const order = ['nhl', 'mlb', 'nba', 'nfl'];
const results = {};

for (const id of order) {
  const league = LEAGUES[id];
  console.log(`${(league?.label || id).toUpperCase()}`);
  results[id] = { espn: await probe('ESPN', espnScoreboardUrl(id), (j) => eventsFromEspn(j, id)) };
  row('ESPN', results[id].espn);

  const build = FIRST_PARTY[id];
  if (build) {
    const parse = id === 'nhl' ? eventsFromNhl : eventsFromMlb;
    results[id].first = await probe('first-party', build(), parse);
    row('first-party', results[id].first);
  } else {
    row('first-party', null);
  }
  console.log('');
}

/* ------------------------------------------------------------- the verdict -- */

console.log('Verdict\n');

for (const id of order) {
  const r = results[id];
  if (!r.first) {
    console.log(`  ${id.toUpperCase().padEnd(6)} ESPN is the only option. No decision to make.`);
    continue;
  }
  const espn = r.espn?.data;
  const first = r.first?.data;
  if (!espn || !first) {
    console.log(`  ${id.toUpperCase().padEnd(6)} one side failed — re-run when both respond before deciding.`);
    continue;
  }
  const channelRatio = espn.events ? first.channels / Math.max(espn.channels, 1) : 0;
  if (first.events === 0 && espn.events > 0) {
    console.log(`  ${id.toUpperCase().padEnd(6)} first-party returned nothing while ESPN returned ${espn.events}.`
      + ' Check the date/season before reordering — an empty feed may just be the offseason.');
  } else if (channelRatio >= 1) {
    console.log(`  ${id.toUpperCase().padEnd(6)} reorder: the league feed carries at least as much as ESPN`
      + ` (${first.channels} vs ${espn.channels} games with channels), and it is first-party data.`);
  } else {
    console.log(`  ${id.toUpperCase().padEnd(6)} keep ESPN first: the league feed covers only`
      + ` ${Math.round(channelRatio * 100)}% of ESPN's channel coverage, and channels are the product.`);
  }
}

console.log('\nReminder: none of these endpoints are documented or licensed. Preferring first-party');
console.log('data lowers the exposure; it does not remove it. The RSS/JSON feed feature remains the');
console.log('only source with no provenance question at all.\n');
