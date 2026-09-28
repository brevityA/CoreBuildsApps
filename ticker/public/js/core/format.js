/**
 * Formatting and derived values.
 *
 * Pure functions over one event or one state object. Nothing here touches the
 * DOM, so these are the parts that could be unit-tested without a browser.
 */

import { LEAGUES } from '/lib/scoreboard.mjs';
import { matchesFavorite } from '/lib/favorites.mjs';

/** The league accent for an event, for the card's left edge and league tag. */
export function accentFor(ev) {
  for (const league of Object.values(LEAGUES)) {
    if (league.label === ev.league) return league.accent;
  }
  return 'var(--accent)';
}

/** Badge class + label for an event's status. */
export function statusOf(ev) {
  if (ev.status === 'live') return { badge: 'live', label: ev.detail || 'LIVE' };
  if (ev.status === 'final') return { badge: 'final', label: 'FINAL' };
  return { badge: 'upcoming', label: ev.detail || 'UP' };
}

/** The uppercase abbreviation set the user has starred. */
export function favSet(state = null) {
  // Imported lazily so this module stays pure over the object it is given.
  const raw = (state ? state.favorites : null) ?? '';
  return new Set(
    String(raw)
      .split(/[,\s]+/)
      .map((s) => s.trim().toUpperCase())
      .filter(Boolean),
  );
}

export function isFav(ev, favs) {
  return matchesFavorite(ev, favs);
}

/** The two abbreviations on a card (empty for a headline-only listing). */
export function cardFavTeams(ev) {
  return [ev.away?.abbr, ev.home?.abbr]
    .filter(Boolean)
    .map((s) => String(s).toUpperCase());
}

export function clockOptions(fmt) {
  return fmt === '24'
    ? { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }
    : { hour: 'numeric', minute: '2-digit' };
}

/** "7:00 PM" from an ISO start, in the viewer's own timezone. */
export function startLabel(ev) {
  if (!ev.start) return '';
  const d = new Date(ev.start);
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
}
