import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

import {
  LOGO_DOMAINS,
  logoDomainAllowed,
  logoUrl,
  logoPlate,
  logoOf,
  monogram,
  teamMark,
} from '../lib/logos.mjs';
import { teamLine, gameTeams, heroMatch } from '../lib/team-rows.mjs';

/**
 * Team logos: the policy, the markup, and the CSP that enforces the same
 * policy again at fetch time.
 *
 * The interesting failures here are not "does a logo render". They are: a
 * payload pointing the TV at a private address, a team whose mark is a
 * `javascript:` URL, a logo CDN that moves to a new subdomain, and a CSP that
 * quietly disagrees with the module about which hosts are allowed — the last
 * of which produces a logo that the code renders and the browser refuses,
 * with nothing in either file looking wrong.
 */

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..');
const read = (rel) => readFileSync(join(root, rel), 'utf8');

const indexHtml = read('public/index.html');
const appJs = read('public/js/app.js');
const boardJs = read('public/js/ui/board.js');
const detailJs = read('public/js/ui/detail.js');
const rowsJs = read('lib/team-rows.mjs');
const swJs = read('public/sw.js');

const csp = indexHtml.match(/http-equiv="Content-Security-Policy" content="([^"]+)"/)?.[1] ?? '';
const directive = (name) => csp.match(new RegExp(`(?:^|;\\s*)${name}\\s+([^;]+)`))?.[1]?.trim() ?? '';

const ESPN = 'https://a.espncdn.com/i/teamlogos/nba/500/scoreboard/tor.png';

/* ---- What we are allowed to fetch --------------------------------------- */

test('an ESPN logo URL is accepted, and its subdomains are not pinned', () => {
  assert.equal(logoUrl(ESPN), ESPN);
  // ESPN spreads the same art across a1-a4; pinning the exact host would mean
  // a logo silently disappearing the first time they move a bucket.
  for (const h of ['a1', 'a2', 'a4']) {
    assert.ok(logoUrl(`https://${h}.espncdn.com/i/teamlogos/nba/500/scoreboard/tor.png`));
  }
  // The exact host the NHL payload serves, taken from the live endpoint:
  // https://api-web.nhle.com/v1/score/now → assets.nhle.com/.../_light.svg
  assert.ok(logoUrl('https://assets.nhle.com/logos/nhl/svg/TOR_light.svg'));
});

test('the bare domain is allowed too, not only its subdomains', () => {
  // `LOGO_DOMAINS.some(d => h.endsWith('.' + d))` alone would reject these,
  // and CSP wildcards do not match a bare domain either — so both halves of
  // this agreement have to say it twice.
  assert.ok(logoDomainAllowed('espncdn.com'));
  assert.ok(logoDomainAllowed('nhle.com'));
  assert.ok(logoUrl('https://espncdn.com/i/teamlogos/nba/500/scoreboard/tor.png'));
});

test('a CDN that is not ours is refused, however plausible', () => {
  assert.equal(logoUrl('https://a.espncdn.com.evil.example/tor.png'), null);
  assert.equal(logoUrl('https://espncdn.com.attacker.net/tor.png'), null);
  assert.equal(logoUrl('https://notespncdn.com/tor.png'), null);
  assert.equal(logoUrl('https://example.com/tor.png'), null);
});

test('cleartext is refused: the app is served over https', () => {
  // The native shell serves the UI from https://coreline.local, so an http
  // image is mixed content the WebView blocks — a hole where a logo should
  // be, which is the exact failure this feature exists to avoid.
  assert.equal(logoUrl('http://a.espncdn.com/tor.png'), null);
});

test('a feed cannot point the television at its own network', () => {
  for (const host of ['127.0.0.1', '10.0.0.5', '192.168.1.1', '169.254.169.254', 'localhost']) {
    assert.equal(logoUrl(`https://${host}/tor.png`), null, `${host} was accepted`);
  }
  // ...and the allowlist is what refuses it, so it does not depend on a
  // second, weaker check being correct.
});

test('a logo URL carries no credentials and no non-standard port', () => {
  // This assertion used to be `ESPN.endsWith('.png') ? X : null` — a condition
  // that is always true, so it compared a value with itself and pinned
  // nothing. Meanwhile `logoUrl` accepted both of these, because it only ever
  // checked the protocol and the hostname.
  assert.equal(logoUrl('https://user:pass@a.espncdn.com/tor.png'), null, 'credentials accepted');
  assert.equal(logoUrl('https://a.espncdn.com:8443/tor.png'), null, 'odd port accepted');
  // The explicit default port is the same origin and stays allowed.
  assert.equal(logoUrl('https://a.espncdn.com:443/tor.png'), 'https://a.espncdn.com/tor.png');
});

