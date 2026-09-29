/**
 * Team marks: which remote logo a team is allowed to use, what to draw when we
 * will not or cannot fetch it, and the markup for both.
 *
 * The slate has carried a `logo` on every competitor since the scoreboard
 * sources were written — ESPN's `team.logo`, the NHL's `teamLogo` — and no
 * view ever rendered one.
 *
 * The markup lives here rather than in a `public/js/` module for one practical
 * reason: the browser modules import the shared parsers as `/lib/...`, which
 * Node cannot resolve, so a `public/js` renderer can only ever be read as text
 * by the test runner. Here it can be called — and the escaping, the two mark
 * forms and the fallback are all things worth calling. The rows those marks
 * sit in are `lib/team-rows.mjs`, for the same reason.
 *
 * Two questions, and they are separate:
 *
 * 1. *May we fetch this URL?* The URL arrives from a third-party payload, and
 *    the TV will make a request to it. A logo field is not user input, so it
 *    does not go through `lib/ssrf.mjs`'s feed rules — but it is also not
 *    trusted, because a compromised or spoofed feed response is exactly the
 *    thing that would point us at `https://192.168.1.1/...`. So: https only,
 *    and only the CDNs the compiled providers actually emit.
 *
 *    https is not a style preference. The native shell serves the app from
 *    `https://coreline.local`, so an `http:` image is mixed content that the
 *    WebView blocks — a silent hole where a logo should be.
 *
 * 2. *What if it fails?* A dead CDN, a team with no logo, a URL that fails
 *    policy, or a VPN that blocks the CDN must not leave a hole. Every mark
 *    renders the monogram *first and always*, with the logo drawn over it;
 *    see the `.mark` rules in `screens.css` for why that ordering is the
 *    fallback rather than a JavaScript error handler.
 */

/**
 * The logo CDNs the compiled providers emit, by registrable domain.
 *
 * Deliberately a *domain* list rather than a host list: ESPN serves team
 * logos from `a.espncdn.com` and league art from `a1`-`a4.espncdn.com`, and
 * pinning exact hosts would mean a logo silently vanishing the first time
 * they move a bucket. A subdomain match keeps every host they use while
 * still refusing anything else.
 *
 * This list is mirrored in the `img-src` of `public/index.html`. The CSP is
 * the enforcement; this is the same policy stated where it can be tested,
 * and `tests/logos.test.mjs` asserts the two agree so they cannot drift.
 *
 * `mlbstatic.com` was here and is not any more. Nothing in the app ever built
 * a URL on that host — `eventsFromMlb` sets `logo: null`, and MLB reaches ESPN
 * first — so it was permission for a fetch that could not happen. An allowlist
 * entry nothing uses is surface with no benefit, and it made the changelog's
 * "from ESPN, the NHL and MLB" read as true when the MLB fallback supplies no
 * logo at all.
 */
export const LOGO_DOMAINS = ['espncdn.com', 'nhle.com'];

/** True when `host` is one of LOGO_DOMAINS, or a subdomain of one. */
export function logoDomainAllowed(host) {
  const h = String(host ?? '').toLowerCase().replace(/\.$/, '');
  if (!h) return false;
  return LOGO_DOMAINS.some((d) => h === d || h.endsWith(`.${d}`));
}

/**
 * The URL to put in `src`, or null to fall back to the monogram alone.
 *
 * Returning null rather than a placeholder is the point: there is no
 * "broken image" state to style, because a mark we cannot fetch is never
 * given an `<img>` at all.
 */
export function logoUrl(raw) {
  const text = String(raw ?? '').trim();
  if (!text) return null;
  let url;
  try {
    url = new URL(text);
  } catch {
    return null;
  }
  if (url.protocol !== 'https:') return null;
  if (!logoDomainAllowed(url.hostname)) return null;
  // Credentials in an image URL are never legitimate here, and a logo has no
  // business on a non-standard port: the first leaks whatever the payload's
  // author wanted sent to a host we trust, the second is a way to reach a
  // service the CDN domain happens to expose. Both were accepted before this
  // check existed — the port case was even pinned by a test that asserted
  // nothing, which is how it survived review.
  if (url.username || url.password) return null;
  if (url.port && url.port !== '443') return null;
  return url.toString();
}

