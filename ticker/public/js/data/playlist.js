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
import { readPlaylistChannels, savePlaylistChannels } from '../state.js';
import { DEFAULTS } from '../state.js';

export function renderChannelsPanel() {
  const input = $('playlistUrl');
  if (!input) return;
  if (store.state.playlist.url && document.activeElement !== input) input.value = store.state.playlist.url;
  const n = store.playlistChannels.length;
  const when = store.state.playlist.importedAt
    ? ` · imported ${new Date(store.state.playlist.importedAt).toLocaleDateString()}`
    : '';
  setText('playlistStatus', n
    ? `${n} channels${when}${n !== store.state.playlist.count ? ' (cached)' : ''}`
    : 'No playlist imported yet.');
  setHidden('playlistClearBtn', !n && !store.state.playlist.url);
}

export async function importPlaylist() {
  const url = $('playlistUrl')?.value.trim();
  if (!url) { emit('toast', 'Paste your M3U link first'); return; }
  const btn = $('playlistImportBtn');
  if (btn) btn.disabled = true;
  setText('playlistStatus', 'Importing…');
  try {
    const res = await fetch(`/api/playlist?url=${encodeURIComponent(url)}`);
    const data = await res.json().catch(() => ({ ok: false, error: 'bad response' }));
    if (!data.ok) {
      emit('toast', `Import failed: ${data.error || 'unknown error'}`);
      return;
    }
    store.playlistChannels = data.channels || [];
    const stored = savePlaylistChannels(store.playlistChannels);
    store.state.playlist = { url, importedAt: Date.now(), count: data.count || store.playlistChannels.length };
    persist();
    emit('toast', `Imported ${data.count || store.playlistChannels.length} channels${stored ? '' : ' — storage full, kept for this session'}`);
  } catch (err) {
    emit('toast', `Import failed: ${err?.message || 'network error'}`);
  } finally {
    if (btn) btn.disabled = false;
    renderChannelsPanel();
  }
}

export function clearPlaylist() {
  store.playlistChannels = [];
  savePlaylistChannels([]);
  store.state.playlist = { ...DEFAULTS.playlist };
  store.state.preferredChannels = {};
  persist();
  renderChannelsPanel();
  emit('toast', 'Playlist removed');
}

/** Channels from the user's own playlist that carry this game. */
export function matchesFor(ev) {
  if (!store.playlistChannels.length) return [];
  return matchChannels(ev, store.playlistChannels, {
    limit: 6,
    preferred: store.state.preferredChannels,
  });
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
