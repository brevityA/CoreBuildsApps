/**
 * Slate search.
 *
 * Deliberately generous about what counts as a match. Someone searching "tsn"
 * wants the game on TSN; someone searching "leafs" wants Toronto even though
 * the board shows "TOR". A search that only matches abbreviations would be
 * technically correct and useless, so the haystack is built from every string
 * a viewer might reasonably type.
 *
 * Lives in lib/ rather than public/js/ so it can be unit-tested in Node like
 * the rest of the parsers (see tests/query.test.mjs).
 */

/**
 * The searchable text for one event. Exported so the tests assert against the
 * same haystack the UI does.
 */
export function queryText(ev) {
  return [
    ev.headline,
    ev.rawTitle,
    ev.league,
    ev.feed,
    ev.detail,
    ev.venue,
    ev.away?.abbr,
    ev.away?.name,
    ev.home?.abbr,
    ev.home?.name,
    ...(ev.channels || []),
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase();
}

/**
 * Does an event match the query?
 *
 * Every whitespace-separated term must appear somewhere (AND, not OR): "leafs
 * tsn" should narrow to the Toronto game on TSN, not widen to everything on
 * either team or channel.
 */
export function matchesQuery(ev, query) {
  const q = String(query || '').trim().toLowerCase();
  if (!q) return true;
  const haystack = queryText(ev);
  return q.split(/\s+/).every((term) => haystack.includes(term));
}
