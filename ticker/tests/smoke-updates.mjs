// Headless smoke: web (desktop), mobile, TV, and the new Updates panel.
import { chromium } from 'playwright-core';

const BASE = 'http://127.0.0.1:8787';
const bin = process.env.CHROMIUM_PATH || '/usr/bin/chromium';
let failures = 0;

function ok(cond, msg) {
  console.log((cond ? 'PASS' : 'FAIL') + '  ' + msg);
  if (!cond) failures++;
}

const browser = await chromium.launch({
  executablePath: bin,
  args: ['--no-sandbox', '--disable-dev-shm-usage'],
});

async function open(path, viewport) {
  const page = await browser.newPage({ viewport });
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  await page.goto(BASE + path, { waitUntil: 'networkidle' });
  return { page, errors };
}

// ---- 1. Desktop web renders scoreboard + chyron --------------------------
{
  const { page, errors } = await open('/', { width: 1440, height: 900 });
  await page.waitForSelector('.card', { timeout: 15000 });
  const games = await page.locator('.card').count();
  ok(games > 0, `desktop: ${games} game cards render`);
  ok(await page.locator('.chyron').count() === 1, 'desktop: chyron present');
  ok(errors.length === 0, `desktop: no page errors (${errors.join(' | ').slice(0, 140) || 'none'})`);
  await page.close();
}

// ---- 2. Mobile viewport ---------------------------------------------------
{
  const { page, errors } = await open('/', { width: 390, height: 844 });
  await page.waitForSelector('.card', { timeout: 15000 });
  ok(await page.locator('.card').count() > 0, 'mobile: cards render at 390px');
  ok(errors.length === 0, 'mobile: no page errors');
  await page.close();
}

// ---- 3. TV mode -----------------------------------------------------------
{
  const { page, errors } = await open('/?tv=1', { width: 1920, height: 1080 });
  await page.waitForSelector('.card', { timeout: 15000 });
  const isTv = await page.evaluate(() => document.documentElement.hasAttribute('data-tv'));
  ok(isTv, 'tv: data-tv attribute set');
  ok(errors.length === 0, 'tv: no page errors');

  // TV consistency (the coherent 10-foot ladder): same-role elements share a
  // size, nothing is stranded tiny (micro ≥16px) or blown out, and no card
  // text overflows its column.
  const tv = await page.evaluate(() => {
    const fs = (sel) => { const el = document.querySelector(sel); return el ? parseFloat(getComputedStyle(el).fontSize) : null; };
    const abbr = document.querySelector('.team-row .abbr');
    return {
      health: fs('.health'), brandSub: fs('.brand-sub'), when: fs('.when'),
      leagueTag: fs('.league-tag'), tick: fs('.tick'), brandName: fs('.brand-name'),
      who: fs('.gt .who'), abbr: fs('.abbr'), score: fs('.score'), hint: fs('.hint'),
      abbrOverflow: abbr ? abbr.scrollWidth > abbr.clientWidth + 1 : false,
    };
  });
  ok(tv.health >= 16, `tv: health pill not stranded tiny (${tv.health}px ≥ 16)`);
  ok(tv.when >= 16, `tv: .when meta ≥ 16px (${tv.when})`);
  ok(tv.tick >= 32, `tv: chyron tick large (${tv.tick}px)`);
  ok(tv.who === tv.brandName, `tv: card team names and brand share one display size (${tv.who}px == ${tv.brandName}px)`);
  ok(tv.abbr <= tv.score, `tv: hero numerals tame (abbr ${tv.abbr} ≤ score ${tv.score})`);
  ok(!tv.abbrOverflow, 'tv: hero abbreviation does not overflow its column');
  await page.close();
}

