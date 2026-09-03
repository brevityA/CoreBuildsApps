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

const leagueBackoffs = new Map();
const feedBackoffs = new Map();
const lastGoodLeague = new Map();
const lastGoodFeed = new Map();

function getLeagueBackoff(id) {
  if (!leagueBackoffs.has(id)) leagueBackoffs.set(id, createBackoff());
  return leagueBackoffs.get(id);
}

function getFeedBackoff(key) {
  if (!feedBackoffs.has(key)) feedBackoffs.set(key, createBackoff());
  return feedBackoffs.get(key);
}

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
      json(res, { ok: true, name: 'core-line', version: '1.0.2' });
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
      const result = await cached(JSON.stringify(['rss', safety.url, label]), () => fetchFeed(safety.url, { source: 'rss', label }));
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
        ...feeds.map((feed) => resilientFeed(feed)),
      ]);
      const rssEvents = feedResults.flatMap((r) => r.events || []);
      const allOk = feedResults.every((r) => r.ok);
      const someStale = feedResults.some((r) => r.stale);
      json(res, {
        ok: true,
        generatedAt: new Date().toISOString(),
        demo: board.demo,
        health: {
          leagues: board.health || 'ok',
          feeds: allOk ? (someStale ? 'stale' : 'ok') : 'degraded',
        },
        sources: board.sources,
        feeds: feedResults.map((r, i) => ({
          label: feeds[i].label,
          url: feeds[i].url,
          ok: r.ok,
          error: r.error || null,
          count: (r.events || []).length,
          stale: r.stale || false,
        })),
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

async function resilientFeed(feed) {
  if (isBundledSample(feed.url)) {
    return { ...(await readBundledSample(feed.label)), stale: false };
  }
  const safety = isSafeFeedUrl(feed.url);
  if (!safety.ok) {
    return { ok: false, events: [], error: safety.reason, stale: false };
  }
  const key = safety.url;
  const bo = getFeedBackoff(key);
  if (bo.shouldSkip()) {
    const lg = lastGoodFeed.get(key) || [];
    return { ok: true, events: lg, error: null, stale: lg.length > 0 };
  }
  try {
    const result = await cached(JSON.stringify(['rss', key, feed.label]), () => fetchFeed(key, { source: 'rss', label: feed.label }));
    bo.succeed();
    lastGoodFeed.set(key, result.events || []);
    return { ...result, stale: false };
  } catch (err) {
    bo.fail(null);
    const lg = lastGoodFeed.get(key) || [];
    return { ok: false, events: lg, error: err?.message || 'fetch failed', stale: lg.length > 0 };
  }
}

async function getScoreboard(leagues) {
  return cached(`board:${leagues.join(',')}`, async () => {
    const sources = [];
    const groups = [];
    let anyDegraded = false;
    let anyStale = false;
    await Promise.all(leagues.map(async (id) => {
      const bo = getLeagueBackoff(id);
      if (bo.shouldSkip()) {
        const lg = lastGoodLeague.get(id) || [];
        groups.push(lg);
        sources.push({ id, provider: 'cached', ok: true, count: lg.length, stale: true });
        anyStale = true;
        return;
      }
      const espnUrl = espnScoreboardUrl(id);
      try {
        const data = await fetchJson(espnUrl);
        const events = eventsFromEspn(data, id);
        groups.push(events);
        sources.push({ id, provider: 'espn', ok: true, count: events.length });
        bo.succeed();
        lastGoodLeague.set(id, events);
      } catch (err) {
        if (id === 'nhl') {
          try {
            const data = await fetchJson('https://api-web.nhle.com/v1/score/now');
            const events = eventsFromNhl(data);
            groups.push(events);
            sources.push({ id, provider: 'nhl', ok: true, count: events.length });
            bo.succeed();
            lastGoodLeague.set(id, events);
            return;
          } catch { /* fall through */ }
        }
        if (id === 'mlb') {
          try {
            const today = new Date().toISOString().slice(0, 10);
            const data = await fetchJson(`https://statsapi.mlb.com/api/v1/schedule?sportId=1&date=${today}&hydrate=team,linescore,broadcasts(all)`);
            const events = eventsFromMlb(data);
            groups.push(events);
            sources.push({ id, provider: 'mlb', ok: true, count: events.length });
            bo.succeed();
            lastGoodLeague.set(id, events);
            return;
          } catch { /* fall through */ }
        }
        bo.fail(null);
        const lg = lastGoodLeague.get(id) || [];
        groups.push(lg);
        sources.push({ id, provider: 'espn', ok: false, error: err?.message || 'fetch failed', count: lg.length, stale: lg.length > 0 });
        if (lg.length) anyStale = true; else anyDegraded = true;
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
      health: anyDegraded ? 'degraded' : anyStale ? 'stale' : 'ok',
      sources,
      events,
    };
  });
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
