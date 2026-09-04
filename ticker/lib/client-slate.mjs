/**
 * Assemble a slate in the browser / Android WebView.
 * The native shell proxies third-party hosts through /api/proxy so CORS
 * never enters the picture. The Node server still owns /api/slate for the web.
 *
 * Phase 1: per-source backoff, last-good retention via SourceRegistry.
 */

import { parseFeed, MAX_ITEMS_PER_FEED } from './parser.mjs';
import { SourceRegistry } from './source-registry.mjs';
import {
  LEAGUES,
  espnScoreboardUrl,
  eventsFromEspn,
  eventsFromNhl,
  eventsFromMlb,
  buildDemoSlate,
  mergeEvents,
} from './scoreboard.mjs';

const registry = new SourceRegistry();

export { registry };

export function isNativeShell() {
  return Boolean(globalThis.CORELINE_NATIVE);
}

export async function buildClientSlate({ leagues = [], feeds = [] } = {}) {
  registry.restore();

  const groups = await Promise.all(leagues.map(async (id) => {
    const key = `league:${id}`;
    if (!LEAGUES[id]) return [];
    if (registry.shouldSkip(key)) {
      return registry.getLastGood(key);
    }
    try {
      const events = await fetchLeague(id);
      registry.recordSuccess(key, events);
      return events;
    } catch (err) {
      const retryAfter = null;
      registry.recordFailure(key, err, retryAfter);
      return registry.getLastGood(key);
    }
  }));

  const feedResults = await Promise.all(feeds.map(async (feed) => {
    const key = `feed:${feed.url}`;
    if (registry.shouldSkip(key)) {
      return { ok: true, events: registry.getLastGood(key), label: feed.label, url: feed.url, error: null, count: registry.getLastGood(key).length, stale: true };
    }
    try {
      const text = await loadText(feed.url);
      const events = parseFeed(text, { source: 'rss', label: feed.label || 'RSS' });
      registry.recordSuccess(key, events);
      return { ok: true, events, label: feed.label, url: feed.url, error: null, count: events.length, stale: false };
    } catch (err) {
      registry.recordFailure(key, err, null);
      const lastGood = registry.getLastGood(key);
      return { ok: false, events: lastGood, label: feed.label, url: feed.url, error: err?.message || 'fetch failed', count: lastGood.length, stale: lastGood.length > 0 };
    }
  }));

  registry.persist();

  let events = mergeEvents([...groups, ...feedResults.map((r) => r.events)]);
  let demo = false;
  if (!events.length) {
    events = mergeEvents([buildDemoSlate(), ...feedResults.map((r) => r.events)]);
    demo = true;
  }

  const health = registry.summary();

  return {
    ok: true,
    demo,
    generatedAt: new Date().toISOString(),
    health,
    feeds: feedResults.map(({ ok, label, url, error, count, stale }) => ({ ok, label, url, error, count, stale })),
    events,
  };
}

async function fetchLeague(id) {
  try {
    const data = await loadJson(espnScoreboardUrl(id));
    return eventsFromEspn(data, id);
  } catch (espnErr) {
    if (id === 'nhl') {
      try {
        const data = await loadJson('https://api-web.nhle.com/v1/score/now');
        return eventsFromNhl(data);
      } catch { /* fall through */ }
    }
    if (id === 'mlb') {
      try {
        const today = new Date().toISOString().slice(0, 10);
        const data = await loadJson(`https://statsapi.mlb.com/api/v1/schedule?sportId=1&date=${today}&hydrate=team,linescore,broadcasts(all)`);
        return eventsFromMlb(data);
      } catch { /* fall through */ }
    }
    throw espnErr;
  }
}

async function loadJson(url) {
  const res = await fetchRemote(url);
  return res.json();
}

async function loadText(url) {
  if (isBundledSample(url)) {
    const res = await fetch('/feeds/sample-sports.xml');
    if (!res.ok) throw new Error(`sample ${res.status}`);
    return res.text();
  }
  const res = await fetchRemote(url);
  return res.text();
}

async function fetchRemote(url) {
  const target = isNativeShell() ? `/api/proxy?url=${encodeURIComponent(url)}` : url;
  const res = await fetch(target, {
    headers: {
      accept: 'application/json, application/rss+xml, application/xml, text/xml, text/plain;q=0.8',
    },
  });
  if (!res.ok) throw new Error(`http ${res.status}`);
  return res;
}

function isBundledSample(raw) {
  try {
    return new URL(raw, 'http://core.line').pathname.endsWith('/feeds/sample-sports.xml');
  } catch {
    return String(raw || '').includes('sample-sports.xml');
  }
}
