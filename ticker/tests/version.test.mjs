/**
 * lib/version.mjs — semver compare + GitHub coreline-v release parsing.
 */
import test from 'node:test';
import assert from 'node:assert/strict';

import {
  compareVersions, versionFromCorelineTag, latestCorelineRelease, updateStatus,
} from '../lib/version.mjs';

test('compareVersions orders dotted versions numerically', () => {
  assert.equal(compareVersions('1.2.0', '1.10.3'), -1);
  assert.equal(compareVersions('1.10.3', '1.2.0'), 1);
  assert.equal(compareVersions('1.2.0', '1.2.0'), 0);
  assert.equal(compareVersions('1.2', '1.2.0'), 0);
  assert.equal(compareVersions('2.0.0', '1.99.99'), 1);
});

test('versionFromCorelineTag strips the prefix, rejects others', () => {
  assert.equal(versionFromCorelineTag('coreline-v1.1.0'), '1.1.0');
  assert.equal(versionFromCorelineTag('coreline-v1.2.0-rc1'), '1.2.0-rc1');
  assert.equal(versionFromCorelineTag('v1.8.0'), null); // iconpack tags
  assert.equal(versionFromCorelineTag('doctor-v0.1.0'), null);
});

test('latestCorelineRelease picks the newest coreline tag and its apk asset', () => {
  const releases = [
    { tag_name: 'v1.8.0', assets: [{ name: 'app-release.apk', browser_download_url: 'https://x/app.apk' }] },
    { tag_name: 'coreline-v1.0.2', assets: [{ name: 'coreline-release.apk', browser_download_url: 'https://x/old.apk' }] },
    { tag_name: 'coreline-v1.2.0', assets: [{ name: 'coreline-release.apk', browser_download_url: 'https://x/new.apk' }] },
    { tag_name: 'coreline-v1.1.0', assets: [{ name: 'coreline-release.apk', browser_download_url: 'https://x/mid.apk' }] },
  ];
  const best = latestCorelineRelease(releases);
  assert.equal(best.version, '1.2.0');
  assert.equal(best.apkUrl, 'https://x/new.apk');
});

test('latestCorelineRelease returns null without a matching asset', () => {
  const releases = [{ tag_name: 'coreline-v1.2.0', assets: [] }];
  const best = latestCorelineRelease(releases);
  assert.equal(best.apkUrl, null);
  assert.equal(best.version, '1.2.0');
});

test('updateStatus flags newer and survives empty/absent releases', () => {
  const rels = [{ tag_name: 'coreline-v1.2.0', assets: [{ name: 'coreline-release.apk', browser_download_url: 'https://x/a.apk' }] }];
  assert.equal(updateStatus(rels, '1.1.0').newer, true);
  assert.equal(updateStatus(rels, '1.2.0').newer, false);
  assert.equal(updateStatus(rels, '1.3.0').newer, false);
  const none = updateStatus(null, '1.1.0');
  assert.equal(none.ok, true);
  assert.equal(none.newer, false);
  assert.equal(none.latest, null);
});
