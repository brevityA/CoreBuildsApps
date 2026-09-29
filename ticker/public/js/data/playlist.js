/**
 * IPTV playlist import and channel handoff.
 *
 * Core Line matches a game to the channels the viewer actually receives and
 * then gets out of the way: it opens the stream in the player app and plays
 * nothing itself. That boundary is the product, so it is stated in the UI too.
 */

import { matchChannels } from '/lib/playlist.mjs';
import { store, persist } from '../core/store.js';
import { emit } from '../core/bus.js';
import { $, esc, setHidden, setText } from '../core/dom.js';
import { nativeBridge } from '../core/bridge.js';
import { readPlaylistChannels, savePlaylistChannels, cleanChannel, DEFAULTS } from '../state.js';
import { maskUrl } from '/lib/guide.mjs';
import { canImportNatively, runNativeJob, readNativeJson } from './native-import.js';
import { notePlaylistGuideUrls, remergeGuide, dropGuide, renderGuidePanel } from './guide.js';

export function renderChannelsPanel() {
  const input = $('playlistUrl');
  if (!input) return;
  // The link carries the provider account: never put it back on screen.
  // Show its host, and let an empty field mean "re-import the saved link".
  const saved = store.state.playlist.url;
  input.placeholder = saved ? `${maskUrl(saved)} — paste a new link to replace` : 'https://provider.example/get.php?…m3u_plus';
  const n = store.playlistChannels.length;
  const when = store.state.playlist.importedAt
    ? ` · imported ${new Date(store.state.playlist.importedAt).toLocaleDateString()}`
    : '';
  setText('playlistStatus', n
    ? `${n} channels${when}${n !== store.state.playlist.count ? ' (cached)' : ''}`
    : 'No playlist imported yet.');
  setHidden('playlistClearBtn', !n && !saved);
  renderGuidePanel();
}

let importing = false;

export async function importPlaylist() {
  const typed = $('playlistUrl')?.value.trim();
  const url = typed || store.state.playlist.url;
  if (!url) { emit('toast', 'Paste your M3U link first'); return; }
  if (importing) return;
  importing = true;
  const btn = $('playlistImportBtn');
  if (btn) btn.disabled = true;
  setText('playlistStatus', 'Importing…');
  try {
    const data = canImportNatively() ? await importNative(url) : await importViaServer(url);
    if (!data.ok) {
      emit('toast', `Import failed: ${data.error || 'unknown error'}`);
      return;
    }
    store.playlistChannels = (data.channels || []).map(cleanChannel);
    const stored = savePlaylistChannels(store.playlistChannels);
    const count = data.count || store.playlistChannels.length;
    store.state.playlist = { url, importedAt: Date.now(), count };
    persist();
    const input = $('playlistUrl');
    if (input) input.value = '';
    const vod = data.vodSkipped ? ` (skipped ${data.vodSkipped} movies/series)` : '';
    emit('toast', `Imported ${count} channels${vod}${stored ? '' : ' — storage full, kept for this session'}`);
    notePlaylistGuideUrls(data.guideUrls);
    remergeGuide();
  } catch {
    // Never echo the error: a fetch failure can quote the link.
    emit('toast', 'Import failed: network error');
  } finally {
    importing = false;
    if (btn) btn.disabled = false;
    renderChannelsPanel();
  }
}

async function importViaServer(url) {
  const res = await fetch(`/api/playlist?url=${encodeURIComponent(url)}`);
  return res.json().catch(() => ({ ok: false, error: 'bad response' }));
}

/** On the TV: the shell downloads and parses it (Importer.kt); /api/playlist does not exist there. */
async function importNative(url) {
  const res = await runNativeJob((b) => b.startPlaylistImport(url));
  if (res.state !== 'done') return { ok: false, error: res.message || 'the import did not finish' };
  const data = readNativeJson('playlist');
  if (!data?.ok || !Array.isArray(data.channels)) return { ok: false, error: 'the playlist could not be read back' };
  return data;
}

/** A playlist imported natively that this page's storage lost (quota): take the shell's copy. */
export function hydrateNativePlaylist() {
  if (store.playlistChannels.length || !canImportNatively()) return;
  const data = readNativeJson('playlist');
  if (data?.ok && Array.isArray(data.channels) && data.channels.length) {
    store.playlistChannels = data.channels.map(cleanChannel).slice(0, 4000);
  }
}

export function clearPlaylist() {
  store.playlistChannels = [];
  savePlaylistChannels([]);
  store.state.playlist = { ...DEFAULTS.playlist };
  store.state.preferredChannels = {};
  persist();
  try { nativeBridge()?.clearImports?.(); } catch { /* shell without imports */ }
  dropGuide();
  renderChannelsPanel();
  emit('toast', 'Playlist removed');
}

/** Channels from the user's own playlist that carry this game. */
export function matchesFor(ev) {
  if (!store.playlistChannels.length) return [];
  // The guide's answer first: those channels are listed as airing this very
  // game, where a name match is only a network that usually carries it.
  const guided = (ev.guideChannels || []).slice(0, 6).map((c) => ({
    name: c.name, url: c.url, group: c.group || '', reason: 'guide', bug: '', preferred: false,
  }));
  const seen = new Set(guided.map((m) => m.url));
  const named = matchChannels(ev, store.playlistChannels, {
    limit: 6,
    preferred: store.state.preferredChannels,
  }).filter((m) => !seen.has(m.url));
  return [...guided, ...named].slice(0, 8);
}

/** Open a matched channel in an external player; remember the choice. */
export function openMatchedChannel(match) {
  if (!match) return;
  if (match.bug) {
    store.state.preferredChannels[match.bug] = match.name;
    persist();
  }
  const bridge = nativeBridge();
  if (bridge?.openStream) {
    try {
      if (bridge.openStream(match.url)) {
        emit('toast', 'Opening in player…');
        return;
      }
      emit('toast', 'No player found — try TiviMate or VLC');
    } catch { /* fall through to web */ }
  }
  try {
    window.open(match.url, '_blank', 'noopener');
    emit('toast', 'Opening stream link');
  } catch {
    emit('toast', 'Could not open stream');
  }
}

export { readPlaylistChannels };
export function channelLabel(count) {
  return esc(String(count));
}
