import http from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { LEAGUES, DEFAULT_LEAGUES, espnScoreboardUrl, eventsFromEspn, eventsFromNhl, eventsFromMlb, buildDemoSlate, mergeEvents } from './lib/scoreboard.mjs';
import { fetchFeed, fetchJson } from './lib/rss.mjs';
import { isSafeFeedUrl } from './lib/ssrf.mjs';
import { parseFeed } from './lib/parser.mjs';
import { createBackoff } from './lib/backoff.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PUBLIC_DIR = path.join(__dirname, 'public');
const LIB_DIR = path.join(__dirname, 'lib');
const PORT = Number(process.env.PORT || 8787);
const HOST = process.env.HOST || '0.0.0.0';

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.xml': 'application/rss+xml; charset=utf-8',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
};

const cache = new Map();
const CACHE_MS = 45_000;

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
    if (req.method === 'OPTIONS') {
      send(res, 204, '', { 'access-control-allow-origin': '*', 'access-control-allow-methods': 'GET,OPTIONS', 'access-control-allow-headers': 'content-type' });
      return;
    }
    if (req.method !== 'GET') {
      send(res, 405, 'Method not allowed');
      return;
    }
    if (url.pathname === '/api/health') {
      json(res, { ok: true, name: 'core-line', version: '1.0.0' });
      return;
    }
    if (url.pathname === '/api/leagues') {
      json(res, Object.values(LEAGUES).map(({ id, label, accent }) => ({ id, label, accent })));
      return;
    }
    if (url.pathname === '/api/scoreboard') {
      const leagues = (url.searchParams.get('leagues') || DEFAULT_LEAGUES.join(','))
        .split(',')
        .map((s) => s.trim().toLowerCase())
        .filter((id) => LEAGUES[id]);
      const payload = await getScoreboard(leagues);
      json(res, payload);
      return;
    }
    if (url.pathname === '/api/rss') {
      const target = url.searchParams.get('url') || '';
      const label = url.searchParams.get('label') || 'RSS';
      if (isBundledSample(target)) {
        json(res, await readBundledSample(label));
        return;
      }
      const safety = isSafeFeedUrl(target);
      if (!safety.ok) {
        json(res, { ok: false, error: safety.reason, events: [] }, 400);
        return;
      }
      const result = await resilientFeed(safety.url, label);
      json(res, result);
      return;
    }
    if (url.pathname === '/api/slate') {
      const leagues = (url.searchParams.get('leagues') || DEFAULT_LEAGUES.join(','))
        .split(',')
        .map((s) => s.trim().toLowerCase())
        .filter((id) => LEAGUES[id]);
      const feeds = parseFeedsParam(url.searchParams.get('feeds') || '').slice(0, 20);
      const [board, ...feedResults] = await Promise.all([
        getScoreboard(leagues),
        ...feeds.map((feed) => resilientFeed(feed.url, feed.label)),
      ]);
      const rssEvents = feedResults.flatMap((r) => r.events || []);
      const staleFeeds = feedResults.filter((r) => r.stale).length;
      json(res, {
        ok: true,
        generatedAt: new Date().toISOString(),
        demo: board.demo,
        sources: board.sources,
        feeds: feedResults.map((r, i) => ({
          label: feeds[i].label,
          url: feeds[i].url,
          ok: r.ok,
          error: r.error || null,
          count: (r.events || []).length,
          stale: Boolean(r.stale),
        })),
        health: { degraded: feedResults.filter((r) => !r.ok).length, stale: staleFeeds },
        events: mergeEvents([board.events, rssEvents]),
      });
      return;
    }

    await serveStatic(url.pathname, res);
  } catch (err) {
    json(res, { ok: false, error: err?.message || 'server error' }, 500);
  }
});

server.listen(PORT, HOST, () => {
  console.log(`Core Line listening on http://${HOST}:${PORT}`);
});

async function getScoreboard(leagues) {
  return cached(`board:${leagues.join(',')}`, async () => {
    const sources = [];
    const groups = [];
    await Promise.all(leagues.map(async (id) => {
      const slot = leagueSlot(id);
      if (!slot.backoff.canTry()) {
        if (slot.lastGood.length) groups.push(slot.lastGood);
        sources.push({ id, provider: 'espn', ok: false, error: 'backoff', count: slot.lastGood.length, stale: slot.lastGood.length > 0 });
        return;
      }
      const res = await fetchLeagueServer(id);
      if (res.ok) {
        slot.backoff.success();
        if (res.events.length) slot.lastGood = res.events;
        groups.push(res.events);
        sources.push(res.report);
      } else {
        slot.backoff.fail();
        if (slot.lastGood.length) groups.push(slot.lastGood);
        sources.push({ ...res.report, stale: slot.lastGood.length > 0, count: slot.lastGood.length });
      }
    }));

    let events = mergeEvents(groups);
    let demo = false;
    if (!events.length) {
      events = buildDemoSlate();
      demo = true;
    }
    return {
      ok: true,
      demo,
      generatedAt: new Date().toISOString(),
      sources,
      events,
    };
  });
}

