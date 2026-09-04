/**
 * Assemble a slate in the browser / Android WebView.
 * The native shell proxies third-party hosts through /api/proxy so CORS
 * never enters the picture. The Node server still owns /api/slate for the web.
 *
 * Reliability contract (mapped to AUDIT.md B1/B2/C5):
 *   - every source (league or feed) is fetched in isolation (one dead feed
 *     never blocks the others),
 *   - failures are retried with exponential backoff + jitter,
 *   - last-good items for a failing source stay on screen (marked stale),
 *   - the demo slate only appears when there is *nothing at all*.
 */

import { parseFeed } from './parser.mjs';
import {
  LEAGUES,
  espnScoreboardUrl,
  eventsFromEspn,
  eventsFromNhl,
  eventsFromMlb,
  buildDemoSlate,
  mergeEvents,
} from './scoreboard.mjs';
import { SourceRegistry, MAX_ITEMS_PER_SOURCE } from './source-registry.mjs';

export function isNativeShell() {
  return Boolean(globalThis.CORELINE_NATIVE);
}

const registry = new SourceRegistry();
const STORAGE_KEY = 'coreline.v1.sources';

export function getClientSlateRegistry() {
  return registry;
}

/** Test hook + app boot: reload last-good events from localStorage. */
export function hydrateClientSlateRegistry() {
  try {
    if (typeof localStorage !== 'undefined') {
      registry.hydrate(localStorage.getItem(STORAGE_KEY));
    }
  } catch {
    /* storage unavailable (private mode etc.) */
  }
}

function persistRegistry() {
  try {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(STORAGE_KEY, registry.dehydrate());
    }
  } catch {
    /* quota — non-fatal */
  }
}

export async function buildClientSlate({ leagues = [], feeds = [] } = {}) {
  const now = Date.now();
  const sources = [];
  const freshGroups = [];
  const staleGroups = [];
  const leaguesList = (Array.isArray(leagues) ? leagues : []).filter((id) => LEAGUES[id]);
  const feedsList = (Array.isArray(feeds) ? feeds : []).slice(0, 20);

  // --- scoreboards: one league at a time, never one-blocks-all ------------
  await Promise.all(leaguesList.map(async (id) => {
    const key = registry.keyForLeague(id);
    if (!registry.canTry(key, now)) {
      const kept = registry.lastGood(key);
      if (kept.length) staleGroups.push(kept);
      sources.push({
        id,
        provider: 'espn',
        ok: false,
        error: registry.lastError(key) || 'backoff',
        count: kept.length,
        skipped: true,
        stale: kept.length > 0,
        nextRetryMs: registry.waitMs(key, now),
      });
      return;
    }

    const out = await fetchLeague(id, key);
    if (out.ok) {
      freshGroups.push(out.events);
    } else if (out.events.length) {
      staleGroups.push(out.events); // last-good kept on failure
    }
    sources.push(out.report);
  }));

  // --- feeds: one at a time -------------------------------------------------
  const feedResults = await Promise.all(feedsList.map(async (feed) => {
    const url = String(feed?.url || '');
    if (!url) return emptyFeedReport(feed);
    const key = registry.keyForFeed(url);
    const label = feed.label || 'RSS';
    if (!registry.canTry(key, now)) {
      const kept = registry.lastGood(key);
      return {
        ok: false,
        events: kept,
        label,
        url,
        error: registry.lastError(key) || 'backoff',
        count: kept.length,
        skipped: true,
        stale: kept.length > 0,
        nextRetryMs: registry.waitMs(key, now),
      };
    }
    try {
      const text = await loadText(url);
      const events = parseFeed(text, { source: 'rss', label }).slice(0, MAX_ITEMS_PER_SOURCE);
      registry.recordSuccess(key, label, events);
      return { ok: true, events, label, url, error: null, count: events.length, stale: false };
    } catch (err) {
      const kept = registry.lastGood(key);
      registry.recordFailure(key, label, err?.message || 'fetch failed');
      return {
        ok: false,
        events: kept,
        label,
        url,
        error: err?.message || 'fetch failed',
        count: kept.length,
        stale: kept.length > 0,
        nextRetryMs: registry.waitMs(key, now),
      };
    }
  }));

  persistRegistry();

  let events = mergeEvents([
    ...freshGroups,
    ...staleGroups,
    ...feedResults.map((r) => r.events),
  ]);

  let demo = false;
  if (!events.length) {
    events = mergeEvents([buildDemoSlate(), ...feedResults.map((r) => r.events)]);
    demo = true;
  }

  const health = registry.health();
  return {
    ok: true,
    demo,
    generatedAt: new Date().toISOString(),
    sources,
    feeds: feedResults.map(({ ok, label, url, error, count, stale, skipped, nextRetryMs }) => ({
      ok,
      label,
      url,
      error,
      count,
      stale: Boolean(stale),
      skipped: Boolean(skipped),
      nextRetryMs: nextRetryMs || 0,
    })),
    health: {
      degraded: health.degraded,
      backingOff: health.backingOff,
      stale: staleGroups.length + feedResults.filter((r) => r.stale).length,
    },
    events,
  };
}

async function fetchLeague(id, key) {
  // 1) ESPN
  try {
    const data = await loadJson(espnScoreboardUrl(id));
    const events = eventsFromEspn(data, id);
    registry.recordSuccess(key, id, events);
    return {
      ok: true,
      events,
      report: { id, provider: 'espn', ok: true, count: events.length },
    };
  } catch (err) {
    // 2) league-specific fallbacks
    if (id === 'nhl') {
      try {
        const data = await loadJson('https://api-web.nhle.com/v1/score/now');
        const events = eventsFromNhl(data);
        registry.recordSuccess(key, id, events);
        return {
          ok: true,
          events,
          report: { id, provider: 'nhl', ok: true, count: events.length },
        };
      } catch {
        /* fall through */
      }
    }
    if (id === 'mlb') {
      try {
        const today = new Date().toISOString().slice(0, 10);
        const data = await loadJson(`https://statsapi.mlb.com/api/v1/schedule?sportId=1&date=${today}&hydrate=team,linescore,broadcasts(all)`);
        const events = eventsFromMlb(data);
        registry.recordSuccess(key, id, events);
        return {
          ok: true,
          events,
          report: { id, provider: 'mlb', ok: true, count: events.length },
        };
      } catch {
        /* fall through */
      }
    }
    const kept = registry.lastGood(key);
    registry.recordFailure(key, id, err?.message || 'fetch failed');
    return {
      ok: false,
      events: kept,
      report: {
        id,
        provider: 'espn',
        ok: false,
        error: err?.message || 'fetch failed',
        count: kept.length,
        stale: kept.length > 0,
        nextRetryMs: registry.waitMs(key),
      },
    };
  }
}

function emptyFeedReport(feed) {
  return { ok: false, events: [], label: feed?.label || 'RSS', url: '', error: 'no url', count: 0, stale: false };
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
