/**
 * Score alerts — the stateful half.
 *
 * The comparison itself lives in lib/alerts.mjs so it can be tested in Node.
 * This module owns the only two things that need a browser: the previous
 * snapshot, and the rule that the first slate of a session seeds the baseline
 * without firing — otherwise every app start would announce the whole board.
 */

import { store } from '../core/store.js';
import { emit } from '../core/bus.js';
import { favSet } from '../core/format.js';
import { snapshotOf, diffSlate } from '/lib/alerts.mjs';

let baseline = new Map();
let seeded = false;

/** Compare the current slate against the last one and announce changes. */
export function noteScores(events) {
  const next = snapshotOf(events);
  const favs = favSet(store.state);

  if (!seeded || !store.state.alerts || favs.size === 0) {
    baseline = next;
    seeded = true;
    return;
  }

  const messages = diffSlate(baseline, next, favs);
  baseline = next;

  // One alert per cycle. Three toasts stacked on a TV is noise, and the crawl
  // still carries every score for anyone who wants to read it.
  if (messages.length) emit('alert', messages[0]);
}

export function resetAlerts() {
  baseline = new Map();
  seeded = false;
}
