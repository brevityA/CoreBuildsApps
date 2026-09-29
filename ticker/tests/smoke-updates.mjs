// Headless smoke: web (desktop), mobile, TV, and the new Updates panel.
import { chromium } from 'playwright-core';

const BASE = 'http://127.0.0.1:8787';
const bin = process.env.CHROMIUM_PATH || '/usr/bin/chromium';
let failures = 0;

/**
 * Failures are emitted twice: to stdout, for a human reading the job log, and
 * as a GitHub workflow command, which becomes a check-run *annotation*.
 *
 * That second copy is not decoration. Downloading this repo's job logs fails
 * at the archive endpoint (`EOF` from results-receiver), so a failing run in
 * this workflow is otherwise invisible from outside CI — "Process completed
 * with exit code 1" and nothing else. Annotations are readable through the
 * API, so a failing assertion names itself.
 */
function ok(cond, msg) {
  console.log((cond ? 'PASS' : 'FAIL') + '  ' + msg);
  if (!cond) {
    failures++;
    console.log(`::error title=smoke::${msg.replace(/[\r\n]+/g, ' ').slice(0, 800)}`);
  }
}

/** The app's console output, surfaced whether or not the count check passed. */
function reportConsole(label, errors) {
  if (!errors.length) return;
  console.log(`::notice title=${label}-console::${errors.join(' | ').replace(/[\r\n]+/g, ' ').slice(0, 700)}`);
}

// A thrown assertion (a `waitForSelector` timeout, a null dereference) exits
// the script before any summary, which is the least diagnosable outcome. Turn
// it into an annotation too.
for (const ev of ['uncaughtException', 'unhandledRejection']) {
  process.on(ev, (err) => {
    console.log(`::error title=smoke-crash (${ev})::${String(err && err.stack || err).replace(/[\r\n]+/g, ' ').slice(0, 900)}`);
    process.exit(1);
  });
}

const browser = await chromium.launch({
  executablePath: bin,
  args: ['--no-sandbox', '--disable-dev-shm-usage'],
});

const STATE_KEY = 'coreline.v1';

/**
 * Seed the one thing a returning viewer would have: that they have finished
 * onboarding.
 *
 * A fresh CI profile is a fresh *install*, so the first-run overlay appears and
 * — being a full-screen dialog — intercepts pointer events. The Updates block
 * failed with a 30-second click timeout because of it, and failed *only* in CI:
 * locally the profile doing the authoring had onboarded long ago. A test that
 * exercises the settings drawer has to look like a viewer who has used the app.
 *
 * Merged rather than replaced, because block 6 stores an OLED opt-out in the
 * same key and reloads; a seed that overwrote the blob would silently undo it.
 */
async function seedReturningViewer(page) {
  await page.addInitScript((key) => {
    try {
      const raw = localStorage.getItem(key);
      const state = raw ? JSON.parse(raw) : {};
      if (state.onboarded !== true) {
        state.onboarded = true;
        localStorage.setItem(key, JSON.stringify(state));
      }
    } catch { /* a storage-less context still renders; the assertions will say so */ }
  }, STATE_KEY);
}

async function open(path, viewport) {
  const page = await browser.newPage({ viewport });
  await seedReturningViewer(page);
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
  reportConsole('desktop', errors);
  ok(errors.length === 0, `desktop: no page errors (${errors.join(' | ').slice(0, 140) || 'none'})`);
  await page.close();
}

// ---- 2. Mobile viewport ---------------------------------------------------
{
  const { page, errors } = await open('/', { width: 390, height: 844 });
  await page.waitForSelector('.card', { timeout: 15000 });
  ok(await page.locator('.card').count() > 0, 'mobile: cards render at 390px');
  reportConsole('mobile', errors);
  ok(errors.length === 0, 'mobile: no page errors');
  await page.close();
}

