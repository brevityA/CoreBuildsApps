/**
 * First run.
 *
 * Two questions, both skippable: which teams do you follow, and do you have a
 * feed. The bundled sample feed already fills the crawl, so nothing here is
 * blocking — Skip is a first-class button, not a buried one, because an
 * onboarding flow that has to be completed before the product works is a
 * product that does not work yet.
 */

import { store, persist } from '../core/store.js';
import { emit } from '../core/bus.js';
import { $, $$, esc, setHidden, restoreFocus } from '../core/dom.js';
import { favSet } from '../core/format.js';
import { teamListFromEvents } from '../data/slate.js';

let step = 0;
let returnFocus = null;
const STEPS = 2;

export function maybeShowOnboarding() {
  if (store.state.onboarded) return;
  // Someone upgrading has already added feeds or starred teams — they do not
  // need to be walked through a product they are already using. Onboarding is
  // for a fresh install, so the test is "has this person configured anything
  // yet", not merely "have they dismissed this before".
  if (store.state.feeds.length > 0 || String(store.state.favorites || '').trim()) {
    store.state.onboarded = true;
    persist();
    return;
  }
  // Only offer team choice when there is actually a slate to choose from.
  if (step === 0 && teamListFromEvents().length === 0) step = 1;
  returnFocus = document.activeElement;
  render();
  setHidden('onboard', false);
  $('onboard')?.querySelector('.focusable')?.focus();
}

export function isOnboarding() {
  return !$('onboard')?.hidden;
}

export function nextStep() {
  step += 1;
  if (step >= STEPS) return finish();
  render();
  $('onboard')?.querySelector('.focusable')?.focus();
}

export function finish() {
  store.state.onboarded = true;
  persist();
  setHidden('onboard', true);
  emit('state');
  restoreFocus(returnFocus);
  returnFocus = null;
}

function render() {
  $$('#onboardStep .onboard__dot').forEach((dot, i) => {
    dot.classList.toggle('is-on', i === step);
  });
  $('onboardBody').innerHTML = step === 0 ? teamsStep() : feedsStep();
  $('onboardNext').textContent = step === STEPS - 1 ? 'Start watching' : 'Next';

  $('onboardBody').querySelectorAll('[data-abbr]').forEach((chip) => {
    chip.addEventListener('click', () => {
      toggleTeam(chip.dataset.abbr);
      render();
      $$('#onboardBody [data-abbr]').find((c) => c.dataset.abbr === chip.dataset.abbr)?.focus();
    });
  });
}

function teamsStep() {
  const set = favSet(store.state);
  const teams = teamListFromEvents().slice(0, 24);
  return `
    <div class="kicker">Step 1 of 2</div>
    <h2 class="onboard__title">Which teams do you follow?</h2>
    <p class="hint">Picked teams sort to the top of the board, appear under My Teams in the rail, and get a score alert when something happens.</p>
    <div class="chip-grid">
      ${teams.map((t) => `
        <button class="chip focusable" data-abbr="${esc(t.abbr)}" aria-pressed="${set.has(t.abbr)}" title="${esc(t.name)}">
          ${set.has(t.abbr) ? '★' : '☆'} ${esc(t.abbr)}
        </button>`).join('') || '<span class="hint">No teams yet — skip and pick them later in Settings.</span>'}
    </div>`;
}

function feedsStep() {
  return `
    <div class="kicker">Step 2 of 2</div>
    <h2 class="onboard__title">Have a listings feed?</h2>
    <p class="hint">Core Line reads any RSS, Atom, or JSON listing — including the channel apps that already publish one. You can add it now or later in Settings.</p>
    <div class="feed-add" style="grid-template-columns: minmax(0, 1fr) auto">
      <input class="field focusable" id="onboardFeedUrl" type="url" placeholder="https://example.com/sports.xml" autocomplete="off" spellcheck="false">
      <button class="btn focusable" data-action="onboard-add-feed">Add</button>
    </div>
    <p class="hint hint--small">The bundled sample feed keeps the crawl running either way.</p>`;
}

function toggleTeam(abbr) {
  const key = String(abbr).toUpperCase();
  const list = String(store.state.favorites || '')
    .split(/[,\s]+/).map((s) => s.trim().toUpperCase()).filter(Boolean);
  const i = list.indexOf(key);
  if (i >= 0) list.splice(i, 1); else list.push(key);
  store.state.favorites = list.join(', ');
  persist();
  const input = $('favorites');
  if (input) input.value = store.state.favorites;
  emit('state');
}
