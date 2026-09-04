import test from 'node:test';
import assert from 'node:assert/strict';

import { parseFeed, parseJsonFeed, parseListing, decodeXmlEntities, MAX_ITEMS_PER_FEED } from '../lib/parser.mjs';

// AUDIT B-group: malformed/empty external data must never crash the loop.

test('parseFeed returns [] for empty and whitespace input', () => {
  assert.deepEqual(parseFeed(''), []);
  assert.deepEqual(parseFeed('   \n\t '), []);
  assert.deepEqual(parseFeed(null), []);
  assert.deepEqual(parseFeed(undefined), []);
});

test('parseFeed returns [] for garbage that is not XML/JSON', () => {
  assert.deepEqual(parseFeed('\x00\x01\x02binary\u0000garbage'), []);
  assert.deepEqual(parseFeed('definitely not a feed'), []);
});

test('parseFeed skips truncated/unclosed items without throwing', () => {
  const xml = `<rss><channel><item><title>Broken`;
  const items = parseFeed(xml);
  assert.ok(Array.isArray(items));
  assert.equal(items.length, 0);
});

test('parseFeed tolerates malformed siblings around good items', () => {
  const xml = `<rss><channel>
    <item><title>Good vs Team — ESPN</title></item>
    <item><title>unclosed
    <<<junk>>>
    <item><title>Another vs One — TSN1</title></item>
  </channel></rss>`;
  const items = parseFeed(xml);
  // The regex parser never throws on unclosed items; the well-formed item
  // must survive. (Unclosed siblings may greedily absorb later text — that
  // is acceptable garbling, not a crash.)
  assert.ok(Array.isArray(items));
  assert.ok(items.some((i) => i.rawTitle.startsWith('Good vs')));
});

test('parseFeed caps a huge feed at MAX_ITEMS_PER_FEED', () => {
  const items = Array.from({ length: 300 }, (_, i) => `<item><title>Game ${i} vs Team ${i}</title></item>`).join('');
  const xml = `<rss><channel>${items}</channel></rss>`;
  const parsed = parseFeed(xml);
  assert.equal(parsed.length, MAX_ITEMS_PER_FEED);
});

test('parseJsonFeed returns [] for invalid JSON', () => {
  assert.deepEqual(parseJsonFeed('{ nope'), []);
  assert.deepEqual(parseJsonFeed(''), []);
});

test('parseJsonFeed skips non-object rows without throwing', () => {
  const json = JSON.stringify([null, 42, 'string row', { title: 'Real vs Fake' }]);
  const parsed = parseJsonFeed(json);
  assert.equal(parsed.length, 2); // the string row + the object row
  assert.ok(parsed.some((e) => e.rawTitle === 'Real vs Fake'));
});

test('decodeXmlEntities handles weird but legal numeric entities', () => {
  assert.equal(decodeXmlEntities('&#65;&#66;&#67;'), 'ABC');
  assert.equal(decodeXmlEntities('&#x41;&#x42;'), 'AB');
  // Out-of-range code units degrade gracefully instead of throwing.
  assert.doesNotThrow(() => decodeXmlEntities('&#99999999;'));
});

test('CDATA content is extracted and not treated as a tag', () => {
  const xml = `<rss><channel><item><title><![CDATA[Leafs <vs> Habs]]></title></item></channel></rss>`;
  const items = parseFeed(xml);
  assert.equal(items.length, 1);
  assert.ok(items[0].rawTitle.includes('Leafs'));
  assert.ok(items[0].rawTitle.includes('Habs'));
});

test('parseListing never throws on adversarial titles', () => {
  assert.doesNotThrow(() => parseListing('<script>alert(1)</script>'));
  assert.doesNotThrow(() => parseListing('a'.repeat(5000)));
  const e = parseListing('<script>alert(1)</script> vs <img src=x onerror=alert(1)>');
  // Both tags are stripped; the matchup collapses to empty team names and
  // yields null teams — no throw, a sane event object back.
  assert.ok(e && typeof e.league === 'string');
});
