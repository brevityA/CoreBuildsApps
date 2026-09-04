/**
 * In-app update checks (sideloaded, no Play Store).
 * The Android shell downloads + installs; this module only compares versions
 * and shapes the update payload. Pure logic — unit tested.
 */

/**
 * Split a version string into numeric core and optional prerelease parts.
 * "1.2.0-rc1" → { core: "1.2.0", pre: "rc1" }
 */
function splitPrerelease(version) {
  const m = String(version || '').trim().match(/^(\d+(?:\.\d+)*)(?:-(.+))?$/);
  if (!m) return { core: '0', pre: null };
  return { core: m[1], pre: m[2] || null };
}

/**
 * Compare two dotted semver version strings (e.g. "1.2.0" vs "1.10.3",
 * "1.2.0-rc1" vs "1.2.0"). Prereleases sort BELOW their stable release
 * per semver precedence. Returns -1 / 0 / 1.
 */
export function compareVersions(a, b) {
  const va = splitPrerelease(a);
  const vb = splitPrerelease(b);
  const pa = va.core.split('.');
  const pb = vb.core.split('.');
  const len = Math.max(pa.length, pb.length);
  for (let i = 0; i < len; i++) {
    const x = pa[i] ?? '0';
    const y = pb[i] ?? '0';
    const nx = Number(x);
    const ny = Number(y);
    if (!Number.isNaN(nx) && !Number.isNaN(ny) && nx !== ny) {
      return nx < ny ? -1 : 1;
    }
    if (x !== y) return x < y ? -1 : 1;
  }
  // Same numeric core — prerelease sorts below stable
  if (va.pre === null && vb.pre === null) return 0;
  if (va.pre === null) return 1;  // stable > prerelease
  if (vb.pre === null) return -1; // prerelease < stable
  // Both have prereleases — compare lexically/numerically by dot-separated identifiers
  const ida = va.pre.split('.');
  const idb = vb.pre.split('.');
  const len2 = Math.max(ida.length, idb.length);
  for (let i = 0; i < len2; i++) {
    if (i >= ida.length) return -1; // fewer fields = lower precedence
    if (i >= idb.length) return 1;
    const x = ida[i], y = idb[i];
    const nx = Number(x), ny = Number(y);
    if (!Number.isNaN(nx) && !Number.isNaN(ny)) {
      if (nx !== ny) return nx < ny ? -1 : 1;
    } else if (!Number.isNaN(nx)) {
      return -1; // numeric < string per semver
    } else if (!Number.isNaN(ny)) {
      return 1;
    } else {
      if (x !== y) return x < y ? -1 : 1;
    }
  }
  return 0;
}

/** Strip the "coreline-v" prefix from a release tag → "1.2.0" (or null). */
export function versionFromCorelineTag(tag) {
  const m = String(tag || '').trim().match(/^coreline-v(\d+\.\d+\.\d+.*)$/i);
  return m ? m[1] : null;
}

/**
 * Pick the newest coreline-v release from a GitHub "releases" JSON array
 * (as returned by https://api.github.com/repos/OWNER/REPO/releases).
 * Returns { tag, version, apkUrl, notes } or null when there is none.
 */
export function latestCorelineRelease(releases, apkName = 'coreline-release.apk') {
  if (!Array.isArray(releases)) return null;
  let best = null;
  for (const r of releases) {
    const version = versionFromCorelineTag(r?.tag_name);
    if (!version) continue;
    // Skip releases that lack the expected APK asset — they cannot be installed
    const apk = (r.assets || []).find((a) => a?.name === apkName);
    if (!apk?.browser_download_url) continue;
    if (!best || compareVersions(version, best.version) > 0) {
      best = {
        tag: r.tag_name,
        version,
        apkUrl: apk.browser_download_url,
        notes: r.body || '',
      };
    }
  }
  return best;
}

/**
 * Build the update status payload for the UI.
 * currentVersion is the running app's version (BuildConfig.VERSION_NAME).
 */
export function updateStatus(releases, currentVersion, apkName) {
  const current = String(currentVersion || '0').trim();
  const latest = latestCorelineRelease(releases, apkName);
  if (!latest) {
    return { ok: true, current, latest: null, newer: false, checked: true };
  }
  return {
    ok: true,
    current,
    latest: latest.version,
    tag: latest.tag,
    apkUrl: latest.apkUrl,
    notes: latest.notes,
    newer: compareVersions(latest.version, current) > 0,
    checked: true,
  };
}
