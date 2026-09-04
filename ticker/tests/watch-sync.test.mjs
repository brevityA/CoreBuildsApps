/**
 * Enforces the invariant between SPORTS_APPS in watch.mjs and the
 * <queries> block in AndroidManifest.xml. This catches drift like
 * phantom package IDs or missing manifest entries.
 */

import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { SPORTS_APPS } from '../lib/watch.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const manifestPath = resolve(__dirname, '../android/app/src/main/AndroidManifest.xml');

function getManifestPackages() {
  const manifest = readFileSync(manifestPath, 'utf-8');
  const packages = new Set();
  const regex = /<package android:name="([^"]+)"\s*\/>/g;
  let match;
  while ((match = regex.exec(manifest)) !== null) {
    packages.add(match[1]);
  }
  return packages;
}

test('watch.mjs SPORTS_APPS has no duplicate packages or labels', () => {
  const packages = new Set();
  const labels = new Set();
  
  for (const app of SPORTS_APPS) {
    assert.ok(app.pkg, `App entry missing pkg: ${JSON.stringify(app)}`);
    assert.ok(app.label, `App entry missing label: ${JSON.stringify(app)}`);
    assert.ok(typeof app.pkg === 'string', `pkg must be string: ${JSON.stringify(app)}`);
    assert.ok(typeof app.label === 'string', `label must be string: ${JSON.stringify(app)}`);
    
    // Check for duplicates
    assert.ok(!packages.has(app.pkg), `Duplicate package: ${app.pkg}`);
    assert.ok(!labels.has(app.label), `Duplicate label: ${app.label}`);
    
    packages.add(app.pkg);
    labels.add(app.label);
  }
});

test('watch.mjs SPORTS_APPS entries are well-formed', () => {
  for (const app of SPORTS_APPS) {
    // Package ID format: letters (case-insensitive), numbers, dots, underscores
    // Must start with a letter and contain at least one dot
    assert.match(app.pkg, /^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$/, 
      `Invalid package ID format: ${app.pkg}`);
    
    // Label length constraint (matches native code's .take(48))
    assert.ok(app.label.length <= 48, 
      `Label too long (${app.label.length} chars): ${app.label}`);
    assert.ok(app.label.length > 0, `Label cannot be empty`);
  }
});

test('SPORTS_APPS and AndroidManifest.xml are in sync', () => {
  const manifestPkgs = getManifestPackages();
  const sportsAppsPkgs = new Set(SPORTS_APPS.map(app => app.pkg));
  
  // Every SPORTS_APPS entry must be in the manifest
  for (const pkg of sportsAppsPkgs) {
    assert.ok(manifestPkgs.has(pkg), 
      `Package ${pkg} in SPORTS_APPS but not in AndroidManifest.xml <queries>`);
  }
  
  // Every manifest <queries> package must be in SPORTS_APPS
  for (const pkg of manifestPkgs) {
    assert.ok(sportsAppsPkgs.has(pkg), 
      `Package ${pkg} in AndroidManifest.xml <queries> but not in SPORTS_APPS`);
  }
});
