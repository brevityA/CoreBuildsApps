export function initTvNav(root) {
  const onKey = (event) => {
    const map0 = { ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right' };
    if (isEditing(event.target, map0[event.key])) return;
    const map = {
      ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right',
      Enter: 'ok', Escape: 'back',
    };
    const dir = map[event.key];
    if (!dir) return;
    if (dir === 'ok') {
      const el = document.activeElement;
      if (el && el.classList.contains('focusable')) {
        event.preventDefault();
        el.click();
      }
      return;
    }
    if (dir === 'back') {
      // Back closes the topmost layer: calibration, onboarding, game detail,
      // then the drawer.
      const layers = [
        ['calibrate', '[data-action="calibrate-done"]'],
        ['onboard', '[data-action="onboard-skip"]'],
        ['gameDetail', '[data-action="detail-close"]'],
      ];
      for (const [id, selector] of layers) {
        const layer = document.getElementById(id);
        if (layer && !layer.hidden) {
          event.preventDefault();
          layer.querySelector(selector)?.click();
          return;
        }
      }
      const close = root.querySelector('[data-action="close-settings"]');
      if (close && !document.getElementById('drawer').hidden) {
        event.preventDefault();
        close.click();
      }
      return;
    }
    const next = nearest(document.activeElement, dir);
    if (next) {
      event.preventDefault();
      next.focus();
      // Keep the focused item fully visible (grid rows below the fold, the
      // horizontally scrolling filter row, and the drawer sections).
      try { next.scrollIntoView({ block: 'nearest', inline: 'nearest' }); } catch { /* older WebView */ }
    }
  };
  window.addEventListener('keydown', onKey);
  root.addEventListener('click', (event) => {
    const el = event.target.closest('.focusable');
    if (el) el.focus();
  });
  if (!document.activeElement || document.activeElement === document.body) {
    // Prefer the league rail over the chrome: it is what a viewer came to
    // change, and the action buttons are one press away from it.
    (root.querySelector('#leagues .focusable')
      || root.querySelector('.stage .focusable')
      || root.querySelector('.focusable'))?.focus();
  }
  return () => window.removeEventListener('keydown', onKey);
}

function nearest(current, dir) {
  const drawerOpen = drawerShown();
  const detailOpen = detailShown();
  const nodes = [...document.querySelectorAll('.focusable')].filter((el) => {
    if (!visible(el)) return false;
    // While the Game Detail modal is open, D-pad stays inside it.
    if (detailOpen) return el.closest('#gameDetail') !== null;
    if (drawerOpen && !el.closest('#drawer')) return false;
    return true;
  });
  if (!nodes.length) return null;
  if (!current || !nodes.includes(current)) return nodes[0];

  const inRail = current.closest('.drawer__rail');
  const inSections = current.closest('.drawer__sections');

  if (drawerOpen && inRail && dir === 'right') {
    const active = document.querySelector('.drawer__section.is-active .focusable');
    if (active && visible(active)) return active;
  }
  if (drawerOpen && inSections && dir === 'left') {
    const active = document.querySelector('.drawer__rail .chip--row.is-active');
    if (active && visible(active)) return active;
  }

  const candidates = drawerOpen && (dir === 'up' || dir === 'down')
    ? nodes.filter((el) => {
        if (inRail) return el.closest('.drawer__rail');
        if (inSections) return el.closest('.drawer__sections');
        return true;
      })
    : nodes;

  // Try to find a target within the current zone first
  const inZone = candidates.length ? geometricNearest(current, dir, candidates) : null;
  // Zone-edge fallback: when nothing lies in the pressed direction inside the
  // current zone (e.g. Up from the top of the sections pane), fall back to the
  // full drawer set so focus is never trapped and the header close button
  // stays reachable by D-pad.
  return inZone ?? geometricNearest(current, dir, nodes);
}

function geometricNearest(current, dir, nodes) {
  const a = box(current);
  const horiz = dir === 'left' || dir === 'right';
  const sign = dir === 'left' || dir === 'up' ? -1 : 1;

  let best = null;
  let bestScore = Infinity;
  for (const node of nodes) {
    if (node === current) continue;
    const b = box(node);
    const along = (horiz ? b.cx - a.cx : b.cy - a.cy) * sign;
    if (along <= 4) continue;

    const cross = horiz ? Math.abs(b.cy - a.cy) : Math.abs(b.cx - a.cx);
    const overlap = horiz
      ? Math.min(a.y2, b.y2) - Math.max(a.y1, b.y1)
      : Math.min(a.x2, b.x2) - Math.max(a.x1, b.x1);
    const minSpan = Math.min(horiz ? a.h : a.w, horiz ? b.h : b.w);
    const aligned = overlap > minSpan * 0.35;

    const score = aligned ? along + cross * 0.5 : along + cross * 2.4 + 1000;
    if (score < bestScore) {
      bestScore = score;
      best = node;
    }
  }
  return best;
}

function box(el) {
  const r = el.getBoundingClientRect();
  return {
    cx: r.left + r.width / 2, cy: r.top + r.height / 2,
    x1: r.left, x2: r.right, y1: r.top, y2: r.bottom,
    w: r.width, h: r.height,
  };
}

function visible(el) {
  if (el.disabled) return false;
  if (el.closest('[hidden]')) return false;
  const r = el.getBoundingClientRect();
  return r.width > 0 && r.height > 0;
}

function drawerShown() {
  const drawer = document.getElementById('drawer');
  return Boolean(drawer && !drawer.hidden);
}

function detailShown() {
  const detail = document.getElementById('gameDetail');
  return Boolean(detail && !detail.hidden);
}

/**
 * Is the focused control consuming this key itself?
 *
 * A range input is the interesting case (AUDIT D7): exempting it entirely
 * meant the D-pad moved focus instead of the value, so the speed slider could
 * not be operated from a remote at all. Exempting it for every key is also
 * wrong, because a range only handles horizontal arrows — vertical arrows
 * would strand the cursor on it. So: horizontal arrows drive the slider,
 * vertical arrows navigate.
 */
function isEditing(el, dir) {
  if (!el || el === document.body) return false;
  const tag = (el.tagName || '').toLowerCase();
  if (tag === 'textarea' || tag === 'select') return true;
  if (tag === 'input') {
    const type = (el.type || 'text').toLowerCase();
    if (type === 'range') return dir === 'left' || dir === 'right';
    return !['button', 'submit', 'checkbox', 'radio'].includes(type);
  }
  return Boolean(el.isContentEditable);
}
