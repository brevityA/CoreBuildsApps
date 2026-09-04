import test from 'node:test';
import assert from 'node:assert/strict';

import { buildClientSlate, getClientSlateRegistry } from '../lib/client-slate.mjs';

const FEED_A = 'https://feeds.example/a.xml';
const FEED_B = 'https://feeds.example/b.xml';

const GOOD_RSS = `<rss><channel>
  <item><title>Leafs vs Habs — TSN4, SN 3</title></item>
</channel></rss>`;

function resetRegistry() {
  getClientSlateRegistry().clear();
}

test('one dead feed never blanks the slate and never blocks the healthy feed', async (t) => {
  resetRegistry();
  t.mock.method(globalThis, 'fetch', async (input) => {
    const url = String(input);
    if (url.includes('/api/proxy')) {
      const target = decodeURIComponent(url.split('url=')[1]);
      if (target === FEED_A) return new Response(GOOD_RSS, { status: 200 });
      return new Response('nope', { status: 500 });
    }
    if (url.includes('site.api.espn.com')) return new Response('{}', { status: 200 });
    if (url.includes(FEED_A)) return new Response(GOOD_RSS, { status: 200 });
    if (url.includes(FEED_B)) return new Response('boom', { status: 500 });
    return new Response('not found', { status: 404 });
  });

  const slate = await buildClientSlate({
    leagues: [],
    feeds: [
      { url: FEED_A, label: 'A' },
      { url: FEED_B, label: 'B' },
    ],
  });

  assert.equal(slate.ok, true);
  assert.equal(slate.demo, false);
  assert.ok(slate.events.length > 0, 'healthy feed still contributes');
  const a = slate.feeds.find((f) => f.url === FEED_A);
  const b = slate.feeds.find((f) => f.url === FEED_B);
  assert.equal(a.ok, true);
  assert.equal(b.ok, false);
  assert.equal(b.count, 0); // no last-good yet
  assert.equal(slate.health.degraded, 1);
});

test('a failing feed keeps its last-good items on screen (stale flag)', async (t) => {
  resetRegistry();
  let failB = false;
  t.mock.method(globalThis, 'fetch', async (input) => {
    const url = String(input);
    if (url.includes('site.api.espn.com')) return new Response('{}', { status: 200 });
    if (url.includes(FEED_A)) return new Response(GOOD_RSS, { status: 200 });
    if (url.includes(FEED_B)) {
      return failB
        ? new Response('down', { status: 500 })
        : new Response(`<rss><channel><item><title>Blue Jays vs Yankees — SN 1</title></item></channel></rss>`, { status: 200 });
    }
    return new Response('not found', { status: 404 });
  });

  const feeds = [{ url: FEED_A, label: 'A' }, { url: FEED_B, label: 'B' }];

  const first = await buildClientSlate({ leagues: [], feeds });
  assert.equal(first.feeds.find((f) => f.url === FEED_B).ok, true);

  failB = true;
  const second = await buildClientSlate({ leagues: [], feeds });
  const b = second.feeds.find((f) => f.url === FEED_B);
  assert.equal(b.ok, false);
  assert.equal(b.stale, true);
  assert.equal(b.count, 1, 'last-good items retained for the dead feed');
  assert.ok(second.events.some((e) => e.rawTitle.includes('Blue Jays')), 'dead feed content still on the ribbon');
});

test('a repeatedly-failing source is skipped (backoff) instead of re-fetched every cycle', async (t) => {
  resetRegistry();
  let calls = 0;
  t.mock.method(globalThis, 'fetch', async (input) => {
    const url = String(input);
    if (url.includes('site.api.espn.com')) return new Response('{}', { status: 200 });
    if (url.includes(FEED_B)) {
      calls += 1;
      return new Response('down', { status: 500 });
    }
    return new Response('not found', { status: 404 });
  });

  const feeds = [{ url: FEED_B, label: 'B' }];
  await buildClientSlate({ leagues: [], feeds });
  const afterFail = calls;
  await buildClientSlate({ leagues: [], feeds }); // immediately again
  assert.equal(calls, afterFail, 'second call must skip the backing-off source');
  const b = (await buildClientSlate({ leagues: [], feeds })).feeds[0];
  assert.equal(b.skipped, true);
});

test('demo slate appears only when there is nothing at all (no fresh, no last-good)', async (t) => {
  resetRegistry();
  t.mock.method(globalThis, 'fetch', async () => new Response('{}', { status: 200 }));
  const slate = await buildClientSlate({ leagues: [], feeds: [] });
  assert.equal(slate.demo, true);
  assert.ok(slate.events.length > 0);
});
