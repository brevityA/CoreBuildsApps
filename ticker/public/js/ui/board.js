/**
 * The board: hero tile, card grid, and the empty state.
 *
 * Everything here rebuilds its container's innerHTML on every render, which
 * is cheap and simple but destroys whatever had focus (AUDIT D5 — focus a
 * card, wait for the 60s refresh, and the focus ring vanishes). So every
 * render is wrapped in captureFocus/restoreFocus, which identifies the focused
 * element by *event id* rather than by node, because the node it was is about
 * to be replaced.
 */

import { store } from '../core/store.js';
import { emit } from '../core/bus.js';
import { $, $$, esc, cssEscape, setHidden } from '../core/dom.js';
import { accentFor, accentStyle, statusOf, cardFavTeams, favSet, startLabel } from '../core/format.js';

export function captureFocus() {
  const el = document.activeElement;
  if (!el || !el.classList || !el.classList.contains('focusable')) return null;
  const card = el.closest('[data-id]');
  if (card?.dataset?.id) {
    return { eventId: card.dataset.id, container: el.closest('#hero, #board')?.id || '' };
  }
  if (el.dataset?.action === 'filter' && el.dataset.league) return { filter: el.dataset.league };
  if (el.dataset?.action === 'fav-filter' && el.dataset.abbr) return { fav: el.dataset.abbr };
  return null;
}

export function restoreFocus(snap) {
  if (!snap) return;
  if (snap.eventId) {
    const root = snap.container ? $(snap.container) : document;
    const target = root?.querySelector(`.focusable[data-id="${cssEscape(snap.eventId)}"]`);
    if (target) { target.focus(); return; }
  }
  if (snap.filter) {
    $(`leagues`)?.querySelector(`[data-action="filter"][data-league="${cssEscape(snap.filter)}"]`)?.focus();
    return;
  }
  if (snap.fav) {
    $$('#myTeams [data-abbr]').find((c) => c.dataset.abbr === snap.fav)?.focus();
  }
}

export function renderBoard(list) {
  const focusSnap = captureFocus();
  renderHero(list);
  renderCards(list);
  renderEmpty(list);
  restoreFocus(focusSnap);
}

/** The featured game: the first live game with two teams. */
function renderHero(list) {
  const hero = $('hero');
  if (!hero) return;
  const featured = list.find((e) => e.status === 'live' && e.away && e.home);
  if (!featured) {
    hero.hidden = true;
    hero.innerHTML = '';
    return;
  }
  hero.hidden = false;
  hero.innerHTML = `
    <article class="tile focusable" tabindex="0" data-action="game-detail" data-id="${esc(featured.id)}" style="${esc(accentStyle(featured))}">
      <div>
        <div class="hero-meta">
          <span class="badge badge--live">LIVE</span>
          <span>${esc(featured.league || '')}</span>
          <span>${esc(featured.detail || '')}</span>
        </div>
        <div class="teams">
          ${teamLine(featured.away, featured)}
          ${teamLine(featured.home, featured)}
        </div>
      </div>
      <div class="hero-side">
        <div class="pills">${(featured.channels || []).map((c) => `<span class="pill">${esc(c)}</span>`).join('')}</div>
        <button class="btn focusable" data-action="watch" data-id="${esc(featured.id)}">&#9654; Watch</button>
        <div class="when">${esc(featured.venue || featured.feed || '')}</div>
      </div>
    </article>`;
}

function teamLine(team, event) {
  const win = event.status === 'final' && team.winner;
  return `
    <div class="team-row">
      <div class="abbr">${esc(team.abbr || '—')}</div>
      <div class="team-name">${esc(team.name || '')}</div>
      <div class="score ${win ? 'is-win' : ''}">${team.score ?? ''}</div>
    </div>`;
}

function renderCards(list) {
  const host = $('board');
  if (!host) return;
  const cards = list.filter((e) => e.away && e.home);
  const headlines = list.filter((e) => !(e.away && e.home));
  host.innerHTML = [...cards.map(gameCard), ...headlines.map(headlineCard)].join('');
  setHidden('board', cards.length + headlines.length === 0);
}

