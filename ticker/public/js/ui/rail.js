/**
 * The left rail: counts, source health, My Teams, and the league nav.
 *
 * The rail is a fixed width so the board's column count is derived from what
 * is left over, not guessed. It is also the only place the app summarises
 * itself, so the three counts and the health pill are all it gets.
 */

import { LEAGUES, LEAGUE_LABELS, SPORT_GROUPS } from '/lib/scoreboard.mjs';
import { store, persist } from '../core/store.js';
import { emit } from '../core/bus.js';
import { $, $$, esc, setHidden, setText } from '../core/dom.js';
import { favSet } from '../core/format.js';

export function renderStats() {
  const { events } = store;
  setText('statLive', events.filter((e) => e.status === 'live').length);
  setText('statUp', events.filter((e) => e.status === 'upcoming').length);
  setText('statRss', events.filter((e) => e.source === 'rss').length);
}

/**
 * My Teams: the user's starred abbreviations, with a live count each.
 *
 * Favourites already sort to the top of the board, but a rail entry makes
 * them addressable — you can jump straight to Toronto without scanning.
 */
export function renderMyTeams() {
  const host = $('myTeams');
  if (!host) return;
  const favs = favSet(store.state);
  const rows = [...favs].map((abbr) => {
    const games = store.events.filter(
      (e) => [e.away?.abbr, e.home?.abbr].filter(Boolean).map((s) => String(s).toUpperCase()).includes(abbr),
    );
    const live = games.filter((g) => g.status === 'live').length;
    return { abbr, count: games.length, live };
  }).sort((a, b) => (b.live - a.live) || (b.count - a.count) || a.abbr.localeCompare(b.abbr));

  setHidden('myTeamsBlock', rows.length === 0);
  // The chip is a shortcut into the search box, so its pressed state is the
  // query — one filter mechanism, visible and clearable, rather than two.
  const activeQuery = String(store.query || '').trim().toUpperCase();
  host.innerHTML = rows.map((t) => `
    <button class="chip chip--row focusable" data-action="fav-filter" data-abbr="${esc(t.abbr)}" aria-pressed="${activeQuery === t.abbr}">
      ${t.live ? '<span class="badge badge--live">LIVE</span>' : ''}
      ${esc(t.abbr)}
      <span class="chip__count">${t.count}</span>
    </button>`).join('');
}

/**
 * The league nav. Sport super-tabs sit above the individual leagues:
 * "Football" is a bigger target than "NCAAF" and is how people actually
 * talk about what they want to watch.
 */
export function renderFilters() {
  const host = $('leagues');
  if (!host) return;
  const { events, state } = store;
  const counts = {
    ALL: events.length,
    LIVE: events.filter((e) => e.status === 'live').length,
    RSS: events.filter((e) => e.source === 'rss').length,
    TV: events.filter((e) => e.source === 'epg').length,
  };
  for (const label of LEAGUE_LABELS) {
    counts[label] = events.filter((e) => e.league === label).length;
  }

  const chips = ['ALL', 'LIVE', 'RSS'].map((label) => filterChip(label, label, counts[label]));
  // Live events only the TV guide lists (Sky, BBC, conference networks).
  if (counts.TV) chips.push(filterChip('TV', 'On TV', counts.TV));
  for (const g of SPORT_GROUPS) {
    const n = g.leagues.reduce((sum, id) => sum + (counts[LEAGUES[id]?.label] || 0), 0);
    if (n) chips.push(filterChip(g.id, g.label, n));
  }
  for (const label of LEAGUE_LABELS) {
    if (counts[label]) chips.push(filterChip(label, label, counts[label]));
  }
  // Each custom feed gets its own tab, so a pasted feed can be viewed whole
  // instead of being scattered across auto-detected league tabs.
  for (const feed of state.feeds) {
    const n = events.filter((e) => e.feed === feed.label).length;
    if (n) chips.push(filterChip(`feed:${feed.label}`, feed.label, n));
  }
  host.innerHTML = chips.join('') || '<span class="hint hint--small">No leagues yet</span>';
}

function filterChip(filterKey, display, count) {
  const active = store.state.leagueFilter === filterKey;
  return `
    <button class="chip chip--row focusable" data-action="filter" data-league="${esc(filterKey)}" aria-pressed="${active}">
      ${esc(display)}<span class="chip__count">${count || 0}</span>
    </button>`;
}

export function setFilter(filter) {
  store.state.leagueFilter = filter;
  persist();
  renderFilters();
  emit('filter');
}

/** The favourites picker inside Settings → Ticker & display. */
export function renderTeamPicker(teams) {
  const el = $('teamPicker');
  if (!el) return;
  const set = favSet(store.state);
  el.innerHTML = teams.length
    ? teams.map((t) => `
      <button class="chip focusable" data-action="toggle-team" data-abbr="${esc(t.abbr)}" aria-pressed="${set.has(t.abbr)}" title="${esc(t.name)}">
        ${set.has(t.abbr) ? '★' : '☆'} ${esc(t.abbr)}
      </button>`).join('')
    : '<span class="hint">No teams yet — add a feed or wait for the next refresh.</span>';
}

/** Keep the just-toggled chip focused after the picker re-renders. */
export function refocusTeam(abbr) {
  const chip = $$('#teamPicker [data-abbr]').find((c) => c.dataset.abbr === abbr);
  chip?.focus();
}
