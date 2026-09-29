import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

/**
 * True black (OLED) — the ramp, and the seams that carry it.
 *
 * The value that matters here is a colour, and a colour is exactly the kind of
 * thing that rots silently: raise --cb-void in the house palette some release
 * later and an OLED field that used to be #000000 quietly becomes #05070A
 * again, which on a 65" panel in a dark room is the whole bug back. So this
 * file does not assert "the token exists". It parses the ramp out of the
 * stylesheet and compares the numbers.
 */

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..');
const read = (rel) => readFileSync(join(root, rel), 'utf8');

const tokensCss = read('public/css/tokens.css');
const stateJs = read('public/js/state.js');
const settingsJs = read('public/js/ui/settings.js');
const indexHtml = read('public/index.html');

const SURFACES = ['void', 'night', 'panel', 'card', 'raised'];

/** Every `--name: value` pair inside the first block whose selector is sel. */
function block(sel) {
  const at = tokensCss.indexOf(sel);
  assert.notEqual(at, -1, `tokens.css has no ${sel} block`);
  const open = tokensCss.indexOf('{', at);
  const close = tokensCss.indexOf('}', open);
  const body = tokensCss.slice(open + 1, close);
  return Object.fromEntries(
    [...body.matchAll(/--([a-z-]+):\s*([^;]+);/g)].map((m) => [m[1], m[2].trim()]),
  );
}

const luma = (hex) => {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};

test('the OLED block paints every surface, so no near-black leaks through', () => {
  const oled = block('[data-oled]');
  for (const name of SURFACES) {
    assert.ok(oled[`surface-${name}`], `--surface-${name} is not remapped`);
  }
});

test('the two fields a panel actually shows are pure black', () => {
  const oled = block('[data-oled]');
  // void is the app shell behind everything; night is the field behind the
  // board. These are the large areas — the ones that light the room and the
  // whole reason to offer this at all.
  assert.equal(oled['surface-void'], '#000000');
  assert.equal(oled['surface-night'], '#000000');
  assert.equal(oled['surface-void'], oled['surface-night']);
});

test('the OLED field is darker than the house one it replaces', () => {
  const oled = block('[data-oled]');
  const base = { 'surface-void': '#04070F', 'surface-night': '#0D1117' };
  for (const [name, houseHex] of Object.entries(base)) {
    assert.ok(
      luma(oled[name]) < luma(houseHex),
      `${name}: ${oled[name]} is not darker than ${houseHex}`,
    );
  }
});

test('elevation survives: every step is strictly above the field', () => {
  const oled = block('[data-oled]');
  const order = ['void', 'night', 'panel', 'card', 'raised'];
  const ramp = order.map((n) => luma(oled[`surface-${n}`]));
  for (let i = 1; i < ramp.length; i += 1) {
    // A pure-black ramp has no headroom, so a card that does not step up is a
    // card that disappears into the field behind it.
    assert.ok(ramp[i] >= ramp[i - 1], `--surface-${order[i]} is below --surface-${order[i - 1]}`);
  }
  assert.ok(ramp.at(-1) > ramp[0], 'raised is not above the field — nothing would separate');
});

test('it is a second axis, not a fifth theme: accents are untouched', () => {
  const body = tokensCss.slice(tokensCss.indexOf('[data-oled]'));
  const oled = body.slice(0, body.indexOf('}'));
  // The moment this block remaps --accent it has become a theme, and a viewer
  // cannot have broadcast red on an OLED panel.
  assert.doesNotMatch(oled, /--accent|--cta-|--st-/);
});

test('the setting is stored, sanitised, applied and wired to a real control', () => {
  assert.match(stateJs, /oled:\s*true/, 'not in DEFAULTS, or no longer on by default');
  assert.match(stateJs, /out\.oled\s*=\s*Boolean\(out\.oled\)/, 'not sanitised on load');
  assert.match(settingsJs, /toggleAttribute\('data-oled'/, 'never applied to the root element');
  assert.match(indexHtml, /id="oled"/, 'markup has no control with that id');
  assert.match(settingsJs, /\$\('oled'\)\?\.addEventListener\('change'/, 'control is inert');
});

test('toggling off removes the attribute rather than falsifying it', () => {
  // The selector is a presence check. `data-oled="false"` would match and
  // leave the viewer stuck on an OLED ramp they just switched off.
  assert.match(settingsJs, /toggleAttribute\('data-oled',\s*Boolean\(s\.oled\)\)/);
  assert.match(tokensCss, /^\[data-oled\]\s*\{/m, 'the selector is not a bare presence check');
});
