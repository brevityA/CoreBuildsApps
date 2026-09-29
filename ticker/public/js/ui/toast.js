/**
 * Transient messages.
 *
 * Two ranks, because a score change is not the same kind of news as "feed
 * added": a toast is quiet chrome, an alert is the thing the viewer was
 * waiting for and takes the live status colour.
 */

import { on } from '../core/bus.js';
import { $ } from '../core/dom.js';

const TOAST_MS = 2200;
const ALERT_MS = 4000;
let timer = null;

export function toast(message, kind = '') {
  const el = $('toast');
  if (!el || !message) return;
  el.hidden = false;
  el.className = kind ? `toast toast--${kind}` : 'toast';
  el.textContent = message;
  clearTimeout(timer);
  timer = setTimeout(() => { el.hidden = true; }, kind === 'alert' ? ALERT_MS : TOAST_MS);
}

export function initToasts() {
  on('toast', (message) => toast(message));
  on('alert', (message) => toast(message, 'alert'));
}
