/**
 * The score rows a team mark sits in.
 *
 * The hero, the board cards and the game detail all show the same thing — a
 * competitor's identity, then its score — and until now they each had their
 * own copy of that markup. That is part of why the logo gap survived: there
 * was no single place to fix it. These are the single place.
 *
 * The mark itself — which logo is allowed, what it falls back to, and the
 * markup for both — is in `lib/logos.mjs`, where a Node test can call it.
 * This module only decides how a row is laid out.
 */

import { esc } from './dom.js';
import { teamMark } from '/lib/logos.mjs';

/**
 * One competitor's line: mark, name, score.
 *
 * Shared by the hero banner and the game detail, which is what keeps a logo
 * sized the same in both and every score in the same column.
 */
export function teamLine(team, event) {
  const win = event?.status === 'final' && team?.winner;
  return `
    <div class="team-row">
      <div class="abbr">${teamMark(team)}</div>
      <div class="team-name">${esc(team?.name || '')}</div>
      <div class="score ${win ? 'is-win' : ''}">${team?.score ?? ''}</div>
    </div>`;
}

/**
 * The compact two-line score block a board card uses.
 *
 * The mark goes in front of the abbreviation here rather than over it; see
 * `teamMark` for why the two forms differ.
 */
export function gameTeams(ev) {
  const line = (team) => `
    <div class="gt ${team?.winner ? 'is-win' : ''}">`
    + `<span class="who">${teamMark(team, { compact: true })}</span>`
    + `<span class="sc">${team?.score ?? ''}</span></div>`;
  return `<div class="game-teams">${line(ev.away)}${line(ev.home)}</div>`;
}

/**
 * The banner's match line: away team, the score, home team — the layout a
 * broadcast graphic uses, and the one in the supporter's reference. One row
 * instead of two stacked score rows is what lets the banner and a row of
 * cards share a 540dp screen. The name may take two lines ("Philadelphia /
 * Eagles") rather than truncate; the mark carries the abbreviation, as a
 * logo or, without one, as the monogram.
 */
export function heroMatch(ev) {
  const side = (team, home) => `
    <div class="hm__team${home ? ' hm__team--home' : ''}${team?.winner ? ' is-win' : ''}">
      <span class="hm__mark">${teamMark(team)}</span>
      <span class="hm__name">${esc(team?.name || team?.abbr || '')}</span>
    </div>`;
  const a = ev?.away?.score;
  const h = ev?.home?.score;
  const hasScore = (a ?? '') !== '' || (h ?? '') !== '';
  const score = hasScore
    ? `${esc(String(a ?? 0))}<span class="hm__dash">&ndash;</span>${esc(String(h ?? 0))}`
    : '<span class="hm__vs">vs</span>';
  return `<div class="hm">${side(ev?.away, false)}<div class="hm__score">${score}</div>${side(ev?.home, true)}</div>`;
}
