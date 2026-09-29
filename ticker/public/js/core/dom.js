/**
 * Tiny DOM helpers.
 *
 * The app has no framework and does not want one: it renders static markup
 * into a handful of containers and moves one transform per frame. What it does
 * need is a place where HTML escaping lives, because every renderer in the app
 * builds strings from data that arrives off the network.
 */

export const $ = (id) => document.getElementById(id);

export const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

/** Escape for interpolation into HTML. Every renderer calls this. */
export function esc(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/** Escape for interpolation into a CSS selector. */
export function cssEscape(value) {
  if (typeof CSS !== 'undefined' && CSS.escape) return CSS.escape(value);
  return String(value).replace(/["\\]/g, '\\$&');
}

export function clampInt(value, min, max, fallback = min) {
  const n = Number(value);
  if (!Number.isFinite(n)) return fallback;
  return Math.max(min, Math.min(max, Math.round(n)));
}

/** Set textContent when the element exists; overlays legitimately lack nodes. */
export function setText(id, text) {
  const el = $(id);
  if (el) el.textContent = text;
}

/** Toggle `hidden` on an element that may be absent. */
export function setHidden(id, hidden) {
  const el = $(id);
  if (el) el.hidden = Boolean(hidden);
}

export function toggleAttr(name, on) {
  document.documentElement.toggleAttribute(name, Boolean(on));
}

export function timeout(ms) {
  return new Promise((resolve) => setTimeout(() => resolve(null), ms));
}

/**
 * Give focus back when an overlay closes. A TV has no pointer: if focus is
 * left on a node that was just hidden, it falls to <body> and the screen shows
 * no selection at all until the viewer presses an arrow and hopes. [previous]
 * is where focus was when the overlay opened; if it is gone, the league rail
 * and then the board are the places a viewer expects to land.
 */
export function restoreFocus(previous) {
  // Never hand focus back to a text field: on a TV that raises the on-screen
  // keyboard, which is not what closing a dialog should do.
  const typing = (el) => /^(input|textarea|select)$/i.test(el.tagName)
    && !['button', 'submit', 'checkbox', 'radio', 'range'].includes((el.type || '').toLowerCase());
  const usable = (el) => el && el.isConnected && !el.closest('[hidden]')
    && el.getBoundingClientRect().width > 0 && !typing(el);
  const target = [previous,
    document.querySelector('#leagues .focusable'),
    document.querySelector('.stage .focusable'),
    document.querySelector('.focusable')].find(usable);
  target?.focus();
}
