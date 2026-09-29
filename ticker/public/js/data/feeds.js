/**
 * The user's RSS feeds.
 *
 * Ordering is a feature, not bookkeeping: the crawl renders feeds in the order
 * they are listed, so "move up" is a real editorial control over what the
 * viewer reads first.
 */

import { isSafeFeedUrl } from '/lib/ssrf.mjs';
import { store, persist } from '../core/store.js';
import { emit } from '../core/bus.js';
import { MAX_FEEDS } from './slate.js';

/**
 * Validate and add a feed.
 *
 * The SSRF guard runs here, at add time, not only at fetch time: an unsafe URL
 * should never enter persisted state and be retried forever (AUDIT B7).
 */
export function addFeed(rawUrl, rawLabel) {
  const url = String(rawUrl ?? '').trim();
  const label = String(rawLabel ?? '').trim().slice(0, 24) || 'RSS';
  if (!url) { emit('toast', 'Paste a feed URL first'); return false; }
  if (store.state.feeds.length >= MAX_FEEDS) { emit('toast', `At most ${MAX_FEEDS} feeds`); return false; }

  const safety = isSafeFeedUrl(url);
  if (!safety.ok) { emit('toast', safety.reason); return false; }
  if (store.state.feeds.some((f) => f.url === safety.url)) { emit('toast', 'Already added'); return false; }

  store.state.feeds.push({ url: safety.url, label });
  persist();
  emit('feeds');
  emit('toast', `Added ${label}`);
  return true;
}

export function removeFeed(url) {
  store.state.feeds = store.state.feeds.filter((f) => f.url !== url);
  persist();
  emit('feeds');
}

export function moveFeed(url, delta) {
  const i = store.state.feeds.findIndex((f) => f.url === url);
  const j = i + delta;
  if (i < 0 || j < 0 || j >= store.state.feeds.length) return;
  const [item] = store.state.feeds.splice(i, 1);
  store.state.feeds.splice(j, 0, item);
  persist();
  emit('feeds');
}

export function moveLeague(id, delta) {
  const i = store.state.leagues.indexOf(id);
  const j = i + delta;
  if (i < 0 || j < 0 || j >= store.state.leagues.length) return;
  const [item] = store.state.leagues.splice(i, 1);
  store.state.leagues.splice(j, 0, item);
  persist();
  emit('feeds');
}