test('junk is not a URL, and is not rendered as one', () => {
  for (const raw of [null, undefined, '', '   ', 'not a url', 'javascript:alert(1)', 'data:image/svg+xml,<svg/>', { href: ESPN }, 42]) {
    assert.equal(logoUrl(raw), null, `${String(raw)} was accepted`);
  }
});

test('the team-shaped accessor reads the field the slate actually sets', () => {
  assert.equal(logoOf({ logo: ESPN }), ESPN);
  assert.equal(logoOf({ logo: 'http://a.espncdn.com/tor.png' }), null);
  assert.equal(logoOf(null), null);
  assert.equal(logoOf(undefined), null);
});

test('the monogram is the abbreviation the cards already print', () => {
  assert.equal(monogram({ abbr: 'TOR' }), 'TOR');
  assert.equal(monogram({ abbr: '  ' }), '—');
  assert.equal(monogram({}), '—');
  assert.equal(monogram(null), '—');
});

/* ---- What gets drawn ---------------------------------------------------- */

test('a logo we may fetch is drawn over its monogram', () => {
  const html = teamMark({ abbr: 'TOR', logo: ESPN });
  assert.match(html, /class="mark"/);
  assert.match(html, /TOR/, 'the fallback monogram is not in the markup');
  assert.match(html, new RegExp(`src="${ESPN.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}"`));
  // The fallback is the monogram being *underneath*, so it must come first.
  assert.ok(html.indexOf('TOR') < html.indexOf('<img'), 'the image is not painted over the monogram');
});

test('a logo we may not fetch is not drawn at all', () => {
  for (const bad of ['http://a.espncdn.com/tor.png', 'https://evil.example/tor.png', null]) {
    const html = teamMark({ abbr: 'TOR', logo: bad });
    assert.equal(html, '<span class="mark mark--mono">TOR</span>', `${bad} rendered an img`);
    assert.doesNotMatch(html, /<img/, 'a refused URL still produced an image element');
  }
});

test('the plate follows the artwork, not one colour for everything', () => {
  // Verified from the live payload: the NHL publishes only the
  // light-background variant (`_light.svg`, i.e. dark ink), so it needs a
  // light plate. ESPN's scoreboard marks are drawn for a dark UI.
  assert.equal(logoPlate('https://assets.nhle.com/logos/nhl/svg/FLA_light.svg'), 'light');
  assert.equal(logoPlate('https://a.espncdn.com/i/teamlogos/nba/500/scoreboard/tor.png'), 'dark');
  // Defensive: a light-background variant from any source is dark ink.
  assert.equal(logoPlate('https://a.espncdn.com/x/TOR_light.png'), 'light');
  // Junk must not throw and must not claim a light plate.
  assert.equal(logoPlate(null), 'dark');
  assert.equal(logoPlate('not a url'), 'dark');
});

test('the plate reaches the markup, in both mark forms', () => {
  const nhl = { abbr: 'FLA', logo: 'https://assets.nhle.com/logos/nhl/svg/FLA_light.svg' };
  assert.match(teamMark(nhl), /data-plate="light"/);
  assert.match(teamMark(nhl, { compact: true }), /data-plate="light"/);
  const espn = { abbr: 'TOR', logo: ESPN };
  assert.match(teamMark(espn), /data-plate="dark"/);
});

test('the compact form puts the mark beside a readable label', () => {
  const html = teamMark({ abbr: 'TOR', logo: ESPN }, { compact: true });
  assert.match(html, /class="mark mark--row"/);
  assert.match(html, /<img[^>]*>TOR/, 'the label does not follow the image');
  // A card with no logo must render exactly the row it renders today.
  assert.equal(teamMark({ abbr: 'TOR' }, { compact: true }), '<span class="mark mark--mono">TOR</span>');
});

