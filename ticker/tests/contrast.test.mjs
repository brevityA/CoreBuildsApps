import test from 'node:test';
import assert from 'node:assert/strict';
import { contrastRatio, readableOn } from '../lib/contrast.mjs';
import { LEAGUES } from '../lib/scoreboard.mjs';

// The lightest surface a card sits on (--surface-raised, a focused card).
const CARD = '#1F2736';

test('every league tag clears WCAG AA on the card surface', () => {
  for (const league of Object.values(LEAGUES)) {
    const ink = readableOn(league.accent, CARD);
    assert.ok(contrastRatio(ink, CARD) >= 4.5, `${league.label}: ${ink} is ${contrastRatio(ink, CARD).toFixed(2)}:1`);
  }
});

test('a colour that already reads is left alone', () => {
  assert.equal(readableOn('#F472B6', CARD), '#f472b6'); // WNBA pink
});

test('a dark brand colour keeps its hue and is only lifted', () => {
  const ink = readableOn('#002d72', CARD); // MLB navy, ~1.3:1 as shipped
  assert.ok(contrastRatio('#002d72', CARD) < 2);
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(ink.slice(i, i + 2), 16));
  assert.ok(b > r && b > g, `still blue: ${ink}`);
  assert.notEqual(ink, '#ffffff');
});

test('non-hex input passes through untouched', () => {
  assert.equal(readableOn('var(--accent)', CARD), 'var(--accent)');
});
