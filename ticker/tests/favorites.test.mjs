import test from 'node:test';
import assert from 'node:assert/strict';
import { matchesFavorite } from '../lib/favorites.mjs';

test('favorites match an abbreviation exactly, or a whole word in the name', () => {
  const chiefs = { away: { abbr: 'KC', name: 'Kansas City Chiefs' }, home: { abbr: 'BUF', name: 'Buffalo Bills' } };
  const yankees = { away: { abbr: 'NYY', name: 'New York Yankees' }, home: { abbr: 'BOS', name: 'Boston Red Sox' } };
  const hill = { away: { abbr: 'RIVE', name: 'Riverside' }, home: { abbr: 'OAK', name: 'Oak Hill' } };
  assert.equal(matchesFavorite(chiefs, ['CHI']), false);
  assert.equal(matchesFavorite(chiefs, ['KC']), true);
  assert.equal(matchesFavorite(chiefs, 'chiefs'), true);
  assert.equal(matchesFavorite(yankees, ['NY']), false);
  assert.equal(matchesFavorite(yankees, ['NYY']), true);
  assert.equal(matchesFavorite(hill, ['OAK']), true);
  assert.equal(matchesFavorite(hill, ['HILL']), true);
  assert.equal(matchesFavorite(hill, []), false);
});
