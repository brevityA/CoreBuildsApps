import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

/**
 * The VPN dot's seams, asserted rather than eyeballed.
 *
 * `lib/vpn.mjs` is unit-tested; these are the joins around it that a Node test
 * cannot execute — the drawer's markup, the settings model, and the Kotlin
 * bridge — but that fail the same way in production: a control wired to an id
 * that does not exist, a setting written but never sanitised (so it is dropped
 * on the next load), or a JS call to a bridge method Android never exposes.
 */

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..');
const read = (rel) => readFileSync(join(root, rel), 'utf8');

const settingsJs = read('public/js/ui/settings.js');
const indexHtml = read('public/index.html');
const stateJs = read('public/js/state.js');
const lineBridge = read('android/app/src/main/java/dev/corebuilds/line/LineBridge.kt');
const mainActivity = read('android/app/src/main/java/dev/corebuilds/line/MainActivity.kt');

test('every element id the settings drawer touches exists in the markup', () => {
  const ids = [...settingsJs.matchAll(/\$\('([A-Za-z][\w.-]*)'\)/g)].map((m) => m[1]);
  const missing = [...new Set(ids)].filter((id) => !indexHtml.includes(`id="${id}"`));
  assert.deepEqual(missing, [], `settings.js binds to ids that are not in index.html: ${missing}`);
});

test('the VPN dot controls sit inside the native overlay block', () => {
  // The block is what hides the controls in a browser and on Fire TV, where
  // overlays cannot be drawn at all. A dot toggle outside it would offer a
  // switch that can never do anything.
  const blockStart = indexHtml.indexOf('id="overlayBlock"');
  const nextSection = indexHtml.indexOf('Favorite teams');
  assert.ok(blockStart > 0 && nextSection > blockStart, 'overlay block not found');
  const block = indexHtml.slice(blockStart, nextSection);
  for (const id of ['vpnDotEnabled', 'vpnDotCorner', 'vpnDotOpacity', 'vpnDotHideWhenOk', 'vpnDotBlink', 'vpnStatusDot', 'vpnStatusText']) {
    assert.ok(block.includes(`id="${id}"`), `${id} is outside the overlay block`);
  }
});

test('the dot reads its shape from the tested module, not from ad-hoc numbers', () => {
  assert.match(settingsJs, /from '\/lib\/vpn\.mjs'/, 'settings.js must import the vpn module');
  assert.match(settingsJs, /vpnDotConfig\(/, 'the config payload must come from lib/vpn.mjs');
  assert.match(settingsJs, /JSON\.stringify\(dotConfig\(\)\)/, 'the bridge takes a JSON payload');
});

test('every VPN bridge call is implemented on both sides of the Kotlin bridge', () => {
  const calls = [...settingsJs.matchAll(/\?\.(vpn\w+)\?\.\(/g)].map((m) => m[1]);
  assert.ok(calls.length >= 5, `expected the dot to call the bridge, saw ${calls.length}`);
  for (const name of new Set(calls)) {
    assert.match(lineBridge, new RegExp(`fun ${name}\\(`), `LineBridge.kt has no ${name}`);
    assert.match(mainActivity, new RegExp(`fun ${name}\\(`), `MainActivity.kt has no ${name}`);
  }
});

test('the persisted dot settings are declared and sanitised', () => {
  // A key in DEFAULTS but not in sanitizeState() is silently dropped whenever
  // the drawer closes, which looks like "the setting does not stick".
  const keys = ['vpnDot', 'vpnDotCorner', 'vpnDotOpacity', 'vpnDotHideWhenOk', 'vpnDotBlink'];
  for (const key of keys) {
    assert.match(stateJs, new RegExp(`^\\s+${key}:`, 'm'), `${key} is not in DEFAULTS`);
    assert.match(stateJs, new RegExp(`out\\.${key} =`), `${key} is not sanitised`);
  }
});