async function fetchLeagueServer(id) {
  try {
    const data = await fetchJson(espnScoreboardUrl(id));
    const events = eventsFromEspn(data, id);
    return { ok: true, events, report: { id, provider: 'espn', ok: true, count: events.length } };
  } catch (err) {
    if (id === 'nhl') {
      try {
        const data = await fetchJson('https://api-web.nhle.com/v1/score/now');
        const events = eventsFromNhl(data);
        return { ok: true, events, report: { id, provider: 'nhl', ok: true, count: events.length } };
      } catch { /* fall through */ }
    }
    if (id === 'mlb') {
      try {
        const today = new Date().toISOString().slice(0, 10);
        const data = await fetchJson(`https://statsapi.mlb.com/api/v1/schedule?sportId=1&date=${today}&hydrate=team,linescore,broadcasts(all)`);
        const events = eventsFromMlb(data);
        return { ok: true, events, report: { id, provider: 'mlb', ok: true, count: events.length } };
      } catch { /* fall through */ }
    }
    return { ok: false, events: [], report: { id, provider: 'espn', ok: false, error: err?.message || 'fetch failed', count: 0 } };
  }
}

// --- per-source backoff + last-good (server side of AUDIT B1/B2) ------------

const feedState = new Map();
const leagueState = new Map();

function feedSlot(url) {
  let slot = feedState.get(url);
  if (!slot) {
    slot = { backoff: createBackoff(), lastGood: [] };
    feedState.set(url, slot);
  }
  return slot;
}

function leagueSlot(id) {
  let slot = leagueState.get(id);
  if (!slot) {
    slot = { backoff: createBackoff(), lastGood: [] };
    leagueState.set(id, slot);
  }
  return slot;
}

async function resilientFeed(url, label) {
  const slot = feedSlot(url);
  if (!slot.backoff.canTry()) {
    return { ok: false, error: 'backoff', events: slot.lastGood, stale: slot.lastGood.length > 0 };
  }
  let result;
  if (isBundledSample(url)) {
    result = await readBundledSample(label);
  } else {
    result = await cached(`rss:${url}:${label}`, () => fetchFeed(url, { source: 'rss', label }));
  }
  if (result.ok) {
    slot.backoff.success();
    if (result.events && result.events.length) slot.lastGood = result.events;
    return result;
  }
  slot.backoff.fail();
  return { ...result, events: slot.lastGood, stale: slot.lastGood.length > 0 };
}

function isBundledSample(raw) {
  try {
    const url = new URL(String(raw || ''), 'http://core.line');
    return url.pathname.endsWith('/feeds/sample-sports.xml');
  } catch {
    return String(raw || '').includes('sample-sports.xml');
  }
}

async function readBundledSample(label = 'Sample') {
  const xml = await readFile(path.join(PUBLIC_DIR, 'feeds/sample-sports.xml'), 'utf8');
  return { ok: true, events: parseFeed(xml, { source: 'rss', label }), bytes: xml.length };
}

function parseFeedsParam(raw) {
  if (!raw) return [];
  return raw.split(',').map((part) => {
    const [url, label] = part.split('|');
    return { url: decodeURIComponent(url || ''), label: decodeURIComponent(label || 'RSS') };
  }).filter((f) => f.url);
}

const MAX_CACHE_KEYS = 200;

async function cached(key, fn) {
  const hit = cache.get(key);
  if (hit && Date.now() - hit.at < CACHE_MS) return hit.value;
  const value = await fn();
  if (cache.size >= MAX_CACHE_KEYS) {
    const now = Date.now();
    for (const [k, v] of cache.entries()) {
      if (now - v.at >= CACHE_MS) cache.delete(k);
    }
    if (cache.size >= MAX_CACHE_KEYS) {
      cache.delete(cache.keys().next().value);
    }
  }
  cache.set(key, { at: Date.now(), value });
  return value;
}

async function serveStatic(pathname, res) {
  let rel = decodeURIComponent(pathname);
  if (rel === '/') rel = '/index.html';
  const root = rel.startsWith('/lib/') ? LIB_DIR : PUBLIC_DIR;
  const slice = rel.startsWith('/lib/') ? rel.slice('/lib/'.length) : rel.slice(1);
  const file = path.normalize(path.join(root, slice));
  const rootWithSep = root.endsWith(path.sep) ? root : root + path.sep;
  if (file !== root && !file.startsWith(rootWithSep)) {
    send(res, 403, 'Forbidden');
    return;
  }
  try {
    const info = await stat(file);
    if (!info.isFile()) {
      send(res, 404, 'Not found');
      return;
    }
    const body = await readFile(file);
    const type = MIME[path.extname(file).toLowerCase()] || 'application/octet-stream';
    send(res, 200, body, { 'content-type': type, 'cache-control': rel === '/index.html' ? 'no-cache' : 'public, max-age=300' });
  } catch {
    send(res, 404, 'Not found');
  }
}

function json(res, data, status = 200) {
  send(res, status, JSON.stringify(data), {
    'content-type': 'application/json; charset=utf-8',
    'access-control-allow-origin': '*',
    'cache-control': 'no-store',
  });
}

function send(res, status, body, headers = {}) {
  res.writeHead(status, {
    'x-content-type-options': 'nosniff',
    ...headers,
  });
  res.end(body);
}