// ---- 3. TV mode -----------------------------------------------------------
{
  const { page, errors } = await open('/?tv=1', { width: 1920, height: 1080 });
  await page.waitForSelector('.card', { timeout: 15000 });
  const isTv = await page.evaluate(() => document.documentElement.hasAttribute('data-tv'));
  ok(isTv, 'tv: data-tv attribute set');
  reportConsole('tv', errors);
  ok(errors.length === 0, 'tv: no page errors');

  // TV consistency (the coherent 10-foot ladder): same-role elements share a
  // size, nothing is stranded tiny (micro ≥16px) or blown out, and no card
  // text overflows its column.
  const tv = await page.evaluate(() => {
    const fs = (sel) => { const el = document.querySelector(sel); return el ? parseFloat(getComputedStyle(el).fontSize) : null; };
    const abbr = document.querySelector('.team-row .abbr');
    return {
      health: fs('.health'), brandSub: fs('.brand-sub'), when: fs('.when'),
      leagueTag: fs('.league-tag'), tick: fs('.tick'), brandName: fs('.brand__name'),
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
  // Service workers blocked for this block, and that is the whole reason it
  // now works. The app registers one in web mode; it claims the page on
  // activation and then mediates the page's fetches, and Playwright's routing
  // does not see requests a service worker handles — so the stub below was
  // never consulted and the block read the live manifest. Block 1 still loads
  // the app with the worker enabled, so the worker itself stays covered.
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    serviceWorkers: 'block',
  });
  const page = await context.newPage();
  await seedReturningViewer(page);
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  // Say out loud whether the manifest request even happened, and who answered
  // it: a future failure should not need four runs to diagnose.
  page.on('request', (r) => {
    if (/coreline-version/.test(r.url())) console.log(`::notice title=update-manifest-request::${r.url()}`);
  });
  // Stub the *manifest the app actually reads* so the test is deterministic
  // offline. This used to stub `api.github.com`, which the updater stopped
  // calling at some point — so the stub was bypassed, the live manifest was
  // fetched, and the block asserted "an update is available" against an app
  // that was correctly reporting it was up to date. The source is
  // `raw.githubusercontent.com/.../Latestrelease/coreline-version.json`.
  await page.route('**/coreline-version.json', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        versionCode: 99,
        versionName: '99.0.0',
        releaseDate: '2099-01-01',
        apkUrl: 'https://github.com/brevityA/CoreBuildsApps/releases/download/coreline/coreline-release.apk',
        releaseNotesUrl: 'https://github.com/brevityA/CoreBuildsApps/releases/tag/coreline-v99.0.0',
        minSdk: 24,
      }),
    });
  });
  // ...and only then load the app. The updater reads the manifest at boot, so
  // a route registered after `goto` is a route the app has already been past —
  // which is why this block reported the *real* version ("up to date (latest
  // 1.4.0)") while claiming to test an update to 99.0.0.
  await page.goto(`${BASE}/`, { waitUntil: 'networkidle' });
  await page.waitForSelector('.card', { timeout: 15000 });

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
  // What is asserted here is the *plumbing*: the panel settled, and the version
  // it reports is the one the stub served. That proves fetch → parse → render
  // end to end, which is what a browser can prove.
  //
  // What is deliberately NOT asserted is the verdict — "Update 99.0.0 is
  // available" — because it is a function of the running app's version code,
  // and a browser has none: `latestCorelineRelease` ... the manifest builder
  // compares against a native code. This block used to assert the verdict, and
  // it passed for as long as the stub was bypassed and the *live* manifest's
  // older version made the comparison come out the other way. The verdict is
  // gated by tests/test_update_manifest_gate.py and tools/check_published_update.py;
  // asserting it here would mean encoding a guess about a native code path.
  ok(/99\.0\.0/.test(txt), `updates: the panel reports the manifest it read — "${txt.replace(/\n/g, ' ').slice(0, 90)}…"`);
  ok(/(You’re up to date|Update 99\.0\.0 is available)/.test(txt), 'updates: the panel reaches a settled verdict');
  const verdictIsNewer = /Update 99\.0\.0 is available/.test(txt);
  ok(!verdictIsNewer || /Android TV app/.test(txt),
    'updates: when it does offer an update, it says where updates install');
  // Filter out resource-404s from /api/proxy (native-only endpoint)
  const realErrors = errors.filter((e) => !/Failed to load resource|404/.test(e));
  ok(realErrors.length === 0, 'updates: no page errors');
  await context.close();
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
    // `.rail`, `.stage` and `.drawer` are what `[data-overlay]` hides
    // (chyron.css). This block used to assert `.topbar`, a class that has not
    // been in the markup for a long time — so the check reported `missing`
    // rather than `hidden`, and had been failing on a class name.
    return {
      rail: vis('.rail'), stage: vis('.stage'), drawer: vis('.drawer'),
      chyron: vis('.chyron'),
      bodyBg: getComputedStyle(document.body).backgroundColor,
    };
  });
  ok(chrome.rail === 'hidden', `overlay: rail hidden (${chrome.rail})`);
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
  await seedReturningViewer(page);
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


// ---- 7. A fresh install is still offered onboarding -----------------------
//
// The seed above suppresses this overlay for the interaction blocks, so this
// asserts the surface it suppresses still exists — otherwise "dismissed in
// tests" would quietly become "removed from the product".
{
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  await page.goto(`${BASE}/`, { waitUntil: 'networkidle' });
  let shown = true;
  try {
    await page.locator('#onboard').waitFor({ state: 'visible', timeout: 10000 });
  } catch {
    shown = false;
  }
  ok(shown, 'onboarding: a fresh profile is offered the first-run overlay');
  await page.close();
}

await browser.close();
console.log(failures === 0 ? '\nALL SMOKE CHECKS PASSED' : `\n${failures} CHECK(S) FAILED`);
process.exit(failures === 0 ? 0 : 1);
