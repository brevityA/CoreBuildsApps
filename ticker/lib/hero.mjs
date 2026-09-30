/**
 * Hero selection: which live game the banner shows, and when it moves on.
 *
 * Split out of the board so the awkward half is testable. Which game to
 * feature is a sort; *when to change it* is the part that goes wrong in
 * production, because a banner that swaps out from under a viewer is worse
 * than a banner that never swaps at all.
 *
 * The rules, and why each one exists:
 *
 * - A scoreboard game outranks a feed listing. An RSS item can say "LIVE" with
 *   no score and no clock, and it used to take the hero from a real game.
 * - The hero only moves while nobody is standing on it. A D-pad viewer reading
 *   the banner has focus on the banner; rotating then replaces the node they
 *   are on, `restoreFocus` finds no match, and the focus ring drops to the
 *   body — the viewer loses their place mid-read.
 * - It only moves while the app is visible. A TV in standby keeps its timers;
 *   there is no reason to burn cycles repainting a banner nobody can see.
 * - Honours reduced motion. Auto-advancing content is motion, and on a TV it
 *   is the kind that is hardest to look away from.
 */

/** How long a featured game holds the banner before the next one takes it. */
export const HERO_ROTATE_MS = 12000;

/**
 * Live games that can hold the banner, in the order they should hold it:
 * real games first, then feed listings, each group keeping feed order (which
 * is recency — the slate is already sorted by the sources).
 */
export function heroCandidates(list) {
  const live = (list || []).filter((e) => e && e.status === 'live' && e.away && e.home);
  return [
    ...live.filter((e) => e.source !== 'rss'),
    ...live.filter((e) => e.source === 'rss'),
  ];
}

/**
 * The game to feature at `index`, wrapping. Returns null when nothing is on,
 * which is the board's empty state rather than an error.
 */
export function pickHero(list, index = 0) {
  const candidates = heroCandidates(list);
  if (!candidates.length) return null;
  // A non-finite index is treated as "the first one" rather than allowed to
  // reach the modulo: NaN % n is NaN, and `candidates[NaN]` is undefined, so
  // the banner would render empty over a slate that is plainly live.
  const i = Number.isFinite(index) ? Math.trunc(index) : 0;
  const n = ((i % candidates.length) + candidates.length) % candidates.length;
  return candidates[n];
}

/**
 * Whether the banner should advance right now.
 *
 * Deliberately takes its whole world as arguments: it is the one rule in this
 * file that cannot be checked by reading the output, so it is checked by
 * calling it.
 */
export function shouldRotate({
  list = [],
  focused = false,
  reducedMotion = false,
  hidden = false,
} = {}) {
  if (focused || reducedMotion || hidden) return false;
  return heroCandidates(list).length > 1;
}
