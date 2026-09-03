import test from 'node:test';
import assert from 'node:assert/strict';

import { parseFeed, parseJsonFeed, stripTags, MAX_ITEMS_PER_FEED } from '../lib/parser.mjs';

test('MAX_ITEMS_PER_FEED is 100', () => {
  assert.equal(MAX_ITEMS_PER_FEED, 100);
});

test('parseFeed caps items at MAX_ITEMS_PER_FEED', () => {
  const items = Array.from({ length: 150 }, (_, i) =>
    `<item><title>Event ${i}</title><description>Team A vs Team B espn</description></item>`
  ).join('');
  const xml = `<rss><channel>${items}</channel></rss>`;
  const result = parseFeed(xml, { source: 'rss', label: 'Test' });
  assert.ok(result.length <= MAX_ITEMS_PER_FEED);
});

test('parseJsonFeed caps items at MAX_ITEMS_PER_FEED', () => {
  const items = Array.from({ length: 150 }, (_, i) => ({ title: `Event ${i}` }));
  const json = JSON.stringify({ version: 'https://jsonfeed.org/version/1.1', items });
  const result = parseJsonFeed(json, { source: 'rss', label: 'Test' });
  assert.ok(result.length <= MAX_ITEMS_PER_FEED);
});

test('parseJsonFeed skips null, boolean, and number rows but keeps strings and objects', () => {
  const json = JSON.stringify({
    version: 'https://jsonfeed.org/version/1.1',
    items: [null, true, 42, { title: 'Real event' }, false, 'text entry'],
  });
  const result = parseJsonFeed(json, { source: 'rss', label: 'Test' });
  assert.equal(result.length, 2);
});

test('stripTags handles CDATA before stripping HTML', () => {
  const input = '<![CDATA[<b>Bold</b> text]]>';
  assert.equal(stripTags(input), 'Bold text');
});

test('stripTags handles nested CDATA safely', () => {
  assert.equal(stripTags('<![CDATA[plain]]>'), 'plain');
  assert.equal(stripTags('<![CDATA[a <em>b</em> c]]>'), 'a b c');
});

test('parseFeed with empty string returns empty array', () => {
  assert.deepEqual(parseFeed('', {}), []);
});

test('parseFeed with garbage XML returns empty array', () => {
  assert.deepEqual(parseFeed('not xml at all {}[]', {}), []);
});

test('parseFeed with valid XML but no items returns empty array', () => {
  const xml = '<rss><channel><title>Empty</title></channel></rss>';
  assert.deepEqual(parseFeed(xml, {}), []);
});

test('parseJsonFeed with empty items returns empty array', () => {
  const json = JSON.stringify({ version: 'https://jsonfeed.org/version/1.1', items: [] });
  assert.deepEqual(parseJsonFeed(json, {}), []);
});
