/**
 * The one mutable object.
 *
 * Everything the UI reads lives here: persisted settings, the current slate,
 * the imported playlist, the installed-app list, and the active search query.
 * Splitting these across modules would mean passing four arguments to every
 * renderer; keeping them in one place means a renderer takes no arguments and
 * reads what it needs.
 *
 * `state` is persisted; the rest is session-scoped or re-derived on boot.
 */

import { loadState, saveState, readPlaylistChannels } from '../state.js';

export const store = {
  /** Persisted settings (see state.js DEFAULTS). */
  state: loadState(),
  /** Normalised events for the current slate. */
  events: [],
  /** Imported IPTV channels (also cached under their own storage key). */
  playlistChannels: readPlaylistChannels(),
  /** Installed, launchable apps from the TV shell. */
  installedApps: [],
  /** Active search query. Session-only: it is not a setting. */
  query: '',
};

export function persist() {
  saveState(store.state);
}
