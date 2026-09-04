import { createBackoff } from './backoff.mjs';

const MAX_ITEMS_PER_SOURCE = 100;
const STORAGE_KEY = 'coreline.v1.sources';

export class SourceRegistry {
  constructor() {
    this._entries = new Map();
  }

  _ensure(key) {
    if (!this._entries.has(key)) {
      this._entries.set(key, {
        backoff: createBackoff(),
        lastGood: [],
        lastError: null,
        health: 'unknown',
        stale: false,
      });
    }
    return this._entries.get(key);
  }

  shouldSkip(key, now = Date.now()) {
    const e = this._entries.get(key);
    return e ? e.backoff.shouldSkip(now) : false;
  }

  recordSuccess(key, items) {
    const e = this._ensure(key);
    e.backoff.succeed();
    e.lastGood = (items || []).slice(0, MAX_ITEMS_PER_SOURCE);
    e.lastError = null;
    e.health = 'ok';
    e.stale = false;
  }

  recordFailure(key, error, retryAfterHeader) {
    const e = this._ensure(key);
    e.backoff.fail(retryAfterHeader);
    e.lastError = String(error || 'unknown');
    e.health = e.lastGood.length ? 'stale' : 'degraded';
    e.stale = true;
  }

  getLastGood(key) {
    const e = this._entries.get(key);
    return e ? e.lastGood : [];
  }

  getHealth(key) {
    const e = this._entries.get(key);
    return e ? e.health : 'unknown';
  }

  isStale(key) {
    const e = this._entries.get(key);
    return e ? e.stale : false;
  }

  summary() {
    let ok = 0, degraded = 0, stale = 0;
    for (const e of this._entries.values()) {
      if (e.health === 'ok') ok++;
      else if (e.health === 'degraded') degraded++;
      else if (e.health === 'stale') stale++;
    }
    const total = this._entries.size;
    let label = 'All sources live';
    if (degraded > 0) label = `${degraded} retrying`;
    else if (stale > 0) label = 'Showing cached';
    return { total, ok, degraded, stale, label };
  }

  dehydrate() {
    const out = {};
    for (const [key, e] of this._entries) {
      out[key] = {
        backoff: e.backoff.toJSON(),
        lastGood: e.lastGood,
        lastError: e.lastError,
        health: e.health,
        stale: e.stale,
      };
    }
    return out;
  }

  hydrate(obj) {
    if (!obj || typeof obj !== 'object') return;
    for (const [key, data] of Object.entries(obj)) {
      const e = this._ensure(key);
      if (data.backoff) e.backoff.hydrate(data.backoff);
      if (Array.isArray(data.lastGood)) e.lastGood = data.lastGood.slice(0, MAX_ITEMS_PER_SOURCE);
      if (data.lastError !== undefined) e.lastError = data.lastError;
      if (data.health) e.health = data.health;
      if (data.stale !== undefined) e.stale = data.stale;
    }
  }

  persist() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.dehydrate()));
    } catch { /* quota */ }
  }

  restore() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) this.hydrate(JSON.parse(raw));
    } catch { /* corrupt */ }
  }

  clear() {
    this._entries.clear();
  }
}
