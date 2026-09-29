/**
 * Search across the whole slate.
 *
 * A ticker app with dozens of listings needs a way to jump to one without
 * walking the grid. The query lives in the store (not in settings — it is
 * session state) and is applied in visibleEvents(), so the board, the hero and
 * the crawl all see the same filtered set.
 */

import { store } from '../core/store.js';
import { emit } from '../core/bus.js';
import { $ } from '../core/dom.js';

export function initSearch() {
  const input = $('search');
  if (!input) return;

  let debounce = null;
  input.addEventListener('input', () => {
    clearTimeout(debounce);
    // Short enough to feel live, long enough not to re-render on every
    // keystroke of a D-pad keyboard.
    debounce = setTimeout(() => {
      store.query = input.value;
      emit('filter');
    }, 120);
  });

  input.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
      clearSearch();
      event.stopPropagation();
    }
  });
}

export function clearSearch() {
  store.query = '';
  const input = $('search');
  if (input) input.value = '';
  emit('filter');
}

export function focusSearch() {
  const input = $('search');
  if (!input) return;
  input.focus();
  input.select?.();
}
