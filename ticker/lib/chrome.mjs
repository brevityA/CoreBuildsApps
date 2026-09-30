/**
 * Which chrome the board shows by default, and when the ticker is on.
 *
 * Split out of state.js so the upgrade rule is unit-tested: state.js imports
 * through the page's absolute paths and cannot be loaded by Node.
 */

/**
 * The scrolling ticker's stored setting, from whatever was in storage.
 *
 * A fresh install starts with it off (supporter feedback, 2026-09-29: a TV
 * board wants the banner and the list, and the crawl is a second thing
 * moving). But an existing install has never stored the key, because it did
 * not exist, and the ticker is what that viewer has been looking at — so a
 * stored state without the key keeps it on. Only a real boolean is trusted;
 * "false" from a hand-edited store is not false.
 */
export function resolveTicker(raw) {
  const stored = raw && typeof raw === 'object' ? raw : null;
  if (!stored || Object.keys(stored).length === 0) return false;
  if (!('ticker' in stored)) return true;
  return stored.ticker === true;
}

/**
 * Whether the chyron is drawn. Crawl mode is the ticker full-screen, and the
 * phone's floating overlay is nothing but the ticker, so both show it
 * whatever the setting says.
 */
export function tickerShown(state, { overlay = false } = {}) {
  if (overlay) return true;
  if (state?.mode === 'crawl') return true;
  return state?.ticker === true;
}
