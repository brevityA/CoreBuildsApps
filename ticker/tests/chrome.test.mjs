import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

import { resolveTicker, tickerShown } from '../lib/chrome.mjs';

const read = (p) => readFileSync(new URL(p, import.meta.url), 'utf8');

test('a fresh install starts with the ticker off', () => {
  assert.equal(resolveTicker(undefined), false);
  assert.equal(resolveTicker(null), false);
  assert.equal(resolveTicker({}), false);
});

test('an install from before the setting existed keeps its ticker', () => {
  // 1.3.1 / 1.4.0 stored state has other keys but never `ticker`.
  assert.equal(resolveTicker({ theme: 'core', leagues: ['nfl'] }), true);
});

test('a stored choice wins, and only a real boolean counts', () => {
  assert.equal(resolveTicker({ theme: 'core', ticker: false }), false);
  assert.equal(resolveTicker({ theme: 'core', ticker: true }), true);
  assert.equal(resolveTicker({ theme: 'core', ticker: 'true' }), false);
});

test('crawl mode and the phone overlay always show the ticker', () => {
  assert.equal(tickerShown({ ticker: false, mode: 'board' }), false);
  assert.equal(tickerShown({ ticker: true, mode: 'board' }), true);
  assert.equal(tickerShown({ ticker: false, mode: 'crawl' }), true);
  assert.equal(tickerShown({ ticker: false, mode: 'board' }, { overlay: true }), true);
});

test('the setting is stored, applied and wired to a real control', () => {
  const stateJs = read('../public/js/state.js');
  const settingsJs = read('../public/js/ui/settings.js');
  const indexHtml = read('../public/index.html');
  const layoutCss = read('../public/css/layout.css');
  assert.match(stateJs, /^\s+ticker:\s*false,/m, 'fresh-install default is not off');
  assert.match(stateJs, /out\.ticker\s*=\s*resolveTicker\(raw\)/, 'not resolved on load');
  assert.match(indexHtml, /id="ticker"/, 'no control');
  assert.match(settingsJs, /\$\('ticker'\)\?\.addEventListener\('change'/, 'control is inert');
  assert.match(settingsJs, /toggleAttribute\('data-no-ticker'/, 'never applied to the root');
  // Hidden must mean stopped, or the watchdog reads a hidden ribbon as stalled.
  assert.match(settingsJs, /if \(!shown && t\.running\) t\.stop\(\)/, 'hidden ticker keeps running');
  assert.match(layoutCss, /:root\[data-no-ticker\] \.chyron \{\s*display: none;/, 'chyron not hidden');
});
