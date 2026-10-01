import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

import { reconcile } from '../lib/reconcile.mjs';

// A list-shaped stand-in for the #board element: just the calls reconcile uses.
function fakeHost() {
  const kids = [];
  return {
    kids,
    // DOM mutations made, so a test can say how much work an update cost.
    inserts: 0,
    removes: 0,
    get children() { return kids; },
    insertBefore(node, ref) {
      this.inserts += 1;
      const from = kids.indexOf(node);
      if (from >= 0) kids.splice(from, 1);
      const at = ref ? kids.indexOf(ref) : -1;
      if (at >= 0) kids.splice(at, 0, node);
      else kids.push(node);
    },
    removeChild(node) {
      this.removes += 1;
      kids.splice(kids.indexOf(node), 1);
    },
  };
}

/** Render `list`, then zero the counters so the next call is measured alone. */
function settled(list) {
  const host = fakeHost();
  const first = reconcile(host, list, new Map(), make);
  host.inserts = 0;
  host.removes = 0;
  return { host, cache: first.cache };
}

const make = (html) => ({ html });
const items = (...pairs) => pairs.map(([key, html]) => ({ key, html }));

test('first render creates every node in order', () => {
  const host = fakeHost();
  const r = reconcile(host, items(['a', '<A>'], ['b', '<B>']), new Map(), make);
  assert.deepEqual(host.kids.map((n) => n.html), ['<A>', '<B>']);
  assert.equal(r.created, 2);
  assert.equal(r.kept, 0);
});

test('an unchanged refresh keeps every node (no rebuild, focus survives)', () => {
  const host = fakeHost();
  const list = items(['a', '<A>'], ['b', '<B>'], ['c', '<C>']);
  const first = reconcile(host, list, new Map(), make);
  const before = [...host.kids];
  const r = reconcile(host, list, first.cache, make);
  assert.equal(r.created, 0);
  assert.equal(r.kept, 3);
  host.kids.forEach((n, i) => assert.equal(n, before[i], `node ${i} was replaced`));
});

test('only the changed card is rebuilt', () => {
  const host = fakeHost();
  const first = reconcile(host, items(['a', '<A>'], ['b', '<B 0-0>'], ['c', '<C>']), new Map(), make);
  const [a, , c] = host.kids;
  const r = reconcile(host, items(['a', '<A>'], ['b', '<B 1-0>'], ['c', '<C>']), first.cache, make);
  assert.equal(r.created, 1);
  assert.equal(host.kids[0], a);
  assert.equal(host.kids[1].html, '<B 1-0>');
  assert.equal(host.kids[2], c);
});

test('reordering moves nodes rather than rebuilding them', () => {
  const host = fakeHost();
  const first = reconcile(host, items(['a', '<A>'], ['b', '<B>'], ['c', '<C>']), new Map(), make);
  const [a, b, c] = host.kids;
  const r = reconcile(host, items(['c', '<C>'], ['a', '<A>'], ['b', '<B>']), first.cache, make);
  assert.equal(r.created, 0);
  assert.deepEqual(host.kids, [c, a, b]);
});

test('removed items leave the grid; added ones appear where they belong', () => {
  const host = fakeHost();
  const first = reconcile(host, items(['a', '<A>'], ['b', '<B>'], ['c', '<C>']), new Map(), make);
  reconcile(host, items(['a', '<A>'], ['n', '<N>'], ['c', '<C>']), first.cache, make);
  assert.deepEqual(host.kids.map((n) => n.html), ['<A>', '<N>', '<C>']);
});

test('an empty list clears the grid', () => {
  const host = fakeHost();
  const first = reconcile(host, items(['a', '<A>'], ['b', '<B>']), new Map(), make);
  reconcile(host, [], first.cache, make);
  assert.equal(host.kids.length, 0);
});

test('a repeated key gets its own node instead of moving the first one', () => {
  const host = fakeHost();
  const r = reconcile(host, items(['x', '<X1>'], ['x', '<X2>']), new Map(), make);
  assert.equal(host.kids.length, 2);
  assert.notEqual(host.kids[0], host.kids[1]);
  assert.equal(r.cache.get('x').html, '<X1>');
});

test('a card dropping off the top costs one removal, not a move per later card', () => {
  const list = Array.from({ length: 50 }, (_, i) => [`k${i}`, `<${i}>`]);
  const { host, cache } = settled(items(...list));
  const later = host.kids.slice(1);
  reconcile(host, items(...list.slice(1)), cache, make);
  assert.equal(host.removes, 1);
  assert.equal(host.inserts, 0, 'kept cards were detached and re-inserted');
  assert.deepEqual(host.kids, later);
});

test('a changed card costs one insert and one removal', () => {
  const { host, cache } = settled(items(['a', '<A>'], ['b', '<B 0-0>'], ['c', '<C>'], ['d', '<D>']));
  reconcile(host, items(['a', '<A>'], ['b', '<B 1-0>'], ['c', '<C>'], ['d', '<D>']), cache, make);
  assert.equal(host.inserts, 1);
  assert.equal(host.removes, 1);
});

test('an unchanged refresh makes no DOM mutations at all', () => {
  const list = items(['a', '<A>'], ['b', '<B>'], ['c', '<C>']);
  const { host, cache } = settled(list);
  reconcile(host, list, cache, make);
  assert.equal(host.inserts + host.removes, 0);
});

test('the board renders cards through reconcile, not a full innerHTML rebuild', () => {
  const board = readFileSync(new URL('../public/js/ui/board.js', import.meta.url), 'utf8');
  const fn = board.slice(board.indexOf('function renderCards('), board.indexOf('export function gameCard('));
  assert.match(fn, /reconcile\(host, items, cardCache, cardNode\)/);
  assert.doesNotMatch(fn, /host\.innerHTML\s*=/);
});
