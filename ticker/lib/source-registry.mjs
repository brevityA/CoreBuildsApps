/**
 * Per-source state registry: backoff schedule + last-good events.
 *
 * This is the "per-source isolation + self-healing" heart of the ticker.
 * Each scoreboard league and each RSS/JSON feed gets its own slot, so:
 *   - one dead feed never blocks (or blanks) the others,
 *   - a failing source is retried with exponential backoff + jitter,
 *   - the last successfully-parsed items are kept and re-shown while the
 *     source is failing (the ribbon keeps its content instead of dropping it).
 *
 * Runs in both the browser/WebView and Node (tests). localStorage
 * persistence is optional and guarded so Node tests stay isolated.
 */

import { createBackoff } from './backoff.mjs';

export const MAX_ITEMS_PER_SOURCE = 100;

export class SourceRegistry {
  constructor() {
    this.sources = new Map(); // key -> { backoff, lastGood, lastError, label }
  }

  keyForLeague(id) {
    return `league:${id}`;
  }

  keyForFeed(url) {
    return `feed:${url}`;
  }

  _slot(key, label) {
    let slot = this.sources.get(key);
    if (!slot) {
      slot = { backoff: createBackoff(), lastGood: [], lastError: null, label };
      this.sources.set(key, slot);
    }
    if (label) slot.label = label;
    return slot;
  }

  canTry(key, now = Date.now()) {
    const slot = this.sources.get(key);
    return !slot || slot.backoff.canTry(now);
  }

  waitMs(key, now = Date.now()) {
    const slot = this.sources.get(key);
    return slot ? slot.backoff.waitMs(now) : 0;
  }

  recordSuccess(key, label, events) {
    const slot = this._slot(key, label);
    slot.backoff.success();
    slot.lastGood = Array.isArray(events) ? events.slice(0, MAX_ITEMS_PER_SOURCE) : [];
    slot.lastError = null;
  }

  recordFailure(key, label, error, retryAfterMs = 0) {
    const slot = this._slot(key, label);
    slot.backoff.fail(Date.now(), retryAfterMs);
    slot.lastError = error || 'source failed';
  }

  setLastGood(key, label, events) {
    const slot = this._slot(key, label);
    if (Array.isArray(events)) slot.lastGood = events.slice(0, MAX_ITEMS_PER_SOURCE);
  }

  lastGood(key) {
    const slot = this.sources.get(key);
    return slot ? slot.lastGood : [];
  }

  isDegraded(key) {
    const slot = this.sources.get(key);
    return Boolean(slot && slot.lastError);
  }

  lastError(key) {
    const slot = this.sources.get(key);
    return slot ? slot.lastError : null;
  }

  /** { ok: n, degraded: n, backingOff: n } across all registered sources. */
  health() {
    let degraded = 0;
    let backingOff = 0;
    for (const slot of this.sources.values()) {
      if (slot.lastError) {
        degraded += 1;
        if (!slot.backoff.canTry()) backingOff += 1;
      }
    }
    return { total: this.sources.size, degraded, backingOff };
  }

  clear() {
    this.sources.clear();
  }

  // --- optional persistence (browser only) ---------------------------------

  hydrate(json) {
    if (!json) return;
    try {
      const data = JSON.parse(json);
      const lastGood = data?.lastGood;
      if (!lastGood || typeof lastGood !== 'object') return;
      for (const [key, entry] of Object.entries(lastGood)) {
        if (entry && Array.isArray(entry.events)) {
          this.setLastGood(key, entry.label || '', entry.events);
        }
      }
    } catch {
      /* corrupt cache is not an error */
    }
  }

  dehydrate() {
    const lastGood = {};
    for (const [key, slot] of this.sources.entries()) {
      if (slot.lastGood.length) {
        lastGood[key] = { label: slot.label || '', events: slot.lastGood };
      }
    }
    return JSON.stringify({ lastGood });
  }
}
