/**
 * Exponential backoff with jitter — shared by the browser/WebView client
 * (public/js/app.js via lib/client-slate.mjs) and the Node server.
 *
 * Pure logic, no I/O, so it is unit-testable in Node and safe to import in a
 * WebView (no Buffer/fs dependencies).
 *
 * Policy (broadcast chyron tuning):
 *   - base wait 15 s, double per consecutive failure, cap 10 min
 *   - ±25% jitter so a room full of sticks never synchronizes retries
 *   - `Retry-After` (seconds or HTTP-date) can override the computed wait
 */
export const BACKOFF_DEFAULTS = {
  baseMs: 15_000,
  maxMs: 600_000,
  factor: 2,
  jitter: 0.25,
};

export function createBackoff(opts = {}) {
  const o = { ...BACKOFF_DEFAULTS, ...opts };
  let failCount = 0;
  let nextAt = 0; // epoch ms at which the next attempt is permitted

  return {
    get failCount() {
      return failCount;
    },
    get nextAt() {
      return nextAt;
    },
    /** Record a failure; returns the number of ms to wait before retrying. */
    fail(now = Date.now(), retryAfterMs = 0) {
      failCount += 1;
      const exponent = Math.min(failCount - 1, 8);
      const base = o.baseMs * Math.pow(o.factor, exponent);
      const capped = Math.min(base, o.maxMs);
      const spread = capped * o.jitter;
      const jittered = capped + (Math.random() * 2 - 1) * spread;
      const wait = Math.max(Number(retryAfterMs) || 0, Math.round(jittered));
      nextAt = now + wait;
      return wait;
    },
    /** Record a success; resets the schedule. */
    success() {
      failCount = 0;
      nextAt = 0;
    },
    /** True when a retry is permitted now. */
    canTry(now = Date.now()) {
      return now >= nextAt;
    },
    /** ms remaining until the next attempt is permitted (0 = now). */
    waitMs(now = Date.now()) {
      return Math.max(0, nextAt - now);
    },
    reset() {
      failCount = 0;
      nextAt = 0;
    },
    toJSON() {
      return { failCount, nextAt };
    },
    fromJSON(json) {
      if (json && typeof json === 'object') {
        failCount = Number(json.failCount) || 0;
        nextAt = Number(json.nextAt) || 0;
      }
      return this;
    },
  };
}

/**
 * Parse an HTTP `Retry-After` header value into milliseconds.
 * Accepts either an integer number of seconds or an HTTP-date.
 * Returns 0 when absent/unparseable, and clamps to the backoff cap.
 */
export function parseRetryAfterMs(value) {
  if (value == null) return 0;
  const text = String(value).trim();
  if (!text) return 0;
  if (/^\d+$/.test(text)) {
    return Math.min(Number(text) * 1000, BACKOFF_DEFAULTS.maxMs);
  }
  const parsed = Date.parse(text);
  if (!Number.isNaN(parsed)) {
    return Math.max(0, Math.min(parsed - Date.now(), BACKOFF_DEFAULTS.maxMs));
  }
  return 0;
}
