/**
 * Game detail: the one screen that answers "so where do I watch this?"
 *
 * It lists the game's broadcast channels and, when the viewer has imported an
 * IPTV playlist, their own channels that carry it — with the reason each one
 * matched, because "why is TiviMate opening on channel 412?" is a real
 * question and a bare list does not answer it.
 */

import { store } from '../core/store.js';
import { emit } from '../core/bus.js';
import { $, esc, setHidden } from '../core/dom.js';
import { accentFor, statusOf, startLabel } from '../core/format.js';
import { matchesFor, openMatchedChannel } from '../data/playlist.js';
import { watchEvent, openWebForEvent } from '../data/watch.js';
import { teamLine } from '../core/marks.js';

// Why a channel is listed: the guide says it airs this game; the network
// name matches the broadcaster; or the team's name is in the channel name.
const REASONS = { guide: 'GUIDE', network: 'NETWORK', team: 'TEAM' };

let detailEvent = null;
let detailMatches = [];
let lastFocus = null;

export function getDetailEvent() {
  return detailEvent;
}

export function openGameDetail(ev) {
  if (!ev) return;
  detailEvent = ev;
  detailMatches = matchesFor(ev);
  lastFocus = document.activeElement;
  $('gameDetailPanel').innerHTML = detailHtml(ev, detailMatches);
  setHidden('gameDetail', false);
  $('gameDetailPanel')?.querySelector('.focusable')?.focus();
}

export function closeGameDetail() {
  const overlay = $('gameDetail');
  if (!overlay || overlay.hidden) return;
  overlay.hidden = true;
  detailEvent = null;
  detailMatches = [];
  if (lastFocus && document.contains(lastFocus)) {
    lastFocus.focus();
  } else {
    document.querySelector('.stage .focusable')?.focus();
  }
  lastFocus = null;
}

export function openMatched(index) {
  openMatchedChannel(detailMatches[Number(index)]);
}

export function watchDetail() {
  watchEvent(detailEvent);
}

export function watchDetailWeb() {
  openWebForEvent(detailEvent);
}

export function detailHtml(ev, matches) {
  const { badge, label } = statusOf(ev);
  const pills = (ev.channels || []).map((c) => `<span class="pill">${esc(c)}</span>`).join('');
  const teams = ev.away && ev.home
    ? `<div class="teams">${teamLine(ev.away, ev)}${teamLine(ev.home, ev)}</div>`
    : (ev.headline ? `<p class="hint">${esc(ev.headline)}</p>` : '');

  let channelBlock;
  if (!store.playlistChannels.length) {
    channelBlock = `
      <p class="hint">Import your IPTV playlist to see which of your channels carry this game.</p>
      <button class="btn btn--ghost focusable" data-action="open-channels-settings">Set up in Settings → Channels</button>`;
  } else if (!matches.length) {
    channelBlock = `
      <p class="hint">No channels matched ${esc((ev.channels || []).join(', ') || 'this game')}. Your provider may carry it under a different network name${store.state.guide.fetchedAt ? '' : ' — adding your TV guide in Settings → Channels finds more'}.</p>`;
  } else {
    channelBlock = matches.map((m, i) => `
      <button class="gd-row focusable" data-action="open-channel" data-mindex="${i}">
        <span class="gd-ch">${esc(m.name)}</span>
        ${m.group ? `<span class="gd-why">${esc(m.group)}</span>` : ''}
        <span class="gd-why">${REASONS[m.reason] || 'TEAM'}</span>
        ${m.preferred ? '<span class="gd-star" title="Preferred channel">★</span>' : ''}
        <span class="gd-open">Open ▸</span>
      </button>`).join('');
  }

  return `
    <header class="gd-head">
      <div class="gd-meta">
        <span class="badge badge--${badge}">${esc(label)}</span>
        <span style="--acc:${esc(accentFor(ev))}">${esc(ev.league || ev.feed || '')}</span>
        ${ev.status === 'upcoming' && startLabel(ev) ? `<span>${esc(startLabel(ev))}</span>` : ''}
      </div>
      <button class="btn--icon focusable" data-action="detail-close" aria-label="Close">&#10005;</button>
    </header>
    ${teams}
    ${pills ? `<div class="gd-pills">${pills}</div>` : ''}
    <div class="gd-actions">
      ${ev.away && ev.home ? `<button class="btn focusable" data-action="watch" data-id="${esc(ev.id)}">&#9654; Watch</button>` : ''}
      <button class="btn btn--ghost focusable" data-action="watch-web" data-id="${esc(ev.id)}">Web page</button>
    </div>
    <div class="gd-section">Your channels</div>
    ${channelBlock}
    <div class="gd-foot">${esc(ev.venue || ev.feed || '')}</div>`;
}

export { emit };
