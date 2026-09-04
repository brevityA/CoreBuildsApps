/**
 * In-app update checks (sideloaded, no Play Store).
 * The Android shell downloads + installs; this module only compares versions
 * and shapes the update payload. Pure logic — unit tested.
 */

/**
 * Compare two dotted semver-ish version strings (e.g. "1.2.0" vs "1.10.3").
 * Returns -1 / 0 / 1. Non-numeric segments compare as strings.
 */
export function compareVersions(a, b) {
  const pa = String(a || '').trim().split('.');
  const pb = String(b || '').trim().split('.');
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
    if (!best || compareVersions(version, best.version) > 0) {
      const apk = (r.assets || []).find((a) => a?.name === apkName);
      best = {
        tag: r.tag_name,
        version,
        apkUrl: apk?.browser_download_url || null,
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
