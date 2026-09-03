const DEFAULT_BASE_MS = 15_000;
const DEFAULT_CAP_MS = 600_000;

export function createBackoff({ baseMs = DEFAULT_BASE_MS, capMs = DEFAULT_CAP_MS } = {}) {
  let attempt = 0;
  let nextAt = 0;

  return {
    get attempt() { return attempt; },
    get nextAt() { return nextAt; },

    shouldSkip(now = Date.now()) {
      return now < nextAt;
    },

    fail(retryAfterHeader, now = Date.now()) {
      attempt++;
      let delay = Math.min(baseMs * Math.pow(2, attempt - 1), capMs);
      delay += Math.random() * delay * 0.25;
      const retryMs = parseRetryAfter(retryAfterHeader, now);
      if (retryMs > 0) delay = Math.max(delay, retryMs);
      nextAt = now + delay;
      return delay;
    },

    succeed() {
      attempt = 0;
      nextAt = 0;
    },

    reset() {
      attempt = 0;
      nextAt = 0;
    },

    toJSON() {
      return { attempt, nextAt };
    },

    hydrate(obj) {
      if (!obj) return;
      attempt = Number(obj.attempt) || 0;
      nextAt = Number(obj.nextAt) || 0;
    },
  };
}

export function parseRetryAfter(header, now = Date.now()) {
  if (!header) return 0;
  const s = String(header).trim();
  const n = Number(s);
  if (Number.isFinite(n) && n > 0) return n * 1000;
  const d = Date.parse(s);
  if (Number.isFinite(d)) return Math.max(0, d - now);
  return 0;
}