export function gameCard(ev) {
  const { badge, label } = statusOf(ev);
  const favs = favSet(store.state);
  const teams = cardFavTeams(ev);
  const favOn = teams.length > 0 && teams.every((t) => favs.has(t));
  const when = ev.status === 'upcoming' ? startLabel(ev) : (ev.venue || '');
  return `
    <article class="card focusable" tabindex="0" data-action="game-detail" data-id="${esc(ev.id)}" style="${esc(accentStyle(ev))}">
      <div class="card__top">
        <span class="league-tag">${esc(ev.league || ev.feed || 'RSS')}</span>
        <div class="card__top-right">
          <span class="badge badge--${badge}">${esc(label)}</span>
          <button class="fav-star ${favOn ? 'is-on' : ''}" data-action="toggle-card-fav" data-id="${esc(ev.id)}"
                  aria-label="${favOn ? 'Unfavorite' : 'Favorite'} these teams" aria-pressed="${favOn}">&#9733;</button>
          <button class="btn--mini focusable" data-action="watch" data-id="${esc(ev.id)}" aria-label="Watch in app" title="Watch in app">&#9654;</button>
        </div>
      </div>
      <div class="game-teams">
        <div class="gt ${ev.away?.winner ? 'is-win' : ''}"><span class="who">${esc(ev.away.abbr)}</span><span class="sc">${ev.away.score ?? ''}</span></div>
        <div class="gt ${ev.home?.winner ? 'is-win' : ''}"><span class="who">${esc(ev.home.abbr)}</span><span class="sc">${ev.home.score ?? ''}</span></div>
      </div>
      <div class="card__foot">
        <div class="channels">${(ev.channels || []).map((c) => `<span class="pill pill--plain">${esc(c)}</span>`).join('') || '<span class="when">No channel listed</span>'}</div>
        <span class="when">${esc(when)}</span>
      </div>
    </article>`;
}

export function headlineCard(ev) {
  const { badge, label } = statusOf(ev);
  return `
    <article class="card focusable" tabindex="0" data-action="game-detail" data-id="${esc(ev.id)}" style="${esc(accentStyle(ev))}">
      <div class="card__top">
        <span class="league-tag">${esc(ev.league || ev.feed || 'RSS')}</span>
        <span class="badge badge--${badge}">${esc(label)}</span>
      </div>
      <div class="gt"><span class="who">${esc(ev.headline || ev.rawTitle || 'Listing')}</span></div>
      <div class="card__foot">
        <div class="channels">${(ev.channels || []).map((c) => `<span class="pill pill--plain">${esc(c)}</span>`).join('')}</div>
      </div>
    </article>`;
}

/**
 * The empty state.
 *
 * There are three different empty states and they need different copy: no
 * listings at all, no results for the search box, and nothing under the
 * selected league tab. Each one offers the action that undoes it — that is
 * the entire point of an empty state.
 */
function renderEmpty(list) {
  const el = $('empty');
  if (!el) return;
  const searching = Boolean(store.query.trim());
  const filtered = store.state.leagueFilter && store.state.leagueFilter !== 'ALL';
  const show = list.length === 0;
  el.hidden = !show;
  if (!show) return;

  setText('emptyKicker', searching ? 'No matches' : filtered ? 'Nothing here' : 'No listings yet');
  setText('emptyTitle', searching
    ? `Nothing matches “${store.query.trim()}”`
    : filtered ? 'Nothing on this tab' : 'Add a sports RSS feed');
  setText('emptyBody', searching
    ? 'Try a team abbreviation, a channel name, or a shorter word.'
    : filtered
      ? 'This league has no games in the current slate. Pick another tab, or clear the filter.'
      : 'The three channel apps already publish the slate. Core Line is the reader — a crawl that says Team vs Team · ESPN, TSN4, SN 3.');

  const clear = $('emptyClear');
  if (clear) {
    clear.hidden = !(searching || filtered);
    clear.textContent = searching ? 'Clear search' : 'Clear filters';
  }
}

function setText(id, text) {
  const el = $(id);
  if (el) el.textContent = text;
}

export { emit };
