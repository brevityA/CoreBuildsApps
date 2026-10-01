/**
 * Keyed list updates for the board.
 *
 * The board used to rebuild its whole grid with `innerHTML` on every 60s
 * refresh: 155 cards and their team logos thrown away and re-parsed, laid
 * out and decoded again, and the card the remote was on destroyed with them.
 * On a TV system-on-chip that is a visible hitch every minute, measured at
 * about a second of main-thread time per refresh at a 6x CPU slowdown.
 *
 * Here each item carries a key (the event id) and the exact markup it would
 * render. A node whose markup has not changed is kept as it is, so a refresh
 * where three scores moved touches three cards; the rest are not parsed, not
 * laid out again, and keep focus.
 *
 * Pure apart from the small DOM surface it is handed (`children`,
 * `insertBefore`, `removeChild`, and a `make(html)` factory), so Node can run
 * it against a stand-in.
 */

/**
 * @param {{ children: ArrayLike<object>, insertBefore(n: object, ref: object|null): void, removeChild(n: object): void }} host
 * @param {{ key: string, html: string }[]} items  in display order
 * @param {Map<string, { html: string, node: object }>} cache  from the previous call
 * @param {(html: string) => object} make  builds one node from markup
 * @returns {{ cache: Map<string, { html: string, node: object }>, created: number, kept: number }}
 */
export function reconcile(host, items, cache, make) {
  const next = new Map();
  const nodes = [];
  let created = 0;
  let kept = 0;
  for (const { key, html } of items) {
    const prev = cache.get(key);
    // A repeated key (two listings sharing an id) gets a fresh node rather
    // than the first one's: one node cannot sit in the grid twice.
    if (prev && prev.html === html && !next.has(key)) {
      nodes.push(prev.node);
      next.set(key, prev);
      kept += 1;
    } else {
      const node = make(html);
      nodes.push(node);
      if (!next.has(key)) next.set(key, { html, node });
      created += 1;
    }
  }

  // Place each node at its index, moving only the ones out of place.
  for (let i = 0; i < nodes.length; i += 1) {
    const at = host.children[i] || null;
    if (at !== nodes[i]) host.insertBefore(nodes[i], at);
  }
  // Whatever is left after the last kept or created node is stale.
  while (host.children.length > nodes.length) {
    host.removeChild(host.children[host.children.length - 1]);
  }
  return { cache: next, created, kept };
}