test('markup is escaped rather than trusted', () => {
  const evil = 'https://a.espncdn.com/x" onload="alert(1)';
  const html = teamMark({ abbr: '<script>x</script>', logo: evil });
  assert.doesNotMatch(html, /<script>/, 'the monogram was not escaped');
  assert.match(html, /&lt;script&gt;/);

  // The `src` attribute must survive a URL that tries to close it. `new URL`
  // has already percent-encoded the quote by the time it gets here, and the
  // escaping is the second belt — so the assertion is on the outcome: the
  // img tag contains no quote it did not open itself, and no attribute the
  // attacker asked for.
  const img = html.match(/<img\b[^>]*>/)?.[0] ?? '';
  assert.ok(img, 'no image element was emitted');
  const attrs = [...img.matchAll(/([a-z-]+)="/g)].map((m) => m[1]);
  assert.deepEqual(
    attrs,
    ['class', 'data-plate', 'src', 'alt', 'loading', 'decoding', 'referrerpolicy'],
    `an attribute broke out of the tag: ${img}`,
  );
  // `onload` is present in the src value as `%20onload=%22`, which is a URL
  // and not an attribute. What must not exist is a whitespace-delimited
  // handler attribute — the attribute list above already rules it out, and
  // this says the same thing in the form a reviewer reads first.
  assert.doesNotMatch(img, /\son[a-z]+\s*=/, 'an injected handler attribute reached the markup');
  assert.match(img, /%22/, 'the quote should have been encoded, not dropped');
});

test('the fallback does not depend on an inline handler', () => {
  // `onerror=` would be dead under the policy below, and the whole point of
  // the layered monogram is that it does not need one.
  const html = teamMark({ abbr: 'TOR', logo: ESPN });
  assert.doesNotMatch(html, /\son[a-z]+=/i, 'an inline event handler was emitted');
});

test('a hostile score cannot escape its own element', () => {
  // The rows live in lib/ precisely so this test can execute them. Before
  // that, the same markup sat in two browser modules and the test could only
  // read the source for `esc(` — which is not the same assertion.
  const hostile = '<img src=x onerror=alert(1)>';
  for (const html of [
    teamLine({ abbr: 'TOR', name: 'Raptors', score: hostile }, { status: 'live' }),
    gameTeams({ away: { abbr: 'TOR', score: hostile }, home: { abbr: 'MTL', score: 2 } }),
    // The banner's one-line match (layout PR) is the third row builder.
    heroMatch({ away: { abbr: 'TOR', name: '<i>n</i>', score: hostile }, home: { abbr: 'MTL', score: 2 } }),
  ]) {
    assert.doesNotMatch(html, /<img[^>]*onerror/, 'the score was interpolated as markup');
    assert.match(html, /&lt;img/);
  }
  // A numeric zero is a score, not an absence.
  assert.match(gameTeams({ away: { abbr: 'TOR', score: 0 }, home: { abbr: 'MTL' } }), />0</);
});

test('every remote value in a row is escaped', () => {
  const html = teamLine(
    { abbr: '<b>x</b>', name: '<i>n</i>', score: '<u>s</u>' },
    { status: 'live' },
  );
  for (const tag of ['<b>', '<i>', '<u>']) assert.doesNotMatch(html, new RegExp(tag));
  assert.match(html, /&lt;b&gt;/);
});

/* ---- The policy is enforced twice, and the two must agree ---------------- */

test('there is a CSP, and it forbids inline script', () => {
  assert.ok(csp, 'index.html has no Content-Security-Policy');
  assert.match(directive('script-src'), /'self'/);
  assert.doesNotMatch(directive('script-src'), /'unsafe-inline'/, 'inline script is allowed again');
  assert.match(directive('default-src'), /'none'/);
});

test('no inline script survives in the document the CSP governs', () => {
  const inline = [...indexHtml.matchAll(/<script\b([^>]*)>/g)]
    .filter((m) => !/\bsrc=/.test(m[1]));
  assert.deepEqual(inline, [], 'an inline <script> would be blocked by the very policy in this file');
  // The boot guard is the one that used to be inline; it must still be loaded.
  assert.match(indexHtml, /<script src="\.\/js\/boot\.js"><\/script>/);
  assert.ok(read('public/js/boot.js').includes('__CORELINE_BOOT'), 'the boot guard moved but lost its job');
});

test('style-src allows inline styles, because the app is built on them', () => {
  // Declared deliberately rather than discovered: every card carries its
  // league accent as style="--acc:#7FD4FF". If that ever changes, this test
  // should change with it — the assertion is here to make the allowance a
  // decision instead of a shrug.
  assert.match(directive('style-src'), /'unsafe-inline'/);
});

test('every logo CDN in the module is in img-src, bare domain and wildcard', () => {
  const imgSrc = directive('img-src');
  assert.match(imgSrc, /'self'/);
  for (const domain of LOGO_DOMAINS) {
    assert.ok(imgSrc.includes(`https://${domain}`), `img-src is missing https://${domain}`);
    assert.ok(imgSrc.includes(`https://*.${domain}`), `img-src is missing https://*.${domain}`);
  }
});

test('img-src allows nothing else beyond the QR code’s data URLs', () => {
  const allowed = directive('img-src')
    .split(/\s+/)
    .filter((t) => t && !t.startsWith('https://'));
  // 'self' for the app's own art, and data: for the pairing QR code, which is
  // a canvas URL rather than a file.
  assert.deepEqual(allowed.sort(), ["'self'", 'data:'].sort());
});

test('connect-src stays open to http(s), because feeds are user input', () => {
  // Pasting in your own RSS or IPTV source is the app's purpose, so an
  // allowlist here would break it. The control on where the app may fetch is
  // lib/ssrf.mjs, not this directive — the comment in index.html says so, and
  // this test fails if someone tightens it into a regression.
  const connect = directive('connect-src');
  assert.match(connect, /'self'/);
  assert.match(connect, /https:/);
  assert.match(connect, /http:/);
});

test('the app can still navigate to a watched stream', () => {
  // Cards open external apps and web pages. `frame-ancestors` cannot be set
  // from a meta policy, and nothing here should add a navigate-to that would
  // break the Watch button.
  assert.doesNotMatch(csp, /navigate-to/);
  assert.match(read('public/index.html'), /manifest\.webmanifest/);
  assert.match(directive('manifest-src'), /'self'/);
  assert.match(directive('worker-src'), /'self'/);
});

/* ---- The seams ---------------------------------------------------------- */

test('all three renderers draw the mark through one module', () => {
  for (const [name, src] of [['board', boardJs], ['detail', detailJs]]) {
    assert.match(src, /from '\/lib\/team-rows\.mjs'/, `${name}.js does not use the shared rows`);
    // The duplicate row builders are what hid the logo gap; assert they are gone.
    assert.doesNotMatch(src, /function teamRow\(/, `${name}.js still has its own teamRow`);
    assert.doesNotMatch(src, /function teamLine\(/, `${name}.js still has its own teamLine`);
  }
  assert.match(rowsJs, /export function teamLine/);
  assert.match(rowsJs, /export function gameTeams/);
  assert.match(rowsJs, /from '\.\/logos\.mjs'/);
  // The browser copy is gone; a second renderer is how the gap survived.
  assert.throws(() => read('public/js/core/marks.js'), 'the duplicate renderer is back');
});

test('a failed image is caught in the capture phase, once for the whole app', () => {
  // Image error events do not bubble, and the board rebuilds its innerHTML on
  // every refresh and every 12s rotation — so a listener per mark would be
  // both wrong and unbounded. One capture-phase listener at the document.
  assert.match(appJs, /addEventListener\('error'/, 'nothing handles a failed logo');
  assert.match(appJs, /,\s*true\s*\)/, 'the error listener is not in the capture phase');
  assert.match(appJs, /classList\??\.contains\('mark__img'\)/);
  assert.match(appJs, /classList\.add\('is-broken'\)/);
});

test('the OLED default is static, so boot does not flash the wrong surface', () => {
  // The page paints `html { background: var(--surface-night) }` at first
  // paint, before any script runs. While the default was off that was
  // invisible; now that it is on, a script-applied attribute would show the
  // house near-black and then snap to #000 on every boot.
  const htmlTag = indexHtml.match(/<html[^>]*>/)?.[0] ?? '';
  assert.match(htmlTag, /\bdata-oled\b/, 'the document does not declare the default surface');
  const stateJs = read('public/js/state.js');
  const defaultOled = /oled:\s*(true|false)/.exec(stateJs)?.[1];
  assert.equal(defaultOled, 'true', 'the static attribute only matches while the default is on');
  // ...and the off path still exists, for the viewer who opted out.
  assert.match(read('public/js/ui/settings.js'), /toggleAttribute\('data-oled'/);
});

test('the mark stylesheet covers both forms and the broken state', () => {
  const css = read('public/css/screens.css');
  assert.match(css, /\.mark\s*\{/, '.mark is unstyled');
  assert.match(css, /\.mark__img\s*\{/);
  assert.match(css, /\.mark--row\s+\.mark__img\s*\{/, 'the compact mark is unsized');
  assert.match(css, /\.mark\.is-broken\s+\.mark__img\s*\{\s*display:\s*none/, 'a broken logo would leave a hole');
  // The plate is what keeps a dark logo visible on a near-black card, and it
  // has to be a per-artwork choice: one colour makes one of the two sources
  // invisible.
  assert.match(css, /\.mark__img\[data-plate="light"\]\s*\{\s*--mark-plate:/, 'no light plate');
  assert.match(css, /\.mark__img\[data-plate="dark"\]\s*\{\s*--mark-plate:/, 'no dark plate');
  assert.match(css, /\.mark--row\s+\.mark__img[\s\S]{0,420}?var\(--mark-plate/, 'the compact mark has no plate');
});

test('the service worker ships the file the page now depends on', () => {
  // boot.js moved out of index.html, so it is on the critical path; a shell
  // cached without it would boot with no guard at all.
  assert.match(swJs, /'\.\/js\/boot\.js'/, 'boot.js is not in the precache shell');
  assert.match(swJs, /const CACHE = 'core-line-v2'/, 'the shell changed but the cache name did not');
});

/* ---- The policy's own syntax --------------------------------------------- */

/**
 * A CSP is the one kind of config where a typo fails open and silently:
 * `img-scr` is not a directive, so a browser drops the token, the policy is
 * still "valid", and the image is blocked by `default-src 'none'` with the
 * console as the only witness. Nothing in the app can observe that, so it is
 * checked here.
 */
const REAL_DIRECTIVES = new Set([
  'default-src', 'script-src', 'script-src-elem', 'script-src-attr',
  'style-src', 'style-src-elem', 'style-src-attr', 'img-src', 'font-src',
  'connect-src', 'media-src', 'object-src', 'frame-src', 'worker-src',
  'manifest-src', 'prefetch-src', 'child-src', 'base-uri', 'form-action',
  'frame-ancestors', 'sandbox', 'upgrade-insecure-requests', 'block-all-mixed-content',
]);

test('every directive is spelled the way a browser expects', () => {
  assert.ok(csp, 'no policy to check');
  const names = csp.split(';').map((t) => t.trim().split(/\s+/)[0]).filter(Boolean);
  assert.ok(names.length >= 10, `only ${names.length} directives parsed out of the policy`);
  for (const name of names) {
    assert.ok(REAL_DIRECTIVES.has(name), `"${name}" is not a CSP directive — it would be ignored`);
  }
});

test('no directive is declared twice, which browsers resolve by taking the first', () => {
  const names = csp.split(';').map((t) => t.trim().split(/\s+/)[0]).filter(Boolean);
  assert.equal(new Set(names).size, names.length, `duplicate directive in: ${names.join(', ')}`);
});

test('the two directives a meta policy cannot honour are left out', () => {
  // Both are ignored in <meta> (with a console warning), so shipping them
  // would be a policy that looks stricter than it is.
  assert.doesNotMatch(csp, /frame-ancestors/, 'frame-ancestors does nothing in a meta policy');
  assert.doesNotMatch(csp, /report-(uri|to)/, 'reporting does nothing in a meta policy');
});

test('every source expression is one a browser will parse', () => {
  // 'self', 'none', 'unsafe-inline' and scheme/host sources only. A stray
  // token is dropped silently, which is the failure this whole block exists
  // for — e.g. `https://*.espncdn.com,` with a trailing comma is not a host.
  const KEYWORDS = new Set(["'self'", "'none'", "'unsafe-inline'", "'unsafe-eval'", "'unsafe-hashes'", 'data:', 'http:', 'https:', 'blob:', 'filesystem:']);
  for (const directiveText of csp.split(';')) {
    const [name, ...sources] = directiveText.trim().split(/\s+/);
    if (!name) continue;
    for (const source of sources) {
      const ok = KEYWORDS.has(source) || /^https:\/\/[a-z0-9*.-]+$/.test(source);
      assert.ok(ok, `"${source}" in ${name} is not a source expression a browser will accept`);
    }
  }
});

/* ---- The second document ------------------------------------------------- */

const overlayHtml = read('public/overlay.html');
const overlayCsp = overlayHtml.match(/http-equiv="Content-Security-Policy" content="([^"]+)"/)?.[1] ?? '';
const scorebugHtml = read('public/scorebug.html');
const scorebugCsp = scorebugHtml.match(/http-equiv="Content-Security-Policy" content="([^"]+)"/)?.[1] ?? '';

test('every document the app serves carries a policy', () => {
  // index.html and overlay.html are separate documents, loaded by
  // MainActivity and OverlayService respectively, and a CSP does not inherit
  // between them — the overlay is the one that floats over other apps.
  assert.ok(csp, 'index.html has no policy');
  assert.ok(overlayCsp, 'overlay.html has no policy — it would inherit nothing');
  assert.match(overlayCsp, /default-src 'none'/);
  assert.match(overlayCsp, /script-src 'self'/);
  // The overlay may allow inline *styles* (it shares the app's inline accent
  // pattern) but never inline script.
  const overlayScriptSrc = overlayCsp.match(/(?:^|;\s*)script-src\s+([^;]+)/)?.[1] ?? '';
  assert.doesNotMatch(overlayScriptSrc, /'unsafe-inline'/, 'inline script allowed in the overlay');
});

test('the overlay policy is spelled correctly too', () => {
  const names = overlayCsp.split(';').map((t) => t.trim().split(/\s+/)[0]).filter(Boolean);
  for (const name of names) {
    assert.ok(REAL_DIRECTIVES.has(name), `"${name}" is not a CSP directive — it would be ignored`);
  }
  assert.equal(new Set(names).size, names.length, 'duplicate directive in the overlay policy');
  assert.doesNotMatch(overlayCsp, /frame-ancestors|report-(uri|to)/);
});

test('the scoreboard bug carries its own policy, and the logo hosts the board allows', () => {
  // The third document: ScoreBugWindow loads it over other apps. Unlike the
  // crawl it shows team marks, so it needs the board's img-src — and nothing
  // wider, and never inline script.
  assert.ok(scorebugCsp, 'scorebug.html has no policy — it would inherit nothing');
  assert.match(scorebugCsp, /default-src 'none'/);
  const scriptSrc = scorebugCsp.match(/(?:^|;\s*)script-src\s+([^;]+)/)?.[1] ?? '';
  assert.doesNotMatch(scriptSrc, /'unsafe-inline'/, 'inline script allowed in the scoreboard bug');
  const imgOf = (policy) => (policy.match(/(?:^|;\s*)img-src\s+([^;]+)/)?.[1] ?? '').trim().split(/\s+/).sort();
  assert.deepEqual(imgOf(scorebugCsp), imgOf(csp), 'the bug must allow exactly the logo hosts the board allows');
  const names = scorebugCsp.split(';').map((t) => t.trim().split(/\s+/)[0]).filter(Boolean);
  for (const name of names) assert.ok(REAL_DIRECTIVES.has(name), `"${name}" is not a CSP directive`);
  assert.equal(new Set(names).size, names.length, 'duplicate directive in the scoreboard bug policy');
  const inline = [...scorebugHtml.matchAll(/<script\b([^>]*)>/g)].filter((m) => !/\bsrc=/.test(m[1]));
  assert.deepEqual(inline, [], 'an inline <script> would be blocked by the scoreboard bug policy');
});

test('the overlay declares no inline script to match', () => {
  const inline = [...overlayHtml.matchAll(/<script\b([^>]*)>/g)].filter((m) => !/\bsrc=/.test(m[1]));
  assert.deepEqual(inline, [], 'an inline <script> would be blocked by the overlay policy');
});

test('every document loads its CSS and modules from its own origin', () => {
  // The policy allows 'self' for scripts, styles and fonts, so an absolute
  // third-party URL in any of the three would be dead on arrival. This is
  // the check that catches it at review time instead of on a television.
  for (const [name, html] of [['index.html', indexHtml], ['overlay.html', overlayHtml], ['scorebug.html', scorebugHtml]]) {
    for (const m of html.matchAll(/<(?:script|link)\b[^>]*?(?:src|href)="([^"]+)"/g)) {
      const url = m[1];
      assert.ok(
        url.startsWith('./') || url.startsWith('/') || url.startsWith('data:'),
        `${name} loads a cross-origin asset that the policy forbids: ${url}`,
      );
    }
  }
});

test('no stylesheet reaches outside the origin either', () => {
  const css = ['tokens', 'base', 'chyron', 'layout', 'components', 'screens', 'tv', 'app']
    .map((n) => read(`public/css/${n}.css`)).join('\n');
  for (const m of css.matchAll(/url\((["']?)([^"')]+)\1\)/g)) {
    const url = m[2].trim();
    assert.ok(
      url.startsWith('./') || url.startsWith('../'),
      `a stylesheet points off-origin at ${url}, which the policy would block`,
    );
  }
});
