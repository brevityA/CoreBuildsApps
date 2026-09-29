/**
 * The score rows a team mark sits in.
 *
 * The hero, the board cards and the game detail all show the same thing — a
 * competitor's identity, then its score — and each used to carry its own copy
 * of that markup. That is why the logo gap survived in three places at once,
 * and why an unescaped score survived in two: there was no single place to fix
 * either.
 *
 * This lives in `lib/` rather than `public/js/` so that Node can execute it.
 * The browser modules import the shared parsers as `/lib/...`, which Node
 * cannot resolve, so a renderer that sits in `public/js/` can only ever be
 * read as *text* by a test — and "the template contains `esc(`" is not the
 * same assertion as "a hostile score comes out inert".
 *
 * The mark itself — which logo is allowed, what plate it needs, what it falls
 * back to — is `lib/logos.mjs`.
 */

import { escapeHtml, teamMark } from './logos.mjs';

/**
 * One competitor's line: mark, name, score.
 *
 * Shared by the hero banner and the game detail, which is what keeps a logo
 * sized the same in both and every score in the same column.
 *
 * Every remote value is escaped. `score` in particular is easy to miss: it is
 * usually a short number, so it reads as data rather than as a string that
 * arrived from a third party, and it is rendered into HTML either way.
 */
export function teamLine(team, event) {
  const win = event?.status === 'final' && team?.winner;
  return `
    <div class="team-row">
      <div class="abbr">${teamMark(team)}</div>
      <div class="team-name">${escapeHtml(team?.name || '')}</div>
      <div class="score ${win ? 'is-win' : ''}">${escapeHtml(team?.score ?? '')}</div>
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
    + `<span class="sc">${escapeHtml(team?.score ?? '')}</span></div>`;
  return `<div class="game-teams">${line(ev.away)}${line(ev.home)}</div>`;
}