/** The logo URL for a competitor-shaped object, or null. */
export function logoOf(team) {
  return logoUrl(team?.logo);
}

/**
 * Which plate the artwork needs behind it: `'light'` or `'dark'`.
 *
 * A plate exists because logos are drawn for a particular background. The
 * mistake this function fixes was picking one plate colour for every source
 * and calling it a guarantee: ESPN's `scoreboard/` marks are drawn for ESPN's
 * own dark UI and read on a dark plate, but the NHL serves
 * `.../svg/FLA_light.svg` — the `_light` variant, meaning dark ink drawn for
 * a **light** background — which on a dark plate is close to invisible. That
 * is the league this app's supporter named first.
 *
 * The NHL exposes only the light-background variant, so the plate is what
 * compensates. Verified against `https://api-web.nhle.com/v1/score/now`:
 * every team logo in that payload is an `assets.nhle.com/.../_light.svg`.
 */
export function logoPlate(raw) {
  let url;
  try {
    url = new URL(String(raw ?? ''));
  } catch {
    return 'dark';
  }
  if (logoDomainAllowed(url.hostname) && /(^|\.)nhle\.com$/.test(url.hostname.toLowerCase())) {
    return 'light';
  }
  // Defensive: a `_light` variant from anywhere is dark ink by convention.
  if (/_light\.(svg|png)$/i.test(url.pathname)) return 'light';
  return 'dark';
}

/**
 * The text a mark falls back to, and the label it always carries.
 *
 * Kept as the abbreviation the cards already print, because the fallback
 * must not be a *different* design — a viewer who has seen TOR in the
 * monogram should see TOR come back when the logo does not.
 */
export function monogram(team) {
  const abbr = String(team?.abbr ?? '').trim();
  return abbr || '—';
}

/* ---- Markup --------------------------------------------------------------
   Kept beside the policy so both are exercised by the same test, and escaped
   here rather than imported from `public/js/core/dom.js`: `lib/` is loaded by
   the server and by Node tests, so it must not depend on the browser tree. */

const ENTITIES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

/** Escape for an attribute or a text node. */
export function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) => ENTITIES[c]);
}

/**
 * A competitor's mark, as markup.
 *
 * Two forms, because a logo has two jobs:
 *
 * - **Over the monogram** (the default; hero banner and game detail): a large
 *   mark filling the column the abbreviation used to take. The team's name is
 *   spelled out beside it, so covering the monogram costs no legibility.
 * - **Beside the label** (`compact`; board cards): a small mark in front of
 *   the abbreviation rather than over it. A 26px logo is a colour cue, not
 *   text — at three metres the viewer reads the letters and recognises the
 *   logo — so on the cards the mark adds to the abbreviation instead of
 *   replacing it, and with no logo the row is exactly the row it is today.
 *
 * There is deliberately no `onerror=` attribute: inline handlers are what
 * `script-src 'self'` forbids, and the structure makes one unnecessary. The
 * monogram is painted first and the logo over it, so a 404, a blocked CDN, a
 * refused URL, or a document with no JavaScript all land on a readable mark.
 * The delegated listener in `public/js/app.js` only tidies up the empty slot
 * a failed image would leave in the compact form.
 */
export function teamMark(team, { compact = false } = {}) {
  const label = escapeHtml(monogram(team));
  const src = logoOf(team);
  if (!src) return `<span class="mark mark--mono">${label}</span>`;
  const img = `<img class="mark__img" data-plate="${logoPlate(src)}" `
    + `src="${escapeHtml(src)}" alt="" `
    + 'loading="lazy" decoding="async" referrerpolicy="no-referrer">';
  return compact
    ? `<span class="mark mark--row">${img}${label}</span>`
    : `<span class="mark">${label}${img}</span>`;
}
