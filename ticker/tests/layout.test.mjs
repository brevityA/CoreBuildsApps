import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', 'public');
const read = (p) => readFileSync(join(root, p), 'utf8');
const html = read('index.html');

function jsFiles(dir) {
  return readdirSync(join(root, dir)).flatMap((f) => {
    const rel = join(dir, f);
    return statSync(join(root, rel)).isDirectory() ? jsFiles(rel) : (f.endsWith('.js') ? [rel] : []);
  });
}

// The pages that float over other apps (the crawl and the scoreboard bug)
// have their own markup and their own scripts.
const appJs = jsFiles('js').filter((f) => !f.endsWith('overlay.js') && !f.endsWith('scorebug.js') && !f.endsWith('boot.js'));

test('every element id the app looks up exists in index.html', () => {
  // The 2026-09-29 layout moved most of the chrome out of the rail. A moved
  // id that no longer exists does not throw — $('x') just returns null and
  // the feature goes quiet — so the markup and the lookups are checked here.
  const ids = new Set();
  for (const f of appJs) {
    const src = read(f);
    for (const m of src.matchAll(/\$\('([A-Za-z][\w-]*)'\)|getElementById\('([A-Za-z][\w-]*)'\)|setText\('([A-Za-z][\w-]*)'|setHidden\('([A-Za-z][\w-]*)'/g)) {
      ids.add(m[1] || m[2] || m[3] || m[4]);
    }
  }
  // Ids a module renders itself (onboarding's steps, for one) exist once it
  // has rendered, so a template in the JS counts as well as the markup.
  const templates = appJs.map(read).join('\n');
  const missing = [...ids].filter((id) => !html.includes(`id="${id}"`) && !templates.includes(`id="${id}"`));
  assert.deepEqual(missing, [], `ids looked up but not in index.html: ${missing.join(', ')}`);
});

test('the rail is the menu, and the chrome lives in the stage header', () => {
  const rail = html.slice(html.indexOf('<aside class="rail"'), html.indexOf('</aside>'));
  for (const action of ['refresh', 'mode', 'settings']) {
    assert.match(rail, new RegExp(`data-action="${action}"`), `rail has no ${action}`);
  }
  assert.doesNotMatch(rail, /id="leagues"|id="statLive"|id="health"/, 'chrome is back in the rail');
  const header = html.slice(html.indexOf('<header class="topbar"'), html.indexOf('<div class="stage__body'));
  for (const id of ['statLive', 'statUp', 'health', 'search', 'topClock', 'leagues', 'myTeams']) {
    assert.match(header, new RegExp(`id="${id}"`), `${id} is not in the fixed header`);
  }
});

test('only the stage body scrolls, so the header stays in view', () => {
  const css = read('css/layout.css');
  assert.match(html, /<main class="stage" id="stage">/, 'the stage itself scrolls again');
  assert.match(css, /\.stage__body \{[^}]*overflow-y: auto;/, 'the body does not scroll');
});

test('a card leads with the channel and has no second Watch stop', () => {
  const board = read('js/ui/board.js');
  const card = board.slice(board.indexOf('export function gameCard'), board.indexOf('export function headlineCard'));
  assert.match(card, /class="card__net"/, 'no channel headline');
  assert.doesNotMatch(card, /data-action="watch"/, 'cards grew a Watch button again');
});
