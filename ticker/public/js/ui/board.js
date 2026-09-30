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
import { HERO_ROTATE_MS, heroCandidates, pickHero, shouldRotate } from '/lib/hero.mjs';
import { gameTeams, heroMatch } from '/lib/team-rows.mjs';

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
  renderHero(list, heroIndex);
  renderCards(list);
  renderEmpty(list);
  restoreFocus(focusSnap);
  armHeroRotation(list);
}

/* ---- The banner's clock -------------------------------------------------
   `heroIndex` is the banner's position in the running order, and it is
   module-level because it has to survive the 60s refresh — a re-render that
   reset the index would snap the viewer back to the same game every minute,
   which is the bug this feature exists to fix. */
let heroIndex = 0;
let heroTimer = null;
let lastList = [];

function prefersReducedMotion() {
  return globalThis.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches === true;
}

/**
 * (Re)start the banner's timer for the list we just rendered.
 *
 * The timer is torn down and rebuilt on every render rather than left running,
 * because the render is what decides whether there is more than one game worth
 * rotating through; and because an interval that outlives the slate it was
 * built for is how a stale game ends up back on screen.
 */
function armHeroRotation(list) {
  lastList = list;
  clearInterval(heroTimer);
  heroTimer = null;
  if (heroCandidates(list).length < 2) return;
  heroTimer = setInterval(tickHero, HERO_ROTATE_MS);
}

function tickHero() {
  const hero = $('hero');
  // The list is re-read from the module rather than closed over: the tick runs
  // up to 12s after the render that armed it, and a refresh in between has
  // replaced the slate.
  if (!shouldRotate({
    list: lastList,
    focused: Boolean(hero && hero.contains(document.activeElement)),
    reducedMotion: prefersReducedMotion(),
    hidden: Boolean(document.hidden),
  })) return;
  heroIndex += 1;
  renderHero(lastList, heroIndex);
}

/**
 * The featured game: a live game with two teams, a scoreboard game before a
 * feed listing, rotating through them on the clock armed in `armHeroRotation`.
 *
 * The running order and the "may I move?" rule live in `lib/hero.mjs`, where
 * they are unit-tested; this function only paints whichever game that rule
 * picked.
 */
function renderHero(list, index = 0) {
  const hero = $('hero');
  if (!hero) return;
  const featured = pickHero(list, index);
  if (!featured) {
    hero.hidden = true;
    hero.innerHTML = '';
    return;
  }
  hero.hidden = false;
  const liveCount = heroCandidates(list).length;
  hero.innerHTML = `
    <h2 class="section-label">Live now &middot; ${liveCount}</h2>
    <article class="tile focusable" tabindex="0" data-action="game-detail" data-id="${esc(featured.id)}" style="${esc(accentStyle(featured))}">
      <div class="hero-meta">
        <span class="badge badge--live">LIVE</span>
        <span>${esc(featured.league || '')}</span>
        <span class="hero-meta__clock">${esc(featured.detail || '')}</span>
        <span class="hero-meta__chans">${(featured.channels || []).map((c) => `<span class="pill">${esc(c)}</span>`).join('')}</span>
        <button class="btn btn--slim focusable" data-action="watch" data-id="${esc(featured.id)}">&#9654; Watch</button>
      </div>
      ${heroMatch(featured)}
    </article>`;
}

function renderCards(list) {
  const host = $('board');
  if (!host) return;
  const cards = list.filter((e) => e.away && e.home);
  const headlines = list.filter((e) => !(e.away && e.home));
  host.innerHTML = [...cards.map(gameCard), ...headlines.map(headlineCard)].join('');
  setHidden('board', cards.length + headlines.length === 0);
}

/**
 * A game card, laid out the way a guide reads: when (or the live clock), the
 * two teams with their marks, and the channel — the answer to "where is it
 * on" — as the card's headline. Watch lives one OK away, in Game Detail and
 * on the banner; a second button on every card was a second D-pad stop on
 * every card.
 */
export function gameCard(ev) {
  const { badge, label } = statusOf(ev);
  const favs = favSet(store.state);
  const teams = cardFavTeams(ev);
  const favOn = teams.length > 0 && teams.every((t) => favs.has(t));
  // Upcoming games show their local start time; ESPN's `detail` for a game
  // that has not started is a full date sentence that no card can fit.
  const when = ev.status === 'upcoming' ? (startLabel(ev) || label) : label;
  const [net, ...more] = ev.channels || [];
  return `
    <article class="card focusable" tabindex="0" data-action="game-detail" data-id="${esc(ev.id)}" style="${esc(accentStyle(ev))}">
      <div class="card__top">
        <span class="badge badge--${badge}" title="${esc(label)}">${esc(when)}</span>
        <span class="league-tag">${esc(ev.league || ev.feed || 'RSS')}</span>
      </div>
      ${gameTeams(ev)}
      <div class="card__foot">
        <span class="card__net">${esc(net || 'No channel listed')}</span>
        ${more.length ? `<span class="card__more" title="${esc(more.join(', '))}" aria-label="${esc(moreChannelsLabel(more))}">+${more.length}</span>` : ''}
        <button class="fav-star ${favOn ? 'is-on' : ''}" data-action="toggle-card-fav" data-id="${esc(ev.id)}"
                aria-label="${favOn ? 'Unfavorite' : 'Favorite'} these teams" aria-pressed="${favOn}">&#9733;</button>
      </div>
    </article>`;
}

/** What "+N" says aloud: a title tooltip is not reliably read, and a TV never hovers. */
function moreChannelsLabel(more) {
  return `${more.length} more ${more.length === 1 ? 'channel' : 'channels'}: ${more.join(', ')}`;
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
        <span class="card__net">${esc((ev.channels || [])[0] || ev.feed || 'Listing')}</span>
        ${(ev.channels || []).length > 1 ? `<span class="card__more" title="${esc(ev.channels.slice(1).join(', '))}" aria-label="${esc(moreChannelsLabel(ev.channels.slice(1)))}">+${ev.channels.length - 1}</span>` : ''}
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