// ---- 4. Updates panel (web mode) ------------------------------------------
{
  const { page, errors } = await open('/', { width: 1440, height: 900 });
  await page.waitForSelector('.card', { timeout: 15000 });
  // Stub the GitHub releases API so the test is deterministic offline.
  await page.route('https://api.github.com/**', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          tag_name: 'coreline-v99.0.0',
          body: 'Test release notes',
          assets: [
            { name: 'coreline-release.apk', browser_download_url: 'https://github.com/brevityA/CoreBuildsApps/releases/download/coreline-v99.0.0/coreline-release.apk' },
          ],
        },
      ]),
    });
  });
  // Open settings drawer via its rail trigger.
  await page.click('[data-action="settings"]');
  await page.click('[data-action="drawer-section"][data-section="updates"]');
  await page.waitForSelector('#updatePanel', { timeout: 5000 });
  // Web build has no native version → should report an update is available.
  await page.click('[data-action="check-updates"]');
  await page.waitForFunction(() => {
    const el = document.getElementById('updatePanel');
    return el && /available|up to date|failed/i.test(el.textContent) && !/Checking/.test(el.textContent);
  }, { timeout: 25000 });
  const txt = await page.locator('#updatePanel').innerText();
  ok(/Update \d+\.\d+\.\d+ is available/.test(txt), `updates: web build reports newer release — "${txt.replace(/\n/g, ' ').slice(0, 90)}…"`);
  ok(/Android TV app/.test(txt), 'updates: web build tells user updates install on TV app');
  // Filter out resource-404s from /api/proxy (native-only endpoint)
  const realErrors = errors.filter((e) => !/Failed to load resource|404/.test(e));
  ok(realErrors.length === 0, 'updates: no page errors');
  await page.close();
}

// ---- 5. Phone floating overlay page (native=1&overlay=1) -----------------
{
  const { page, errors } = await open('/?native=1&overlay=1', { width: 390, height: 64 });
  await page.waitForSelector('.chyron', { timeout: 15000 });
  const isOverlay = await page.evaluate(() => document.documentElement.hasAttribute('data-overlay'));
  ok(isOverlay, 'overlay: data-overlay attribute set');
  const chrome = await page.evaluate(() => {
    const vis = (sel) => {
      const el = document.querySelector(sel);
      if (!el) return 'missing';
      const cs = getComputedStyle(el);
      return cs.display === 'none' ? 'hidden' : 'visible';
    };
    return {
      topbar: vis('.topbar'), stage: vis('.stage'), drawer: vis('.drawer'),
      chyron: vis('.chyron'),
      bodyBg: getComputedStyle(document.body).backgroundColor,
    };
  });
  ok(chrome.topbar === 'hidden', `overlay: topbar hidden (${chrome.topbar})`);
  ok(chrome.stage === 'hidden', `overlay: stage hidden (${chrome.stage})`);
  ok(chrome.drawer === 'hidden', `overlay: drawer hidden (${chrome.drawer})`);
  ok(chrome.chyron === 'visible', 'overlay: chyron visible');
  ok(/rgba\(0, 0, 0, 0\)/.test(chrome.bodyBg), `overlay: body background transparent (${chrome.bodyBg})`);
  await page.waitForSelector('.tick', { timeout: 15000 });
  ok(await page.locator('.tick').count() > 0, 'overlay: tick items rendered');
  // native=1 routes scoreboard/RSS fetches through /api/proxy, which only the
  // Android shell serves — the Node dev server 404s it. Filter those expected
  // resource-404s; anything else (JS exceptions, etc.) is a real failure.
  const realErrors = errors.filter((e) => !/Failed to load resource|404/.test(e));
  ok(realErrors.length === 0, `overlay: no real page errors (${realErrors.join(' | ').slice(0, 140) || 'none'})`);
  await page.close();
}


