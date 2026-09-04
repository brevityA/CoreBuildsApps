export function initTvNav(root) {
  const onKey = (event) => {
    if (isEditing(event.target)) return;
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
    root.querySelector('.focusable')?.focus();
  }
  return () => window.removeEventListener('keydown', onKey);
}

function nearest(current, dir) {
  const drawerOpen = drawerShown();
  const nodes = [...document.querySelectorAll('.focusable')].filter((el) => {
    if (!visible(el)) return false;
    // While the settings drawer is open, D-pad must stay inside it (AUDIT D1).
    if (drawerOpen && !el.closest('#drawer')) return false;
    return true;
  });
  if (!nodes.length) return null;
  if (!current || !nodes.includes(current)) return nodes[0];

  const a = box(current);
  const horiz = dir === 'left' || dir === 'right';
  const sign = dir === 'left' || dir === 'up' ? -1 : 1;

  let best = null;
  let bestScore = Infinity;
  for (const node of nodes) {
    if (node === current) continue;
    const b = box(node);
    const along = (horiz ? b.cx - a.cx : b.cy - a.cy) * sign;
    if (along <= 4) continue; // candidate must lie in the pressed direction

    // Cross-axis overlap: prefer the item directly above/below (or left/right
    // in the same row) over a diagonal neighbour — "down means down".
    const cross = horiz ? Math.abs(b.cy - a.cy) : Math.abs(b.cx - a.cx);
    const overlap = horiz
      ? Math.min(a.y2, b.y2) - Math.max(a.y1, b.y1)
      : Math.min(a.x2, b.x2) - Math.max(a.x1, b.x1);
    const minSpan = Math.min(horiz ? a.h : a.w, horiz ? b.h : b.w);
    const aligned = overlap > minSpan * 0.35;

    // Aligned candidates are strongly preferred; diagonal ones pay a penalty.
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

function isEditing(el) {
  if (!el || el === document.body) return false;
  const tag = (el.tagName || '').toLowerCase();
  if (tag === 'textarea' || tag === 'select') return true;
  if (tag === 'input') {
    const type = (el.type || 'text').toLowerCase();
    return !['button', 'submit', 'checkbox', 'radio', 'range'].includes(type);
  }
  return Boolean(el.isContentEditable);
}
