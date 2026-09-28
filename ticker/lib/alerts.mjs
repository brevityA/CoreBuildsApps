/**
 * Score alerts.
 *
 * A crawl tells you the score if you happen to be looking at the bottom of the
 * screen at the moment it changes — and then it scrolls past once and is gone.
 * For the two or three teams someone actually follows, that is the wrong
 * trade-off, so a change on a starred team interrupts once and gets out of the
 * way.
 *
 * Pure functions, no DOM and no state: they take a before-snapshot and an
 * after-snapshot and return the lines worth announcing.
 */

/** The parts of an event that can trigger an alert. */
export function signatureOf(ev) {
  return {
    status: ev.status,
    away: ev.away?.score ?? null,
    home: ev.home?.score ?? null,
    awayAbbr: ev.away?.abbr || '',
    homeAbbr: ev.home?.abbr || '',
  };
}

export function snapshotOf(events) {
  const map = new Map();
  for (const ev of events || []) if (ev?.id) map.set(ev.id, signatureOf(ev));
  return map;
}

function involvesFavourite(sig, favs) {
  return favs.has(String(sig.awayAbbr).toUpperCase())
    || favs.has(String(sig.homeAbbr).toUpperCase());
}

/** The leading abbreviation, or null when the game is level or unscored. */
export function leaderOf(sig) {
  if (sig.away == null || sig.home == null) return null;
  if (sig.away === sig.home) return null;
  return sig.away > sig.home ? sig.awayAbbr : sig.homeAbbr;
}

const scoreLine = (sig) => `${sig.awayAbbr} ${sig.away}–${sig.home} ${sig.homeAbbr}`;

/**
 * Compare two snapshots and return the alert lines for starred teams, in the
 * order the events appear.
 *
 * @param {Map}  before  previous snapshot (Map<id, signature>)
 * @param {Map}  after   current snapshot
 * @param {Set}  favs    uppercase team abbreviations the user follows
 * @returns {string[]}
 */
export function diffSlate(before, after, favs) {
  if (!before || !favs || favs.size === 0) return [];
  const messages = [];

  for (const [id, cur] of after) {
    const prev = before.get(id);
    if (!prev) continue;
    if (!involvesFavourite(cur, favs)) continue;

    const scoresChanged = prev.away !== cur.away || prev.home !== cur.home;
    const wentLive = prev.status === 'upcoming' && cur.status === 'live';
    const wentFinal = prev.status !== 'final' && cur.status === 'final';

    if (wentLive) {
      messages.push(`${cur.awayAbbr} vs ${cur.homeAbbr} is underway`);
    } else if (scoresChanged) {
      const beforeLeader = leaderOf(prev);
      const afterLeader = leaderOf(cur);
      const line = scoreLine(cur);
      if (afterLeader && beforeLeader && afterLeader !== beforeLeader) {
        messages.push(`Lead change — ${line}`);
      } else if (wentFinal) {
        messages.push(`Final — ${line}`);
      } else {
        messages.push(line);
      }
    } else if (wentFinal) {
      messages.push(`Final — ${scoreLine(cur)}`);
    }
  }

  return messages;
}
