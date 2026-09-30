/**
 * The scoreboard bug: one game at a time, pinned to the top of the screen,
 * over whatever app the viewer is watching.
 *
 * It is the VPN dot's pattern (`lib/vpn.mjs`, `VpnDotWindow.kt`) applied to a
 * score: its own small always-on-top window, anchored to an edge, non-focusable
 * and touch-through, so the remote behaves as if it were not there; its own
 * switch and shape; and a payload the native side persists. What it is not is
 * the crawl — the crawl is a full-width strip of every item, the bug is a
 * broadcast-style score box of the game that matters right now.
 *
 * Everything here is pure so Node can execute it: which games the bug cycles
 * through, what it says about each one, and the config the native window is
 * built from. The page (`public/js/scorebug.js`) only fetches and paints.
 */

import { escapeHtml, teamMark } from './logos.mjs';
import { heroCandidates } from './hero.mjs';
import { compareEvents } from './scoreboard.mjs';
import { matchesFavorite } from './favorites.mjs';

/** Top edge only: the bottom of a TV picture is where captions and the player's own controls live. */
export const SCOREBUG_POSITIONS = ['top_center', 'top_left', 'top_right'];

/** Opacity of the whole bug, percent. It carries its own dark plate, so the floor is higher than the dot's. */
export const SCOREBUG_OPACITY = Object.freeze({ min: 40, max: 100, default: 92 });

/** How long one game holds the bug before the next live game takes it. */
export const SCOREBUG_ROTATE_MS = 10000;

/** With nothing live, how far ahead an upcoming game may be to be shown instead. */
export const SCOREBUG_LOOKAHEAD_MS = 12 * 3600e3;

function clampInt(value, min, max, fallback) {
  const n = Number(value);
  if (!Number.isFinite(n)) return fallback;
  return Math.max(min, Math.min(max, Math.round(n)));
}

/**
 * The payload the native window is built from, with the panel's calibrated
 * overscan folded into its margin — the same seam as `vpnDotConfig`, for the
 * same reason: a bug in the 3% a set crops is a bug nobody sees.
 */
export function scoreBugConfig(prefs, { overscanPx = 0, devicePixelRatio = 1 } = {}) {
  const dpr = Number(devicePixelRatio);
  const scale = Number.isFinite(dpr) && dpr > 0 ? dpr : 1;
  const overscan = Number.isFinite(Number(overscanPx)) ? Number(overscanPx) : 0;
  return {
    position: SCOREBUG_POSITIONS.includes(prefs?.scoreBugPosition) ? prefs.scoreBugPosition : 'top_center',
    opacity: clampInt(prefs?.scoreBugOpacity, SCOREBUG_OPACITY.min, SCOREBUG_OPACITY.max, SCOREBUG_OPACITY.default),
    marginDp: Math.min(200, Math.max(16, Math.round(overscan / scale) + 16)),
  };
}

/**
 * The games the bug cycles through, in order.
 *
 * Live games first — the hero's rule, so a scoreboard game outranks an RSS
 * item that merely says "LIVE" — with the viewer's starred teams at the front.
 * With nothing live, the next few games starting within the lookahead, so the
 * bug says "next up" instead of sitting empty. Finals never: the bug is for
 * what is on now. An empty list means the window shows nothing at all.
 */
export function scoreBugQueue(events, { favorites = '', now = Date.now() } = {}) {
  const list = Array.isArray(events) ? events : [];
  const favFirst = (a, b) => {
    const af = matchesFavorite(a, favorites) ? 0 : 1;
    const bf = matchesFavorite(b, favorites) ? 0 : 1;
    return af - bf;
  };
  const live = heroCandidates(list);
  if (live.length) {
    // Stable sort: keeps heroCandidates' scoreboard-before-feed order inside
    // each favourite group.
    return [...live].sort(favFirst);
  }
  const soon = list.filter((e) => {
    if (!e || e.status !== 'upcoming' || !e.away || !e.home) return false;
    const t = Date.parse(e.start || '');
    return Number.isFinite(t) && t >= now - 15 * 60e3 && t - now <= SCOREBUG_LOOKAHEAD_MS;
  });
  return soon.sort((a, b) => favFirst(a, b) || compareEvents(a, b)).slice(0, 3);
}

/** The game on the bug at `index`, wrapping; null when the queue is empty. */
export function scoreBugAt(queue, index) {
  if (!queue?.length) return null;
  const i = ((Number(index) || 0) % queue.length + queue.length) % queue.length;
  return queue[i];
}

/**
 * The status slot: the clock for a live game, the local start time for an
 * upcoming one. `startLabel` is passed in because it formats in the viewer's
 * locale, which Node's test runner does not share.
 */
export function scoreBugStatus(ev, startLabel = () => '') {
  if (!ev) return '';
  if (ev.status === 'live') return String(ev.detail || 'LIVE');
  const at = startLabel(ev);
  return at ? `Next · ${at}` : 'Next';
}

/**
 * The bug's markup: league and status on a slim top rail, then the broadcast
 * line — away mark and score, home score and mark — then the channel. Every
 * remote value is escaped; the marks come from `teamMark`, which escapes its
 * own and never emits an inline handler (the page's CSP forbids them).
 */
export function scoreBugHtml(ev, { position = 0, total = 0, startLabel = () => '' } = {}) {
  if (!ev) return '';
  const live = ev.status === 'live';
  const hasScore = live && ((ev.away?.score ?? '') !== '' || (ev.home?.score ?? '') !== '');
  const side = (team, home) => `
    <div class="sb__team${home ? ' sb__team--home' : ''}">
      <span class="sb__mark">${teamMark(team)}</span>
      <span class="sb__abbr">${escapeHtml(team?.abbr || team?.name || '')}</span>
      ${hasScore ? `<span class="sb__score">${escapeHtml(String(team?.score ?? 0))}</span>` : ''}
    </div>`;
  const channel = (ev.channels || [])[0];
  const count = total > 1
    ? `<span class="sb__count">${escapeHtml(String(position + 1))}/${escapeHtml(String(total))}</span>`
    : '';
  return `
    <div class="sb${live ? ' is-live' : ' is-next'}">
      <div class="sb__rail">
        ${live ? '<span class="sb__live">LIVE</span>' : ''}
        <span class="sb__league">${escapeHtml(ev.league || ev.feed || '')}</span>
        <span class="sb__status">${escapeHtml(scoreBugStatus(ev, startLabel))}</span>
        ${count}
      </div>
      <div class="sb__line">
        ${side(ev.away, false)}
        <span class="sb__sep">${hasScore ? '&ndash;' : '@'}</span>
        ${side(ev.home, true)}
      </div>
      ${channel ? `<div class="sb__chan">${escapeHtml(channel)}</div>` : ''}
    </div>`;
}
