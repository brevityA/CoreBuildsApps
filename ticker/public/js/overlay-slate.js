/**
 * The slate for the surfaces that float over other apps: the crawl
 * (`overlay.js`) and the scoreboard bug (`scorebug.js`).
 *
 * Both are separate documents from the board, on the same origin, so they
 * share its localStorage: they paint the board's cached slate at once, reuse
 * it while it is fresh, and otherwise fetch their own — through the on-device
 * client slate in the Android shell, or `/api/slate` from the web server. One
 * copy of that logic, so the two surfaces cannot disagree about what is on.
 */

import { loadState, readCachedSlate } from './state.js';
import { buildClientSlate, hydrateClientSlateRegistry, isNativeShell } from '/lib/client-slate.mjs';
import { buildDemoSlate, mergeEvents } from '/lib/scoreboard.mjs';
import { parseFeed } from '/lib/parser.mjs';

const SAMPLE = { url: `${location.origin}/feeds/sample-sports.xml`, label: 'Sample' };

/**
 * @param {(events: object[]) => void} onEvents  called with every new slate
 * @param {{ demoFallback?: boolean }} opts  the crawl shows a demo slate while
 *   it waits; the bug would rather show nothing than a fake score
 */
export function startOverlaySlate(onEvents, { demoFallback = true } = {}) {
  let state = loadState();
  let refreshing = false;

  hydrateClientSlateRegistry();

  const cached = readCachedSlate();
  if (cached?.events?.length) onEvents(cached.events);
  else if (demoFallback) onEvents(buildDemoSlate());

  async function refresh() {
    if (refreshing) return;
    refreshing = true;
    state = loadState();
    try {
      const data = freshCache(state) || await loadSlate(state);
      if (data?.events) onEvents(data.events);
    } catch {
      const cachedSlate = readCachedSlate();
      if (cachedSlate?.events?.length) onEvents(cachedSlate.events);
      else if (demoFallback) onEvents(await localFallback());
    } finally {
      refreshing = false;
    }
  }

  refresh();
  const timer = setInterval(refresh, Math.max(15, state.refreshSec || 60) * 1000);
  return { refresh, stop: () => clearInterval(timer) };
}

function freshCache(state) {
  try {
    const raw = localStorage.getItem('coreline.v1.slate');
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    const age = Date.now() - Number(parsed.at || 0);
    const limit = Math.max(15, state.refreshSec || 60) * 1000;
    if (!parsed.payload?.events?.length || age > limit) return null;
    return parsed.payload;
  } catch {
    return null;
  }
}

async function loadSlate(state) {
  const feeds = [...(state.feeds || []), ...(state.sampleFeed ? [SAMPLE] : [])].slice(0, 20);
  if (isNativeShell()) return buildClientSlate({ leagues: state.leagues, feeds });
  const query = new URLSearchParams({
    leagues: (state.leagues || []).join(','),
    feeds: feeds.map((f) => `${encodeURIComponent(f.url)}|${encodeURIComponent(f.label)}`).join(','),
  });
  const res = await fetch(`/api/slate?${query}`);
  if (!res.ok) throw new Error(`slate ${res.status}`);
  return res.json();
}

async function localFallback() {
  try {
    const xml = await fetch('./feeds/sample-sports.xml').then((r) => r.text());
    return mergeEvents([buildDemoSlate(), parseFeed(xml, { source: 'rss', label: 'Sample' })]);
  } catch {
    return buildDemoSlate();
  }
}
