/**
 * Update checking and install handoff.
 *
 * The web build can only tell you an update exists; installing means the
 * Android app, which downloads the APK and hands it to the system installer.
 * So the update panel reports what it can and stays honest about the rest.
 */

import { updateStatusFromManifest } from '/lib/version.mjs';
import { isNativeShell } from '/lib/client-slate.mjs';
import { emit } from '../core/bus.js';
import { $, esc } from '../core/dom.js';
import { nativeBridge } from '../core/bridge.js';

const UPDATE_MANIFEST_URL =
  'https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Latestrelease/coreline-version.json';

let updateInfo = null;
let updateChecking = false;

export function getUpdateInfo() {
  return updateInfo;
}

export function isChecking() {
  return updateChecking;
}

function currentVersion() {
  try {
    const v = nativeBridge()?.getVersion?.();
    return typeof v === 'string' && v ? v : null;
  } catch {
    return null;
  }
}

/** GitHub release bodies arrive as markdown; flatten to plain panel text. */
export function cleanNotes(body) {
  return String(body || '')
    .replace(/^#+\s*/gm, '')
    .replace(/^\s*[-*]\s+/gm, '· ')
    .replace(/[*_`~]/g, '')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/\s{2,}/g, ' ')
    .trim();
}

export function renderUpdates() {
  const el = $('updatePanel');
  if (!el) return;
  const native = isNativeShell();
  const current = currentVersion();
  const rows = [];

  if (current) {
    rows.push(`<div class="update-row"><span class="update-label">Current version</span><span class="update-value">${esc(current)}</span></div>`);
  } else {
    rows.push(`<div class="update-row"><span class="update-label">Build</span><span class="update-value">${native ? 'TV app' : 'Web build'}</span></div>`);
  }

  if (updateChecking) {
    rows.push('<div class="update-note is-checking">Checking for updates…</div>');
  } else if (updateInfo && updateInfo.ok) {
    if (updateInfo.newer) {
      rows.push(`<div class="update-banner">
        <div class="update-banner__title">Update ${esc(updateInfo.latest)} is available</div>
        ${updateInfo.notes ? `<div class="update-notes">${esc(cleanNotes(updateInfo.notes).slice(0, 300))}</div>` : ''}
      </div>`);
      if (native) {
        rows.push(`<button class="btn focusable" data-action="install-update">Download &amp; install ${esc(updateInfo.latest)}</button>`);
      } else {
        rows.push('<div class="update-note">Updates install on the Android TV app (Settings → Updates).</div>');
      }
    } else {
      rows.push(`<div class="update-note is-ok">You’re up to date${updateInfo.latest ? ` (latest ${esc(updateInfo.latest)})` : ''}.</div>`);
    }
  } else if (updateInfo && !updateInfo.ok) {
    rows.push(`<div class="update-note is-error">${esc(updateInfo.error || 'Update check failed.')}</div>`);
  } else {
    rows.push('<div class="update-note">Not checked yet.</div>');
  }

  rows.push(`<button class="btn--ghost focusable" data-action="check-updates" ${updateChecking ? 'disabled' : ''}>Check for updates</button>`);
  el.innerHTML = rows.join('');
}

export async function checkForUpdates(manual = false) {
  if (updateChecking) return;
  updateChecking = true;
  renderUpdates();
  try {
    const signal = typeof AbortSignal?.timeout === 'function'
      ? AbortSignal.timeout(20000)
      : (() => { const ac = new AbortController(); setTimeout(() => ac.abort(), 20000); return ac.signal; })();
    const manifestUrl = isNativeShell()
      ? `/api/proxy?url=${encodeURIComponent(UPDATE_MANIFEST_URL)}`
      : UPDATE_MANIFEST_URL;
    const res = await fetch(manifestUrl, { headers: { Accept: 'application/json' }, signal });
    if (!res.ok) throw new Error('http ' + res.status);
    const manifest = await res.json();
    const currentCode = nativeBridge()?.getVersionCode
      ? Number(nativeBridge().getVersionCode())
      : Number(manifest?.versionCode || 0);
    updateInfo = updateStatusFromManifest(manifest, currentCode, currentVersion() || '0');
  } catch {
    updateInfo = { ok: false, error: 'Could not reach the update server' };
  }
  updateChecking = false;
  renderUpdates();
  if (manual) {
    emit('toast', updateInfo?.newer ? 'Update available' : updateInfo?.ok ? 'You are up to date' : 'Update check failed');
  }
}

export function installUpdateFlow() {
  const url = updateInfo?.apkUrl;
  if (!url) return;
  const bridge = nativeBridge();
  if (bridge?.installUpdateVerified) {
    try {
      if (bridge.installUpdateVerified(url, updateInfo?.apkSha256 || '', Number(updateInfo?.latestCode || 0))) {
        emit('toast', 'Downloading verified update — the installer opens when ready');
        return;
      }
    } catch { /* fall back to legacy/native */ }
  }
  if (bridge?.installUpdate) {
    try {
      if (bridge.installUpdate(url)) {
        emit('toast', 'Downloading update — the installer opens when ready');
        return;
      }
    } catch { /* fall back to browser */ }
  }
  if (bridge?.openUrl) {
    try {
      bridge.openUrl(`https://github.com/brevityA/CoreBuildsApps/releases/tag/${encodeURIComponent(updateInfo.tag || 'coreline-v' + updateInfo.latest)}`);
      return;
    } catch { /* ignore */ }
  }
  emit('toast', 'Could not start the update');
}