// ---- 6. Shipped policy, OLED default, banner clock ------------------------
//
// Everything above this line is a rendering check. These are the assertions
// for the three things this branch can only get wrong in a browser:
//
//  - a Content-Security-Policy directive. A CSP is the one kind of config that
//    fails *open* in a unit test and *closed* in a browser: `img-scr` is not a
//    directive, so the token is dropped, the policy still parses, and the only
//    witness is a blocked request. `securitypolicyviolation` is that witness.
//  - the true-black default, which is declared statically in the document and
//    removed by script when a viewer opts out — two halves that can disagree.
//  - the banner's clock, which either advances or silently never fires.
{
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  // Registered before any page script runs, so nothing is missed.
  await page.addInitScript(() => {
    window.__cspViolations = [];
    document.addEventListener('securitypolicyviolation', (e) => {
      window.__cspViolations.push(`${e.violatedDirective} ${e.blockedURI}`);
    });
  });
  await page.goto(`${BASE}/?tv=1`, { waitUntil: 'networkidle' });
  await page.waitForSelector('.card', { timeout: 15000 });

  const violations = await page.evaluate(() => window.__cspViolations);
  ok(violations.length === 0, `csp: nothing the app asked for was blocked (${violations.slice(0, 3).join(' | ') || 'clean'})`);
  ok(errors.length === 0, `csp: no page errors under the shipped policy (${errors.join(' | ').slice(0, 140) || 'none'})`);

  const oledOn = await page.evaluate(() => document.documentElement.hasAttribute('data-oled'));
  ok(oledOn, 'oled: true black is on for a fresh install');

  // Every mark that rendered must carry a plate decision (finding: one plate
  // colour for both ESPN's dark-UI art and the NHL's light-background art).
  const marks = await page.evaluate(() => {
    const imgs = [...document.querySelectorAll('.mark__img')];
    return { count: imgs.length, unplated: imgs.filter((i) => !i.dataset.plate).length };
  });
  ok(marks.unplated === 0, `plate: every rendered mark has one (${marks.count} marks, ${marks.unplated} unplated)`);

  // The opt-out half: with `oled: false` stored, script must remove the static
  // attribute. If it did not, the document's default would win over the
  // viewer's choice.
  await page.evaluate(() => {
    const KEY = 'coreline.v1';
    const state = JSON.parse(localStorage.getItem(KEY) || '{}');
    state.oled = false;
    localStorage.setItem(KEY, JSON.stringify(state));
  });
  await page.reload({ waitUntil: 'networkidle' });
  await page.waitForSelector('.card', { timeout: 15000 });
  ok(!(await page.evaluate(() => document.documentElement.hasAttribute('data-oled'))),
    'oled: the stored opt-out removes the static attribute');

  // ---- the banner clock ----
  // The bundled sample feed has no live games, so the slate is injected rather
  // than waited for: a test that depends on today's fixtures is a test that
  // fails on a quiet Tuesday.
  await page.bringToFront();
  await page.evaluate(() => {
    const live = (id, away, home) => ({
      id, status: 'live', league: 'NHL', source: 'espn', detail: 'P2 04:11', venue: 'Smoke',
      away: { abbr: away, name: away, score: 1, logo: null, winner: false },
      home: { abbr: home, name: home, score: 2, logo: null, winner: false },
      channels: ['TSN4'],
    });
    const events = window.__CORELINE__.getEvents();
    events.unshift(live('smoke-1', 'TOR', 'MTL'), live('smoke-2', 'BOS', 'NYR'));
    window.__CORELINE__.render();
  });
  const heroId = () => page.locator('#hero [data-id]').first().getAttribute('data-id');
  const first = await heroId();
  ok(Boolean(first), `rotation: the banner took a live game (${first})`);
  await page.waitForTimeout(13500);
  const second = await heroId();
  ok(second !== first, `rotation: the banner advanced on its own (${first} → ${second})`);

  // ...and holds still while the viewer has focus on it, because rotating then
  // replaces the node they are reading.
  await page.locator('#hero .focusable').first().focus();
  const focused = await heroId();
  await page.waitForTimeout(13500);
  ok((await heroId()) === focused, 'rotation: the banner holds while focused');

  await page.close();
}

await browser.close();
console.log(failures === 0 ? '\nALL SMOKE CHECKS PASSED' : `\n${failures} CHECK(S) FAILED`);
process.exit(failures === 0 ? 0 : 1);
