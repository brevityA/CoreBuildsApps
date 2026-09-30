/**
 * Runs a playlist or guide import in the Android shell and waits for it.
 *
 * The download and parse happen natively (Importer.kt) on a worker thread;
 * the page starts a job, polls its status, and reads the result file back.
 * One job at a time: the shell refuses a second start while one runs.
 */

import { nativeBridge } from '../core/bridge.js';

const POLL_MS = 750;
// Past the native job's own ceiling (Importer.kt: 3 min budget + one
// 150 s download), so the page never gives up on a job that is still going.
const MAX_WAIT_MS = 7 * 60_000;

export function canImportNatively() {
  const b = nativeBridge();
  return Boolean(b?.startPlaylistImport && b?.startGuideImport && b?.importStatus);
}

/**
 * @param {() => boolean} start  kicks off the job; false when refused
 * @returns {Promise<{state: string, message: string, count: number}>}
 */
export async function runNativeJob(start) {
  const b = nativeBridge();
  let started = false;
  try { started = Boolean(start(b)); } catch { started = false; }
  if (!started) {
    const s = readStatus(b);
    // Refused because another import is running, or because the link was
    // rejected up front (the shell has already recorded why).
    if (s.state === 'running') return { state: 'busy', message: 'Another import is still running.', count: 0 };
    return { state: 'error', message: s.message || 'Couldn\'t start the import.', count: 0 };
  }
  const deadline = Date.now() + MAX_WAIT_MS;
  while (Date.now() < deadline) {
    await new Promise((r) => setTimeout(r, POLL_MS));
    const s = readStatus(b);
    if (s.state !== 'running') return s;
  }
  return { state: 'error', message: 'The import took too long.', count: 0 };
}

function readStatus(b) {
  try {
    const s = JSON.parse(b?.importStatus?.() || '{}');
    return { state: String(s.state || 'idle'), message: String(s.message || ''), count: Number(s.count) || 0 };
  } catch {
    return { state: 'error', message: '', count: 0 };
  }
}

/** JSON the shell wrote, or null. Never throws. */
export function readNativeJson(which) {
  const b = nativeBridge();
  try {
    const text = which === 'guide' ? b?.readGuide?.() : b?.readPlaylist?.();
    return text ? JSON.parse(text) : null;
  } catch {
    return null;
  }
}
