/**
 * Accelerated soak for the client slate assembly path (AUDIT/VERIFICATION).
 *
 * Simulates ~50 hours of 1-minute refresh cycles against four hostile
 * sources (healthy, flaky, malformed, empty) with a controllable clock, and
 * asserts the two invariants that matter for a 24/7 lounge TV:
 *
 *   1. The ribbon is NEVER blank: every cycle returns events (or the labeled
 *      demo slate as an absolute last resort).
 *   2. Memory is bounded: heap growth after warm-up stays tiny (no leak in
 *      the registry / backoff / merge path).
 *
 * Run:  node tests/soak.mjs
 */
import assert from 'node:assert/strict';
import { buildClientSlate, getClientSlateRegistry } from '../lib/client-slate.mjs';

const GOOD = 'https://feeds.example/good.xml';
const FLAKY = 'https://feeds.example/flaky.xml';
const MALFORMED = 'https://feeds.example/malformed.xml';
const EMPTY = 'https://feeds.example/empty.xml';

const RSS = `<rss><channel>
  <item><title>Leafs vs Habs — TSN4, SN 3</title></item>
  <item><title>LIVE Chiefs vs Bills | CBS / NFL Network</title></item>
  <item><title>Blue Jays vs Yankees - SN 1 / ESPN / TVA</title></item>
</channel></rss>`;

let fakeNow = 1_000_000_000;
const realNow = Date.now;
Date.now = () => fakeNow;

let flakyCalls = 0;
globalThis.fetch = async (input) => {
  const url = String(input);
  if (url.includes(GOOD)) return new Response(RSS, { status: 200 });
  if (url.includes(FLAKY)) {
    flakyCalls += 1;
    // fails on every 3rd attempt, like a flapping feed
    return flakyCalls % 3 === 0
      ? new Response('gateway error', { status: 502 })
      : new Response(RSS, { status: 200 });
  }
  if (url.includes(MALFORMED)) return new Response('<<< not xml or json >>>\x00\x01', { status: 200 });
  if (url.includes(EMPTY)) return new Response('', { status: 200 });
  if (url.includes('site.api.espn.com')) return new Response('{}', { status: 200 });
  return new Response('not found', { status: 404 });
};

const feeds = [
  { url: GOOD, label: 'Good' },
  { url: FLAKY, label: 'Flaky' },
  { url: MALFORMED, label: 'Malformed' },
  { url: EMPTY, label: 'Empty' },
];

const registry = getClientSlateRegistry();
registry.clear();

const heap = () => process.memoryUsage().heapUsed;

async function main() {
  // warm-up
  for (let i = 0; i < 100; i++) {
    fakeNow += 60_000;
    await buildClientSlate({ leagues: [], feeds });
  }
  const before = heap();
  let blanks = 0;
  let flakyStaleSeen = 0;

  // ~50 simulated hours of 1-minute cycles
  const CYCLES = 3000;
  for (let i = 0; i < CYCLES; i++) {
    fakeNow += 60_000;
    const slate = await buildClientSlate({ leagues: [], feeds });
    if (!slate.events || slate.events.length === 0) blanks += 1;
    const flaky = slate.feeds.find((f) => f.url === FLAKY);
    if (flaky && flaky.stale) flakyStaleSeen += 1;
  }

  const after = heap();
  const growthMb = (after - before) / (1024 * 1024);

  console.log('=== Core Line accelerated soak ===');
  console.log(`simulated cycles : ${CYCLES} (60 s each => ~${(CYCLES / 60).toFixed(1)} h)`);
  console.log(`blank cycles     : ${blanks}`);
  console.log(`flaky stale hits : ${flakyStaleSeen} (last-good kept on failure)`);
  console.log(`heap before      : ${(before / 1048576).toFixed(2)} MB`);
  console.log(`heap after       : ${(after / 1048576).toFixed(2)} MB`);
  console.log(`heap growth      : ${growthMb.toFixed(2)} MB`);

  assert.equal(blanks, 0, 'ribbon must never be blank');
  assert.ok(flakyStaleSeen > 0, 'flaky feed should be served from last-good at least once');
  assert.ok(growthMb < 10, `heap growth ${growthMb.toFixed(2)} MB exceeds 10 MB budget`);
  console.log('PASS: never blank, backoff+last-good exercised, memory bounded.');
}

main().catch((err) => {
  console.error('SOAK FAILED:', err);
  process.exitCode = 1;
}).finally(() => {
  Date.now = realNow;
});
